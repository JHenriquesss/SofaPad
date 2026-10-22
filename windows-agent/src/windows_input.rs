use crate::{
    input::{InputController, InputError},
    input_plan::{Event, plan},
    protocol::{Button, Command},
};
use std::collections::BTreeSet;
use windows_sys::Win32::UI::Input::KeyboardAndMouse::*;

#[derive(Default)]
pub struct WindowsInput {
    left: bool,
    right: bool,
    keys: BTreeSet<u16>,
    unicode: BTreeSet<u16>,
    remainder_x: f64,
    remainder_y: f64,
    wheel_remainder: f64,
}

fn native(event: Event) -> INPUT {
    let mut input: INPUT = unsafe { std::mem::zeroed() };
    match event {
        Event::Move(x, y) => {
            input.r#type = INPUT_MOUSE;
            input.Anonymous.mi = MOUSEINPUT {
                dx: x,
                dy: y,
                mouseData: 0,
                dwFlags: MOUSEEVENTF_MOVE,
                time: 0,
                dwExtraInfo: 0,
            };
        }
        Event::Button(button, down) => {
            input.r#type = INPUT_MOUSE;
            let flags = match (button, down) {
                (Button::Left, true) => MOUSEEVENTF_LEFTDOWN,
                (Button::Left, false) => MOUSEEVENTF_LEFTUP,
                (Button::Right, true) => MOUSEEVENTF_RIGHTDOWN,
                (Button::Right, false) => MOUSEEVENTF_RIGHTUP,
            };
            input.Anonymous.mi = MOUSEINPUT {
                dx: 0,
                dy: 0,
                mouseData: 0,
                dwFlags: flags,
                time: 0,
                dwExtraInfo: 0,
            };
        }
        Event::Wheel(delta) => {
            input.r#type = INPUT_MOUSE;
            input.Anonymous.mi = MOUSEINPUT {
                dx: 0,
                dy: 0,
                mouseData: delta as u32,
                dwFlags: MOUSEEVENTF_WHEEL,
                time: 0,
                dwExtraInfo: 0,
            };
        }
        Event::Key(key, down) | Event::Unicode(key, down) => {
            let unicode = matches!(event, Event::Unicode(_, _));
            input.r#type = INPUT_KEYBOARD;
            input.Anonymous.ki = KEYBDINPUT {
                wVk: if unicode { 0 } else { key },
                wScan: if unicode { key } else { 0 },
                dwFlags: (if unicode { KEYEVENTF_UNICODE } else { 0 })
                    | (if down { 0 } else { KEYEVENTF_KEYUP }),
                time: 0,
                dwExtraInfo: 0,
            };
        }
    }
    input
}

impl WindowsInput {
    fn track(&mut self, event: Event) {
        match event {
            Event::Button(Button::Left, down) => self.left = down,
            Event::Button(Button::Right, down) => self.right = down,
            Event::Key(key, down) => {
                if down {
                    self.keys.insert(key);
                } else {
                    self.keys.remove(&key);
                }
            }
            Event::Unicode(key, down) => {
                if down {
                    self.unicode.insert(key);
                } else {
                    self.unicode.remove(&key);
                }
            }
            _ => (),
        }
    }

    fn send(&mut self, events: &[Event]) -> Result<(), InputError> {
        if events.is_empty() {
            return Ok(());
        }
        let native: Vec<INPUT> = events.iter().copied().map(native).collect();
        // Registrar os downs antes da chamada permite soltar até uma inserção parcial.
        for event in events {
            if matches!(
                event,
                Event::Key(_, true) | Event::Unicode(_, true) | Event::Button(_, true)
            ) {
                self.track(*event);
            }
        }
        let sent = unsafe {
            SendInput(
                native.len() as u32,
                native.as_ptr(),
                std::mem::size_of::<INPUT>() as i32,
            )
        } as usize;
        for event in events.iter().take(sent) {
            self.track(*event);
        }
        if sent != native.len() {
            let _ = self.release_all();
            return Err(InputError);
        }
        Ok(())
    }
}

impl InputController for WindowsInput {
    fn apply(&mut self, command: &Command) -> Result<(), InputError> {
        let events = match command {
            Command::MouseMove { dx, dy } => {
                self.remainder_x += dx;
                self.remainder_y += dy;
                let (x, y) = (
                    self.remainder_x.trunc() as i32,
                    self.remainder_y.trunc() as i32,
                );
                self.remainder_x -= x as f64;
                self.remainder_y -= y as f64;
                vec![Event::Move(x, y)]
            }
            Command::MouseScroll { vertical } => {
                self.wheel_remainder += vertical * 120.0;
                let delta = self.wheel_remainder.trunc() as i32;
                self.wheel_remainder -= delta as f64;
                vec![Event::Wheel(delta)]
            }
            Command::Shortcut { .. } | Command::TextInsert { .. } | Command::Media { .. } => {
                // Não interferir com modificadores físicos do usuário.
                if [VK_SHIFT, VK_CONTROL, VK_MENU, VK_LWIN, VK_RWIN]
                    .iter()
                    .any(|key| unsafe { GetAsyncKeyState(*key as i32) } < 0)
                {
                    return Err(InputError);
                }
                plan(command)
            }
            Command::ReleaseAll => return self.release_all(),
            _ => plan(command),
        };
        self.send(&events)
    }

    fn release_all(&mut self) -> Result<(), InputError> {
        let mut events = Vec::new();
        if self.left {
            events.push(Event::Button(Button::Left, false));
        }
        if self.right {
            events.push(Event::Button(Button::Right, false));
        }
        events.extend(self.keys.iter().rev().map(|key| Event::Key(*key, false)));
        events.extend(self.unicode.iter().map(|key| Event::Unicode(*key, false)));
        self.remainder_x = 0.0;
        self.remainder_y = 0.0;
        self.wheel_remainder = 0.0;
        if events.is_empty() {
            return Ok(());
        }
        let inputs: Vec<INPUT> = events.iter().copied().map(native).collect();
        let sent = unsafe {
            SendInput(
                inputs.len() as u32,
                inputs.as_ptr(),
                std::mem::size_of::<INPUT>() as i32,
            )
        } as usize;
        for event in events.iter().take(sent) {
            self.track(*event);
        }
        if sent == inputs.len() {
            Ok(())
        } else {
            Err(InputError)
        }
    }
}

impl Drop for WindowsInput {
    fn drop(&mut self) {
        let _ = self.release_all();
    }
}
