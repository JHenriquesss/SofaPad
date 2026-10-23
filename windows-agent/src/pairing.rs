use crate::secure::{Identity, hex};
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::{
    io,
    sync::{
        Mutex,
        atomic::{AtomicU64, Ordering},
    },
    time::{Duration, Instant},
};
use subtle::ConstantTimeEq;

#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Device {
    id: String,
    name: String,
    hash: String,
}
#[derive(Clone, Serialize, Deserialize)]
#[serde(deny_unknown_fields)]
struct Stored {
    v: u8,
    certificate: Vec<u8>,
    key: Vec<u8>,
    devices: Vec<Device>,
}
struct Window {
    code: String,
    deadline: Instant,
}
pub struct PairPermit {
    epoch: u64,
    deadline: Instant,
}
type Save = dyn Fn(&[u8]) -> io::Result<()> + Send + Sync;
type Confirm = dyn Fn(&str) -> bool + Send + Sync;

pub struct Vault {
    state: Mutex<Stored>,
    window: Mutex<Option<Window>>,
    save: Box<Save>,
    confirm: Box<Confirm>,
    epoch: AtomicU64,
}
impl Vault {
    // Persistência recebe somente o documento privado; a plataforma deve protegê-lo antes de gravar.
    pub fn open(bytes: Option<&[u8]>, save: Box<Save>, confirm: Box<Confirm>) -> io::Result<Self> {
        let state = match bytes {
            Some(bytes) => {
                if bytes.len() > 65536 {
                    return Err(io::Error::other("Registro privado excedeu limite"));
                }
                let value: Stored = serde_json::from_slice(bytes).map_err(|_| {
                    io::Error::other("Registro privado inválido; não foi substituído")
                })?;
                if value.v != 1 || value.devices.len() > 10 {
                    return Err(io::Error::other("Versão de registro incompatível"));
                }
                Identity::from_der(value.certificate.clone(), value.key.clone())?;
                value
            }
            None => {
                let identity = Identity::generate("127.0.0.1".parse().expect("IP constante"))?;
                let state = Stored {
                    v: 1,
                    certificate: identity.certificate,
                    key: identity.private_key,
                    devices: vec![],
                };
                save(&serde_json::to_vec(&state)?)?;
                state
            }
        };
        Ok(Self {
            state: Mutex::new(state),
            window: Mutex::new(None),
            save,
            confirm,
            epoch: AtomicU64::new(0),
        })
    }
    pub fn identity(&self) -> io::Result<Identity> {
        let state = self
            .state
            .lock()
            .map_err(|_| io::Error::other("Registro indisponível"))?;
        Identity::from_der(state.certificate.clone(), state.key.clone())
    }
    pub fn open_window(&self) -> io::Result<String> {
        let code = random_hex(4)?;
        let mut window = self
            .window
            .lock()
            .map_err(|_| io::Error::other("Pareamento indisponível"))?;
        self.epoch.fetch_add(1, Ordering::SeqCst);
        *window = Some(Window {
            code: code.clone(),
            deadline: Instant::now() + Duration::from_secs(300),
        });
        Ok(code)
    }
    pub fn cancel_pairing(&self) {
        if let Ok(mut window) = self.window.lock() {
            self.epoch.fetch_add(1, Ordering::SeqCst);
            *window = None;
        }
    }
    pub fn pairing_active(&self) -> bool {
        self.window
            .lock()
            .is_ok_and(|window| window.as_ref().is_some_and(|w| Instant::now() < w.deadline))
    }
    pub fn consume_code(&self, code: &str) -> Option<PairPermit> {
        let Ok(mut window) = self.window.lock() else {
            return None;
        };
        if window.as_ref().is_some_and(|w| {
            Instant::now() < w.deadline && bool::from(w.code.as_bytes().ct_eq(code.as_bytes()))
        }) {
            let permit = PairPermit {
                epoch: self.epoch.load(Ordering::SeqCst),
                deadline: window.as_ref()?.deadline,
            };
            *window = None;
            return Some(permit);
        }
        None
    }
    pub fn grant(&self, name: &str, permit: PairPermit) -> io::Result<String> {
        if name.is_empty()
            || name.len() > 128
            || name.chars().count() > 48
            || name.chars().any(char::is_control)
        {
            return Err(io::Error::other("Nome de aparelho inválido"));
        }
        let began = Instant::now();
        if Instant::now() >= permit.deadline
            || self.epoch.load(Ordering::SeqCst) != permit.epoch
            || !(self.confirm)(name)
            || began.elapsed() > Duration::from_secs(60)
            || Instant::now() >= permit.deadline
            || self.epoch.load(Ordering::SeqCst) != permit.epoch
        {
            return Err(io::Error::other("Pareamento não confirmado"));
        }
        let token = random_hex(32)?;
        let _window = self
            .window
            .lock()
            .map_err(|_| io::Error::other("Pareamento indisponível"))?;
        if self.epoch.load(Ordering::SeqCst) != permit.epoch {
            return Err(io::Error::other("Pareamento cancelado"));
        }
        let mut state = self
            .state
            .lock()
            .map_err(|_| io::Error::other("Registro indisponível"))?;
        if state.devices.len() >= 10 {
            return Err(io::Error::other(
                "Revogue um aparelho antes de parear outro",
            ));
        }
        let mut next = state.clone();
        next.devices.push(Device {
            id: random_hex(8)?,
            name: name.into(),
            hash: hex(&Sha256::digest(token.as_bytes())),
        });
        (self.save)(&serde_json::to_vec(&next)?)?;
        *state = next;
        Ok(token)
    }
    pub fn authorized(&self, token: &str) -> bool {
        if token.len() != 64 || !token.bytes().all(|c| c.is_ascii_hexdigit()) {
            return false;
        }
        let hash = hex(&Sha256::digest(token.as_bytes()));
        self.state.lock().is_ok_and(|s| {
            s.devices
                .iter()
                .any(|d| bool::from(d.hash.as_bytes().ct_eq(hash.as_bytes())))
        })
    }
    pub fn devices(&self) -> Vec<(String, String)> {
        self.state
            .lock()
            .map(|s| {
                s.devices
                    .iter()
                    .map(|d| (d.id.clone(), d.name.clone()))
                    .collect()
            })
            .unwrap_or_default()
    }
    pub fn revoke(&self, id: &str) -> io::Result<()> {
        let mut state = self
            .state
            .lock()
            .map_err(|_| io::Error::other("Registro indisponível"))?;
        let mut next = state.clone();
        next.devices.retain(|d| d.id != id);
        (self.save)(&serde_json::to_vec(&next)?)?;
        *state = next;
        Ok(())
    }
}
fn random_hex(size: usize) -> io::Result<String> {
    let mut bytes = vec![0u8; size];
    getrandom::fill(&mut bytes).map_err(|_| io::Error::other("Falha no gerador seguro"))?;
    Ok(hex(&bytes))
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::Arc;
    #[test]
    fn pairing_reservation_ends_on_consumption_cancellation_or_expiry() {
        let vault = Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| true)).unwrap();
        assert!(!vault.pairing_active());
        let code = vault.open_window().unwrap();
        assert!(vault.pairing_active());
        assert!(vault.consume_code("invalid").is_none());
        assert!(vault.pairing_active());
        assert!(vault.consume_code(&code).is_some());
        assert!(!vault.pairing_active());
        vault.open_window().unwrap();
        vault.cancel_pairing();
        assert!(!vault.pairing_active());
        vault.open_window().unwrap();
        vault.window.lock().unwrap().as_mut().unwrap().deadline = Instant::now();
        assert!(!vault.pairing_active());
    }
    #[test]
    fn canceled_expired_and_rate_limited_pairings_cannot_be_granted() {
        let vault = Arc::new(Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| true)).unwrap());
        let code = vault.open_window().unwrap();
        let permit = vault.consume_code(&code).unwrap();
        vault.cancel_pairing();
        assert!(vault.grant("Celular", permit).is_err());
        let code = vault.open_window().unwrap();
        vault.window.lock().unwrap().as_mut().unwrap().deadline = Instant::now();
        assert!(vault.consume_code(&code).is_none());
        let access = crate::secure::Access::persistent(vault.clone());
        let ip = "127.0.0.1".parse().unwrap();
        for _ in 0..5 {
            assert!(
                access
                    .authorize_pair(ip, Some("Bearer incorreto"))
                    .is_none()
            );
        }
        let code = vault.open_window().unwrap();
        assert!(
            access
                .authorize_pair(ip, Some(&format!("Bearer {code}")))
                .is_none()
        );
        assert!(vault.devices().is_empty());
    }
    #[test]
    fn persistence_rotation_and_revocation() {
        let saved = Arc::new(Mutex::new(Vec::new()));
        let out = saved.clone();
        let vault = Vault::open(
            None,
            Box::new(move |bytes| {
                *out.lock().unwrap() = bytes.to_vec();
                Ok(())
            }),
            Box::new(|_| true),
        )
        .unwrap();
        assert!(vault.consume_code("12345678").is_none());
        let code = vault.open_window().unwrap();
        assert!(vault.consume_code("invalid").is_none());
        let permit = vault.consume_code(&code).unwrap();
        assert!(vault.consume_code(&code).is_none());
        let token = vault.grant("Meu celular", permit).unwrap();
        assert!(vault.authorized(&token));
        let bytes = saved.lock().unwrap().clone();
        assert!(!String::from_utf8_lossy(&bytes).contains(&token));
        let restored =
            Vault::open(Some(&bytes), Box::new(|_| Ok(())), Box::new(|_| false)).unwrap();
        assert_eq!(
            vault.identity().unwrap().fingerprint,
            restored.identity().unwrap().fingerprint
        );
        assert!(restored.authorized(&token));
        restored.revoke(&restored.devices()[0].0).unwrap();
        assert!(!restored.authorized(&token));
        let code = restored.open_window().unwrap();
        assert!(
            restored
                .grant("Outro", restored.consume_code(&code).unwrap())
                .is_err()
        );
    }
    #[test]
    fn failed_save_never_authorizes_and_corruption_never_resets() {
        let source = Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| true)).unwrap();
        let bytes = serde_json::to_vec(&*source.state.lock().unwrap()).unwrap();
        let vault = Vault::open(
            Some(&bytes),
            Box::new(|_| Err(io::Error::other("disco"))),
            Box::new(|_| true),
        )
        .unwrap();
        let code = vault.open_window().unwrap();
        assert!(
            vault
                .grant("Celular", vault.consume_code(&code).unwrap())
                .is_err()
        );
        assert!(vault.devices().is_empty());
        let code = vault.open_window().unwrap();
        assert!(
            vault
                .grant("nome\nforjado", vault.consume_code(&code).unwrap())
                .is_err()
        );
        assert!(
            Vault::open(
                Some(b"corrompido"),
                Box::new(|_| Ok(())),
                Box::new(|_| true)
            )
            .is_err()
        );
    }
}
