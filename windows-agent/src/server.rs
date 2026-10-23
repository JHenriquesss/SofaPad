use crate::{
    input::{InputController, InputError},
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
    serve_io(stream, controller, None, None);
}

pub fn serve_authorized<S: io::Read + io::Write, C: InputController>(
    stream: S,
    controller: C,
    access: &crate::secure::Access,
    peer: std::net::IpAddr,
) {
    serve_io(stream, controller, Some((access, peer)), None);
}

/// Como `serve_authorized`, com o socket TCP para que uma reconexão do mesmo
/// aparelho ou a revogação na bandeja possam encerrar esta sessão.
pub fn serve_authorized_with_socket<S: io::Read + io::Write, C: InputController>(
    stream: S,
    controller: C,
    access: &crate::secure::Access,
    peer: std::net::IpAddr,
    socket: Option<TcpStream>,
) {
    serve_io(stream, controller, Some((access, peer)), socket);
}

/// Só deixa passar entrada enquanto a sessão tiver a vez de controlar.
struct Gated<'a, C> {
    inner: C,
    turn: Option<(&'a crate::secure::Access, u64)>,
}

impl<C: InputController> InputController for Gated<'_, C> {
    fn apply(&mut self, command: &Command) -> Result<(), InputError> {
        match self.turn {
            Some((access, id)) => access
                .with_turn(id, || self.inner.apply(command))
                .unwrap_or(Err(InputError)),
            None => self.inner.apply(command),
        }
    }
    fn release_all(&mut self) -> Result<(), InputError> {
        match self.turn {
            // Sessão substituída não solta o que a nova sessão pressionou.
            Some((access, id)) => access
                .with_turn(id, || self.inner.release_all())
                .unwrap_or(Ok(())),
            None => self.inner.release_all(),
        }
    }
}

fn serve_io<S: io::Read + io::Write, C: InputController>(
    stream: S,
    controller: C,
    access: Option<(&crate::secure::Access, std::net::IpAddr)>,
    tcp: Option<TcpStream>,
) {
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
        // Reserva o servidor sequencial para o novo aparelho, sem consumir tentativas de autorização.
        if !pairing
            && access.is_some_and(|(a, _)| a.vault.as_ref().is_some_and(|v| v.pairing_active()))
        {
            let mut busy = ErrorResponse::new(Some("Pareamento em andamento".into()));
            *busy.status_mut() = tungstenite::http::StatusCode::CONFLICT;
            return Err(busy);
        }
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
        // Outro aparelho controlando: recusa rápida em vez de deixar a conexão pendurada.
        if !pairing && access.is_some_and(|(a, _)| a.in_use_by_other(&credential)) {
            let mut busy = ErrorResponse::new(Some("PC em uso por outro aparelho".into()));
            *busy.status_mut() = tungstenite::http::StatusCode::CONFLICT;
            return Err(busy);
        }
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
    let turn = match access {
        Some((access, _)) => match access.take_over(&credential, tcp) {
            Some(turn) => Some(turn),
            None => {
                let _ = socket.close(None);
                return;
            }
        },
        None => None,
    };
    let mut controller = Gated {
        inner: controller,
        turn: access.zip(turn.as_ref()).map(|((a, _), t)| (a, t.id())),
    };
    // Solta o que uma sessão anterior do mesmo aparelho possa ter deixado pressionado.
    let _ = controller.release_all();
    let mut session = Session::new(controller);
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
        if turn.as_ref().is_some_and(|t| !t.is_current())
            || access.is_some_and(|(access, _)| {
                !access.valid(&credential)
                    || access.vault.as_ref().is_some_and(|v| v.pairing_active())
            })
        {
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
