use std::path::PathBuf;

use crate::GL_Config::GL_Settings;
use crate::GL_Memory::{GL_Memory_Auto, GL_Memory_Choices, GL_Memory_Limit, GL_Memory_Match, GL_Memory_Parse, GL_Memory_Resolve};
use crate::GL_Options::{GL_Launch_Build, GL_Option_Memory, GL_Option_Priority};
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

#[test]
fn GL_Test_Memory_Auto() {
    assert_eq!(GL_Memory_Auto(8), 3);
    assert_eq!(GL_Memory_Auto(12), 4);
    assert_eq!(GL_Memory_Auto(16), 6);
    assert_eq!(GL_Memory_Auto(24), 8);
    assert_eq!(GL_Memory_Auto(32), 10);
    assert_eq!(GL_Memory_Auto(64), 12);
    assert_eq!(GL_Memory_Auto(4), 3);
}

#[test]
fn GL_Test_Memory_Limit() {
    assert_eq!(GL_Memory_Limit(4), 3);
    assert_eq!(GL_Memory_Limit(16), 9);
    assert_eq!(GL_Memory_Choices(8), vec![3, 4]);
    assert_eq!(GL_Memory_Choices(16), vec![3, 4, 6, 8]);
    assert_eq!(GL_Memory_Choices(32), vec![3, 4, 6, 8, 10, 12, 16]);
    assert_eq!(GL_Memory_Resolve("16", 16), 6);
    assert_eq!(GL_Memory_Resolve("8", 16), 8);
    assert_eq!(GL_Memory_Resolve("5", 16), 6);
    assert_eq!(GL_Memory_Resolve("auto", 32), 10);
}

#[test]
fn GL_Test_Memory_Parse() {
    let log = "LOG  : General , 1 > JVM (free: 120 Mb, max: 3072 Mb, total available: 260 Mb)\nLOG  : General , 2 > JVM (free: 300 Mb, max: 8192 Mb, total available: 512 Mb)\n";
    assert_eq!(GL_Memory_Parse(log), Some(8192));
    assert_eq!(GL_Memory_Parse("nothing here"), None);
    assert!(GL_Memory_Match(8192, 8));
    assert!(GL_Memory_Match(7900, 8));
    assert!(!GL_Memory_Match(3072, 8));
}

#[test]
fn GL_Test_Launch_Plan() {
    let mut settings = GL_Settings::default();
    let plan = GL_Launch_Build(&settings, 32);
    assert_eq!(plan.jvm, vec!["-Xmx10g"]);
    assert!(!plan.priority);
    settings.options.insert(GL_Option_Memory.to_owned(), "16".to_owned());
    settings.options.insert(GL_Option_Priority.to_owned(), "true".to_owned());
    let plan = GL_Launch_Build(&settings, 16);
    assert_eq!(plan.jvm, vec!["-Xmx6g"]);
    assert_eq!(plan.memory, 6);
    assert!(plan.priority);
}
