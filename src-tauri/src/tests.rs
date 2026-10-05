use std::path::PathBuf;

use crate::{game, steam, vdf};

const LIBRARY_FOLDERS: &str = r#"
"libraryfolders"
{
	"0"
	{
		"path"		"C:\\Program Files (x86)\\Steam"
		"label"		""
		"contentid"		"123"
		"totalsize"		"0"
		"apps"
		{
			"228980"		"1"
		}
	}
	"1"
	{
		"path"		"D:\\SteamLibrary"
		"label"		""
		"apps"
		{
			"108600"		"5000000000"
			"380870"		"1"
		}
	}
}
"#;

const LEGACY_LIBRARY_FOLDERS: &str = r#"
"LibraryFolders"
{
	"TimeNextStatsReport"		"1600000000"
	"ContentStatsID"		"-1"
	"1"		"E:\\Games\\Steam"
}
"#;

const APP_MANIFEST: &str = r#"
"AppState"
{
	"appid"		"108600"
	"name"		"Project Zomboid"
	"installdir"		"ProjectZomboid"
	"UserConfig"
	{
		"language"		"english"
	}
}
"#;

#[test]
fn library_with_game_comes_first() {
    let dirs = steam::parse_library_folders(LIBRARY_FOLDERS);
    assert_eq!(
        dirs,
        vec![
            PathBuf::from("D:\\SteamLibrary"),
            PathBuf::from("C:\\Program Files (x86)\\Steam"),
        ]
    );
}

#[test]
fn legacy_library_format() {
    let dirs = steam::parse_library_folders(LEGACY_LIBRARY_FOLDERS);
    assert_eq!(dirs, vec![PathBuf::from("E:\\Games\\Steam")]);
}

#[test]
fn install_dir_from_manifest() {
    assert_eq!(steam::parse_install_dir(APP_MANIFEST).as_deref(), Some("ProjectZomboid"));
}

#[test]
fn vdf_escapes_and_comments() {
    let v = vdf::parse("// note\n\"a\" { \"b\" \"x\\\"y\" c d }");
    let a = v.get("a").unwrap();
    assert_eq!(a.get("b").and_then(vdf::Value::as_str), Some("x\"y"));
    assert_eq!(a.get("c").and_then(vdf::Value::as_str), Some("d"));
}

#[test]
fn args_split_on_double_dash() {
    assert!(game::build_args(&[], &[]).is_empty());
    let jvm = vec!["-Xmx8g".to_owned()];
    let game_args = vec!["-novoip".to_owned()];
    assert_eq!(game::build_args(&jvm, &[]), vec!["-Xmx8g", "--"]);
    assert_eq!(game::build_args(&[], &game_args), vec!["--", "-novoip"]);
    assert_eq!(game::build_args(&jvm, &game_args), vec!["-Xmx8g", "--", "-novoip"]);
}
