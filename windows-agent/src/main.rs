use sofapad_agent::{
    input::SimulatedInput,
    server::{bind_loopback, serve_connection},
};
use std::net::{Ipv4Addr, SocketAddr};

fn main() -> std::io::Result<()> {
    let listener = bind_loopback(SocketAddr::from((Ipv4Addr::LOCALHOST, 49375)))?;
    println!("SofaPad — SIMULAÇÃO LOCAL. Não controla o Windows.");
    println!("ws://127.0.0.1:49375/v1/simulate — encerre com Ctrl+C.");
    for stream in listener.incoming() {
        match stream {
            Ok(stream) => serve_connection(stream, SimulatedInput::default()),
            Err(_) => eprintln!("Falha ao aceitar conexão local."),
        }
    }
    Ok(())
}
