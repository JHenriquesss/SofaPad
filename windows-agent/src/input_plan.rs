use crate::protocol::{Button, ButtonState, Command, Media, Shortcut};

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Event {
    Move(i32, i32),
    Button(Button, bool),
    Wheel(i32),
    Key(u16, bool),
    Unicode(u16, bool),
}

pub fn plan(command: &Command) -> Vec<Event> {
    match command {
        Command::MouseMove { dx, dy } => vec![Event::Move(dx.round() as i32, dy.round() as i32)],
        Command::MouseButton { button, state } => {
            vec![Event::Button(*button, *state == ButtonState::Down)]
        }
        Command::MouseScroll { vertical } => vec![Event::Wheel((vertical * 120.0).round() as i32)],
        Command::TextInsert { text } => text
            .encode_utf16()
            .flat_map(|unit| [Event::Unicode(unit, true), Event::Unicode(unit, false)])
            .collect(),
        Command::Shortcut { name } => {
            let keys: &[u16] = match name {
                Shortcut::AltTab => &[0x12, 0x09],
                Shortcut::CtrlC => &[0x11, 0x43],
                Shortcut::CtrlV => &[0x11, 0x56],
                Shortcut::CtrlA => &[0x11, 0x41],
                Shortcut::Enter => &[0x0D],
                Shortcut::Esc => &[0x1B],
                Shortcut::Backspace => &[0x08],
            };
            keys.iter()
                .map(|key| Event::Key(*key, true))
                .chain(keys.iter().rev().map(|key| Event::Key(*key, false)))
                .collect()
        }
        Command::Media { name } => {
            let key = match name {
                Media::PlayPause => 0xB3,
                Media::VolumeUp => 0xAF,
                Media::VolumeDown => 0xAE,
            };
            vec![Event::Key(key, true), Event::Key(key, false)]
        }
        Command::ReleaseAll | Command::Ping => Vec::new(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    #[test]
    fn unicode_preserves_surrogates_without_enter_or_clipboard_shortcut() {
        let events = plan(&Command::TextInsert {
            text: "Olá 😀".into(),
        });
        assert_eq!(events.len(), 12);
        assert_eq!(
            &events[8..],
            &[
                Event::Unicode(0xD83D, true),
                Event::Unicode(0xD83D, false),
                Event::Unicode(0xDE00, true),
                Event::Unicode(0xDE00, false)
            ]
        );
        assert!(
            events
                .iter()
                .all(|event| matches!(event, Event::Unicode(_, _)))
        );
    }
    #[test]
    fn shortcut_releases_modifiers_in_reverse_order() {
        assert_eq!(
            plan(&Command::Shortcut {
                name: Shortcut::AltTab
            }),
            vec![
                Event::Key(0x12, true),
                Event::Key(9, true),
                Event::Key(9, false),
                Event::Key(0x12, false)
            ]
        );
    }
    #[test]
    fn wheel_uses_windows_units() {
        assert_eq!(
            plan(&Command::MouseScroll { vertical: -2.0 }),
            vec![Event::Wheel(-240)]
        );
    }
}
