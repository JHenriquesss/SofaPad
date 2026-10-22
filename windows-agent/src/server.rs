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
    #[allow(
        clippy::result_large_err,
        reason = "Assinatura de callback definida por tungstenite."
    )]
    let callback = |request: &Request, response: Response| -> Result<Response, ErrorResponse> {
        // A simulação local não é uma API para páginas web.
        let endpoint = if access.is_some() {
            "/v1/control"
        } else {
            "/v1/simulate"
        };
        let authorized = access.is_none_or(|(access, peer)| {
            access.authorize(
                peer,
                request
                    .headers()
                    .get("authorization")
                    .and_then(|v| v.to_str().ok()),
            )
        });
        if request.uri().path() != endpoint
            || request.uri().query().is_some()
            || request.headers().contains_key("origin")
            || !authorized
        {
            let mut denied = ErrorResponse::new(Some("Endpoint de simulação local".into()));
            *denied.status_mut() = tungstenite::http::StatusCode::FORBIDDEN;
            return Err(denied);
        }
        Ok(response)
    };
    let Ok(mut socket) = accept_hdr_with_config(stream, callback, Some(config)) else {
        return;
    };
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
        if access.is_some_and(|(access, _)| access.expired()) {
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
