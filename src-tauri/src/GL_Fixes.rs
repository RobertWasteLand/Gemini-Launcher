use std::collections::BTreeMap;
use std::fs;
use std::io::{self, Cursor, Read};
use std::path::{Path, PathBuf};
use std::sync::atomic::Ordering;
use std::time::{Duration, UNIX_EPOCH};

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use tauri::{AppHandle, Manager};
use zip::ZipArchive;

use crate::GL_Buddy::{GL_File_Write, GL_Hash_Bytes, GL_Hash_File, GL_Key, GL_Place_Error};
use crate::GL_Config::{GL_Config_Dir, GL_Settings};
use crate::GL_Options::{GL_Option_Fixes, GL_Patch_On};
use crate::{GL_Settings_Snapshot, GL_State};

pub const GL_Fixes_Version: &str = "0.3.8";
pub const GL_Fixes_Game: &str = "42.22.2";
pub const GL_Fixes_Zip_Sha: &str = "b0b87463e5327319f36fb741c78389fd1e9e7e42d4d16ef4334c801dbe150323";
pub const GL_Fixes_Jar_Sha: &str = "3c4a7c5f149d784758c6fb5f8fa85f3a21d572e38bc081400e89035f8120d287";
pub const GL_Fixes_Folder: &str = ".mdc-patches";
pub const GL_Fixes_Game_Jar: &str = "projectzomboid.jar";
pub const GL_Fixes_Launch_Json: &str = "ProjectZomboid64.json";
const GL_Fixes_Agent: &str = "MinidoracatClientPatches.jar";
const GL_Fixes_Loader: &str = "mdc-agent-loader.dll";
const GL_Fixes_Manifest: &str = "manifest.json";
const GL_Fixes_State_File: &str = "state.json";
const GL_Fixes_Journal: &str = "journal.json";
const GL_Fixes_Zip_File: &str = "GL_Fixes.zip";
const GL_Fixes_Core: &str = "core";
const GL_Fixes_Schema: u64 = 2;
const GL_Fixes_Standard_Gb: u64 = 32;
const GL_Fixes_Timeout: Duration = Duration::from_secs(120);
const GL_Fixes_Client: &str = "Gemini-Launcher";

pub const GL_Text_Fixes_Outdated: &str = "Minidoracat Patcher is off until it's updated for this version of Project Zomboid.";
pub const GL_Text_Fixes_Waiting: &str = "Minidoracat Patcher isn't downloaded yet, so it's off this time.";
pub const GL_Text_Fixes_Foreign: &str = "Minidoracat Patcher is off because his own install is in your game.";
const GL_Reason_Fixes_Journal: &str = "Minidoracat's patch manager left a change unfinished in your game folder";
const GL_Reason_Fixes_Foreign: &str = "files from Minidoracat's own install are in your game folder";

#[derive(Clone, Copy, Debug, PartialEq)]
pub enum GL_Fixes_Variant {
    Standard,
    Lowmem,
}

#[derive(Debug, PartialEq)]
pub enum GL_Fixes_Choice {
    Foreign,
    Outdated,
    Waiting,
    Load(GL_Fixes_Variant),
}

pub struct GL_Fixes_Package {
    pub jar: Vec<u8>,
    pub loader: Vec<u8>,
    pub manifest: Value,
}

#[derive(Serialize, Deserialize, Clone, PartialEq, Debug)]
pub struct GL_Fixes_Jar {
    pub size: u64,
    pub modified: u64,
    pub sha: String,
}

pub fn GL_Fixes_Url() -> String {
    format!(
        "https://github.com/Minidoracat/MinidoracatJavaPatchFor42/releases/download/client-v{GL_Fixes_Version}/MinidoracatClientPatches-{GL_Fixes_Game}-{GL_Fixes_Version}.zip"
    )
}

pub fn GL_Fixes_Zip_Path() -> Option<PathBuf> {
    GL_Config_Dir().map(|dir| dir.join(GL_Fixes_Zip_File))
}

pub fn GL_Fixes_Zip_Read() -> Option<Vec<u8>> {
    let bytes = fs::read(GL_Fixes_Zip_Path()?).ok()?;
    (GL_Hash_Bytes(&bytes) == GL_Fixes_Zip_Sha).then_some(bytes)
}

pub fn GL_Fixes_On(settings: &GL_Settings) -> bool {
    GL_Patch_On(settings, GL_Option_Fixes)
}

pub fn GL_Fixes_Variant_Pick(total_gb: u64) -> GL_Fixes_Variant {
    if total_gb >= GL_Fixes_Standard_Gb {
        GL_Fixes_Variant::Standard
    } else {
        GL_Fixes_Variant::Lowmem
    }
}

pub fn GL_Fixes_Module(variant: GL_Fixes_Variant) -> &'static str {
    match variant {
        GL_Fixes_Variant::Standard => "client-fixes-standard",
        GL_Fixes_Variant::Lowmem => "client-fixes-lowmem",
    }
}

fn GL_Fixes_Rel(name: &str) -> String {
    format!("{GL_Fixes_Folder}/{name}")
}

pub fn GL_Fixes_Arg(variant: GL_Fixes_Variant) -> String {
    format!(
        "-agentpath:{}={}=modules={GL_Fixes_Core},{}",
        GL_Fixes_Rel(GL_Fixes_Loader),
        GL_Fixes_Rel(GL_Fixes_Agent),
        GL_Fixes_Module(variant)
    )
}

pub fn GL_Fixes_Foreign(json: &str) -> bool {
    let lower = json.to_ascii_lowercase();
    [GL_Fixes_Loader, GL_Fixes_Agent].iter().any(|name| lower.contains(&name.to_ascii_lowercase()))
}

pub fn GL_Fixes_Decide(json: &str, jar_sha: Option<&str>, zip_ready: bool, total_gb: u64) -> GL_Fixes_Choice {
    if GL_Fixes_Foreign(json) {
        GL_Fixes_Choice::Foreign
    } else if jar_sha != Some(GL_Fixes_Jar_Sha) {
        GL_Fixes_Choice::Outdated
    } else if !zip_ready {
        GL_Fixes_Choice::Waiting
    } else {
        GL_Fixes_Choice::Load(GL_Fixes_Variant_Pick(total_gb))
    }
}

fn GL_Fixes_Entry(archive: &mut ZipArchive<Cursor<&[u8]>>, name: &str) -> Result<Vec<u8>, String> {
    let mut entry = archive.by_name(name).map_err(|_| format!("the download has no {name}"))?;
    let mut bytes = Vec::new();
    entry
        .read_to_end(&mut bytes)
        .map_err(|e| format!("{name} in the download couldn't be read ({e})"))?;
    Ok(bytes)
}

fn GL_Fixes_Matches(manifest: &Value, key: &str, bytes: &[u8]) -> bool {
    manifest[key]["sha256"]
        .as_str()
        .is_some_and(|sha| sha.eq_ignore_ascii_case(&GL_Hash_Bytes(bytes)))
}

pub fn GL_Fixes_Unpack(zip: &[u8], zip_sha: &str, jar_sha: &str) -> Result<GL_Fixes_Package, String> {
    if GL_Hash_Bytes(zip) != zip_sha {
        return Err("the download isn't the release the launcher expects".to_owned());
    }
    let mut archive = ZipArchive::new(Cursor::new(zip)).map_err(|e| format!("the download isn't a zip ({e})"))?;
    let manifest: Value = serde_json::from_slice(&GL_Fixes_Entry(&mut archive, GL_Fixes_Manifest)?)
        .map_err(|e| format!("{GL_Fixes_Manifest} in the download couldn't be read ({e})"))?;
    if manifest["schemaVersion"].as_u64() != Some(GL_Fixes_Schema) {
        return Err(format!("{GL_Fixes_Manifest} in the download has another layout"));
    }
    if !manifest["jarSha256"].as_str().is_some_and(|sha| sha.eq_ignore_ascii_case(jar_sha)) {
        return Err("the download was built for another version of Project Zomboid".to_owned());
    }
    let jar = GL_Fixes_Entry(&mut archive, GL_Fixes_Agent)?;
    let loader = GL_Fixes_Entry(&mut archive, GL_Fixes_Loader)?;
    if !GL_Fixes_Matches(&manifest, "agent", &jar) || !GL_Fixes_Matches(&manifest, "loader", &loader) {
        return Err(format!("the files in the download don't match its {GL_Fixes_Manifest}"));
    }
    Ok(GL_Fixes_Package { jar, loader, manifest })
}

fn GL_Fixes_State_Module(module: &Value) -> Option<Value> {
    Some(json!({
        "id": module["id"].as_str()?,
        "name": module["name"].as_str()?,
        "version": module["version"].as_str()?,
        "requires": module.get("requires").cloned().unwrap_or_else(|| json!([])),
    }))
}

pub fn GL_Fixes_State(package: &GL_Fixes_Package, variant: GL_Fixes_Variant) -> Result<String, String> {
    let manifest = &package.manifest;
    let wanted = [GL_Fixes_Core, GL_Fixes_Module(variant)];
    let modules: Vec<Value> = manifest["modules"]
        .as_array()
        .into_iter()
        .flatten()
        .filter(|module| module["id"].as_str().is_some_and(|id| wanted.contains(&id)))
        .filter_map(GL_Fixes_State_Module)
        .collect();
    if modules.len() != wanted.len() {
        return Err(format!("the download lacks the module {}", GL_Fixes_Module(variant)));
    }
    let state = json!({
        "schemaVersion": GL_Fixes_Schema,
        "jarSha256": manifest["jarSha256"].as_str().unwrap_or_default().to_ascii_lowercase(),
        "packageVersion": manifest["packageVersion"].as_str().unwrap_or_default(),
        "agent": { "path": GL_Fixes_Rel(GL_Fixes_Agent), "sha256": GL_Hash_Bytes(&package.jar) },
        "loader": { "path": GL_Fixes_Rel(GL_Fixes_Loader), "sha256": GL_Hash_Bytes(&package.loader) },
        "modules": modules,
    });
    serde_json::to_string_pretty(&state).map_err(|e| e.to_string())
}

pub fn GL_Fixes_Place(
    game: &Path,
    package: &GL_Fixes_Package,
    state: &str,
    installed: &mut BTreeMap<String, String>,
    save: impl FnOnce(&BTreeMap<String, String>) -> Result<(), String>,
) -> Result<(), String> {
    let folder = game.join(GL_Fixes_Folder);
    if folder.join(GL_Fixes_Journal).exists() {
        return Err(GL_Reason_Fixes_Journal.to_owned());
    }
    let files: [(&str, &[u8]); 3] = [
        (GL_Fixes_Agent, &package.jar),
        (GL_Fixes_Loader, &package.loader),
        (GL_Fixes_State_File, state.as_bytes()),
    ];
    let mut record = installed.clone();
    let mut writes: Vec<(PathBuf, &[u8])> = Vec::new();
    for (name, bytes) in files {
        let target = folder.join(name);
        let key = GL_Key(&target);
        let hash = GL_Hash_Bytes(bytes);
        match GL_Hash_File(&target) {
            Ok(current) if current == hash => {}
            Ok(_) if !record.contains_key(&key) => return Err(GL_Reason_Fixes_Foreign.to_owned()),
            Err(e) if e.kind() != io::ErrorKind::NotFound => return Err(GL_Place_Error(e)),
            _ => {
                record.insert(key, hash);
                writes.push((target, bytes));
            }
        }
    }
    save(&record)?;
    *installed = record;
    if !writes.is_empty() {
        fs::create_dir_all(&folder).map_err(GL_Place_Error)?;
    }
    for (target, bytes) in writes {
        GL_File_Write(&target, bytes).map_err(GL_Place_Error)?;
    }
    Ok(())
}

pub fn GL_Fixes_Jar_Hash(jar: &Path, known: Option<&GL_Fixes_Jar>) -> Option<GL_Fixes_Jar> {
    let meta = fs::metadata(jar).ok()?;
    let size = meta.len();
    let modified = meta.modified().ok()?.duration_since(UNIX_EPOCH).ok()?.as_secs();
    if let Some(known) = known.filter(|k| k.size == size && k.modified == modified) {
        return Some(known.clone());
    }
    Some(GL_Fixes_Jar {
        size,
        modified,
        sha: GL_Hash_File(jar).ok()?,
    })
}

pub async fn GL_Fixes_Fetch(url: &str) -> Result<Vec<u8>, String> {
    if rustls::crypto::CryptoProvider::get_default().is_none() {
        let _ = rustls::crypto::ring::default_provider().install_default();
    }
    let client = reqwest::Client::builder()
        .user_agent(GL_Fixes_Client)
        .timeout(GL_Fixes_Timeout)
        .build()
        .map_err(|e| e.to_string())?;
    let response = client
        .get(url)
        .send()
        .await
        .and_then(reqwest::Response::error_for_status)
        .map_err(|e| e.to_string())?;
    response.bytes().await.map(|bytes| bytes.to_vec()).map_err(|e| e.to_string())
}

fn GL_Fixes_Save(bytes: &[u8]) -> io::Result<()> {
    let path = GL_Fixes_Zip_Path().ok_or_else(|| io::Error::from(io::ErrorKind::NotFound))?;
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    GL_File_Write(&path, bytes)
}

pub fn GL_Fixes_Start(app: &AppHandle) {
    let state = app.state::<GL_State>();
    if !GL_Fixes_On(&GL_Settings_Snapshot(&state)) || state.fixes_busy.swap(true, Ordering::SeqCst) {
        return;
    }
    let app = app.clone();
    tauri::async_runtime::spawn(async move {
        if GL_Fixes_Zip_Read().is_none() {
            if let Ok(bytes) = GL_Fixes_Fetch(&GL_Fixes_Url()).await {
                if GL_Hash_Bytes(&bytes) == GL_Fixes_Zip_Sha {
                    let _ = GL_Fixes_Save(&bytes);
                }
            }
        }
        app.state::<GL_State>().fixes_busy.store(false, Ordering::SeqCst);
    });
}
