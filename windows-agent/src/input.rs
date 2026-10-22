use crate::protocol::{Button, ButtonState, Command};

#[derive(Debug)]
pub struct InputError;

pub trait InputController {
    fn apply(&mut self, command: &Command) -> Result<(), InputError>;
    fn release_all(&mut self) -> Result<(), InputError>;
}

#[derive(Default)]
pub struct SimulatedInput {
    pub left_down: bool,
    pub right_down: bool,
    pub applied: u64,
    pub releases: u64,
}

impl InputController for SimulatedInput {
    fn apply(&mut self, command: &Command) -> Result<(), InputError> {
        if let Command::MouseButton { button, state } = command {
            let pressed = *state == ButtonState::Down;
            match button {
                Button::Left => self.left_down = pressed,
                Button::Right => self.right_down = pressed,
            }
        }
        self.applied += 1;
        Ok(())
    }

    fn release_all(&mut self) -> Result<(), InputError> {
        self.left_down = false;
        self.right_down = false;
        self.releases += 1;
        Ok(())
    }
}
