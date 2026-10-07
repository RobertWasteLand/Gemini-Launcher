use std::path::Path;
use std::process::{Child, Command, Stdio};

use sysinfo::{ProcessRefreshKind, ProcessesToUpdate, System};

use crate::GL_Steam::{GL_Game_Exe, GL_Steam_Signed};

fn GL_Process_Find(names: &[&str]) -> bool {
    let mut sys = System::new();
    sys.refresh_processes_specifics(ProcessesToUpdate::All, true, ProcessRefreshKind::nothing());
    sys.processes().values().any(|p| {
        let name = p.name().to_string_lossy();
        names.iter().any(|n| name.eq_ignore_ascii_case(n))
    })
}

pub fn GL_Steam_Running() -> bool {
    GL_Process_Find(&["steam.exe", "steam"])
}

pub fn GL_Steam_Ready() -> bool {
    GL_Steam_Running() && GL_Steam_Signed()
}

pub fn GL_Game_Running() -> bool {
    GL_Process_Find(&[GL_Game_Exe])
}

pub fn GL_Game_Args(jvm: &[String], game: &[String]) -> Vec<String> {
    if jvm.is_empty() && game.is_empty() {
        return Vec::new();
    }
    let mut args = jvm.to_vec();
    args.push("--".to_owned());
    args.extend(game.iter().cloned());
    args
}

#[cfg(windows)]
pub fn GL_Process_Detach(cmd: &mut Command, priority: bool) {
    use std::os::windows::process::CommandExt;
    const GL_Flag_Detached: u32 = 0x0000_0008;
    const GL_Flag_Group: u32 = 0x0000_0200;
    const GL_Flag_Priority: u32 = 0x0000_8000;
    let flags = GL_Flag_Detached | GL_Flag_Group | if priority { GL_Flag_Priority } else { 0 };
    cmd.creation_flags(flags);
}

#[cfg(not(windows))]
pub fn GL_Process_Detach(_cmd: &mut Command, _priority: bool) {}

pub fn GL_Game_Spawn(dir: &Path, args: &[String], priority: bool) -> std::io::Result<Child> {
    let mut cmd = Command::new(dir.join(GL_Game_Exe));
    cmd.current_dir(dir)
        .args(args)
        .env_remove("_JAVA_OPTIONS")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    GL_Process_Detach(&mut cmd, priority);
    cmd.spawn()
}
