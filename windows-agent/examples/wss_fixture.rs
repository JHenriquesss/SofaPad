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
        Arc,
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
    let identity = Identity::generate(IpAddr::V4(Ipv4Addr::LOCALHOST))?;
    let access = Access::generate()?;
    let listener = bind_private(SocketAddr::from((Ipv4Addr::LOCALHOST, 0)))?;
    let address = listener.local_addr()?;
    // Consumido diretamente por Process.StandardOutput; nunca salvo em arquivo nem exibido pelo script.
    println!(
        "{}",
        serde_json::json!({"host":"127.0.0.1","port":address.port(),"fingerprint":identity.fingerprint,"token":access.code_for_local_display()})
    );
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
