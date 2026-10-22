use crate::{
    input::InputController,
    protocol::{Command, MAX_MESSAGE_BYTES},
    session::Session,
};
use serde_json::json;
use std::{
    io,
    net::{SocketAddr, TcpListener, TcpStream},
    time::{Duration, Instant},
};
use tungstenite::{
    Message, accept_hdr_with_config,
    handshake::server::{ErrorResponse, Request, Response},
    protocol::WebSocketConfig,
};

pub fn bind_loopback(address: SocketAddr) -> io::Result<TcpListener> {
    if !address.ip().is_loopback() {
        return Err(io::Error::new(
            io::ErrorKind::PermissionDenied,
            "Etapa A aceita apenas loopback",
        ));
    }
    TcpListener::bind(address)
}

pub fn serve_connection<C: InputController>(stream: TcpStream, controller: C) {
    if stream
        .set_read_timeout(Some(Duration::from_secs(3)))
        .is_err()
        || stream
            .set_write_timeout(Some(Duration::from_secs(3)))
            .is_err()
    {
        return;
    }
    let _ = stream.set_nodelay(true);
    serve_io(stream, controller, None);
}

pub fn serve_authorized<S: io::Read + io::Write, C: InputController>(
    stream: S,
    controller: C,
    access: &crate::secure::Access,
    peer: std::net::IpAddr,
) {
    serve_io(stream, controller, Some((access, peer)));
}

fn serve_io<S: io::Read + io::Write, C: InputController>(
    stream: S,
    controller: C,
    access: Option<(&crate::secure::Access, std::net::IpAddr)>,
) {
    let mut session = Session::new(controller);
    let config = WebSocketConfig::default()
        .read_buffer_size(MAX_MESSAGE_BYTES)
        .write_buffer_size(0)
        .max_write_buffer_size(MAX_MESSAGE_BYTES * 2)
        .max_message_size(Some(MAX_MESSAGE_BYTES))
        .max_frame_size(Some(MAX_MESSAGE_BYTES));
    let mut credential = String::new();
    let mut pair_permit = None;
    #[allow(
        clippy::result_large_err,
        reason = "Assinatura de callback definida por tungstenite."
    )]
    let callback = |request: &Request, response: Response| -> Result<Response, ErrorResponse> {
        if request.uri().query().is_some() || request.headers().contains_key("origin") {
            let mut denied = ErrorResponse::new(Some("Conexão recusada".into()));
            *denied.status_mut() = tungstenite::http::StatusCode::FORBIDDEN;
            return Err(denied);
        }
        // A simulação local não é uma API para páginas web.
        let endpoint = if access.is_some() {
            "/v1/control"
        } else {
            "/v1/simulate"
        };
        let header = request
            .headers()
            .get("authorization")
            .and_then(|v| v.to_str().ok());
        let pairing = request.uri().path() == "/v1/pair" && access.is_some();
        let authorized = if pairing {
            pair_permit = access.and_then(|(access, peer)| access.authorize_pair(peer, header));
            pair_permit.is_some()
        } else {
            access.is_none_or(|(access, peer)| access.authorize(peer, header))
        };
        if (!pairing && request.uri().path() != endpoint) || !authorized {
            let mut denied = ErrorResponse::new(Some("Conexão recusada".into()));
            *denied.status_mut() = tungstenite::http::StatusCode::FORBIDDEN;
            return Err(denied);
        }
        credential = header.unwrap_or("").to_owned();
        Ok(response)
    };
    let Ok(mut socket) = accept_hdr_with_config(stream, callback, Some(config)) else {
        return;
    };
    if let Some(permit) = pair_permit {
        #[derive(serde::Deserialize)]
        #[serde(deny_unknown_fields)]
        struct PairRequest {
            v: u8,
            r#type: String,
            name: String,
        }
        if let Ok(Message::Text(raw)) = socket.read()
            && let Ok(request) = serde_json::from_str::<PairRequest>(&raw)
            && request.v == 1
            && request.r#type == "pair"
            && let Some(vault) = access.and_then(|(a, _)| a.vault.as_ref())
            && let Ok(token) = vault.grant(&request.name, permit)
        {
            let response = json!({"v":1,"type":"paired","token":token});
            let _ = socket.send(Message::text(response.to_string()));
        }
        let _ = socket.close(None);
        return;
    }
    let mode = if access.is_some() {
        "control"
    } else {
        "simulation"
    };
    let hello = json!({"v":1,"type":"hello","mode":mode,"authorized":access.is_some(),"max_text_bytes":4096,"idle_timeout_ms":3000});
    if socket.send(Message::text(hello.to_string())).is_err() {
        return;
    }
    let started = Instant::now();
    while let Ok(message) = socket.read() {
        if access.is_some_and(|(access, _)| !access.valid(&credential)) {
            break;
        }
        match message {
            Message::Text(raw) => match session.handle(raw.as_str(), started.elapsed()) {
                Ok(envelope) => {
                    let kind = if matches!(envelope.command, Command::Ping) {
                        "pong"
                    } else {
                        "ack"
                    };
                    let response = json!({"v":1,"type":kind,"seq":envelope.seq,"mode":mode});
                    if socket.send(Message::text(response.to_string())).is_err() {
                        break;
                    }
                }
                Err(error) => {
                    let response = json!({"v":1,"type":"error","code":error.code()});
                    let _ = socket.send(Message::text(response.to_string()));
                    break;
                }
            },
            Message::Ping(_) | Message::Pong(_) => {
                if socket.flush().is_err() {
                    break;
                }
            }
            Message::Close(_) => break,
            _ => break,
        }
    }
    session.close();
    let _ = socket.close(None);
}
