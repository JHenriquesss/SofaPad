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

fn connect_persistent(
    address: SocketAddr,
    certificate: &[u8],
    path: &str,
    secret: &str,
) -> Result<
    tungstenite::WebSocket<StreamOwned<ClientConnection, TcpStream>>,
    Box<dyn std::error::Error>,
> {
    let mut roots = RootCertStore::empty();
    roots
        .add(CertificateDer::from(certificate.to_vec()))
        .unwrap();
    let config = ClientConfig::builder()
        .with_root_certificates(roots)
        .with_no_client_auth();
    let connection =
        ClientConnection::new(Arc::new(config), "sofapad.local".try_into().unwrap()).unwrap();
    let stream = TcpStream::connect(address).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(5)))
        .unwrap();
    let mut request = format!("wss://sofapad.local:{}{path}", address.port())
        .into_client_request()
        .unwrap();
    request
        .headers_mut()
        .insert("authorization", format!("Bearer {secret}").parse().unwrap());
    tungstenite::client(request, StreamOwned::new(connection, stream))
        .map(|(socket, _)| socket)
        .map_err(|e| Box::new(e) as Box<dyn std::error::Error>)
}

#[test]
fn one_time_pairing_cannot_control_and_revocation_releases_active_drag() {
    let vault = Arc::new(
        sofapad_agent::pairing::Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| true))
            .unwrap(),
    );
    let identity = vault.identity().unwrap();
    let certificate = identity.certificate.clone();
    let code = vault.open_window().unwrap();
    let listener = bind_private("127.0.0.1:0".parse().unwrap()).unwrap();
    let address = listener.local_addr().unwrap();
    let input = Arc::new(Mutex::new(SimulatedInput::default()));
    let shared = input.clone();
    let access = Access::persistent(vault.clone());
    let worker = thread::spawn(move || {
        for _ in 0..4 {
            serve_tls(
                listener.accept().unwrap().0,
                Shared(shared.clone()),
                identity.config.clone(),
                &access,
            );
        }
    });
    assert!(connect_persistent(address, &certificate, "/v1/control", &code).is_err());
    let mut pair = connect_persistent(address, &certificate, "/v1/pair", &code).unwrap();
    pair.send(Message::text(
        r#"{"v":1,"type":"pair","name":"Celular de teste"}"#,
    ))
    .unwrap();
    let response: serde_json::Value =
        serde_json::from_str(pair.read().unwrap().to_text().unwrap()).unwrap();
    assert_eq!(response["type"], "paired");
    let token = response["token"].as_str().unwrap();
    let _ = pair.close(None);
    drop(pair);
    assert!(connect_persistent(address, &certificate, "/v1/pair", &code).is_err());
    let mut control = connect_persistent(address, &certificate, "/v1/control", token).unwrap();
    control.read().unwrap();
    control
        .send(Message::text(
            r#"{"v":1,"type":"mouse_button","seq":1,"button":"left","state":"down"}"#,
        ))
        .unwrap();
    control.read().unwrap();
    assert!(input.lock().unwrap().left_down);
    vault.revoke(&vault.devices()[0].0).unwrap();
    control
        .send(Message::text(
            r#"{"v":1,"type":"mouse_move","seq":2,"dx":10,"dy":0}"#,
        ))
        .unwrap();
    let _ = control.read();
    drop(control);
    worker.join().unwrap();
    let state = input.lock().unwrap();
    assert_eq!(state.applied, 1);
    assert!(!state.left_down);
}

#[test]
fn pairing_window_releases_old_drag_and_reserves_the_sequential_server() {
    let vault = Arc::new(
        sofapad_agent::pairing::Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| true))
            .unwrap(),
    );
    let initial_code = vault.open_window().unwrap();
    let token = vault
        .grant("Anterior", vault.consume_code(&initial_code).unwrap())
        .unwrap();
    let identity = vault.identity().unwrap();
    let certificate = identity.certificate.clone();
    let listener = bind_private("127.0.0.1:0".parse().unwrap()).unwrap();
    let address = listener.local_addr().unwrap();
    let input = Arc::new(Mutex::new(SimulatedInput::default()));
    let shared = input.clone();
    let access = Access::persistent(vault.clone());
    let worker = thread::spawn(move || {
        for _ in 0..9 {
            serve_tls(
                listener.accept().unwrap().0,
                Shared(shared.clone()),
                identity.config.clone(),
                &access,
            );
        }
    });
    let mut old = connect_persistent(address, &certificate, "/v1/control", &token).unwrap();
    old.read().unwrap();
    old.send(Message::text(
        r#"{"v":1,"type":"mouse_button","seq":1,"button":"left","state":"down"}"#,
    ))
    .unwrap();
    old.read().unwrap();
    assert!(input.lock().unwrap().left_down);
    let code = vault.open_window().unwrap();
    old.send(Message::text(r#"{"v":1,"type":"ping","seq":2}"#))
        .unwrap();
    let _ = old.read();
    drop(old);
    for _ in 0..6 {
        let error = connect_persistent(address, &certificate, "/v1/control", &token).unwrap_err();
        assert!(error.to_string().contains("409 Conflict"));
    }
    assert!(!input.lock().unwrap().left_down);
    // As reconexões recusadas não esgotam o limite de tentativas nem consomem o código.
    let mut pair = connect_persistent(address, &certificate, "/v1/pair", &code).unwrap();
    pair.send(Message::text(r#"{"v":1,"type":"pair","name":"Novo"}"#))
        .unwrap();
    let response: serde_json::Value =
        serde_json::from_str(pair.read().unwrap().to_text().unwrap()).unwrap();
    assert_eq!(response["type"], "paired");
    let _ = pair.close(None);
    drop(pair);
    let mut resumed = connect_persistent(address, &certificate, "/v1/control", &token).unwrap();
    resumed.read().unwrap();
    let _ = resumed.close(None);
    drop(resumed);
    worker.join().unwrap();
    assert!(vault.authorized(&token));
    assert_eq!(vault.devices().len(), 2);
    assert_eq!(input.lock().unwrap().applied, 1);
}

#[test]
fn declined_pc_confirmation_never_creates_a_credential() {
    let vault = Arc::new(
        sofapad_agent::pairing::Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| false))
            .unwrap(),
    );
    let identity = vault.identity().unwrap();
    let certificate = identity.certificate.clone();
    let code = vault.open_window().unwrap();
    let listener = bind_private("127.0.0.1:0".parse().unwrap()).unwrap();
    let address = listener.local_addr().unwrap();
    let access = Access::persistent(vault.clone());
    let worker = thread::spawn(move || {
        serve_tls(
            listener.accept().unwrap().0,
            SimulatedInput::default(),
            identity.config,
            &access,
        );
    });
    let mut socket = connect_persistent(address, &certificate, "/v1/pair", &code).unwrap();
    socket
        .send(Message::text(
            r#"{"v":1,"type":"pair","name":"Celular recusado"}"#,
        ))
        .unwrap();
    assert!(!matches!(socket.read(), Ok(Message::Text(_))));
    drop(socket);
    worker.join().unwrap();
    assert!(vault.devices().is_empty());
}

#[test]
fn same_device_reconnect_replaces_stale_session_and_other_device_gets_busy() {
    let vault = Arc::new(
        sofapad_agent::pairing::Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| true))
            .unwrap(),
    );
    let mut tokens = Vec::new();
    for name in ["Sala", "Visita"] {
        let code = vault.open_window().unwrap();
        tokens.push(
            vault
                .grant(name, vault.consume_code(&code).unwrap())
                .unwrap(),
        );
    }
    let identity = vault.identity().unwrap();
    let certificate = identity.certificate.clone();
    let listener = bind_private("127.0.0.1:0".parse().unwrap()).unwrap();
    let address = listener.local_addr().unwrap();
    let input = Arc::new(Mutex::new(SimulatedInput::default()));
    let shared = input.clone();
    let access = Arc::new(Access::persistent(vault));
    let server_access = access.clone();
    let config = identity.config.clone();
    let server = thread::spawn(move || {
        let mut workers = Vec::new();
        for _ in 0..3 {
            let stream = listener.accept().unwrap().0;
            let (access, config, shared) = (server_access.clone(), config.clone(), shared.clone());
            workers.push(thread::spawn(move || {
                serve_tls(stream, Shared(shared), config, &access)
            }));
        }
        for worker in workers {
            worker.join().unwrap();
        }
    });
    // Sessão antiga com arrasto ativo que "some" sem fechar (Wi-Fi caiu).
    let mut stale = connect_persistent(address, &certificate, "/v1/control", &tokens[0]).unwrap();
    stale.read().unwrap();
    stale
        .send(Message::text(
            r#"{"v":1,"type":"mouse_button","seq":1,"button":"left","state":"down"}"#,
        ))
        .unwrap();
    stale.read().unwrap();
    assert!(input.lock().unwrap().left_down);

    let error = connect_persistent(address, &certificate, "/v1/control", &tokens[1]).unwrap_err();
    assert!(error.to_string().contains("409 Conflict"));

    let started = std::time::Instant::now();
    let mut fresh = connect_persistent(address, &certificate, "/v1/control", &tokens[0]).unwrap();
    let hello: serde_json::Value =
        serde_json::from_str(fresh.read().unwrap().to_text().unwrap()).unwrap();
    assert_eq!(hello["type"], "hello");
    assert!(
        started.elapsed() < Duration::from_secs(2),
        "reconexão deve substituir a sessão obsoleta sem esperar o tempo ocioso"
    );
    assert!(
        !input.lock().unwrap().left_down,
        "sessão antiga liberou o arrasto"
    );
    fresh
        .send(Message::text(r#"{"v":1,"type":"ping","seq":1}"#))
        .unwrap();
    fresh.read().unwrap();
    let _ = fresh.close(None);
    drop(fresh);
    drop(stale);
    server.join().unwrap();
}
