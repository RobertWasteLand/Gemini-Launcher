use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use crate::GL_Process::GL_Process_Detach;
use crate::GL_Vdf::{GL_Vdf_Parse, GL_Vdf_Value};

pub const GL_App_Id: &str = "108600";
pub const GL_Game_Exe: &str = "ProjectZomboid64.exe";
const GL_Install_Default: &str = "ProjectZomboid";

pub fn GL_Steam_Find() -> Option<PathBuf> {
    GL_Steam_Candidates()
        .into_iter()
        .find(|p| p.join("steamapps").is_dir())
}

#[cfg(windows)]
fn GL_Steam_Candidates() -> Vec<PathBuf> {
    use winreg::enums::{HKEY_CURRENT_USER, HKEY_LOCAL_MACHINE};
    use winreg::RegKey;

    let mut out = Vec::new();
    if let Ok(key) = RegKey::predef(HKEY_CURRENT_USER).open_subkey("Software\\Valve\\Steam") {
        if let Ok(path) = key.get_value::<String, _>("SteamPath") {
            out.push(PathBuf::from(path));
        }
    }
    for sub in ["SOFTWARE\\WOW6432Node\\Valve\\Steam", "SOFTWARE\\Valve\\Steam"] {
        if let Ok(key) = RegKey::predef(HKEY_LOCAL_MACHINE).open_subkey(sub) {
            if let Ok(path) = key.get_value::<String, _>("InstallPath") {
                out.push(PathBuf::from(path));
            }
        }
    }
    out.push(PathBuf::from("C:\\Program Files (x86)\\Steam"));
    out.push(PathBuf::from("C:\\Program Files\\Steam"));
    out
}

#[cfg(not(windows))]
fn GL_Steam_Candidates() -> Vec<PathBuf> {
    let home = std::env::var_os("HOME").map(PathBuf::from).unwrap_or_default();
    vec![home.join(".steam/steam"), home.join(".local/share/Steam")]
}

#[cfg(windows)]
pub fn GL_Steam_Signed() -> bool {
    use winreg::enums::HKEY_CURRENT_USER;
    use winreg::RegKey;

    RegKey::predef(HKEY_CURRENT_USER)
        .open_subkey("Software\\Valve\\Steam\\ActiveProcess")
        .and_then(|key| key.get_value::<u32, _>("ActiveUser"))
        .map(|user| user != 0)
        .unwrap_or(false)
}

#[cfg(not(windows))]
pub fn GL_Steam_Signed() -> bool {
    true
}

pub fn GL_Steam_Start(steam: &Path) -> std::io::Result<()> {
    let exe = steam.join(if cfg!(windows) { "steam.exe" } else { "steam" });
    let mut cmd = Command::new(exe);
    cmd.arg("-silent")
        .current_dir(steam)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    GL_Process_Detach(&mut cmd);
    cmd.spawn().map(|_| ())
}

pub fn GL_Steam_Libraries(text: &str) -> Vec<PathBuf> {
    let root = GL_Vdf_Parse(text);
    let Some(folders) = root.GL_Get("libraryfolders") else {
        return Vec::new();
    };
    let mut with_app = Vec::new();
    let mut rest = Vec::new();
    for (key, value) in folders.GL_Entries() {
        if key.is_empty() || !key.chars().all(|c| c.is_ascii_digit()) {
            continue;
        }
        match value {
            GL_Vdf_Value::Str(path) => rest.push(PathBuf::from(path)),
            GL_Vdf_Value::Obj(_) => {
                let Some(path) = value.GL_Get("path").and_then(GL_Vdf_Value::GL_Text) else {
                    continue;
                };
                let has_app = value
                    .GL_Get("apps")
                    .map(|apps| apps.GL_Get(GL_App_Id).is_some())
                    .unwrap_or(false);
                if has_app {
                    with_app.push(PathBuf::from(path));
                } else {
                    rest.push(PathBuf::from(path));
                }
            }
        }
    }
    with_app.extend(rest);
    with_app
}

pub fn GL_Steam_Installdir(text: &str) -> Option<String> {
    GL_Vdf_Parse(text)
        .GL_Get("AppState")
        .and_then(|state| state.GL_Get("installdir"))
        .and_then(GL_Vdf_Value::GL_Text)
        .map(str::to_owned)
}

fn GL_Steam_Folders(steam: &Path) -> Vec<PathBuf> {
    let mut out = Vec::new();
    if let Ok(text) = std::fs::read_to_string(steam.join("steamapps").join("libraryfolders.vdf")) {
        out.extend(GL_Steam_Libraries(&text));
    }
    out.push(steam.to_path_buf());
    let mut seen = Vec::new();
    out.retain(|p| {
        let key = p.to_string_lossy().replace('/', "\\").to_lowercase();
        let key = key.trim_end_matches('\\').to_owned();
        if seen.contains(&key) {
            false
        } else {
            seen.push(key);
            true
        }
    });
    out
}

pub fn GL_Game_Find(steam: &Path) -> Option<PathBuf> {
    GL_Steam_Folders(steam).into_iter().find_map(|lib| {
        let apps = lib.join("steamapps");
        let install = std::fs::read_to_string(apps.join(format!("appmanifest_{GL_App_Id}.acf")))
            .ok()
            .and_then(|text| GL_Steam_Installdir(&text))
            .unwrap_or_else(|| GL_Install_Default.to_owned());
        let dir = apps.join("common").join(install);
        GL_Game_Check(&dir).then_some(dir)
    })
}

pub fn GL_Game_Check(dir: &Path) -> bool {
    dir.join(GL_Game_Exe).is_file()
}
