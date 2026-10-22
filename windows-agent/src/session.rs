use crate::{
    input::InputController,
    protocol::{self, Command, Envelope, MAX_COMMANDS_PER_SECOND},
};
use std::time::Duration;

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum SessionError {
    Protocol(protocol::ProtocolError),
    Sequence,
    RateLimit,
    Input,
    Closed,
}

impl SessionError {
    pub fn code(self) -> &'static str {
        match self {
            Self::Protocol(error) => error.code(),
            Self::Sequence => "INVALID_SEQUENCE",
            Self::RateLimit => "RATE_LIMIT",
            Self::Input => "INPUT_ERROR",
            Self::Closed => "SESSION_CLOSED",
        }
    }
}

pub struct Session<C: InputController> {
    controller: C,
    last_seq: u64,
    window: Duration,
    count: u32,
    closed: bool,
}

impl<C: InputController> Session<C> {
    pub fn new(controller: C) -> Self {
        Self {
            controller,
            last_seq: 0,
            window: Duration::ZERO,
            count: 0,
            closed: false,
        }
    }

    pub fn controller(&self) -> &C {
        &self.controller
    }

    pub fn handle(&mut self, raw: &str, elapsed: Duration) -> Result<Envelope, SessionError> {
        if self.closed {
            return Err(SessionError::Closed);
        }
        let result = self.handle_inner(raw, elapsed);
        if result.is_err() {
            self.close();
        }
        result
    }

    fn handle_inner(&mut self, raw: &str, elapsed: Duration) -> Result<Envelope, SessionError> {
        let envelope = protocol::decode(raw).map_err(SessionError::Protocol)?;
        if envelope.seq <= self.last_seq {
            return Err(SessionError::Sequence);
        }
        if elapsed.saturating_sub(self.window) >= Duration::from_secs(1) {
            self.window = elapsed;
            self.count = 0;
        }
        self.count += 1;
        if self.count > MAX_COMMANDS_PER_SECOND {
            return Err(SessionError::RateLimit);
        }
        match &envelope.command {
            Command::ReleaseAll => self.controller.release_all(),
            Command::Ping => Ok(()),
            command => self.controller.apply(command),
        }
        .map_err(|_| SessionError::Input)?;
        self.last_seq = envelope.seq;
        Ok(envelope)
    }

    pub fn close(&mut self) {
        self.closed = true;
        // Tenta novamente mesmo se já fechada: o backend real poderá ter falhado parcialmente.
        let _ = self.controller.release_all();
    }
}

impl<C: InputController> Drop for Session<C> {
    fn drop(&mut self) {
        self.close();
    }
}
