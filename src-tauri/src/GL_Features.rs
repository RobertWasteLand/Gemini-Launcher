use std::path::Path;
use std::sync::OnceLock;

use crate::GL_Addons::GL_Addon_Folder;
use crate::GL_Buddy;
use crate::GL_Config::GL_Settings;

pub const GL_Features_Text: &str = include_str!("../../GL_Java/GM_Environment/GM_Features.txt");
pub const GL_Features_Addon: &str = "GM_Environment";
pub const GL_Preset_Vanilla: &str = "vanilla";
pub const GL_Preset_Gemini: &str = "gemini";
pub const GL_Cfg_File: &str = "GM_Environment.cfg";

#[derive(Debug, Clone, Copy, PartialEq)]
pub enum GL_Feature_Kind {
    Switch,
    Slider,
}

#[derive(Debug, Clone)]
pub struct GL_Feature {
    pub id: String,
    pub kind: GL_Feature_Kind,
    pub min: i64,
    pub max: i64,
    pub default: i64,
    pub group: String,
    pub name: String,
}

impl GL_Feature {
    pub fn GL_Kind_Name(&self) -> &'static str {
        match self.kind {
            GL_Feature_Kind::Switch => "switch",
            GL_Feature_Kind::Slider => "slider",
        }
    }
}

pub fn GL_Features_Parse(text: &str) -> Result<Vec<GL_Feature>, String> {
    let mut out: Vec<GL_Feature> = Vec::new();
    for (number, line) in text.lines().enumerate() {
        if line.trim().is_empty() {
            continue;
        }
        let feature = GL_Feature_Parse(line).ok_or_else(|| format!("GM_Features.txt line {}: {line}", number + 1))?;
        if out.iter().any(|f| f.id == feature.id) {
            return Err(format!("GM_Features.txt line {}: {} is listed twice", number + 1, feature.id));
        }
        out.push(feature);
    }
    Ok(out)
}

fn GL_Feature_Parse(line: &str) -> Option<GL_Feature> {
    let columns: Vec<&str> = line.split('|').map(str::trim).collect();
    if columns.len() != 6 || columns[0].is_empty() || columns[4].is_empty() || columns[5].is_empty() {
        return None;
    }
    let kind: Vec<&str> = columns[1].split_whitespace().collect();
    let (kind, min, max) = match kind.as_slice() {
        ["switch"] => (GL_Feature_Kind::Switch, 0, 1),
        ["slider", min, max] => (GL_Feature_Kind::Slider, min.parse().ok()?, max.parse().ok()?),
        _ => return None,
    };
    let default: i64 = columns[2].parse().ok()?;
    if columns[3] != "live" && columns[3] != "launch" {
        return None;
    }
    if min > max || default < min || default > max {
        return None;
    }
    Some(GL_Feature {
        id: columns[0].to_owned(),
        kind,
        min,
        max,
        default,
        group: columns[4].to_owned(),
        name: columns[5].to_owned(),
    })
}

pub fn GL_Features() -> &'static [GL_Feature] {
    static GL_List: OnceLock<Vec<GL_Feature>> = OnceLock::new();
    GL_List.get_or_init(|| GL_Features_Parse(GL_Features_Text).unwrap_or_default())
}

pub fn GL_Feature_Find(id: &str) -> Option<&'static GL_Feature> {
    GL_Features().iter().find(|f| f.id == id)
}

pub fn GL_Feature_Valid(feature: &GL_Feature, value: &str) -> bool {
    value.parse::<i64>().map(|v| v >= feature.min && v <= feature.max).unwrap_or(false)
}

pub fn GL_Feature_Value(settings: &GL_Settings, feature: &GL_Feature) -> i64 {
    settings
        .options
        .get(&feature.id)
        .filter(|v| GL_Feature_Valid(feature, v))
        .and_then(|v| v.parse().ok())
        .unwrap_or(feature.default)
}

pub fn GL_Preset_Vanilla_Get(preset: &str) -> Result<bool, String> {
    match preset {
        GL_Preset_Vanilla => Ok(true),
        GL_Preset_Gemini => Ok(false),
        _ => Err(format!("Unknown preset: {preset}")),
    }
}

pub fn GL_Preset_Apply(settings: &mut GL_Settings, vanilla: bool) {
    for feature in GL_Features() {
        let value = if vanilla { feature.min } else { feature.default };
        settings.options.insert(feature.id.clone(), value.to_string());
    }
}

pub fn GL_Cfg_Text(settings: &GL_Settings, generation: u128) -> String {
    let mut text = format!("GM_Generation={generation}\n");
    for feature in GL_Features() {
        text.push_str(&format!("{}={}\n", feature.id, GL_Feature_Value(settings, feature)));
    }
    text
}

pub fn GL_Cfg_Write(game: &Path, settings: &GL_Settings, generation: u128) -> Result<Option<(String, String)>, String> {
    let folder = game.join(GL_Addon_Folder);
    if !folder.is_dir() {
        return Ok(None);
    }
    let path = folder.join(GL_Cfg_File);
    GL_Buddy::GL_File_Write(&path, GL_Cfg_Text(settings, generation).as_bytes()).map_err(|e| e.to_string())?;
    let hash = GL_Buddy::GL_Hash_File(&path).map_err(|e| e.to_string())?;
    Ok(Some((GL_Buddy::GL_Key(&path), hash)))
}
