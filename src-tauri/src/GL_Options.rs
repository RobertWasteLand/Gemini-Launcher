use crate::GL_Config::GL_Settings;
use crate::GL_Memory;

#[derive(Clone, Copy, PartialEq)]
pub enum GL_Option_Kind {
    Toggle,
    Choice,
}

pub struct GL_Option {
    pub id: &'static str,
    pub label: &'static str,
    pub description: &'static str,
    pub kind: GL_Option_Kind,
    pub default: &'static str,
}

pub struct GL_Choice {
    pub value: String,
    pub label: String,
}

pub struct GL_Launch_Plan {
    pub jvm: Vec<String>,
    pub game: Vec<String>,
    pub priority: bool,
    pub memory: u64,
}

pub const GL_Option_Memory: &str = "GL_Memory";
pub const GL_Option_Priority: &str = "GL_Priority";
pub const GL_Option_Debug: &str = "GL_Debug";
pub const GL_Arg_Debug: &str = "-debug";
pub const GL_Value_Auto: &str = "auto";
pub const GL_Value_On: &str = "true";
pub const GL_Value_Off: &str = "false";

pub const GL_Options: &[GL_Option] = &[
    GL_Option {
        id: GL_Option_Memory,
        label: "Game memory",
        description: "How much memory Project Zomboid may use. Auto picks a size for a large mod pack from your PC's RAM.",
        kind: GL_Option_Kind::Choice,
        default: GL_Value_Auto,
    },
    GL_Option {
        id: GL_Option_Priority,
        label: "Higher priority",
        description: "Tells Windows to favour Project Zomboid over apps running in the background.",
        kind: GL_Option_Kind::Toggle,
        default: GL_Value_Off,
    },
    GL_Option {
        id: GL_Option_Debug,
        label: "Debug mode",
        description: "Starts Project Zomboid with -debug (debug menu, logs). For testing.",
        kind: GL_Option_Kind::Toggle,
        default: GL_Value_Off,
    },
];

pub fn GL_Option_Find(id: &str) -> Option<&'static GL_Option> {
    GL_Options.iter().find(|o| o.id == id)
}

pub fn GL_Option_Choices(option: &GL_Option, total: u64) -> Vec<GL_Choice> {
    match option.id {
        GL_Option_Memory => {
            let mut out = vec![GL_Choice {
                value: GL_Value_Auto.to_owned(),
                label: format!("Auto ({} GB)", GL_Memory::GL_Memory_Auto(total)),
            }];
            out.extend(GL_Memory::GL_Memory_Choices(total).into_iter().map(|size| GL_Choice {
                value: size.to_string(),
                label: format!("{size} GB"),
            }));
            out
        }
        _ => Vec::new(),
    }
}

pub fn GL_Option_Valid(option: &GL_Option, value: &str, total: u64) -> bool {
    match option.kind {
        GL_Option_Kind::Toggle => value == GL_Value_On || value == GL_Value_Off,
        GL_Option_Kind::Choice => GL_Option_Choices(option, total).iter().any(|c| c.value == value),
    }
}

pub fn GL_Option_Value(settings: &GL_Settings, option: &GL_Option, total: u64) -> String {
    settings
        .options
        .get(option.id)
        .filter(|value| GL_Option_Valid(option, value, total))
        .cloned()
        .unwrap_or_else(|| option.default.to_owned())
}

pub fn GL_Launch_Build(settings: &GL_Settings, total: u64) -> GL_Launch_Plan {
    let memory_option = GL_Option_Find(GL_Option_Memory).expect("memory option");
    let priority_option = GL_Option_Find(GL_Option_Priority).expect("priority option");
    let debug_option = GL_Option_Find(GL_Option_Debug).expect("debug option");
    let memory = GL_Memory::GL_Memory_Resolve(&GL_Option_Value(settings, memory_option, total), total);
    let mut game = Vec::new();
    if GL_Option_Value(settings, debug_option, total) == GL_Value_On {
        game.push(GL_Arg_Debug.to_owned());
    }
    GL_Launch_Plan {
        jvm: vec![GL_Memory::GL_Memory_Arg(memory)],
        game,
        priority: GL_Option_Value(settings, priority_option, total) == GL_Value_On,
        memory,
    }
}
