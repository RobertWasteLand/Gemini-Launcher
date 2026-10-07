use std::path::PathBuf;

use crate::GL_Process::GL_Game_Args;
use crate::GL_Steam::{GL_Steam_Installdir, GL_Steam_Libraries};
use crate::GL_Vdf::{GL_Vdf_Parse, GL_Vdf_Value};

const GL_Sample_Libraries: &str = r#"
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

const GL_Sample_Legacy: &str = r#"
"LibraryFolders"
{
	"TimeNextStatsReport"		"1600000000"
	"ContentStatsID"		"-1"
	"1"		"E:\\Games\\Steam"
}
"#;

const GL_Sample_Manifest: &str = r#"
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
fn GL_Test_Libraries() {
    let dirs = GL_Steam_Libraries(GL_Sample_Libraries);
    assert_eq!(
        dirs,
        vec![
            PathBuf::from("D:\\SteamLibrary"),
            PathBuf::from("C:\\Program Files (x86)\\Steam"),
        ]
    );
}

#[test]
fn GL_Test_Legacy() {
    let dirs = GL_Steam_Libraries(GL_Sample_Legacy);
    assert_eq!(dirs, vec![PathBuf::from("E:\\Games\\Steam")]);
}

#[test]
fn GL_Test_Manifest() {
    assert_eq!(GL_Steam_Installdir(GL_Sample_Manifest).as_deref(), Some("ProjectZomboid"));
}

#[test]
fn GL_Test_Vdf() {
    let v = GL_Vdf_Parse("// note\n\"a\" { \"b\" \"x\\\"y\" c d }");
    let a = v.GL_Get("a").unwrap();
    assert_eq!(a.GL_Get("b").and_then(GL_Vdf_Value::GL_Text), Some("x\"y"));
    assert_eq!(a.GL_Get("c").and_then(GL_Vdf_Value::GL_Text), Some("d"));
}

#[test]
fn GL_Test_Args() {
    assert!(GL_Game_Args(&[], &[]).is_empty());
    let jvm = vec!["-Xmx8g".to_owned()];
    let game_args = vec!["-novoip".to_owned()];
    assert_eq!(GL_Game_Args(&jvm, &[]), vec!["-Xmx8g", "--"]);
    assert_eq!(GL_Game_Args(&[], &game_args), vec!["--", "-novoip"]);
    assert_eq!(GL_Game_Args(&jvm, &game_args), vec!["-Xmx8g", "--", "-novoip"]);
}
