use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

use crate::vdf;

pub const APP_ID: &str = "108600";
pub const GAME_EXE: &str = "ProjectZomboid64.exe";
const DEFAULT_INSTALL_DIR: &str = "ProjectZomboid";

pub fn steam_dir() -> Option<PathBuf> {
    candidates()
        .into_iter()
        .find(|p| p.join("steamapps").is_dir())
}

#[cfg(windows)]
fn candidates() -> Vec<PathBuf> {
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
fn candidates() -> Vec<PathBuf> {
    let home = std::env::var_os("HOME").map(PathBuf::from).unwrap_or_default();
    vec![home.join(".steam/steam"), home.join(".local/share/Steam")]
}

#[cfg(windows)]
pub fn signed_in() -> bool {
    use winreg::enums::HKEY_CURRENT_USER;
    use winreg::RegKey;

    RegKey::predef(HKEY_CURRENT_USER)
        .open_subkey("Software\\Valve\\Steam\\ActiveProcess")
        .and_then(|key| key.get_value::<u32, _>("ActiveUser"))
        .map(|user| user != 0)
        .unwrap_or(false)
}

#[cfg(not(windows))]
pub fn signed_in() -> bool {
    true
}

pub fn start(steam: &Path) -> std::io::Result<()> {
    let exe = steam.join(if cfg!(windows) { "steam.exe" } else { "steam" });
    let mut cmd = Command::new(exe);
    cmd.arg("-silent")
        .current_dir(steam)
        .stdin(Stdio::null())
        .stdout(Stdio::null())
        .stderr(Stdio::null());
    crate::game::detach(&mut cmd);
    cmd.spawn().map(|_| ())
}

pub fn parse_library_folders(text: &str) -> Vec<PathBuf> {
    let root = vdf::parse(text);
    let Some(folders) = root.get("libraryfolders") else {
        return Vec::new();
    };
    let mut with_app = Vec::new();
    let mut rest = Vec::new();
    for (key, value) in folders.entries() {
        if key.is_empty() || !key.chars().all(|c| c.is_ascii_digit()) {
            continue;
        }
        match value {
            vdf::Value::Str(path) => rest.push(PathBuf::from(path)),
            vdf::Value::Obj(_) => {
                let Some(path) = value.get("path").and_then(vdf::Value::as_str) else {
                    continue;
                };
                let has_app = value
                    .get("apps")
                    .map(|apps| apps.get(APP_ID).is_some())
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

pub fn parse_install_dir(text: &str) -> Option<String> {
    vdf::parse(text)
        .get("AppState")
        .and_then(|state| state.get("installdir"))
        .and_then(vdf::Value::as_str)
        .map(str::to_owned)
}

fn library_dirs(steam: &Path) -> Vec<PathBuf> {
    let mut out = Vec::new();
    if let Ok(text) = std::fs::read_to_string(steam.join("steamapps").join("libraryfolders.vdf")) {
        out.extend(parse_library_folders(&text));
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

pub fn find_game(steam: &Path) -> Option<PathBuf> {
    library_dirs(steam).into_iter().find_map(|lib| {
        let apps = lib.join("steamapps");
        let install = std::fs::read_to_string(apps.join(format!("appmanifest_{APP_ID}.acf")))
            .ok()
            .and_then(|text| parse_install_dir(&text))
            .unwrap_or_else(|| DEFAULT_INSTALL_DIR.to_owned());
        let dir = apps.join("common").join(install);
        is_game_dir(&dir).then_some(dir)
    })
}

pub fn is_game_dir(dir: &Path) -> bool {
    dir.join(GAME_EXE).is_file()
}
