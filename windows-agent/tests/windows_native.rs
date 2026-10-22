#![cfg(windows)]
use sofapad_agent::{
    input::InputController,
    protocol::{Button, ButtonState, Command},
    windows_input::WindowsInput,
};
use std::{
    ptr::{null, null_mut},
    time::{Duration, Instant},
};
use windows_sys::Win32::{
    Foundation::{HWND, POINT},
    UI::{
        Input::KeyboardAndMouse::{GetAsyncKeyState, SetFocus, VK_LBUTTON},
        WindowsAndMessaging::*,
    },
};

fn wide(value: &str) -> Vec<u16> {
    value.encode_utf16().chain(Some(0)).collect()
}

struct Fixture {
    window: HWND,
    original: HWND,
    cursor: POINT,
}
impl Drop for Fixture {
    fn drop(&mut self) {
        unsafe {
            DestroyWindow(self.window);
            SetCursorPos(self.cursor.x, self.cursor.y);
            if !self.original.is_null() {
                SetForegroundWindow(self.original);
            }
        }
    }
}

#[test]
#[ignore = "Teste interativo opt-in: usa somente janela de teste própria e restaura foco/cursor"]
fn real_unicode_and_drag_cleanup_preserve_target_focus() {
    unsafe {
        let original = GetForegroundWindow();
        let mut cursor = POINT { x: 0, y: 0 };
        GetCursorPos(&mut cursor);
        let window = CreateWindowExW(
            0,
            wide("STATIC").as_ptr(),
            wide("SofaPad — teste isolado").as_ptr(),
            WS_OVERLAPPEDWINDOW | WS_VISIBLE,
            100,
            100,
            420,
            260,
            null_mut(),
            null_mut(),
            null_mut(),
            null(),
        );
        assert!(!window.is_null());
        let fixture = Fixture {
            window,
            original,
            cursor,
        };
        let edit = CreateWindowExW(
            0,
            wide("EDIT").as_ptr(),
            wide("").as_ptr(),
            WS_CHILD | WS_VISIBLE | ES_MULTILINE as u32,
            10,
            10,
            380,
            180,
            window,
            null_mut(),
            null_mut(),
            null(),
        );
        assert!(!edit.is_null());
        SetForegroundWindow(window);
        SetFocus(edit);
        assert_eq!(
            GetForegroundWindow(),
            window,
            "Sem foco na janela própria: não injetar entrada"
        );
        let mut controller = WindowsInput::default();
        let expected = "Olá, ação! 😀";
        controller
            .apply(&Command::TextInsert {
                text: expected.into(),
            })
            .unwrap();
        let started = Instant::now();
        let mut buffer = [0u16; 128];
        loop {
            let mut msg = std::mem::zeroed();
            while PeekMessageW(&mut msg, null_mut(), 0, 0, PM_REMOVE) != 0 {
                TranslateMessage(&msg);
                DispatchMessageW(&msg);
            }
            let length = GetWindowTextW(edit, buffer.as_mut_ptr(), buffer.len() as i32) as usize;
            if String::from_utf16_lossy(&buffer[..length]) == expected {
                break;
            }
            assert!(
                started.elapsed() < Duration::from_secs(3),
                "Texto Unicode não chegou à janela de teste"
            );
            std::thread::sleep(Duration::from_millis(5));
        }
        assert_eq!(GetForegroundWindow(), window);
        SetCursorPos(160, 160);
        controller
            .apply(&Command::MouseButton {
                button: Button::Left,
                state: ButtonState::Down,
            })
            .unwrap();
        controller
            .apply(&Command::MouseMove { dx: 4.0, dy: 2.0 })
            .unwrap();
        controller.release_all().unwrap();
        std::thread::sleep(Duration::from_millis(30));
        assert!(GetAsyncKeyState(VK_LBUTTON as i32) >= 0);
        assert_eq!(GetForegroundWindow(), window);
        drop(controller);
        drop(fixture);
        if !original.is_null() {
            assert_eq!(
                GetForegroundWindow(),
                original,
                "Restaurar foco original após teste"
            );
        }
        let mut restored = POINT { x: 0, y: 0 };
        GetCursorPos(&mut restored);
        assert_eq!((restored.x, restored.y), (cursor.x, cursor.y));
    }
}
