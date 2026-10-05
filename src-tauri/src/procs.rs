use sysinfo::{ProcessRefreshKind, ProcessesToUpdate, System};

use crate::steam;

fn any_running(names: &[&str]) -> bool {
    let mut sys = System::new();
    sys.refresh_processes_specifics(ProcessesToUpdate::All, true, ProcessRefreshKind::nothing());
    sys.processes().values().any(|p| {
        let name = p.name().to_string_lossy();
        names.iter().any(|n| name.eq_ignore_ascii_case(n))
    })
}

pub fn steam_process_running() -> bool {
    any_running(&["steam.exe", "steam"])
}

pub fn steam_ready() -> bool {
    steam_process_running() && steam::signed_in()
}

pub fn game_running() -> bool {
    any_running(&[steam::GAME_EXE])
}
