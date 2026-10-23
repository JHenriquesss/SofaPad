use sofapad_agent::{
    input::SimulatedInput,
    secure::{Access, Identity, bind_private},
    server::serve_authorized,
};
use std::{
    io::{self, Read, Write},
    net::{SocketAddr, TcpStream},
    time::{Duration, Instant},
};

struct Trace<S>(S);
struct Wire(TcpStream);
impl Read for Wire {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        self.0
            .read(buffer)
            .inspect(|count| eprintln!("wire read: {count} bytes"))
    }
}
impl Write for Wire {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        self.0
            .write(buffer)
            .inspect(|count| eprintln!("wire write: {count} bytes"))
    }
    fn flush(&mut self) -> io::Result<()> {
        self.0.flush()
    }
}
fn report(operation: &str, error: &io::Error) {
    let tls = error
        .get_ref()
        .and_then(|source| source.downcast_ref::<rustls::Error>());
    // Apenas categoria de I/O e erro TLS; nunca registra buffers ou mensagens HTTP.
    eprintln!("{operation}: {:?}; TLS: {tls:?}", error.kind());
}
impl<S: Read> Read for Trace<S> {
    fn read(&mut self, buffer: &mut [u8]) -> io::Result<usize> {
        self.0
            .read(buffer)
            .inspect_err(|error| report("read", error))
    }
}
impl<S: Write> Write for Trace<S> {
    fn write(&mut self, buffer: &[u8]) -> io::Result<usize> {
        self.0
            .write(buffer)
            .inspect_err(|error| report("write", error))
    }
    fn flush(&mut self) -> io::Result<()> {
        self.0.flush().inspect_err(|error| report("flush", error))
    }
}
fn main() -> io::Result<()> {
    let address: SocketAddr = std::env::args()
        .nth(1)
        .ok_or_else(|| io::Error::other("Informe IP_LOCAL:PORTA livre para diagnóstico"))?
        .parse()
        .map_err(|_| io::Error::other("Endereço inválido"))?;
    let listener = bind_private(address)?;
    listener.set_nonblocking(true)?;
    let identity = Identity::generate(address.ip())?;
    // O segredo fica somente em memória e não é disponibilizado: nenhuma conexão autoriza controle.
    let access = Access::generate()?;
    println!(
        "{}",
        serde_json::json!({"host":address.ip().to_string(),"port":address.port(),"fingerprint":identity.fingerprint})
    );
    let started = Instant::now();
    let mut connections = 0;
    while connections < 2 && started.elapsed() < Duration::from_secs(60) {
        let stream = match listener.accept() {
            Ok((stream, _)) => stream,
            Err(error) if error.kind() == io::ErrorKind::WouldBlock => {
                std::thread::sleep(Duration::from_millis(100));
                continue;
            }
            Err(error) => return Err(error),
        };
        connections += 1;
        stream.set_nonblocking(false)?;
        stream.set_read_timeout(Some(Duration::from_secs(3)))?;
        stream.set_write_timeout(Some(Duration::from_secs(3)))?;
        let peer = stream.peer_addr()?.ip();
        let tls = rustls::ServerConnection::new(identity.config.clone())
            .map_err(|_| io::Error::other("Falha ao preparar TLS"))?;
        eprintln!("connection {connections}");
        serve_authorized(
            Trace(rustls::StreamOwned::new(tls, Wire(stream))),
            SimulatedInput::default(),
            &access,
            peer,
        );
    }
    if connections != 2 {
        return Err(io::Error::new(
            io::ErrorKind::TimedOut,
            "Diagnóstico expirou",
        ));
    }
    Ok(())
}
