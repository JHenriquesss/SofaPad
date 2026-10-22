use crate::input::InputController;
use rustls::{ServerConfig, ServerConnection, StreamOwned, pki_types::PrivatePkcs8KeyDer};
use sha2::{Digest, Sha256};
use std::{
    collections::HashMap,
    io,
    net::{IpAddr, SocketAddr, TcpStream},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};
use subtle::ConstantTimeEq;

pub struct Access {
    secret: String,
    deadline: Instant,
    failures: Mutex<HashMap<IpAddr, (Instant, u32)>>,
}

impl Access {
    pub fn generate() -> io::Result<Self> {
        let mut random = [0u8; 32];
        getrandom::fill(&mut random)
            .map_err(|_| io::Error::other("Não foi possível gerar autorização"))?;
        Ok(Self {
            secret: hex(&random),
            deadline: Instant::now() + Duration::from_secs(8 * 3600),
            failures: Mutex::new(HashMap::new()),
        })
    }
    pub fn code_for_local_display(&self) -> &str {
        &self.secret
    }
    pub fn authorize(&self, peer: IpAddr, value: Option<&str>) -> bool {
        let Ok(mut failures) = self.failures.lock() else {
            return false;
        };
        failures.retain(|_, (when, _)| when.elapsed() < Duration::from_secs(60));
        if failures.get(&peer).is_some_and(|(_, count)| *count >= 5)
            || Instant::now() >= self.deadline
        {
            return false;
        }
        let candidate = value.and_then(|s| s.strip_prefix("Bearer ")).unwrap_or("");
        if bool::from(self.secret.as_bytes().ct_eq(candidate.as_bytes())) {
            return true;
        }
        if failures.len() < 64 || failures.contains_key(&peer) {
            failures.entry(peer).or_insert((Instant::now(), 0)).1 += 1;
        }
        false
    }
    pub fn expired(&self) -> bool {
        Instant::now() >= self.deadline
    }
}

pub fn hex(bytes: &[u8]) -> String {
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}

pub struct Identity {
    pub config: Arc<ServerConfig>,
    pub certificate: Vec<u8>,
    pub fingerprint: String,
}

impl Identity {
    pub fn generate(address: IpAddr) -> io::Result<Self> {
        let key = rcgen::generate_simple_self_signed(vec![address.to_string(), "localhost".into()])
            .map_err(|_| io::Error::other("Falha ao gerar identidade TLS"))?;
        let certificate = key.cert.der().to_vec();
        let private = PrivatePkcs8KeyDer::from(key.signing_key.serialize_der());
        let config = ServerConfig::builder()
            .with_no_client_auth()
            .with_single_cert(vec![key.cert.der().clone()], private.into())
            .map_err(|_| io::Error::other("Falha na configuração TLS"))?;
        Ok(Self {
            config: Arc::new(config),
            fingerprint: hex(&Sha256::digest(&certificate)),
            certificate,
        })
    }
}

pub fn bind_private(address: SocketAddr) -> io::Result<std::net::TcpListener> {
    if !is_private(address.ip()) || address.ip().is_unspecified() {
        return Err(io::Error::new(
            io::ErrorKind::PermissionDenied,
            "Use um IP local específico, nunca 0.0.0.0 ou endereço público",
        ));
    }
    std::net::TcpListener::bind(address)
}

pub fn is_private(ip: IpAddr) -> bool {
    match ip {
        IpAddr::V4(ip) => ip.is_private() || ip.is_loopback(),
        IpAddr::V6(ip) => ip.is_loopback() || ip.is_unique_local(),
    }
}

pub fn serve_tls<C: InputController>(
    stream: TcpStream,
    controller: C,
    config: Arc<ServerConfig>,
    access: &Access,
) {
    let Ok(peer) = stream.peer_addr() else {
        return;
    };
    if !is_private(peer.ip())
        || stream
            .set_read_timeout(Some(Duration::from_secs(3)))
            .is_err()
        || stream
            .set_write_timeout(Some(Duration::from_secs(3)))
            .is_err()
    {
        return;
    }
    let _ = stream.set_nodelay(true);
    let Ok(connection) = ServerConnection::new(config) else {
        return;
    };
    crate::server::serve_authorized(
        StreamOwned::new(connection, stream),
        controller,
        access,
        peer.ip(),
    );
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn rejects_public_and_unspecified_bind() {
        for address in ["0.0.0.0:0", "8.8.8.8:0", "[::]:0"] {
            assert!(bind_private(address.parse().unwrap()).is_err());
        }
    }
    #[test]
    fn authorization_is_required_and_attempts_are_limited() {
        let access = Access::generate().unwrap();
        let ip = "127.0.0.1".parse().unwrap();
        assert!(!access.authorize(ip, None));
        let correct = format!("Bearer {}", access.code_for_local_display());
        assert!(access.authorize(ip, Some(&correct)));
        for _ in 0..4 {
            assert!(!access.authorize(ip, Some("Bearer incorreto")));
        }
        assert!(!access.authorize(ip, Some(&correct)));
    }
    #[test]
    fn identity_and_secret_change_on_each_run() {
        let one = Identity::generate("127.0.0.1".parse().unwrap()).unwrap();
        let two = Identity::generate("127.0.0.1".parse().unwrap()).unwrap();
        assert_ne!(one.fingerprint, two.fingerprint);
        assert_ne!(
            Access::generate().unwrap().secret,
            Access::generate().unwrap().secret
        );
    }
}
