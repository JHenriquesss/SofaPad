//! Fixture de interoperabilidade: loopback, entrada simulada, saída JSON somente para pipe em memória.
use sofapad_agent::{
    input::{InputController, InputError, SimulatedInput},
    protocol::Command,
    secure::{Access, Identity, bind_private, serve_tls},
};
use std::{
    io::{self, IsTerminal},
    net::{IpAddr, Ipv4Addr, SocketAddr},
    sync::{
        Arc, Mutex,
        atomic::{AtomicUsize, Ordering},
    },
};

struct FixtureInput {
    simulated: SimulatedInput,
    seen: Arc<AtomicUsize>,
}
impl InputController for FixtureInput {
    fn apply(&mut self, command: &Command) -> Result<(), InputError> {
        if let Command::TextInsert { text } = command {
            if text != "Olá, ação! 😀" {
                return Err(InputError);
            }
            self.seen.fetch_add(1, Ordering::SeqCst);
        }
        self.simulated.apply(command)
    }
    fn release_all(&mut self) -> Result<(), InputError> {
        self.simulated.release_all()
    }
}

fn main() -> io::Result<()> {
    if io::stdout().is_terminal() {
        return Err(io::Error::other(
            "Execute apenas pelo script test-interop.ps1; não registrar a saída IPC",
        ));
    }
    let pairing = std::env::args().nth(1).as_deref() == Some("--pairing");
    let saved = Arc::new(Mutex::new(Vec::new()));
    let output = saved.clone();
    let vault = Arc::new(sofapad_agent::pairing::Vault::open(
        None,
        Box::new(move |bytes| {
            *output
                .lock()
                .map_err(|_| io::Error::other("Registro indisponível"))? = bytes.to_vec();
            Ok(())
        }),
        Box::new(|_| true),
    )?);
    let identity = if pairing {
        vault.identity()?
    } else {
        Identity::generate(IpAddr::V4(Ipv4Addr::LOCALHOST))?
    };
    let access = if pairing {
        Access::persistent(vault.clone())
    } else {
        Access::generate()?
    };
    let listener = bind_private(SocketAddr::from((Ipv4Addr::LOCALHOST, 0)))?;
    let address = listener.local_addr()?;
    // Consumido diretamente por Process.StandardOutput; nunca salvo em arquivo nem exibido pelo script.
    let settings = if pairing {
        serde_json::json!({"host":"127.0.0.1","port":address.port(),"fingerprint":identity.fingerprint,"token":"0".repeat(64),"code":vault.open_window()?})
    } else {
        serde_json::json!({"host":"127.0.0.1","port":address.port(),"fingerprint":identity.fingerprint,"token":access.code_for_local_display()})
    };
    println!("{settings}");
    if pairing {
        serve_tls(
            listener.accept()?.0,
            SimulatedInput::default(),
            identity.config.clone(),
            &access,
        );
        // Recarrega somente bytes persistidos: descarta o registro e a janela temporária anteriores.
        let bytes = saved
            .lock()
            .map_err(|_| io::Error::other("Registro indisponível"))?
            .clone();
        let restored = Arc::new(sofapad_agent::pairing::Vault::open(
            Some(&bytes),
            Box::new(|_| Ok(())),
            Box::new(|_| false),
        )?);
        let restored_identity = restored.identity()?;
        let restored_access = Access::persistent(restored);
        return check_text(listener, restored_identity, restored_access);
    }
    check_text(listener, identity, access)
}
fn check_text(
    listener: std::net::TcpListener,
    identity: Identity,
    access: Access,
) -> io::Result<()> {
    let seen = Arc::new(AtomicUsize::new(0));
    let input = FixtureInput {
        simulated: SimulatedInput::default(),
        seen: seen.clone(),
    };
    serve_tls(listener.accept()?.0, input, identity.config, &access);
    if seen.load(Ordering::SeqCst) != 1 {
        return Err(io::Error::other(
            "Fixture deve receber o texto exatamente uma vez",
        ));
    }
    Ok(())
}
