pub struct GL_Option {
    pub id: &'static str,
    pub label: &'static str,
    pub description: &'static str,
    pub default: bool,
    pub jvm_args: &'static [&'static str],
    pub game_args: &'static [&'static str],
}

pub const GL_Options: &[GL_Option] = &[];

pub fn GL_Option_Find(id: &str) -> Option<&'static GL_Option> {
    GL_Options.iter().find(|o| o.id == id)
}
