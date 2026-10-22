use std::{io, path::Path, ptr};
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
pub fn startup_enabled() -> bool {
    let mut size = 0;
    unsafe {
        RegGetValueW(
            HKEY_CURRENT_USER,
            wide(RUN_KEY).as_ptr(),
            wide("SofaPad").as_ptr(),
            RRF_RT_REG_SZ,
            ptr::null_mut(),
            ptr::null_mut(),
            &mut size,
        ) == 0
    }
}
pub fn set_startup(enabled: bool) -> io::Result<()> {
    let command = if enabled {
        let exe = std::env::current_exe()?;
        let command = format!("\"{}\" --household", exe.display());
        if command.encode_utf16().count() > 259 {
            return Err(io::Error::other(
                "Mova o agente para uma pasta de caminho mais curto",
            ));
        }
        Some(wide(&command))
    } else {
        None
    };
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
                wide("SofaPad").as_ptr(),
                0,
                REG_SZ,
                value.as_ptr().cast(),
                (value.len() * 2) as u32,
            )
        }
    } else {
        unsafe { RegDeleteValueW(key, wide("SofaPad").as_ptr()) }
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
}
