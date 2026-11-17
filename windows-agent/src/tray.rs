use crate::{household::Runtime, platform::*};
use std::{
    io, ptr,
    sync::{
        Arc, OnceLock,
        atomic::{AtomicU32, Ordering},
    },
};
use windows_sys::Win32::{
    Foundation::*,
    System::LibraryLoader::GetModuleHandleW,
    UI::{Shell::*, WindowsAndMessaging::*},
};

static RUNTIME: OnceLock<Arc<Runtime>> = OnceLock::new();
static TASKBAR: AtomicU32 = AtomicU32::new(0);
const TRAY: u32 = WM_APP + 1;

fn icon(window: HWND, action: u32) -> bool {
    let mut data: NOTIFYICONDATAW = unsafe { std::mem::zeroed() };
    data.cbSize = std::mem::size_of::<NOTIFYICONDATAW>() as u32;
    data.hWnd = window;
    data.uID = 1;
    data.uFlags = NIF_MESSAGE | NIF_ICON | NIF_TIP;
    data.uCallbackMessage = TRAY;
    data.hIcon = app_icon();
    let tip = wide(&format!("SofaPad {VERSION} — controle pelo celular"));
    data.szTip[..tip.len()].copy_from_slice(&tip);
    unsafe { Shell_NotifyIconW(action, &data) != 0 }
}
/// Ícone do sofá embutido no executável, no tamanho da bandeja; sem ele, o genérico do Windows.
fn app_icon() -> HICON {
    unsafe {
        let size = GetSystemMetrics(SM_CXSMICON);
        let icon = LoadImageW(
            GetModuleHandleW(ptr::null()),
            ICON_ID as usize as *const u16,
            IMAGE_ICON,
            size,
            size,
            LR_SHARED,
        );
        if icon.is_null() {
            LoadIconW(ptr::null_mut(), IDI_APPLICATION)
        } else {
            icon
        }
    }
}
/// Pede para a instância já aberta (de qualquer versão) encerrar, como se escolhessem Sair.
pub fn close_running_instance() -> bool {
    let window = unsafe { FindWindowW(wide("SofaPadTray").as_ptr(), ptr::null()) };
    !window.is_null() && unsafe { PostMessageW(window, WM_CLOSE, 0, 0) } != 0
}
unsafe extern "system" fn procedure(
    window: HWND,
    message: u32,
    wparam: WPARAM,
    lparam: LPARAM,
) -> LRESULT {
    let Some(runtime) = RUNTIME.get() else {
        return unsafe { DefWindowProcW(window, message, wparam, lparam) };
    };
    if message == TASKBAR.load(Ordering::SeqCst) {
        icon(window, NIM_ADD);
        return 0;
    }
    match message {
        #[cfg(test)]
        WM_TIMER => {
            unsafe {
                DestroyWindow(window);
            }
            0
        }
        TRAY if lparam as u32 == WM_RBUTTONUP || lparam as u32 == WM_LBUTTONUP => {
            let menu = unsafe { CreatePopupMenu() };
            if menu.is_null() {
                return 0;
            }
            let starts_with_windows = startup_enabled();
            let items = [
                (1, "Status"),
                (2, "Parear celular"),
                (3, "Cancelar pareamento"),
                (4, "Iniciar com o Windows"),
                (6, "Sair"),
            ];
            for (id, label) in items {
                let checked = if id == 4 && starts_with_windows {
                    MF_CHECKED
                } else {
                    0
                };
                unsafe {
                    AppendMenuW(menu, MF_STRING | checked, id, wide(label).as_ptr());
                }
            }
            let devices = runtime.vault.devices();
            let device_menu = unsafe { CreatePopupMenu() };
            if !device_menu.is_null() {
                if devices.is_empty() {
                    unsafe {
                        AppendMenuW(
                            device_menu,
                            MF_STRING | MF_GRAYED,
                            0,
                            wide("Nenhum celular autorizado").as_ptr(),
                        );
                    }
                }
                for (index, (_, name)) in devices.iter().enumerate() {
                    unsafe {
                        AppendMenuW(
                            device_menu,
                            MF_STRING,
                            200 + index,
                            wide(&format!("Revogar: {}", name.replace('&', "&&"))).as_ptr(),
                        );
                    }
                }
                unsafe {
                    InsertMenuW(
                        menu,
                        3,
                        MF_BYPOSITION | MF_POPUP,
                        device_menu as usize,
                        wide("Dispositivos").as_ptr(),
                    );
                }
            }
            let mut point = POINT { x: 0, y: 0 };
            unsafe {
                GetCursorPos(&mut point);
                SetForegroundWindow(window);
            }
            let selection = unsafe {
                TrackPopupMenu(
                    menu,
                    TPM_RETURNCMD | TPM_NONOTIFY | TPM_RIGHTBUTTON,
                    point.x,
                    point.y,
                    0,
                    window,
                    ptr::null(),
                )
            };
            unsafe {
                DestroyMenu(menu);
                PostMessageW(window, WM_NULL, 0, 0);
            }
            match selection {
                1 => {
                    let status = runtime.status.lock().map(|s| s.clone()).unwrap_or_default();
                    dialog(
                        &format!(
                            "SofaPad {VERSION}\n{status}\n\nAparelhos autorizados: {}\nInício após login: {}\nArquivo em uso: {}\n\nUse apenas em rede doméstica confiável. Se solicitado, permita o firewall somente em redes privadas.",
                            devices.len(),
                            startup_summary(),
                            std::env::current_exe()
                                .map(|path| path.display().to_string())
                                .unwrap_or_else(|_| "desconhecido".into()),
                        ),
                        MB_OK,
                    );
                }
                2 => {
                    if let Ok(code) = runtime.vault.open_window() {
                        runtime.disconnect();
                        let status = runtime.status.lock().map(|s| s.clone()).unwrap_or_default();
                        if let Ok(identity) = runtime.vault.identity() {
                            dialog(
                                &format!(
                                    "No celular, abra Configurações > Parear PC e siga os passos.\n\n1. Código de conferência (o celular deve mostrar o mesmo):\n      {}\n\n2. Código de uso único (vale 5 minutos):\n      {code}\n\n3. Depois, confirme o nome do celular na janela que abrir neste PC.\n\n{status}\nFechar esta janela mantém o código válido; use Cancelar pareamento no menu para anulá-lo.\n\nIdentidade completa (SHA-256), só para conferência detalhada:\n{}",
                                    check_code(&identity.fingerprint),
                                    identity.fingerprint
                                ),
                                MB_OK,
                            );
                        }
                    }
                }
                3 => runtime.vault.cancel_pairing(),
                4 => {
                    if set_startup(!starts_with_windows).is_err() {
                        dialog(
                            "Não foi possível alterar a inicialização. Mantenha o agente em uma pasta permanente e tente novamente.",
                            MB_OK | MB_ICONERROR,
                        );
                    }
                }
                6 => {
                    runtime.stop();
                    unsafe {
                        DestroyWindow(window);
                    }
                }
                id if id >= 200 && (id as usize) < 200 + devices.len() => {
                    let (device, name) = &devices[id as usize - 200];
                    if dialog(
                        &format!(
                            "Revogar o acesso de «{name}»? O celular precisará de um novo pareamento."
                        ),
                        MB_YESNO | MB_DEFBUTTON2,
                    ) == IDYES
                    {
                        match runtime.vault.revoke(device) {
                            Ok(()) => runtime.disconnect(),
                            Err(_) => {
                                dialog(
                                    "Não foi possível salvar a revogação. Tente novamente.",
                                    MB_OK | MB_ICONERROR,
                                );
                            }
                        }
                    }
                }
                _ => {}
            }
            0
        }
        WM_DESTROY => {
            icon(window, NIM_DELETE);
            runtime.stop();
            unsafe {
                PostQuitMessage(0);
            }
            0
        }
        WM_QUERYENDSESSION => {
            runtime.stop();
            1
        }
        _ => unsafe { DefWindowProcW(window, message, wparam, lparam) },
    }
}
pub fn run(runtime: Arc<Runtime>) -> io::Result<()> {
    RUNTIME
        .set(runtime)
        .map_err(|_| io::Error::other("Bandeja já inicializada"))?;
    let class = wide("SofaPadTray");
    unsafe {
        let instance = GetModuleHandleW(ptr::null());
        let mut definition: WNDCLASSW = std::mem::zeroed();
        definition.lpfnWndProc = Some(procedure);
        definition.hInstance = instance;
        definition.lpszClassName = class.as_ptr();
        if RegisterClassW(&definition) == 0 {
            return Err(io::Error::last_os_error());
        }
        TASKBAR.store(
            RegisterWindowMessageW(wide("TaskbarCreated").as_ptr()),
            Ordering::SeqCst,
        );
        let window = CreateWindowExW(
            0,
            class.as_ptr(),
            wide("SofaPad").as_ptr(),
            0,
            0,
            0,
            0,
            0,
            ptr::null_mut(),
            ptr::null_mut(),
            instance,
            ptr::null(),
        );
        if window.is_null() {
            return Err(io::Error::last_os_error());
        }
        // Logo após o login a barra de tarefas pode ainda não aceitar ícones: insistir por 30 s.
        let mut added = icon(window, NIM_ADD);
        for _ in 0..60 {
            if added {
                break;
            }
            std::thread::sleep(std::time::Duration::from_millis(500));
            added = icon(window, NIM_ADD);
        }
        if !added {
            DestroyWindow(window);
            return Err(io::Error::other(
                "Não foi possível criar o ícone de bandeja",
            ));
        }
        #[cfg(test)]
        SetTimer(window, 1, 500, None);
        let mut message: MSG = std::mem::zeroed();
        loop {
            let result = GetMessageW(&mut message, ptr::null_mut(), 0, 0);
            if result == 0 {
                break;
            }
            if result == -1 {
                DestroyWindow(window);
                return Err(io::Error::last_os_error());
            }
            TranslateMessage(&message);
            DispatchMessageW(&message);
        }
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::sync::{Mutex, atomic::AtomicBool};
    #[test]
    #[ignore = "Teste opt-in da bandeja real: cria e remove somente seu ícone, sem servidor ou entrada"]
    fn native_tray_starts_and_cleans_up() {
        let vault = Arc::new(
            crate::pairing::Vault::open(None, Box::new(|_| Ok(())), Box::new(|_| false)).unwrap(),
        );
        let runtime = Arc::new(Runtime {
            access: Arc::new(crate::secure::Access::persistent(vault.clone())),
            vault,
            status: Mutex::new("Teste local".into()),
            stop: AtomicBool::new(false),
        });
        run(runtime.clone()).unwrap();
        assert!(runtime.stop.load(Ordering::SeqCst));
    }
}
