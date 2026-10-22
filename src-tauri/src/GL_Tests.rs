use std::collections::BTreeMap;
use std::fs;
use std::path::{Path, PathBuf};

use crate::GL_Addons::{GL_Addon, GL_Addon_Enabled, GL_Addon_Find, GL_Addon_Jar, GL_Addons, GL_Addons_Active, GL_Addons_Sorted};
use crate::GL_Buddy::{
    GL_Buddy_Arg, GL_Buddy_Clean, GL_Buddy_Files, GL_Buddy_Prepare, GL_Hash_File, GL_Key, GL_Reason_Configured, GL_Reason_Foreign,
    GL_Reason_Long,
};
use crate::GL_Config::{GL_App_Identifier, GL_Settings};
use crate::GL_Features::{
    GL_Cfg_File, GL_Cfg_Text, GL_Cfg_Write, GL_Feature_Find, GL_Feature_Kind, GL_Feature_Valid, GL_Feature_Value, GL_Features, GL_Features_Parse, GL_Features_Text, GL_Preset_Apply,
    GL_Preset_Gemini, GL_Preset_Vanilla, GL_Preset_Vanilla_Get,
};
use crate::GL_Memory::{GL_Memory_Auto, GL_Memory_Choices, GL_Memory_Limit, GL_Memory_Match, GL_Memory_Parse, GL_Memory_Resolve};
use crate::GL_Options::{GL_Arg_Debug, GL_Launch_Build, GL_Option_Debug, GL_Option_Memory, GL_Option_Priority};
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
    assert!(plan.game.is_empty());
    assert!(!plan.priority);
    settings.options.insert(GL_Option_Memory.to_owned(), "16".to_owned());
    settings.options.insert(GL_Option_Priority.to_owned(), "true".to_owned());
    settings.options.insert(GL_Option_Debug.to_owned(), "true".to_owned());
    let plan = GL_Launch_Build(&settings, 16);
    assert_eq!(plan.jvm, vec!["-Xmx6g"]);
    assert_eq!(plan.game, vec![GL_Arg_Debug]);
    assert_eq!(plan.memory, 6);
    assert!(plan.priority);
    assert_eq!(GL_Game_Args(&plan.jvm, &plan.game), vec!["-Xmx6g", "--", "-debug"]);
}

const GL_Sample_Json: &str = r#"{"mainClass":"zombie/gameStates/MainScreenState","classpath":[".","projectzomboid.jar"],"vmArgs":["-Xmx3072m"]}"#;

fn GL_Test_Dir(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("GL_Test_{name}_{}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();
    dir
}

fn GL_Test_Bundle() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("GL_Bundle")
}

fn GL_Test_Core() -> &'static GL_Addon {
    GL_Addon_Find("GL_Core").unwrap()
}

#[test]
fn GL_Test_Identifier() {
    let conf: serde_json::Value = serde_json::from_str(include_str!("../tauri.conf.json")).unwrap();
    assert_eq!(conf["identifier"].as_str(), Some(GL_App_Identifier));
}

#[test]
fn GL_Test_Addons() {
    let sorted = GL_Addons_Sorted();
    let first_optional = sorted.iter().position(|a| !a.required).unwrap_or(sorted.len());
    assert!(sorted[first_optional..].iter().all(|a| !a.required));
    let core = GL_Test_Core();
    assert!(core.required);
    let mut settings = GL_Settings::default();
    settings.options.insert(core.id.to_owned(), "false".to_owned());
    assert!(GL_Addon_Enabled(&settings, core));
    assert_eq!(GL_Addons_Active(&settings)[0].id, "GL_Core");
}

#[test]
fn GL_Test_Bundle_Files() {
    let bundle = GL_Test_Bundle();
    for file in GL_Buddy_Files {
        assert_eq!(GL_Hash_File(&bundle.join(file.name)).unwrap(), file.sha256, "{}", file.name);
    }
    for addon in GL_Addons {
        assert!(bundle.join(GL_Addon_Jar(addon)).is_file(), "{}", addon.id);
    }
}

#[test]
fn GL_Test_Buddy_Arg() {
    let core = GL_Test_Core();
    let sep = std::path::MAIN_SEPARATOR;
    let arg = GL_Buddy_Arg(&[core], Some(Path::new("/cfg/GL_ZombieBuddy"))).unwrap();
    assert_eq!(
        arg,
        format!("-agentlib:zbNative=policy=deny-new,config_dir=/cfg/GL_ZombieBuddy,patches_jar=GL_Java{sep}GL_Core.jar:GL_Java.GL_Core")
    );
    let arg = GL_Buddy_Arg(&[core], Some(Path::new("/a,b/GL_ZombieBuddy"))).unwrap();
    assert!(!arg.contains("config_dir"));
    let arg = GL_Buddy_Arg(&[core], Some(Path::new("/Jos\u{e9}/GL_ZombieBuddy"))).unwrap();
    assert!(!arg.contains("config_dir"));
    let many: Vec<&GL_Addon> = std::iter::repeat_n(core, 80).collect();
    assert_eq!(GL_Buddy_Arg(&many, None), Err(GL_Reason_Long.to_owned()));
}

#[test]
fn GL_Test_Buddy_Install() {
    let bundle = GL_Test_Bundle();
    let game = GL_Test_Dir("Install");
    fs::write(game.join("ProjectZomboid64.json"), GL_Sample_Json).unwrap();
    fs::write(game.join("projectzomboid.jar"), b"game").unwrap();
    let core = GL_Test_Core();
    let mut installed = BTreeMap::new();

    let arg = GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())).unwrap();
    assert!(arg.starts_with("-agentlib:zbNative=policy=deny-new,patches_jar="));
    for file in GL_Buddy_Files {
        assert_eq!(GL_Hash_File(&game.join(file.name)).unwrap(), file.sha256);
    }
    assert!(game.join(GL_Addon_Jar(core)).is_file());
    assert_eq!(installed.len(), 3);
    let leftovers: Vec<_> = fs::read_dir(&game)
        .unwrap()
        .filter_map(|e| e.ok())
        .filter(|e| e.file_name().to_string_lossy().ends_with(".GL_Temp"))
        .collect();
    assert!(leftovers.is_empty());

    let before = installed.clone();
    GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())).unwrap();
    assert_eq!(installed, before);

    fs::write(game.join("ZombieBuddy.jar"), b"older").unwrap();
    GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())).unwrap();
    assert_eq!(GL_Hash_File(&game.join("ZombieBuddy.jar")).unwrap(), GL_Buddy_Files[1].sha256);

    GL_Buddy_Clean(&mut installed);
    assert!(installed.is_empty());
    assert!(!game.join("zbNative.dll").exists());
    assert!(!game.join("ZombieBuddy.jar").exists());
    assert!(!game.join("GL_Java").exists());
    assert!(game.join("projectzomboid.jar").is_file());
    assert_eq!(fs::read_to_string(game.join("ProjectZomboid64.json")).unwrap(), GL_Sample_Json);
    let _ = fs::remove_dir_all(&game);
}

#[test]
fn GL_Test_Buddy_Foreign() {
    let bundle = GL_Test_Bundle();
    let game = GL_Test_Dir("Foreign");
    fs::write(game.join("ProjectZomboid64.json"), GL_Sample_Json).unwrap();
    fs::write(game.join("ZombieBuddy.jar"), b"theirs").unwrap();
    let core = GL_Test_Core();
    let mut installed = BTreeMap::new();
    assert_eq!(
        GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())),
        Err(GL_Reason_Foreign.to_owned())
    );
    assert!(installed.is_empty());
    assert!(!game.join("zbNative.dll").exists());
    assert!(!game.join("GL_Java").exists());
    assert_eq!(fs::read(game.join("ZombieBuddy.jar")).unwrap(), b"theirs");

    fs::remove_file(game.join("ZombieBuddy.jar")).unwrap();
    GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())).unwrap();
    fs::write(
        game.join("ProjectZomboid64.json"),
        GL_Sample_Json.replace("\"-Xmx3072m\"", "\"-Xmx3072m\",\"-agentlib:zbNative\""),
    )
    .unwrap();
    assert_eq!(
        GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())),
        Err(GL_Reason_Configured.to_owned())
    );
    GL_Buddy_Clean(&mut installed);
    assert!(game.join("zbNative.dll").exists());
    assert!(game.join("ZombieBuddy.jar").exists());
    assert!(!game.join("GL_Java").exists());
    let _ = fs::remove_dir_all(&game);
}

#[test]
fn GL_Test_Buddy_Leftovers() {
    let bundle = GL_Test_Bundle();
    let game = GL_Test_Dir("Leftovers");
    fs::write(game.join("ProjectZomboid64.json"), GL_Sample_Json).unwrap();
    let core = GL_Test_Core();
    let mut installed = BTreeMap::new();

    fs::write(game.join("ZombieBuddy.jar.new"), b"update").unwrap();
    assert_eq!(
        GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())),
        Err(GL_Reason_Foreign.to_owned())
    );
    fs::remove_file(game.join("ZombieBuddy.jar.new")).unwrap();

    assert_eq!(
        GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Err("disk".to_owned())),
        Err("disk".to_owned())
    );
    assert!(!game.join("zbNative.dll").exists());
    assert!(!game.join("GL_Java").exists());

    let mut saved = BTreeMap::new();
    GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |record| {
        saved = record.clone();
        Ok(())
    })
    .unwrap();
    assert_eq!(saved, installed);

    fs::rename(game.join("ZombieBuddy.jar"), game.join("ZombieBuddy.jar.bak")).unwrap();
    fs::write(game.join("ZombieBuddy.jar"), b"newer").unwrap();
    fs::write(game.join("ZombieBuddy.jar.new"), b"newest").unwrap();
    GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())).unwrap();
    assert_eq!(GL_Hash_File(&game.join("ZombieBuddy.jar")).unwrap(), GL_Buddy_Files[1].sha256);
    assert!(!game.join("ZombieBuddy.jar.new").exists());
    assert!(!game.join("ZombieBuddy.jar.bak").exists());

    fs::write(game.join("ZombieBuddy.jar.bak"), b"old").unwrap();
    GL_Buddy_Clean(&mut installed);
    let left: Vec<String> = fs::read_dir(&game)
        .unwrap()
        .filter_map(|e| e.ok())
        .map(|e| e.file_name().to_string_lossy().into_owned())
        .collect();
    assert_eq!(left, vec!["ProjectZomboid64.json".to_owned()]);
    let _ = fs::remove_dir_all(&game);
}

#[test]
fn GL_Test_Features_Shipped() {
    let list = GL_Features_Parse(GL_Features_Text).expect("shipped feature list parses");
    assert_eq!(list.len(), GL_Features().len());
    let ids: Vec<&str> = list.iter().map(|f| f.id.as_str()).collect();
    assert_eq!(ids, vec!["GM_Water", "GM_Water_Shore", "GM_Water_Style", "GM_Fog", "GM_Fog_Wake"]);
    let water = GL_Feature_Find("GM_Water").expect("GM_Water listed");
    assert_eq!(water.kind, GL_Feature_Kind::Switch);
    assert_eq!(water.group, "Water");
    let style = GL_Feature_Find("GM_Water_Style").expect("GM_Water_Style listed");
    assert_eq!((style.kind, style.min, style.max), (GL_Feature_Kind::Slider, 0, 100));
    assert_eq!(GL_Feature_Find("GM_Fog_Wake").map(|f| f.group.as_str()), Some("Fog"));
}

#[test]
fn GL_Test_Features_Old_Settings() {
    let mut s = GL_Settings::default();
    for (id, value) in [("GM_Shadows", "2"), ("GM_Reflections", "1"), ("GM_Glints", "1"), ("GM_Grade", "0"), ("GM_Vignette", "1"), ("GM_Water_Style", "70")] {
        s.options.insert(id.to_owned(), value.to_owned());
    }
    assert!(GL_Feature_Find("GM_Shadows").is_none() && GL_Feature_Find("GM_Reflections").is_none());
    let text = GL_Cfg_Text(&s, 9);
    let lines: Vec<&str> = text.lines().collect();
    assert_eq!(lines.len(), GL_Features().len() + 1);
    assert!(lines.contains(&"GM_Water_Style=70"));
    assert!(lines.iter().all(|l| !l.starts_with("GM_Shadows") && !l.starts_with("GM_Reflections") && !l.starts_with("GM_Glints") && !l.starts_with("GM_Grade") && !l.starts_with("GM_Vignette")));
    GL_Preset_Apply(&mut s, GL_Preset_Vanilla_Get(GL_Preset_Vanilla).unwrap());
    assert!(GL_Features().iter().all(|f| GL_Feature_Value(&s, f) == f.min));
    GL_Preset_Apply(&mut s, GL_Preset_Vanilla_Get(GL_Preset_Gemini).unwrap());
    assert!(GL_Features().iter().all(|f| GL_Feature_Value(&s, f) == f.default));
}

#[test]
fn GL_Test_Features_Parse() {
    assert!(GL_Features_Parse("GM_A | switch | 1 | live | Group | Name A\n\nGM_B | slider 0 10 | 5 | launch | Group | Name B").is_ok());
    for bad in [
        "GM_A | switch | 2 | live | Group | Name",
        "GM_A | switch | 1 | sometimes | Group | Name",
        "GM_A | level | 1 | live | Group | Name",
        "GM_A | slider 10 0 | 5 | live | Group | Name",
        "GM_A | slider 0 | 0 | live | Group | Name",
        "GM_A | dial | 1 | live | Group | Name",
        "GM_A | switch | 1 | live | Group |",
        "GM_A | switch | 1 | live | Group",
        "GM_A | switch | 1 | live | Group | Name\nGM_A | switch | 0 | live | Group | Again",
    ] {
        assert!(GL_Features_Parse(bad).is_err(), "{bad}");
    }
}

#[test]
fn GL_Test_Features_Values() {
    let style = GL_Feature_Find("GM_Water_Style").unwrap();
    let mut s = GL_Settings::default();
    assert_eq!(GL_Feature_Value(&s, style), style.default);
    s.options.insert(style.id.clone(), "80".to_owned());
    assert_eq!(GL_Feature_Value(&s, style), 80);
    s.options.insert(style.id.clone(), "150".to_owned());
    assert_eq!(GL_Feature_Value(&s, style), style.default);
    s.options.insert(style.id.clone(), "soft".to_owned());
    assert_eq!(GL_Feature_Value(&s, style), style.default);
    assert!(GL_Feature_Valid(style, "0") && GL_Feature_Valid(style, "100"));
    assert!(!GL_Feature_Valid(style, "-1") && !GL_Feature_Valid(style, "101") && !GL_Feature_Valid(style, ""));
}

#[test]
fn GL_Test_Features_Presets() {
    let mut s = GL_Settings::default();
    GL_Preset_Apply(&mut s, GL_Preset_Vanilla_Get(GL_Preset_Vanilla).unwrap());
    assert!(GL_Features().iter().all(|f| GL_Feature_Value(&s, f) == f.min));
    GL_Preset_Apply(&mut s, GL_Preset_Vanilla_Get(GL_Preset_Gemini).unwrap());
    assert!(GL_Features().iter().all(|f| GL_Feature_Value(&s, f) == f.default));
    assert!(GL_Preset_Vanilla_Get("bright").is_err());
}

#[test]
fn GL_Test_Cfg_Text() {
    let mut s = GL_Settings::default();
    s.options.insert("GM_Fog".to_owned(), "0".to_owned());
    s.options.insert("GM_Water_Style".to_owned(), "80".to_owned());
    let text = GL_Cfg_Text(&s, 42);
    let lines: Vec<&str> = text.lines().collect();
    assert_eq!(lines[0], "GM_Generation=42");
    assert_eq!(lines.len(), GL_Features().len() + 1);
    assert!(lines.contains(&"GM_Fog=0"));
    assert!(lines.contains(&"GM_Water_Style=80"));
    assert!(lines.contains(&"GM_Water=1"));
    assert!(lines.contains(&"GM_Fog_Wake=1"));
    assert!(text.ends_with('\n'));
}

#[test]
fn GL_Test_Cfg_Write() {
    let game = GL_Test_Dir("Cfg");
    let s = GL_Settings::default();
    assert_eq!(GL_Cfg_Write(&game, &s, 1), Ok(None));
    assert!(!game.join("GL_Java").exists());

    fs::create_dir_all(game.join("GL_Java")).unwrap();
    let (key, hash) = GL_Cfg_Write(&game, &s, 7).unwrap().unwrap();
    let path = game.join("GL_Java").join(GL_Cfg_File);
    assert_eq!(fs::read_to_string(&path).unwrap(), GL_Cfg_Text(&s, 7));
    assert_eq!(GL_Hash_File(&path).unwrap(), hash);
    assert_eq!(key, GL_Key(&path));
    assert!(!game.join("GL_Java").join(format!("{GL_Cfg_File}.GL_Temp")).exists());

    let (_, second) = GL_Cfg_Write(&game, &s, 8).unwrap().unwrap();
    assert_ne!(second, hash);
    let mut installed = BTreeMap::new();
    installed.insert(key, second);
    GL_Buddy_Clean(&mut installed);
    assert!(installed.is_empty());
    assert!(!game.join("GL_Java").exists());
    let _ = fs::remove_dir_all(&game);
}
