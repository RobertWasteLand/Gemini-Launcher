use std::collections::{BTreeMap, BTreeSet};
use std::fs;
use std::io::{self, Read};
use std::path::{Path, PathBuf};

use sha2::{Digest, Sha256};

use crate::GL_Addons::{GL_Addon, GL_Addon_Folder, GL_Addon_Jar, GL_Addon_Package};

pub struct GL_Pinned {
    pub name: &'static str,
    pub sha256: &'static str,
}

pub const GL_Buddy_Files: &[GL_Pinned] = &[
    GL_Pinned {
        name: "zbNative.dll",
        sha256: "c2ae9335e717ee24b2f4a40d1a3bf77f1519762a72a0459e766a2bbafc077f6c",
    },
    GL_Pinned {
        name: GL_Buddy_Jar,
        sha256: "eb79b9876332010733a8e0d7ce4fe0377846059a9e28859029e2a0fb449f6cf2",
    },
];

pub const GL_Bundle_Folder: &str = "GL_Bundle";
pub const GL_Buddy_Config: &str = "GL_ZombieBuddy";
const GL_Buddy_Jar: &str = "ZombieBuddy.jar";
const GL_Buddy_Leftovers: &[&str] = &["ZombieBuddy.jar.new", "ZombieBuddy.jar.bak"];
const GL_Buddy_Configs: &[&str] = &["ProjectZomboid64.json", "ProjectZomboid64.site.json"];
const GL_Buddy_Marks: &[&str] = &["zbnative", "zombiebuddy"];
const GL_Buddy_Limit: usize = 2000;
const GL_Temp_Suffix: &str = ".GL_Temp";

pub const GL_Reason_Configured: &str = "ZombieBuddy is already set up in ProjectZomboid64.json";
pub const GL_Reason_Foreign: &str = "another copy of ZombieBuddy is in your game folder";
pub const GL_Reason_Damaged: &str = "the launcher's addon files are missing or damaged, so reinstall the launcher";
pub const GL_Reason_Blocked: &str = "Windows blocked the launcher from adding files to your game folder";
pub const GL_Reason_Long: &str = "too many addons are on";

pub fn GL_Hash_File(path: &Path) -> io::Result<String> {
    let mut file = fs::File::open(path)?;
    let mut hasher = Sha256::new();
    let mut buf = vec![0u8; 1 << 16];
    loop {
        let n = file.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    Ok(hasher.finalize().iter().map(|b| format!("{b:02x}")).collect())
}

pub fn GL_Buddy_Configured(game: &Path) -> bool {
    GL_Buddy_Configs.iter().any(|name| {
        fs::read_to_string(game.join(name))
            .map(|text| {
                let lower = text.to_ascii_lowercase();
                GL_Buddy_Marks.iter().any(|m| lower.contains(m))
            })
            .unwrap_or(false)
    })
}

pub fn GL_Key(path: &Path) -> String {
    let text = path.to_string_lossy();
    if cfg!(windows) {
        text.replace('/', "\\").to_lowercase()
    } else {
        text.into_owned()
    }
}

fn GL_Temp_Path(target: &Path) -> PathBuf {
    let mut temp = target.as_os_str().to_owned();
    temp.push(GL_Temp_Suffix);
    PathBuf::from(temp)
}

fn GL_File_Place(source: &Path, target: &Path) -> io::Result<()> {
    if let Some(parent) = target.parent() {
        fs::create_dir_all(parent)?;
    }
    let temp = GL_Temp_Path(target);
    let result = fs::copy(source, &temp).and_then(|_| fs::rename(&temp, target));
    if result.is_err() {
        let _ = fs::remove_file(&temp);
    }
    result
}

fn GL_Place_Error(e: io::Error) -> String {
    if e.kind() == io::ErrorKind::PermissionDenied {
        GL_Reason_Blocked.to_owned()
    } else {
        format!("couldn't add files to your game folder ({e})")
    }
}

fn GL_Remove_Missing(path: &Path) -> io::Result<()> {
    match fs::remove_file(path) {
        Err(e) if e.kind() != io::ErrorKind::NotFound => Err(e),
        _ => Ok(()),
    }
}

pub fn GL_Buddy_Arg(addons: &[&GL_Addon], config: Option<&Path>) -> Result<String, String> {
    let mut parts = vec!["policy=deny-new".to_owned()];
    if let Some(dir) = config.and_then(|p| p.to_str()).filter(|s| s.is_ascii() && !s.contains(',')) {
        parts.push(format!("config_dir={dir}"));
    }
    let jars: Vec<String> = addons
        .iter()
        .map(|a| format!("{}:{}", GL_Addon_Jar(a).display(), GL_Addon_Package(a)))
        .collect();
    parts.push(format!("patches_jar={}", jars.join(";")));
    let arg = format!("-agentlib:zbNative={}", parts.join(","));
    if arg.len() > GL_Buddy_Limit {
        return Err(GL_Reason_Long.to_owned());
    }
    Ok(arg)
}

pub fn GL_Buddy_Prepare(
    bundle: &Path,
    game: &Path,
    addons: &[&GL_Addon],
    config: Option<&Path>,
    installed: &mut BTreeMap<String, String>,
    save: impl FnOnce(&BTreeMap<String, String>) -> Result<(), String>,
) -> Result<String, String> {
    if GL_Buddy_Configured(game) {
        return Err(GL_Reason_Configured.to_owned());
    }
    let owned = installed.contains_key(&GL_Key(&game.join(GL_Buddy_Jar)));
    let leftovers: Vec<PathBuf> = GL_Buddy_Leftovers.iter().map(|name| game.join(name)).collect();
    if !owned && leftovers[0].exists() {
        return Err(GL_Reason_Foreign.to_owned());
    }
    let arg = GL_Buddy_Arg(addons, config)?;

    let mut record = installed.clone();
    let mut places: Vec<(PathBuf, PathBuf)> = Vec::new();
    for file in GL_Buddy_Files {
        let source = bundle.join(file.name);
        if GL_Hash_File(&source).ok().as_deref() != Some(file.sha256) {
            return Err(GL_Reason_Damaged.to_owned());
        }
        let target = game.join(file.name);
        let key = GL_Key(&target);
        match GL_Hash_File(&target) {
            Ok(hash) if hash == file.sha256 => {}
            Ok(_) if !record.contains_key(&key) => return Err(GL_Reason_Foreign.to_owned()),
            Err(e) if e.kind() != io::ErrorKind::NotFound => return Err(GL_Place_Error(e)),
            _ => {
                record.insert(key, file.sha256.to_owned());
                places.push((source, target));
            }
        }
    }
    for addon in addons {
        let source = bundle.join(GL_Addon_Jar(addon));
        let hash = GL_Hash_File(&source).map_err(|_| GL_Reason_Damaged.to_owned())?;
        let target = game.join(GL_Addon_Jar(addon));
        if GL_Hash_File(&target).ok().as_deref() != Some(hash.as_str()) {
            places.push((source, target.clone()));
        }
        record.insert(GL_Key(&target), hash);
    }

    save(&record)?;
    *installed = record;
    if owned {
        for leftover in &leftovers {
            GL_Remove_Missing(leftover).map_err(GL_Place_Error)?;
        }
    }
    for (source, target) in places {
        GL_File_Place(&source, &target).map_err(GL_Place_Error)?;
    }
    Ok(arg)
}

pub fn GL_Buddy_Clean(installed: &mut BTreeMap<String, String>) {
    let mut folders = BTreeSet::new();
    for (key, hash) in std::mem::take(installed) {
        let path = PathBuf::from(&key);
        let Some(parent) = path.parent() else {
            continue;
        };
        let in_folder = parent.file_name().is_some_and(|n| n.eq_ignore_ascii_case(GL_Addon_Folder));
        if in_folder {
            folders.insert(parent.to_path_buf());
        } else if GL_Buddy_Configured(parent) {
            continue;
        } else if path.file_name().is_some_and(|n| n.eq_ignore_ascii_case(GL_Buddy_Jar)) {
            for name in GL_Buddy_Leftovers {
                let _ = fs::remove_file(parent.join(name));
            }
        }
        let _ = fs::remove_file(GL_Temp_Path(&path));
        if GL_Hash_File(&path).is_ok_and(|current| current == hash) && fs::remove_file(&path).is_err() {
            installed.insert(key, hash);
        }
    }
    for folder in folders {
        let _ = fs::remove_dir(&folder);
    }
}
