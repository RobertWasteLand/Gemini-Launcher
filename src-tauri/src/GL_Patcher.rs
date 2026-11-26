use std::fs;
use std::io;
use std::path::Path;

use crate::GL_Buddy::GL_File_Write;
use crate::GL_Config::GL_Settings;
use crate::GL_Options::{GL_Option_Patcher, GL_Patch_On};

pub const GL_Patcher_Key: &str = "textureCompression";
pub const GL_Patcher_Wanted: &str = "true";

pub fn GL_Patcher_On(settings: &GL_Settings) -> bool {
    GL_Patch_On(settings, GL_Option_Patcher)
}

pub fn GL_Patcher_Read(text: &str) -> Option<&str> {
    text.lines()
        .find_map(|line| line.strip_prefix(GL_Patcher_Key)?.strip_prefix('='))
        .map(str::trim)
}

pub fn GL_Patcher_Write(text: &str, value: &str) -> String {
    text.split_inclusive('\n')
        .map(|line| {
            let body = line.trim_end_matches(['\r', '\n']);
            if body.strip_prefix(GL_Patcher_Key).is_some_and(|rest| rest.starts_with('=')) {
                format!("{GL_Patcher_Key}={value}{}", &line[body.len()..])
            } else {
                line.to_owned()
            }
        })
        .collect()
}

pub fn GL_Patcher_Plan(text: &str, on: bool, saved: Option<&str>) -> Option<(String, Option<String>)> {
    let current = GL_Patcher_Read(text)?;
    if on {
        (current != GL_Patcher_Wanted).then(|| (GL_Patcher_Write(text, GL_Patcher_Wanted), Some(saved.unwrap_or(current).to_owned())))
    } else {
        saved.map(|old| (GL_Patcher_Write(text, old), None))
    }
}

pub fn GL_Patcher_Apply(
    path: &Path,
    on: bool,
    saved: Option<&str>,
    save: impl FnOnce(Option<String>) -> Result<(), String>,
) -> Result<(), String> {
    let text = match fs::read_to_string(path) {
        Ok(text) => text,
        Err(e) if e.kind() == io::ErrorKind::NotFound => return Ok(()),
        Err(e) => return Err(e.to_string()),
    };
    let Some((changed, kept)) = GL_Patcher_Plan(&text, on, saved) else {
        return Ok(());
    };
    if kept.is_some() {
        save(kept)?;
        GL_File_Write(path, changed.as_bytes()).map_err(|e| e.to_string())
    } else {
        GL_File_Write(path, changed.as_bytes()).map_err(|e| e.to_string())?;
        save(None)
    }
}
