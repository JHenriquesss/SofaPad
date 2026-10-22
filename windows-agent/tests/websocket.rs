use sofapad_agent::{
    input::{InputController, InputError, SimulatedInput},
    protocol::Command,
    server::{bind_loopback, serve_connection},
};
use std::{
    net::{Ipv4Addr, SocketAddr, TcpStream},
    sync::{Arc, Mutex},
    thread,
    time::Duration,
};
use tungstenite::{Message, client::IntoClientRequest};

struct SharedInput(Arc<Mutex<SimulatedInput>>);
impl InputController for SharedInput {
    fn apply(&mut self, command: &Command) -> Result<(), InputError> {
        self.0.lock().unwrap().apply(command)
    }
    fn release_all(&mut self) -> Result<(), InputError> {
        self.0.lock().unwrap().release_all()
    }
}

fn start() -> (String, Arc<Mutex<SimulatedInput>>, thread::JoinHandle<()>) {
    let listener = bind_loopback(SocketAddr::from((Ipv4Addr::LOCALHOST, 0))).unwrap();
    let url = format!("ws://{}/v1/simulate", listener.local_addr().unwrap());
    let input = Arc::new(Mutex::new(SimulatedInput::default()));
    let shared = input.clone();
    let worker = thread::spawn(move || {
        serve_connection(listener.accept().unwrap().0, SharedInput(shared));
    });
    (url, input, worker)
}

#[test]
fn refuses_non_loopback_bind() {
    assert!(bind_loopback("0.0.0.0:0".parse().unwrap()).is_err());
    assert!(bind_loopback("192.168.1.10:0".parse().unwrap()).is_err());
}

#[test]
fn real_websocket_simulates_commands_and_releases_on_disconnect() {
    let (url, input, worker) = start();
    let (mut client, _) = tungstenite::connect(url.as_str()).unwrap();
    let hello: serde_json::Value =
        serde_json::from_str(client.read().unwrap().to_text().unwrap()).unwrap();
    assert_eq!(hello["mode"], "simulation");
    assert_eq!(hello["authorized"], false);
    client
        .send(Message::text(
            r#"{"v":1,"seq":1,"type":"mouse_button","button":"left","state":"down"}"#,
        ))
        .unwrap();
    let ack: serde_json::Value =
        serde_json::from_str(client.read().unwrap().to_text().unwrap()).unwrap();
    assert_eq!(ack["seq"], 1);
    assert!(input.lock().unwrap().left_down);
    client.close(None).unwrap();
    let _ = client.read();
    drop(client);
    worker.join().unwrap();
    assert!(!input.lock().unwrap().left_down);
}

#[test]
fn malformed_input_closes_connection_without_echoing_private_content() {
    let (url, input, worker) = start();
    let (mut client, _) = tungstenite::connect(url.as_str()).unwrap();
    client.read().unwrap();
    client
        .send(Message::text(
            r#"{"v":1,"seq":1,"type":"shell","command":"private-content"}"#,
        ))
        .unwrap();
    let response = client.read().unwrap().into_text().unwrap();
    assert!(!response.contains("private-content"));
    assert!(response.contains("INVALID_MESSAGE"));
    drop(client);
    worker.join().unwrap();
    assert_eq!(input.lock().unwrap().applied, 0);
}

#[test]
fn rejects_browser_origins() {
    let (url, _, worker) = start();
    let mut request = url.into_client_request().unwrap();
    request
        .headers_mut()
        .insert("origin", "https://example.com".parse().unwrap());
    assert!(tungstenite::connect(request).is_err());
    worker.join().unwrap();
}

#[test]
fn idle_timeout_releases_drag() {
    let (url, input, worker) = start();
    let (mut client, _) = tungstenite::connect(url.as_str()).unwrap();
    client.read().unwrap();
    client
        .send(Message::text(
            r#"{"v":1,"seq":1,"type":"mouse_button","button":"left","state":"down"}"#,
        ))
        .unwrap();
    client.read().unwrap();
    worker.join().unwrap();
    assert!(!input.lock().unwrap().left_down);
}

#[test]
fn oversized_frame_is_rejected_before_dispatch() {
    let (url, input, worker) = start();
    let (mut client, _) = tungstenite::connect(url.as_str()).unwrap();
    client.read().unwrap();
    client.send(Message::text("x".repeat(8193))).unwrap();
    drop(client);
    worker.join().unwrap();
    assert_eq!(input.lock().unwrap().applied, 0);
}

#[test]
fn incomplete_handshake_times_out() {
    let (url, input, worker) = start();
    let address = url
        .trim_start_matches("ws://")
        .trim_end_matches("/v1/simulate");
    let stream = TcpStream::connect(address).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .unwrap();
    worker.join().unwrap();
    assert_eq!(input.lock().unwrap().applied, 0);
}
