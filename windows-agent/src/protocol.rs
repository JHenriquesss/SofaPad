use serde::{Deserialize, Serialize};

pub const MAX_MESSAGE_BYTES: usize = 8192;
pub const MAX_TEXT_BYTES: usize = 4096;
pub const MAX_SEQ: u64 = 9_007_199_254_740_991;
pub const MAX_COMMANDS_PER_SECOND: u32 = 240;

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum Button {
    Left,
    Right,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "snake_case")]
pub enum ButtonState {
    Down,
    Up,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Shortcut {
    AltTab,
    CtrlC,
    CtrlV,
    CtrlA,
    Enter,
    Esc,
    Backspace,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq, Deserialize, Serialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum Media {
    PlayPause,
    VolumeUp,
    VolumeDown,
}

#[derive(Clone, PartialEq, Deserialize, Serialize)]
#[serde(tag = "type", rename_all = "snake_case")]
pub enum Command {
    MouseMove { dx: f64, dy: f64 },
    MouseButton { button: Button, state: ButtonState },
    MouseScroll { vertical: f64 },
    TextInsert { text: String },
    Shortcut { name: Shortcut },
    Media { name: Media },
    ReleaseAll,
    Ping,
}

// Sem Debug: mensagens podem conter texto privado.
#[derive(Clone, Deserialize, Serialize)]
pub struct Envelope {
    pub v: u8,
    pub seq: u64,
    #[serde(flatten)]
    pub command: Command,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ProtocolError {
    InvalidMessage,
    UnsupportedVersion,
    InvalidSequence,
    OutOfRange,
    TextTooLarge,
}

impl ProtocolError {
    pub fn code(self) -> &'static str {
        match self {
            Self::InvalidMessage => "INVALID_MESSAGE",
            Self::UnsupportedVersion => "UNSUPPORTED_VERSION",
            Self::InvalidSequence => "INVALID_SEQUENCE",
            Self::OutOfRange => "OUT_OF_RANGE",
            Self::TextTooLarge => "TEXT_TOO_LARGE",
        }
    }
}

pub fn decode(raw: &str) -> Result<Envelope, ProtocolError> {
    if raw.len() > MAX_MESSAGE_BYTES {
        return Err(ProtocolError::InvalidMessage);
    }
    let value: serde_json::Value =
        serde_json::from_str(raw).map_err(|_| ProtocolError::InvalidMessage)?;
    let obj = value.as_object().ok_or(ProtocolError::InvalidMessage)?;
    let extra: &[&str] = match obj.get("type").and_then(|v| v.as_str()) {
        Some("mouse_move") => &["dx", "dy"],
        Some("mouse_button") => &["button", "state"],
        Some("mouse_scroll") => &["vertical"],
        Some("text_insert") => &["text"],
        Some("shortcut" | "media") => &["name"],
        Some("release_all" | "ping") => &[],
        _ => return Err(ProtocolError::InvalidMessage),
    };
    if obj
        .keys()
        .any(|key| !["v", "seq", "type"].contains(&key.as_str()) && !extra.contains(&key.as_str()))
    {
        return Err(ProtocolError::InvalidMessage);
    }
    let envelope: Envelope =
        serde_json::from_value(value).map_err(|_| ProtocolError::InvalidMessage)?;
    validate(&envelope)?;
    Ok(envelope)
}

pub fn validate(envelope: &Envelope) -> Result<(), ProtocolError> {
    if envelope.v != 1 {
        return Err(ProtocolError::UnsupportedVersion);
    }
    if envelope.seq == 0 || envelope.seq > MAX_SEQ {
        return Err(ProtocolError::InvalidSequence);
    }
    let in_range = |n: f64, max: f64| n.is_finite() && n.abs() <= max;
    match &envelope.command {
        Command::MouseMove { dx, dy } if !in_range(*dx, 2048.0) || !in_range(*dy, 2048.0) => {
            Err(ProtocolError::OutOfRange)
        }
        Command::MouseScroll { vertical } if !in_range(*vertical, 120.0) => {
            Err(ProtocolError::OutOfRange)
        }
        Command::TextInsert { text } if text.len() > MAX_TEXT_BYTES => {
            Err(ProtocolError::TextTooLarge)
        }
        _ => Ok(()),
    }
}
