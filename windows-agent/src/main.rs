#![cfg_attr(all(windows, not(debug_assertions)), windows_subsystem = "windows")]

use sofapad_agent::{
    input::SimulatedInput,
    server::{bind_loopback, serve_connection},
};
use std::net::{Ipv4Addr, SocketAddr};

fn main() -> std::io::Result<()> {
    let args: Vec<String> = std::env::args().skip(1).collect();
    #[cfg(windows)]
    if args.is_empty() || args.as_slice() == ["--household"] {
        if let Err(error) = sofapad_agent::household::start() {
            sofapad_agent::platform::dialog(
                &format!("Não foi possível iniciar o SofaPad: {error}"),
                windows_sys::Win32::UI::WindowsAndMessaging::MB_OK
                    | windows_sys::Win32::UI::WindowsAndMessaging::MB_ICONERROR,
            );
            return Err(error);
        }
        return Ok(());
    }
    if args.first().map(String::as_str) == Some("--control") {
        let address = args
            .get(1)
            .ok_or_else(|| std::io::Error::other("Uso: --control IP_LOCAL:49375"))?
            .parse()
            .map_err(|_| std::io::Error::other("Endereço inválido"))?;
        #[cfg(windows)]
        {
            return control(address);
        }
        #[cfg(not(windows))]
        {
            let _: SocketAddr = address;
            return Err(std::io::Error::other(
                "Controle real disponível somente no Windows",
            ));
        }
    }
    if !args.is_empty() && args.as_slice() != ["--simulate"] {
        println!(
            "SofaPad: sem argumentos ou --household = bandeja e pareamento persistente no Windows; --simulate = simulação loopback; --control IP_LOCAL:49375 = sessão temporária de desenvolvimento."
        );
        return Ok(());
    }
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

#[cfg(windows)]
fn dialog(text: &str, flags: u32) -> i32 {
    use windows_sys::Win32::UI::WindowsAndMessaging::MessageBoxW;
    let title: Vec<u16> = "SofaPad — sessão de desenvolvimento\0"
        .encode_utf16()
        .collect();
    let text: Vec<u16> = text.encode_utf16().chain(Some(0)).collect();
    unsafe { MessageBoxW(std::ptr::null_mut(), text.as_ptr(), title.as_ptr(), flags) }
}

#[cfg(windows)]
fn control(address: SocketAddr) -> std::io::Result<()> {
    use sofapad_agent::{
        secure::{Access, Identity, bind_private, serve_tls},
        windows_input::WindowsInput,
    };
    use std::{
        io,
        sync::{
            Arc, Mutex,
            atomic::{AtomicBool, Ordering},
        },
        time::Duration,
    };
    use windows_sys::Win32::UI::WindowsAndMessaging::{IDYES, MB_DEFBUTTON2, MB_OK, MB_YESNO};
    if !sofapad_agent::secure::is_private(address.ip())
        || address.ip().is_unspecified()
        || address.port() == 0
    {
        return Err(io::Error::other(
            "Informe IP local específico e porta não zero",
        ));
    }
    if dialog(
        "Permitir controle deste PC pelo celular nesta sessão?\n\nO celular precisará da identidade e do código exibidos no próximo diálogo. As credenciais duram no máximo 8 horas e são descartadas ao encerrar. Esta ainda é uma versão de desenvolvimento.\n\nO programa não pede administrador nem altera firewall. Ctrl+C no console encerra e libera os controles.",
        MB_YESNO | MB_DEFBUTTON2,
    ) != IDYES
    {
        return Ok(());
    }
    let identity = Identity::generate(address.ip())?;
    let access = Arc::new(Access::generate()?);
    let listener = bind_private(address)?;
    listener.set_nonblocking(true)?;
    let shutdown = Arc::new(AtomicBool::new(false));
    let active: Arc<Mutex<Option<std::net::TcpStream>>> = Arc::new(Mutex::new(None));
    let signal_shutdown = shutdown.clone();
    let signal_active = active.clone();
    ctrlc::set_handler(move || {
        signal_shutdown.store(true, Ordering::SeqCst);
        if let Ok(socket) = signal_active.lock()
            && let Some(socket) = socket.as_ref()
        {
            let _ = socket.shutdown(std::net::Shutdown::Both);
        }
    })
    .map_err(|_| io::Error::other("Falha ao preparar encerramento seguro"))?;
    let mdns = mdns_sd::ServiceDaemon::new().ok();
    if let Some(mdns) = &mdns {
        let host = format!("sofapad-{}.local.", &identity.fingerprint[..8]);
        let service = mdns_sd::ServiceInfo::new(
            "_couchremote._tcp.local.",
            "SofaPad PC",
            &host,
            address.ip(),
            address.port(),
            &[("v", "1"), ("tls", "required")][..],
        );
        if service.and_then(|service| mdns.register(service)).is_err() {
            eprintln!("Descoberta indisponível; use o endereço manual.");
        }
    }
    let display = format!(
        "Autorização temporária deste PC\n\nEndereço: {}\nPorta: {}\n\nIdentidade SHA-256 (copie integralmente):\n{}\n\nCódigo da sessão (privado):\n{}\n\nNo celular, abra Configurações > Conectar ao PC. Não compartilhe o código. Ctrl+C nesta janela copia os dados; nenhum segredo é gravado em disco. Fechar esta janela mantém o agente; Ctrl+C no console encerra.",
        address.ip(),
        address.port(),
        identity.fingerprint,
        access.code_for_local_display()
    );
    std::thread::spawn(move || {
        dialog(&display, MB_OK);
    });
    println!(
        "SofaPad — controle WSS ativo em {address}. Credenciais apenas no diálogo local; Ctrl+C encerra."
    );
    while !shutdown.load(Ordering::SeqCst) && !access.expired() {
        match listener.accept() {
            Ok((stream, _)) => {
                if let Ok(mut active) = active.lock() {
                    *active = stream.try_clone().ok();
                }
                serve_tls(
                    stream,
                    WindowsInput::default(),
                    identity.config.clone(),
                    &access,
                );
                if let Ok(mut active) = active.lock() {
                    *active = None;
                }
            }
            Err(error) if error.kind() == io::ErrorKind::WouldBlock => {
                std::thread::sleep(Duration::from_millis(100))
            }
            Err(error) => return Err(error),
        }
    }
    if let Some(mdns) = mdns {
        let _ = mdns.shutdown();
    }
    Ok(())
}
