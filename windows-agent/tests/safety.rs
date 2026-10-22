use sofapad_agent::{
    input::{InputController, InputError, SimulatedInput},
    protocol::{self, ButtonState, Command},
    session::{Session, SessionError},
};
use std::{
    sync::{Arc, Mutex},
    time::Duration,
};

const DOWN: &str = r#"{"v":1,"type":"mouse_button","seq":1,"button":"left","state":"down"}"#;

#[test]
fn shared_valid_fixtures_round_trip() {
    let fixtures: Vec<serde_json::Value> =
        serde_json::from_str(include_str!("../../protocol/fixtures/valid.json")).unwrap();
    for fixture in fixtures {
        let decoded = protocol::decode(&fixture.to_string()).expect("fixture válida");
        assert_eq!(serde_json::to_value(decoded).unwrap(), fixture);
    }
}

#[test]
fn shared_invalid_fixtures_are_rejected() {
    let fixtures: Vec<serde_json::Value> =
        serde_json::from_str(include_str!("../../protocol/fixtures/invalid.json")).unwrap();
    for fixture in fixtures {
        assert!(protocol::decode(&fixture.to_string()).is_err());
    }
    for raw in [
        "",
        "[]",
        "null",
        "{",
        r#"{"v":1,"type":"mouse_move","seq":1,"dx":1e999,"dy":0}"#,
    ] {
        assert!(protocol::decode(raw).is_err());
    }
}

#[test]
fn text_limit_counts_utf8_bytes_and_never_adds_enter() {
    let accepted = serde_json::json!({"v":1,"type":"text_insert","seq":1,"text":"é".repeat(2048)});
    let decoded = protocol::decode(&accepted.to_string()).unwrap();
    assert!(
        matches!(decoded.command, Command::TextInsert { text } if text.len() == 4096 && !text.ends_with('\n'))
    );
    let rejected = serde_json::json!({"v":1,"type":"text_insert","seq":1,"text":"é".repeat(2049)});
    assert_eq!(
        protocol::decode(&rejected.to_string()).err(),
        Some(protocol::ProtocolError::TextTooLarge)
    );
}

#[test]
fn invalid_message_releases_drag_and_closes_session() {
    let mut session = Session::new(SimulatedInput::default());
    session.handle(DOWN, Duration::ZERO).unwrap();
    assert!(session.controller().left_down);
    assert!(session.handle("invalid", Duration::ZERO).is_err());
    assert!(!session.controller().left_down);
    assert!(matches!(
        session.handle(DOWN, Duration::ZERO),
        Err(SessionError::Closed)
    ));
}

#[test]
fn replay_releases_buttons_instead_of_repeating_input() {
    let mut session = Session::new(SimulatedInput::default());
    session.handle(DOWN, Duration::ZERO).unwrap();
    assert!(matches!(
        session.handle(DOWN, Duration::ZERO),
        Err(SessionError::Sequence)
    ));
    assert!(!session.controller().left_down);
    assert_eq!(session.controller().applied, 1);
}

#[test]
fn rate_limit_releases_drag_and_recovers_only_with_new_session() {
    let mut session = Session::new(SimulatedInput::default());
    session.handle(DOWN, Duration::ZERO).unwrap();
    for seq in 2..=240 {
        session
            .handle(
                &format!(r#"{{"v":1,"type":"ping","seq":{seq}}}"#),
                Duration::ZERO,
            )
            .unwrap();
    }
    assert!(matches!(
        session.handle(r#"{"v":1,"type":"ping","seq":241}"#, Duration::ZERO),
        Err(SessionError::RateLimit)
    ));
    assert!(!session.controller().left_down);
}

#[test]
fn rate_window_resets_after_one_second() {
    let mut session = Session::new(SimulatedInput::default());
    for seq in 1..=300 {
        session
            .handle(
                &format!(r#"{{"v":1,"type":"ping","seq":{seq}}}"#),
                Duration::from_millis(seq * 10),
            )
            .unwrap();
    }
}

struct FailingInput(Arc<Mutex<(bool, u32)>>);
impl InputController for FailingInput {
    fn apply(&mut self, command: &Command) -> Result<(), InputError> {
        if let Command::MouseButton {
            state: ButtonState::Down,
            ..
        } = command
        {
            self.0.lock().unwrap().0 = true;
        }
        Err(InputError)
    }
    fn release_all(&mut self) -> Result<(), InputError> {
        let mut state = self.0.lock().unwrap();
        state.0 = false;
        state.1 += 1;
        Ok(())
    }
}

#[test]
fn partial_backend_failure_and_drop_release_state() {
    let state = Arc::new(Mutex::new((false, 0)));
    {
        let mut session = Session::new(FailingInput(state.clone()));
        assert!(matches!(
            session.handle(DOWN, Duration::ZERO),
            Err(SessionError::Input)
        ));
        assert!(!state.lock().unwrap().0);
    }
    assert!(state.lock().unwrap().1 >= 2);
}

#[test]
fn explicit_release_and_disconnect_clear_both_buttons() {
    let mut session = Session::new(SimulatedInput::default());
    session.handle(DOWN, Duration::ZERO).unwrap();
    session
        .handle(
            r#"{"v":1,"type":"mouse_button","seq":2,"button":"right","state":"down"}"#,
            Duration::ZERO,
        )
        .unwrap();
    session
        .handle(r#"{"v":1,"type":"release_all","seq":3}"#, Duration::ZERO)
        .unwrap();
    assert!(!session.controller().left_down && !session.controller().right_down);
    session.close();
    assert!(session.controller().releases >= 2);
}
