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
    let mut session = Session::new(controller);
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
        if request.uri().path() != "/v1/simulate" || request.headers().contains_key("origin") {
            let mut denied = ErrorResponse::new(Some("Endpoint de simulação local".into()));
            *denied.status_mut() = tungstenite::http::StatusCode::FORBIDDEN;
            return Err(denied);
        }
        Ok(response)
    };
    let Ok(mut socket) = accept_hdr_with_config(stream, callback, Some(config)) else {
        return;
    };
    let hello = json!({"v":1,"type":"hello","mode":"simulation","authorized":false,"max_text_bytes":4096,"idle_timeout_ms":3000});
    if socket.send(Message::text(hello.to_string())).is_err() {
        return;
    }
    let started = Instant::now();
    while let Ok(message) = socket.read() {
        match message {
            Message::Text(raw) => match session.handle(raw.as_str(), started.elapsed()) {
                Ok(envelope) => {
                    let kind = if matches!(envelope.command, Command::Ping) {
                        "pong"
                    } else {
                        "ack"
                    };
                    let response =
                        json!({"v":1,"type":kind,"seq":envelope.seq,"mode":"simulation"});
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
