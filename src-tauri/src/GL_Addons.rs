use std::path::PathBuf;

use crate::GL_Config::GL_Settings;
use crate::GL_Options::{GL_Value_Off, GL_Value_On};

pub const GL_Addon_Folder: &str = "GL_Java";

pub struct GL_Addon {
    pub id: &'static str,
    pub label: &'static str,
    pub description: &'static str,
    pub required: bool,
    pub default: bool,
}

pub const GL_Addons: &[GL_Addon] = &[GL_Addon {
    id: "GL_Core",
    label: "Gemini Core",
    description: "Gemini's own Java patches. Always on.",
    required: true,
    default: true,
}];

pub fn GL_Addon_Find(id: &str) -> Option<&'static GL_Addon> {
    GL_Addons.iter().find(|a| a.id == id)
}

pub fn GL_Addon_Valid(value: &str) -> bool {
    value == GL_Value_On || value == GL_Value_Off
}

pub fn GL_Addon_Enabled(settings: &GL_Settings, addon: &GL_Addon) -> bool {
    addon.required
        || settings
            .options
            .get(addon.id)
            .filter(|v| GL_Addon_Valid(v))
            .map(|v| v == GL_Value_On)
            .unwrap_or(addon.default)
}

pub fn GL_Addons_Sorted() -> Vec<&'static GL_Addon> {
    let mut out: Vec<&GL_Addon> = GL_Addons.iter().collect();
    out.sort_by_key(|a| !a.required);
    out
}

pub fn GL_Addons_Active(settings: &GL_Settings) -> Vec<&'static GL_Addon> {
    GL_Addons_Sorted()
        .into_iter()
        .filter(|a| GL_Addon_Enabled(settings, a))
        .collect()
}

pub fn GL_Addon_Package(addon: &GL_Addon) -> String {
    format!("{GL_Addon_Folder}.{}", addon.id)
}

pub fn GL_Addon_Jar(addon: &GL_Addon) -> PathBuf {
    PathBuf::from(GL_Addon_Folder).join(format!("{}.jar", addon.id))
}

pub fn GL_Addon_Marker(addon: &GL_Addon) -> String {
    format!("[{GL_Addon_Folder}] {} active", addon.id)
}
