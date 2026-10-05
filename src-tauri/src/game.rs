use std::path::Path;
use std::process::{Child, Command, Stdio};

use crate::steam::GAME_EXE;

pub fn build_args(jvm: &[String], game: &[String]) -> Vec<String> {
    if jvm.is_empty() && game.is_empty() {
        return Vec::new();
    }
    let mut args = jvm.to_vec();
    args.push("--".to_owned());
    args.extend(game.iter().cloned());
    args
}

#[cfg(windows)]
pub fn detach(cmd: &mut Command) {
    use std::os::windows::process::CommandExt;
    const DETACHED_PROCESS: u32 = 0x0000_0008;
    const CREATE_NEW_PROCESS_GROUP: u32 = 0x0000_0200;
    cmd.creation_flags(DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP);
}

#[cfg(not(windows))]
pub fn detach(_cmd: &mut Command) {}

pub fn spawn(dir: &Path, args: &[String]) -> std::io::Result<Child> {
    let mut cmd = Command::new(dir.join(GAME_EXE));
    cmd.current_dir(dir)
        .args(args)
        .env_remove("_JAVA_OPTIONS")
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    detach(&mut cmd);
    cmd.spawn()
}
