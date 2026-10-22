use rustls::{
    ClientConfig, ClientConnection, RootCertStore, StreamOwned, pki_types::CertificateDer,
};
use sofapad_agent::{
    input::{InputController, InputError, SimulatedInput},
    protocol::Command,
    secure::{Access, Identity, bind_private, serve_tls},
};
use std::{
    net::{IpAddr, Ipv4Addr, SocketAddr, TcpStream},
    sync::{Arc, Mutex},
    thread,
    time::Duration,
};
use tungstenite::{Message, client::IntoClientRequest};

struct Shared(Arc<Mutex<SimulatedInput>>);
impl InputController for Shared {
    fn apply(&mut self, command: &Command) -> Result<(), InputError> {
        self.0.lock().unwrap().apply(command)
    }
    fn release_all(&mut self) -> Result<(), InputError> {
        self.0.lock().unwrap().release_all()
    }
}

#[test]
fn wss_authenticates_before_applying_input_and_releases_on_disconnect() {
    let identity = Identity::generate(IpAddr::V4(Ipv4Addr::LOCALHOST)).unwrap();
    let access = Access::generate().unwrap();
    let listener = bind_private(SocketAddr::from((Ipv4Addr::LOCALHOST, 0))).unwrap();
    let address = listener.local_addr().unwrap();
    let input = Arc::new(Mutex::new(SimulatedInput::default()));
    let shared = input.clone();
    let secret = access.code_for_local_display().to_string();
    let certificate = identity.certificate.clone();
    let worker = thread::spawn(move || {
        serve_tls(
            listener.accept().unwrap().0,
            Shared(shared),
            identity.config,
            &access,
        );
    });
    let mut roots = RootCertStore::empty();
    roots.add(CertificateDer::from(certificate)).unwrap();
    let config = ClientConfig::builder()
        .with_root_certificates(roots)
        .with_no_client_auth();
    let connection =
        ClientConnection::new(Arc::new(config), IpAddr::V4(Ipv4Addr::LOCALHOST).into()).unwrap();
    let stream = TcpStream::connect(address).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .unwrap();
    let mut request = format!("wss://{address}/v1/control")
        .into_client_request()
        .unwrap();
    request
        .headers_mut()
        .insert("authorization", format!("Bearer {secret}").parse().unwrap());
    let (mut socket, _) =
        tungstenite::client(request, StreamOwned::new(connection, stream)).unwrap();
    let hello: serde_json::Value =
        serde_json::from_str(socket.read().unwrap().to_text().unwrap()).unwrap();
    assert_eq!(hello["authorized"], true);
    assert_eq!(hello["mode"], "control");
    socket
        .send(Message::text(
            r#"{"v":1,"type":"mouse_button","seq":1,"button":"left","state":"down"}"#,
        ))
        .unwrap();
    socket.read().unwrap();
    assert!(input.lock().unwrap().left_down);
    socket.close(None).unwrap();
    let _ = socket.read();
    drop(socket);
    worker.join().unwrap();
    assert!(!input.lock().unwrap().left_down);
}

#[test]
fn missing_authorization_never_reaches_the_controller() {
    let identity = Identity::generate(IpAddr::V4(Ipv4Addr::LOCALHOST)).unwrap();
    let access = Access::generate().unwrap();
    let listener = bind_private(SocketAddr::from((Ipv4Addr::LOCALHOST, 0))).unwrap();
    let address = listener.local_addr().unwrap();
    let input = Arc::new(Mutex::new(SimulatedInput::default()));
    let shared = input.clone();
    let certificate = identity.certificate.clone();
    let worker = thread::spawn(move || {
        serve_tls(
            listener.accept().unwrap().0,
            Shared(shared),
            identity.config,
            &access,
        );
    });
    let mut roots = RootCertStore::empty();
    roots.add(CertificateDer::from(certificate)).unwrap();
    let config = ClientConfig::builder()
        .with_root_certificates(roots)
        .with_no_client_auth();
    let connection =
        ClientConnection::new(Arc::new(config), IpAddr::V4(Ipv4Addr::LOCALHOST).into()).unwrap();
    let stream = TcpStream::connect(address).unwrap();
    assert!(
        tungstenite::client(
            format!("wss://{address}/v1/control"),
            StreamOwned::new(connection, stream)
        )
        .is_err()
    );
    worker.join().unwrap();
    assert_eq!(input.lock().unwrap().applied, 0);
}

#[test]
fn unknown_certificate_cannot_establish_control() {
    let identity = Identity::generate(IpAddr::V4(Ipv4Addr::LOCALHOST)).unwrap();
    let other = Identity::generate(IpAddr::V4(Ipv4Addr::LOCALHOST)).unwrap();
    let access = Access::generate().unwrap();
    let listener = bind_private(SocketAddr::from((Ipv4Addr::LOCALHOST, 0))).unwrap();
    let address = listener.local_addr().unwrap();
    let worker = thread::spawn(move || {
        serve_tls(
            listener.accept().unwrap().0,
            SimulatedInput::default(),
            identity.config,
            &access,
        );
    });
    let mut roots = RootCertStore::empty();
    roots.add(CertificateDer::from(other.certificate)).unwrap();
    let config = ClientConfig::builder()
        .with_root_certificates(roots)
        .with_no_client_auth();
    let connection =
        ClientConnection::new(Arc::new(config), IpAddr::V4(Ipv4Addr::LOCALHOST).into()).unwrap();
    let stream = TcpStream::connect(address).unwrap();
    assert!(
        tungstenite::client(
            format!("wss://{address}/v1/control"),
            StreamOwned::new(connection, stream)
        )
        .is_err()
    );
    worker.join().unwrap();
}
