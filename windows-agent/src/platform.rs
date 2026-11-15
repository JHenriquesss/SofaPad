use std::{
    io,
    path::{Path, PathBuf},
    ptr,
};
use windows_sys::Win32::{
    Foundation::LocalFree,
    Security::Cryptography::{
        CRYPT_INTEGER_BLOB, CRYPTPROTECT_UI_FORBIDDEN, CryptProtectData, CryptUnprotectData,
    },
    Storage::FileSystem::{MOVEFILE_REPLACE_EXISTING, MOVEFILE_WRITE_THROUGH, MoveFileExW},
    System::Registry::*,
    UI::WindowsAndMessaging::MessageBoxW,
};

pub fn wide(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(Some(0)).collect()
}
pub fn dialog(text: &str, flags: u32) -> i32 {
    unsafe {
        MessageBoxW(
            ptr::null_mut(),
            wide(text).as_ptr(),
            wide("SofaPad").as_ptr(),
            flags,
        )
    }
}
pub fn protect(bytes: &[u8], decrypt: bool) -> io::Result<Vec<u8>> {
    if bytes.len() > 131072 {
        return Err(io::Error::other("Registro excedeu limite"));
    }
    let input = CRYPT_INTEGER_BLOB {
        cbData: bytes.len() as u32,
        pbData: bytes.as_ptr().cast_mut(),
    };
    let mut output: CRYPT_INTEGER_BLOB = unsafe { std::mem::zeroed() };
    let ok = unsafe {
        if decrypt {
            CryptUnprotectData(
                &input,
                ptr::null_mut(),
                ptr::null(),
                ptr::null(),
                ptr::null(),
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output,
            )
        } else {
            CryptProtectData(
                &input,
                ptr::null(),
                ptr::null(),
                ptr::null(),
                ptr::null(),
                CRYPTPROTECT_UI_FORBIDDEN,
                &mut output,
            )
        }
    };
    if ok == 0 {
        return Err(io::Error::other(
            "DPAPI não conseguiu proteger/abrir as credenciais deste usuário",
        ));
    }
    let value =
        unsafe { std::slice::from_raw_parts(output.pbData, output.cbData as usize).to_vec() };
    unsafe {
        std::ptr::write_bytes(output.pbData, 0, output.cbData as usize);
        LocalFree(output.pbData.cast());
    }
    Ok(value)
}
pub fn load(path: &Path) -> io::Result<Option<Vec<u8>>> {
    match std::fs::metadata(path) {
        Ok(meta) if meta.len() <= 131072 => protect(&std::fs::read(path)?, true).map(Some),
        Ok(_) => Err(io::Error::other("Registro privado excedeu limite")),
        Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(None),
        Err(e) => Err(e),
    }
}
pub fn save(path: &Path, bytes: &[u8]) -> io::Result<()> {
    use std::io::Write;
    let encrypted = protect(bytes, false)?;
    let temporary = path.with_extension("tmp");
    let mut file = std::fs::File::create(&temporary)?;
    file.write_all(&encrypted)?;
    file.sync_all()?;
    drop(file);
    let source = wide(&temporary.to_string_lossy());
    let target = wide(&path.to_string_lossy());
    if unsafe {
        MoveFileExW(
            source.as_ptr(),
            target.as_ptr(),
            MOVEFILE_REPLACE_EXISTING | MOVEFILE_WRITE_THROUGH,
        )
    } == 0
    {
        return Err(io::Error::last_os_error());
    }
    Ok(())
}
const RUN_KEY: &str = "Software\\Microsoft\\Windows\\CurrentVersion\\Run";
const RUN_VALUE: &str = "SofaPad";

pub fn data_directory() -> io::Result<PathBuf> {
    let directory = PathBuf::from(
        std::env::var_os("LOCALAPPDATA")
            .ok_or_else(|| io::Error::other("Pasta do usuário indisponível"))?,
    )
    .join("SofaPad");
    std::fs::create_dir_all(&directory)?;
    Ok(directory)
}
fn startup_opt_out() -> io::Result<PathBuf> {
    Ok(data_directory()?.join("inicio-automatico-desativado"))
}
fn startup_command() -> io::Result<String> {
    let exe = std::env::current_exe()?;
    let command = format!("\"{}\" --household", exe.display());
    if command.encode_utf16().count() > 259 {
        return Err(io::Error::other(
            "Mova o agente para uma pasta de caminho mais curto",
        ));
    }
    Ok(command)
}
fn read_run(name: &str) -> Option<String> {
    let (key, name) = (wide(RUN_KEY), wide(name));
    let mut size = 0u32;
    let read = |data: *mut core::ffi::c_void, size: &mut u32| unsafe {
        RegGetValueW(
            HKEY_CURRENT_USER,
            key.as_ptr(),
            name.as_ptr(),
            RRF_RT_REG_SZ,
            ptr::null_mut(),
            data,
            size,
        )
    };
    if read(ptr::null_mut(), &mut size) != 0 {
        return None;
    }
    let mut buffer = vec![0u16; (size as usize).div_ceil(2)];
    if read(buffer.as_mut_ptr().cast(), &mut size) != 0 {
        return None;
    }
    let end = buffer.iter().position(|c| *c == 0).unwrap_or(buffer.len());
    Some(String::from_utf16_lossy(&buffer[..end]))
}
pub fn startup_enabled() -> bool {
    read_run(RUN_VALUE).is_some()
}
pub fn set_startup(enabled: bool) -> io::Result<()> {
    let command = if enabled {
        Some(startup_command()?)
    } else {
        None
    };
    choose_startup(RUN_VALUE, &startup_opt_out()?, command.as_deref())
}
fn choose_startup(name: &str, marker: &Path, command: Option<&str>) -> io::Result<()> {
    if command.is_some() {
        write_run(name, command)?;
        match std::fs::remove_file(marker) {
            Err(e) if e.kind() != io::ErrorKind::NotFound => Err(e),
            _ => Ok(()),
        }
    } else {
        // A escolha é gravada antes: se a remoção falhar, o próximo início não reativa.
        std::fs::write(marker, b"")?;
        write_run(name, None)
    }
}
/// Iniciar após o login é o padrão: sem a escolha explícita de desativar, o registro
/// aponta para o executável em uso, inclusive depois de trocar de versão ou de pasta.
pub fn ensure_startup() -> io::Result<()> {
    // Builds de desenvolvimento não se registram: `cargo run` apontaria para target/debug.
    if cfg!(debug_assertions) {
        return Ok(());
    }
    keep_startup(RUN_VALUE, &startup_opt_out()?, &startup_command()?)
}
fn keep_startup(name: &str, marker: &Path, command: &str) -> io::Result<()> {
    if marker.exists() {
        return Ok(());
    }
    sync_run(name, command)
}
fn sync_run(name: &str, command: &str) -> io::Result<()> {
    if read_run(name).as_deref() == Some(command) {
        return Ok(());
    }
    write_run(name, Some(command))
}
fn write_run(name: &str, command: Option<&str>) -> io::Result<()> {
    let enabled = command.is_some();
    let command = command.map(wide);
    let mut key = ptr::null_mut();
    let status = unsafe {
        RegCreateKeyExW(
            HKEY_CURRENT_USER,
            wide(RUN_KEY).as_ptr(),
            0,
            ptr::null(),
            0,
            KEY_SET_VALUE,
            ptr::null(),
            &mut key,
            ptr::null_mut(),
        )
    };
    if status != 0 {
        return Err(io::Error::from_raw_os_error(status as i32));
    }
    let result = if let Some(value) = command {
        unsafe {
            RegSetValueExW(
                key,
                wide(name).as_ptr(),
                0,
                REG_SZ,
                value.as_ptr().cast(),
                (value.len() * 2) as u32,
            )
        }
    } else {
        unsafe { RegDeleteValueW(key, wide(name).as_ptr()) }
    };
    unsafe {
        RegCloseKey(key);
    }
    if result != 0 && (result != 2 || enabled) {
        return Err(io::Error::from_raw_os_error(result as i32));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn dpapi_roundtrip_and_tamper_rejection() {
        let value = b"credenciais ficticias para teste DPAPI";
        let mut encrypted = protect(value, false).unwrap();
        assert!(!encrypted.windows(value.len()).any(|w| w == value));
        assert_eq!(protect(&encrypted, true).unwrap(), value);
        let last = encrypted.len() - 1;
        encrypted[last] ^= 1;
        assert!(protect(&encrypted, true).is_err());
    }
    #[test]
    fn startup_entry_roundtrip_follows_the_current_command() {
        // Valor próprio de teste: não toca na entrada real do SofaPad deste usuário.
        let name = "SofaPadTesteAutomatico";
        write_run(name, None).unwrap();
        assert_eq!(read_run(name), None);
        sync_run(name, "\"C:\\Antigo\\SofaPad.exe\" --household").unwrap();
        assert_eq!(
            read_run(name).as_deref(),
            Some("\"C:\\Antigo\\SofaPad.exe\" --household")
        );
        sync_run(name, "\"C:\\Novo\\SofaPad ação.exe\" --household").unwrap();
        assert_eq!(
            read_run(name).as_deref(),
            Some("\"C:\\Novo\\SofaPad ação.exe\" --household")
        );
        write_run(name, None).unwrap();
        assert_eq!(read_run(name), None);
        write_run(name, None).unwrap();
    }
    #[test]
    fn startup_is_the_default_until_the_user_turns_it_off() {
        let name = "SofaPadTesteEscolha";
        let marker = std::env::temp_dir().join(format!("sofapad-teste-{}", std::process::id()));
        let _ = std::fs::remove_file(&marker);
        write_run(name, None).unwrap();
        // Primeira abertura: registra sozinho.
        keep_startup(name, &marker, "\"C:\\A\\SofaPad.exe\" --household").unwrap();
        assert_eq!(
            read_run(name).as_deref(),
            Some("\"C:\\A\\SofaPad.exe\" --household")
        );
        // Desmarcar no menu remove a entrada e a escolha sobrevive às próximas aberturas.
        choose_startup(name, &marker, None).unwrap();
        assert_eq!(read_run(name), None);
        keep_startup(name, &marker, "\"C:\\A\\SofaPad.exe\" --household").unwrap();
        assert_eq!(read_run(name), None);
        // Marcar de novo volta ao padrão, acompanhando o executável em uso.
        choose_startup(name, &marker, Some("\"C:\\A\\SofaPad.exe\" --household")).unwrap();
        assert!(!marker.exists());
        keep_startup(name, &marker, "\"C:\\B\\SofaPad.exe\" --household").unwrap();
        assert_eq!(
            read_run(name).as_deref(),
            Some("\"C:\\B\\SofaPad.exe\" --household")
        );
        write_run(name, None).unwrap();
    }
    #[test]
    fn startup_command_runs_this_executable_in_household_mode() {
        let command = startup_command().unwrap();
        let exe = std::env::current_exe().unwrap();
        assert_eq!(command, format!("\"{}\" --household", exe.display()));
    }
}
