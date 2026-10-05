pub struct LaunchOption {
    pub id: &'static str,
    pub label: &'static str,
    pub description: &'static str,
    pub default: bool,
    pub jvm_args: &'static [&'static str],
    pub game_args: &'static [&'static str],
}

pub const OPTIONS: &[LaunchOption] = &[];

pub fn find(id: &str) -> Option<&'static LaunchOption> {
    OPTIONS.iter().find(|o| o.id == id)
}
