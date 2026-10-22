use crate::{
    pairing::Vault,
    platform::*,
    secure::{Access, bind_private, is_private, serve_tls},
    windows_input::WindowsInput,
};
use std::{
    io,
    net::{IpAddr, SocketAddr, TcpStream, UdpSocket},
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
    },
    time::{Duration, Instant},
};
use windows_sys::Win32::UI::WindowsAndMessaging::{IDYES, MB_DEFBUTTON2, MB_YESNO};

pub struct Runtime {
    pub vault: Arc<Vault>,
    pub status: Mutex<String>,
    pub stop: AtomicBool,
    pub(crate) active: Mutex<Option<TcpStream>>,
}
impl Runtime {
    pub fn disconnect(&self) {
        if let Ok(active) = self.active.lock()
            && let Some(socket) = active.as_ref()
        {
            let _ = socket.shutdown(std::net::Shutdown::Both);
        }
    }
    pub fn stop(&self) {
        self.vault.cancel_pairing();
        self.stop.store(true, Ordering::SeqCst);
        self.disconnect();
    }
}
pub fn start() -> io::Result<()> {
    use windows_sys::Win32::{
        Foundation::{CloseHandle, ERROR_ALREADY_EXISTS, GetLastError},
        System::Threading::CreateMutexW,
    };
    let mutex = unsafe {
        CreateMutexW(
            std::ptr::null(),
            0,
            wide("Local\\SofaPadHousehold").as_ptr(),
        )
    };
    if mutex.is_null() {
        return Err(io::Error::last_os_error());
    }
    if unsafe { GetLastError() } == ERROR_ALREADY_EXISTS {
        unsafe {
            CloseHandle(mutex);
        }
        return Ok(());
    }
    let result = run();
    unsafe {
        CloseHandle(mutex);
    }
    result
}
fn run() -> io::Result<()> {
    let directory = std::path::PathBuf::from(
        std::env::var_os("LOCALAPPDATA")
            .ok_or_else(|| io::Error::other("Pasta do usuário indisponível"))?,
    )
    .join("SofaPad");
    std::fs::create_dir_all(&directory)?;
    let path = directory.join("credentials.dpapi");
    let saved = load(&path)?;
    let vault = Arc::new(Vault::open(
        saved.as_deref(),
        Box::new(move |bytes| save(&path, bytes)),
        Box::new(|name| {
            dialog(
                &format!(
                    "Autorizar o aparelho «{name}» a controlar este PC?\n\nConfirme somente se você acabou de iniciar o pareamento no seu celular. Esta autorização permanece até você revogá-la na bandeja."
                ),
                MB_YESNO | MB_DEFBUTTON2,
            ) == IDYES
        }),
    )?);
    let runtime = Arc::new(Runtime {
        vault,
        status: Mutex::new("Aguardando rede local…".into()),
        stop: AtomicBool::new(false),
        active: Mutex::new(None),
    });
    let worker_runtime = runtime.clone();
    let worker = std::thread::spawn(move || serve(worker_runtime));
    let result = crate::tray::run(runtime.clone());
    runtime.stop();
    // O servidor sai rapidamente ao fechar seu socket; diálogos de pareamento podem aguardar o usuário.
    for _ in 0..40 {
        if worker.is_finished() {
            break;
        }
        std::thread::sleep(Duration::from_millis(50));
    }
    if worker.is_finished() {
        let _ = worker.join();
    }
    result
}
fn local_address() -> io::Result<IpAddr> {
    let socket = UdpSocket::bind("0.0.0.0:0")?;
    // connect UDP apenas seleciona a rota; não envia dados ao roteador.
    socket.connect("192.168.0.1:9")?;
    let ip = socket.local_addr()?.ip();
    if !is_private(ip) || ip.is_loopback() {
        return Err(io::Error::other("Sem endereço IPv4 privado"));
    }
    Ok(ip)
}
fn serve(runtime: Arc<Runtime>) {
    let Ok(identity) = runtime.vault.identity() else {
        return;
    };
    let access = Access::persistent(runtime.vault.clone());
    while !runtime.stop.load(Ordering::SeqCst) {
        let result = local_address().and_then(|ip| bind_private(SocketAddr::new(ip, 49375)));
        let Ok(listener) = result else {
            if let Ok(mut status) = runtime.status.lock() {
                *status = "Sem rede local ou porta 49375 ocupada. Tentando novamente…".into();
            }
            for _ in 0..50 {
                if runtime.stop.load(Ordering::SeqCst) {
                    return;
                }
                std::thread::sleep(Duration::from_millis(100));
            }
            continue;
        };
        let Ok(address) = listener.local_addr() else {
            return;
        };
        if listener.set_nonblocking(true).is_err() {
            return;
        }
        if let Ok(mut status) = runtime.status.lock() {
            *status = format!("PC disponível em {address}");
        }
        let mdns = mdns_sd::ServiceDaemon::new().ok();
        if let Some(mdns) = &mdns {
            let host = format!("sofapad-{}.local.", &identity.fingerprint[..12]);
            if let Ok(service) = mdns_sd::ServiceInfo::new(
                "_couchremote._tcp.local.",
                "SofaPad PC",
                &host,
                address.ip(),
                address.port(),
                &[
                    ("v", "1"),
                    ("tls", "required"),
                    ("id", identity.fingerprint.as_str()),
                ][..],
            ) {
                let _ = mdns.register(service);
            }
        }
        let mut checked = Instant::now();
        while !runtime.stop.load(Ordering::SeqCst) {
            if checked.elapsed() > Duration::from_secs(5) {
                if local_address().ok() != Some(address.ip()) {
                    break;
                }
                checked = Instant::now();
            }
            match listener.accept() {
                Ok((stream, _)) => {
                    if let Ok(mut active) = runtime.active.lock() {
                        *active = stream.try_clone().ok();
                    }
                    serve_tls(
                        stream,
                        WindowsInput::default(),
                        identity.config.clone(),
                        &access,
                    );
                    if let Ok(mut active) = runtime.active.lock() {
                        *active = None;
                    }
                }
                Err(e) if e.kind() == io::ErrorKind::WouldBlock => {
                    std::thread::sleep(Duration::from_millis(100))
                }
                Err(_) => break,
            }
        }
        if let Some(mdns) = mdns {
            let _ = mdns.shutdown();
        }
    }
}
