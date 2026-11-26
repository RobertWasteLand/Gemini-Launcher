use std::collections::BTreeMap;
use std::fs;
use std::io::{Cursor, Write};
use std::path::{Path, PathBuf};

use serde_json::{json, Value};
use zip::write::SimpleFileOptions;
use zip::ZipWriter;

use crate::GL_Addons::{GL_Addon, GL_Addon_Enabled, GL_Addon_Find, GL_Addon_Jar, GL_Addons, GL_Addons_Active, GL_Addons_Sorted, GL_Media_Folder};
use crate::GL_Buddy::{
    GL_Buddy_Arg, GL_Buddy_Clean, GL_Buddy_Files, GL_Buddy_Prepare, GL_Hash_Bytes, GL_Hash_File, GL_Key, GL_Media_Folders, GL_Reason_Configured,
    GL_Reason_Foreign, GL_Reason_Long,
};
use crate::GL_Config::{GL_App_Identifier, GL_Settings};
use crate::GL_Fixes::{
    GL_Fixes_Arg, GL_Fixes_Choice, GL_Fixes_Decide, GL_Fixes_Fetch, GL_Fixes_Foreign, GL_Fixes_Jar, GL_Fixes_Jar_Hash, GL_Fixes_Jar_Sha, GL_Fixes_On,
    GL_Fixes_Place, GL_Fixes_State, GL_Fixes_Unpack, GL_Fixes_Url, GL_Fixes_Variant, GL_Fixes_Variant_Pick, GL_Fixes_Version, GL_Fixes_Zip_Sha,
};
use crate::GL_Features::{
    GL_Cfg_File, GL_Cfg_Text, GL_Cfg_Write, GL_Feature_Find, GL_Feature_Kind, GL_Feature_Valid, GL_Feature_Value, GL_Features, GL_Features_Parse, GL_Features_Text, GL_Preset_Apply,
    GL_Preset_Gemini, GL_Preset_Vanilla, GL_Preset_Vanilla_Get,
};
use crate::GL_Memory::{GL_Memory_Auto, GL_Memory_Choices, GL_Memory_Limit, GL_Memory_Match, GL_Memory_Parse, GL_Memory_Resolve};
use crate::GL_Options::{
    GL_Arg_Debug, GL_Arg_Fewer, GL_Launch_Build, GL_Option_Debug, GL_Option_Fewer, GL_Option_Find, GL_Option_Fixes, GL_Option_Memory, GL_Option_Patcher,
    GL_Option_Patches, GL_Option_Priority, GL_Value_Off, GL_Value_On,
};
use crate::GL_Patcher::{GL_Patcher_Apply, GL_Patcher_On, GL_Patcher_Plan, GL_Patcher_Read};
use crate::GL_Process::{GL_Game_Args, GL_Link_Valid};
use crate::GL_Steam::{GL_Steam_Installdir, GL_Steam_Libraries};
use crate::GL_Update::{GL_Text_From_Both, GL_Text_From_Fixes, GL_Text_From_Gemini, GL_Update_From_Text};
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

#[test]
fn GL_Test_Particles_Addon() {
    let addon = GL_Addon_Find("GM_Particles").expect("particles addon");
    assert!(addon.required && addon.default);
    assert_eq!(addon.label, "Particles");
    let ids: Vec<&str> = GL_Addons.iter().map(|a| a.id).collect();
    assert_eq!(ids, ["GL_Core", "GM_Environment", "GM_Particles"]);
}

#[test]
fn GL_Test_Launch_Fewer() {
    let mut settings = GL_Settings::default();
    assert!(!GL_Launch_Build(&settings, 16).jvm.contains(&GL_Arg_Fewer.to_owned()));
    settings.options.insert(GL_Option_Fewer.to_owned(), "true".to_owned());
    assert!(GL_Launch_Build(&settings, 16).jvm.contains(&GL_Arg_Fewer.to_owned()));
}

const GL_Sample_Json: &str = r#"{"mainClass":"zombie/gameStates/MainScreenState","classpath":[".","projectzomboid.jar"],"vmArgs":["-Xmx3072m"]}"#;

fn GL_Test_Dir(name: &str) -> PathBuf {
    let dir = std::env::temp_dir().join(format!("GL_Test_{name}_{}", std::process::id()));
    let _ = fs::remove_dir_all(&dir);
    fs::create_dir_all(&dir).unwrap();
    dir
}

fn GL_Test_Game(name: &str) -> PathBuf {
    let game = GL_Test_Dir(name);
    fs::write(game.join("ProjectZomboid64.json"), GL_Sample_Json).unwrap();
    for kind in ["shaders", "sound", "ui"] {
        fs::create_dir_all(game.join(GL_Media_Folder).join(kind)).unwrap();
    }
    game
}

fn GL_Test_Media_Count(bundle: &Path, addons: &[&GL_Addon]) -> usize {
    addons
        .iter()
        .flat_map(|addon| GL_Media_Folders(bundle, addon))
        .map(|folder| fs::read_dir(bundle.join(folder)).unwrap().count())
        .sum()
}

fn GL_Test_Bundle() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("GL_Bundle")
}

fn GL_Test_Core() -> &'static GL_Addon {
    GL_Addon_Find("GL_Core").unwrap()
}
fn GL_Test_Environment() -> &'static GL_Addon {
    GL_Addon_Find("GM_Environment").unwrap()
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
    let core = GL_Media_Folders(&bundle, GL_Test_Core());
    assert_eq!(core, vec![PathBuf::from("media/sound/GL_Core"), PathBuf::from("media/ui/GL_Core")]);
    assert!(bundle.join(&core[0]).join("GM_Gemini_Jingle.ogg").is_file());
    assert!(bundle.join(&core[1]).join("GM_Legal_Warning.png").is_file());
    assert!(bundle.join(&core[0]).join("GM_Main_Menu_Theme.ogg").is_file());
    assert!(bundle.join(&core[1]).join("GM_MainMenu_Frame.png").is_file());
    assert!(bundle.join("media/ui/GM_Particles/GM_Particles.lua").is_file());
    assert!(bundle.join(&core[1]).join("GM_Label_Play_Alone.png").is_file());
    assert!(bundle.join(&core[1]).join("GL_Menu.lua").is_file());
    let env = GL_Media_Folders(&bundle, GL_Test_Environment());
    assert_eq!(env, vec![PathBuf::from("media/shaders/GM_Environment")]);
    assert!(bundle.join(&env[0]).join("GM_Water_High.frag").is_file());
    assert!(bundle.join(&env[0]).join("GM_Shaders_Screen.frag").is_file());
    for kind in fs::read_dir(bundle.join(GL_Media_Folder)).unwrap() {
        for folder in fs::read_dir(kind.unwrap().path()).unwrap() {
            let name = folder.unwrap().file_name();
            assert!(GL_Addon_Find(&name.to_string_lossy()).is_some(), "{name:?}");
        }
    }
}

#[test]
fn GL_Test_Buddy_Media() {
    let bundle = GL_Test_Bundle();
    let game = GL_Test_Game("Media");
    let core = GL_Test_Core();
    let env = GL_Test_Environment();
    let mut installed = BTreeMap::new();

    GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())).unwrap();
    assert!(!game.join("media/shaders/GM_Environment").exists());

    GL_Buddy_Prepare(&bundle, &game, &[core, env], None, &mut installed, |_| Ok(())).unwrap();
    let folders: Vec<PathBuf> = [core, env].iter().flat_map(|addon| GL_Media_Folders(&bundle, addon)).collect();
    assert_eq!(folders.len(), 3);
    for folder in &folders {
        assert_eq!(fs::read_dir(game.join(folder)).unwrap().count(), fs::read_dir(bundle.join(folder)).unwrap().count());
    }
    assert_eq!(installed.len(), GL_Buddy_Files.len() + 2 + GL_Test_Media_Count(&bundle, &[core, env]));
    let water = game.join("media/shaders/GM_Environment/GM_Water_High.frag");
    let hash = GL_Hash_File(&water).unwrap();
    assert_eq!(installed.get(&GL_Key(&water)), Some(&hash));

    let before = installed.clone();
    GL_Buddy_Prepare(&bundle, &game, &[core, env], None, &mut installed, |_| Ok(())).unwrap();
    assert_eq!(installed, before);

    fs::write(&water, b"older").unwrap();
    GL_Buddy_Prepare(&bundle, &game, &[core, env], None, &mut installed, |_| Ok(())).unwrap();
    assert_eq!(GL_Hash_File(&water).unwrap(), hash);

    GL_Buddy_Clean(&mut installed);
    assert!(installed.is_empty());
    for folder in &folders {
        assert!(!game.join(folder).exists(), "{}", folder.display());
    }
    assert!(game.join("media/ui").is_dir());
    assert!(!game.join("GL_Java").exists());
    let _ = fs::remove_dir_all(&game);
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
    let game = GL_Test_Game("Install");
    fs::write(game.join("projectzomboid.jar"), b"game").unwrap();
    let core = GL_Test_Core();
    let mut installed = BTreeMap::new();

    let arg = GL_Buddy_Prepare(&bundle, &game, &[core], None, &mut installed, |_| Ok(())).unwrap();
    assert!(arg.starts_with("-agentlib:zbNative=policy=deny-new,patches_jar="));
    for file in GL_Buddy_Files {
        assert_eq!(GL_Hash_File(&game.join(file.name)).unwrap(), file.sha256);
    }
    assert!(game.join(GL_Addon_Jar(core)).is_file());
    assert!(game.join("media/ui/GL_Core/GM_Legal_Warning.png").is_file());
    assert_eq!(installed.len(), GL_Buddy_Files.len() + 1 + GL_Test_Media_Count(&bundle, &[core]));
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
    assert!(!game.join("media/ui/GL_Core").exists());
    assert!(!game.join("media/sound/GL_Core").exists());
    assert!(game.join("projectzomboid.jar").is_file());
    assert_eq!(fs::read_to_string(game.join("ProjectZomboid64.json")).unwrap(), GL_Sample_Json);
    let _ = fs::remove_dir_all(&game);
}

#[test]
fn GL_Test_Buddy_Foreign() {
    let bundle = GL_Test_Bundle();
    let game = GL_Test_Game("Foreign");
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
    let game = GL_Test_Game("Leftovers");
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
    let mut left: Vec<String> = fs::read_dir(&game)
        .unwrap()
        .filter_map(|e| e.ok())
        .map(|e| e.file_name().to_string_lossy().into_owned())
        .collect();
    left.sort();
    assert_eq!(left, vec!["ProjectZomboid64.json".to_owned(), "media".to_owned()]);
    assert_eq!(fs::read_dir(game.join("media/ui")).unwrap().count(), 0);
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

const GL_Test_Fixes_His: &str = "-agentpath:.mdc-patches/mdc-agent-loader.dll=.mdc-patches/MinidoracatClientPatches.jar=modules=core";

fn GL_Test_Fixes_Module(id: &str, name: &str) -> Value {
    let requires = if id == "core" { json!([]) } else { json!(["core"]) };
    json!({ "id": id, "name": name, "nameEn": name, "version": "t1", "requires": requires })
}

fn GL_Test_Fixes_Package(jar: &[u8]) -> (Vec<u8>, String) {
    let manifest = json!({
        "schemaVersion": 2,
        "packageVersion": "t1",
        "jarSha256": GL_Hash_Bytes(b"game"),
        "agent": { "source": "MinidoracatClientPatches.jar", "sha256": GL_Hash_Bytes(b"jar") },
        "loader": { "source": "mdc-agent-loader.dll", "sha256": GL_Hash_Bytes(b"dll") },
        "modules": [
            GL_Test_Fixes_Module("core", "Core"),
            GL_Test_Fixes_Module("profiler", "Profiler"),
            GL_Test_Fixes_Module("client-fixes-standard", "Standard"),
            GL_Test_Fixes_Module("client-fixes-lowmem", "Low memory"),
            GL_Test_Fixes_Module("client-fixes-compat", "Compatible"),
        ],
    });
    let entries: [(&str, Vec<u8>); 3] = [
        ("manifest.json", manifest.to_string().into_bytes()),
        ("MinidoracatClientPatches.jar", jar.to_vec()),
        ("mdc-agent-loader.dll", b"dll".to_vec()),
    ];
    let mut writer = ZipWriter::new(Cursor::new(Vec::new()));
    for (name, bytes) in entries {
        writer.start_file(name, SimpleFileOptions::default()).unwrap();
        writer.write_all(&bytes).unwrap();
    }
    let zip = writer.finish().unwrap().into_inner();
    let sha = GL_Hash_Bytes(&zip);
    (zip, sha)
}

#[test]
fn GL_Test_Fixes_Url() {
    assert_eq!(
        GL_Fixes_Url(),
        "https://github.com/Minidoracat/MinidoracatJavaPatchFor42/releases/download/client-v0.3.8/MinidoracatClientPatches-42.22.2-0.3.8.zip"
    );
}

#[test]
fn GL_Test_Fixes_Option() {
    let option = GL_Option_Find(GL_Option_Fixes).expect("fixes option");
    assert_eq!(option.label, "Minidoracat Patcher");
    assert_eq!(option.default, GL_Value_On);
    assert_eq!(option.author, Some("Minidoracat"));
    let patches = GL_Option_Find(GL_Option_Patches).expect("patches option");
    assert_eq!(patches.label, "Patches");
    assert_eq!(patches.default, GL_Value_On);
    let mut settings = GL_Settings::default();
    assert!(GL_Fixes_On(&settings));
    settings.options.insert(GL_Option_Patches.to_owned(), GL_Value_Off.to_owned());
    assert!(!GL_Fixes_On(&settings));
    settings.options.insert(GL_Option_Patches.to_owned(), GL_Value_On.to_owned());
    settings.options.insert(GL_Option_Fixes.to_owned(), GL_Value_Off.to_owned());
    assert!(!GL_Fixes_On(&settings));
    assert!(GL_Launch_Build(&settings, 64).jvm.iter().all(|arg| !arg.contains("mdc-agent")));
}

#[test]
fn GL_Test_Fixes_Variant() {
    assert_eq!(GL_Fixes_Variant_Pick(32), GL_Fixes_Variant::Standard);
    assert_eq!(GL_Fixes_Variant_Pick(64), GL_Fixes_Variant::Standard);
    assert_eq!(GL_Fixes_Variant_Pick(31), GL_Fixes_Variant::Lowmem);
    assert_eq!(GL_Fixes_Variant_Pick(16), GL_Fixes_Variant::Lowmem);
    assert_eq!(
        GL_Fixes_Arg(GL_Fixes_Variant::Lowmem),
        "-agentpath:.mdc-patches/mdc-agent-loader.dll=.mdc-patches/MinidoracatClientPatches.jar=modules=core,client-fixes-lowmem"
    );
    assert_eq!(
        GL_Fixes_Arg(GL_Fixes_Variant::Standard),
        "-agentpath:.mdc-patches/mdc-agent-loader.dll=.mdc-patches/MinidoracatClientPatches.jar=modules=core,client-fixes-standard"
    );
}

#[test]
fn GL_Test_Fixes_Decide() {
    let his = GL_Sample_Json.replace("\"-Xmx3072m\"", &format!("\"{GL_Test_Fixes_His}\",\"-Xmx3072m\""));
    let jar = Some(GL_Fixes_Jar_Sha);
    assert!(!GL_Fixes_Foreign(GL_Sample_Json));
    assert!(GL_Fixes_Foreign(&his));
    assert!(GL_Fixes_Foreign(&GL_Sample_Json.replace("\"-Xmx3072m\"", "\"-javaagent:.MDC-PATCHES/MinidoracatClientPatches.JAR\"")));
    assert_eq!(GL_Fixes_Decide(&his, None, false, 16), GL_Fixes_Choice::Foreign);
    assert_eq!(GL_Fixes_Decide(GL_Sample_Json, Some("0000"), true, 16), GL_Fixes_Choice::Outdated);
    assert_eq!(GL_Fixes_Decide(GL_Sample_Json, None, true, 16), GL_Fixes_Choice::Outdated);
    assert_eq!(GL_Fixes_Decide(GL_Sample_Json, jar, false, 16), GL_Fixes_Choice::Waiting);
    assert_eq!(GL_Fixes_Decide(GL_Sample_Json, jar, true, 16), GL_Fixes_Choice::Load(GL_Fixes_Variant::Lowmem));
    assert_eq!(GL_Fixes_Decide(GL_Sample_Json, jar, true, 32), GL_Fixes_Choice::Load(GL_Fixes_Variant::Standard));
}

#[test]
fn GL_Test_Fixes_Unpack() {
    let game = GL_Hash_Bytes(b"game");
    let (zip, sha) = GL_Test_Fixes_Package(b"jar");
    let package = GL_Fixes_Unpack(&zip, &sha, &game).unwrap();
    assert_eq!(package.jar, b"jar");
    assert_eq!(package.loader, b"dll");
    assert!(GL_Fixes_Unpack(&zip, &GL_Hash_Bytes(b"other"), &game).is_err());
    assert!(GL_Fixes_Unpack(&zip, &sha, &GL_Hash_Bytes(b"other game")).is_err());
    let (evil, evil_sha) = GL_Test_Fixes_Package(b"evil");
    assert!(GL_Fixes_Unpack(&evil, &evil_sha, &game).is_err());
    assert!(GL_Fixes_Unpack(b"not a zip", &GL_Hash_Bytes(b"not a zip"), &game).is_err());
}

#[test]
fn GL_Test_Fixes_State() {
    let (zip, sha) = GL_Test_Fixes_Package(b"jar");
    let package = GL_Fixes_Unpack(&zip, &sha, &GL_Hash_Bytes(b"game")).unwrap();
    let state: Value = serde_json::from_str(&GL_Fixes_State(&package, GL_Fixes_Variant::Lowmem).unwrap()).unwrap();
    assert_eq!(state["schemaVersion"], 2);
    assert_eq!(state["jarSha256"], GL_Hash_Bytes(b"game"));
    assert_eq!(state["packageVersion"], "t1");
    assert_eq!(state["agent"]["path"], ".mdc-patches/MinidoracatClientPatches.jar");
    assert_eq!(state["agent"]["sha256"], GL_Hash_Bytes(b"jar"));
    assert_eq!(state["loader"]["path"], ".mdc-patches/mdc-agent-loader.dll");
    assert_eq!(state["loader"]["sha256"], GL_Hash_Bytes(b"dll"));
    let ids: Vec<&str> = state["modules"].as_array().unwrap().iter().map(|m| m["id"].as_str().unwrap()).collect();
    assert_eq!(ids, vec!["core", "client-fixes-lowmem"]);
    assert_eq!(state["modules"][1]["name"], "Low memory");
    assert_eq!(state["modules"][1]["version"], "t1");
    assert_eq!(state["modules"][1]["requires"], json!(["core"]));
    assert!(state["modules"][1].get("nameEn").is_none());
    let standard: Value = serde_json::from_str(&GL_Fixes_State(&package, GL_Fixes_Variant::Standard).unwrap()).unwrap();
    assert_eq!(standard["modules"][1]["id"], "client-fixes-standard");
}

#[test]
fn GL_Test_Fixes_Place() {
    let game = GL_Test_Game("Fixes_Place");
    let folder = game.join(".mdc-patches");
    let (zip, sha) = GL_Test_Fixes_Package(b"jar");
    let package = GL_Fixes_Unpack(&zip, &sha, &GL_Hash_Bytes(b"game")).unwrap();
    let lowmem = GL_Fixes_State(&package, GL_Fixes_Variant::Lowmem).unwrap();
    let mut installed = BTreeMap::new();

    assert_eq!(
        GL_Fixes_Place(&game, &package, &lowmem, &mut installed, |_| Err("disk".to_owned())),
        Err("disk".to_owned())
    );
    assert!(installed.is_empty());
    assert!(!folder.exists());

    let mut saved = BTreeMap::new();
    GL_Fixes_Place(&game, &package, &lowmem, &mut installed, |record| {
        saved = record.clone();
        Ok(())
    })
    .unwrap();
    assert_eq!(saved, installed);
    assert_eq!(installed.len(), 3);
    assert_eq!(fs::read(folder.join("MinidoracatClientPatches.jar")).unwrap(), b"jar");
    assert_eq!(fs::read(folder.join("mdc-agent-loader.dll")).unwrap(), b"dll");
    assert_eq!(fs::read_to_string(folder.join("state.json")).unwrap(), lowmem);
    assert_eq!(installed.get(&GL_Key(&folder.join("state.json"))), Some(&GL_Hash_Bytes(lowmem.as_bytes())));
    assert_eq!(fs::read_dir(&folder).unwrap().count(), 3);

    let before = installed.clone();
    GL_Fixes_Place(&game, &package, &lowmem, &mut installed, |_| Ok(())).unwrap();
    assert_eq!(installed, before);

    let standard = GL_Fixes_State(&package, GL_Fixes_Variant::Standard).unwrap();
    GL_Fixes_Place(&game, &package, &standard, &mut installed, |_| Ok(())).unwrap();
    assert_eq!(fs::read_to_string(folder.join("state.json")).unwrap(), standard);
    assert_eq!(installed.get(&GL_Key(&folder.join("state.json"))), Some(&GL_Hash_Bytes(standard.as_bytes())));

    GL_Buddy_Clean(&mut installed);
    assert!(installed.is_empty());
    assert!(!folder.exists());
    assert_eq!(fs::read_to_string(game.join("ProjectZomboid64.json")).unwrap(), GL_Sample_Json);

    fs::create_dir_all(&folder).unwrap();
    fs::write(folder.join("state.json"), "{}").unwrap();
    assert!(GL_Fixes_Place(&game, &package, &lowmem, &mut installed, |_| Ok(())).is_err());
    assert!(installed.is_empty());
    assert_eq!(fs::read_to_string(folder.join("state.json")).unwrap(), "{}");
    assert!(!folder.join("MinidoracatClientPatches.jar").exists());

    fs::remove_file(folder.join("state.json")).unwrap();
    fs::write(folder.join("journal.json"), "{}").unwrap();
    assert!(GL_Fixes_Place(&game, &package, &lowmem, &mut installed, |_| Ok(())).is_err());
    assert!(installed.is_empty());
    assert!(!folder.join("state.json").exists());

    fs::remove_file(folder.join("journal.json")).unwrap();
    GL_Fixes_Place(&game, &package, &lowmem, &mut installed, |_| Ok(())).unwrap();
    fs::write(folder.join("state.json"), "{\"his\":true}").unwrap();
    let his = GL_Sample_Json.replace("\"-Xmx3072m\"", &format!("\"{GL_Test_Fixes_His}\",\"-Xmx3072m\""));
    fs::write(game.join("ProjectZomboid64.json"), &his).unwrap();
    GL_Buddy_Clean(&mut installed);
    assert!(installed.is_empty());
    assert_eq!(fs::read(folder.join("MinidoracatClientPatches.jar")).unwrap(), b"jar");
    assert_eq!(fs::read(folder.join("mdc-agent-loader.dll")).unwrap(), b"dll");
    assert_eq!(fs::read_to_string(folder.join("state.json")).unwrap(), "{\"his\":true}");
    let _ = fs::remove_dir_all(&game);
}

#[test]
fn GL_Test_Fixes_Jar_Hash() {
    let game = GL_Test_Dir("Fixes_Jar");
    let jar = game.join("projectzomboid.jar");
    fs::write(&jar, b"game").unwrap();
    let first = GL_Fixes_Jar_Hash(&jar, None).unwrap();
    assert_eq!(first.sha, GL_Hash_Bytes(b"game"));
    assert_eq!(first.size, 4);
    let stale = GL_Fixes_Jar {
        sha: "0000".to_owned(),
        ..first.clone()
    };
    assert_eq!(GL_Fixes_Jar_Hash(&jar, Some(&stale)).unwrap().sha, "0000");
    fs::write(&jar, b"game update").unwrap();
    assert_eq!(GL_Fixes_Jar_Hash(&jar, Some(&first)).unwrap().sha, GL_Hash_Bytes(b"game update"));
    assert!(GL_Fixes_Jar_Hash(&game.join("missing.jar"), None).is_none());
    let _ = fs::remove_dir_all(&game);
}

#[test]
fn GL_Test_Update_From() {
    assert_eq!(GL_Update_From_Text(None), GL_Text_From_Gemini);
    assert_eq!(GL_Update_From_Text(Some("New here? Download the setup .exe below.")), GL_Text_From_Gemini);
    assert_eq!(GL_Update_From_Text(Some("New here?\r\n\r\nUpdate from: Gemini\r\n")), GL_Text_From_Gemini);
    assert_eq!(GL_Update_From_Text(Some("New here?\n\nUpdate from: Minidoracat Patcher\n")), GL_Text_From_Fixes);
    assert_eq!(GL_Update_From_Text(Some("Update from: Both")), GL_Text_From_Both);
    assert_eq!(GL_Update_From_Text(Some("Update from: Somebody")), GL_Text_From_Gemini);
    let workflow = include_str!("../../.github/workflows/GL_Release.yml");
    assert!(workflow.contains("Update from: ${{ inputs.GL_Update_From }}"));
    for (choice, text) in [("Gemini", GL_Text_From_Gemini), ("Minidoracat Patcher", GL_Text_From_Fixes), ("Both", GL_Text_From_Both)] {
        assert!(workflow.lines().any(|line| line.trim() == format!("- {choice}")));
        assert_eq!(GL_Update_From_Text(Some(&format!("Update from: {choice}"))), text);
    }
}

#[test]
#[ignore]
fn GL_Test_Fixes_Real() {
    let zip = tauri::async_runtime::block_on(GL_Fixes_Fetch(&GL_Fixes_Url())).unwrap();
    assert_eq!(GL_Hash_Bytes(&zip), GL_Fixes_Zip_Sha);
    let package = GL_Fixes_Unpack(&zip, GL_Fixes_Zip_Sha, GL_Fixes_Jar_Sha).unwrap();
    for (variant, module) in [(GL_Fixes_Variant::Standard, "client-fixes-standard"), (GL_Fixes_Variant::Lowmem, "client-fixes-lowmem")] {
        let state: Value = serde_json::from_str(&GL_Fixes_State(&package, variant).unwrap()).unwrap();
        assert_eq!(state["packageVersion"], GL_Fixes_Version);
        assert_eq!(state["jarSha256"], GL_Fixes_Jar_Sha);
        let ids: Vec<&str> = state["modules"].as_array().unwrap().iter().map(|m| m["id"].as_str().unwrap()).collect();
        assert_eq!(ids, vec!["core", module]);
    }
}

#[test]
fn GL_Test_Patcher_Option() {
    let option = GL_Option_Find(GL_Option_Patcher).expect("patcher option");
    assert_eq!(option.label, "Robert Patcher");
    assert_eq!(option.default, GL_Value_Off);
    assert_eq!(option.author, Some("Robert"));
    let mut settings = GL_Settings::default();
    assert!(!GL_Patcher_On(&settings));
    settings.options.insert(GL_Option_Patcher.to_owned(), GL_Value_On.to_owned());
    assert!(GL_Patcher_On(&settings));
    settings.options.insert(GL_Option_Patches.to_owned(), GL_Value_Off.to_owned());
    assert!(!GL_Patcher_On(&settings));
}

#[test]
fn GL_Test_Patcher_Plan() {
    let off = "version=1\r\ntextureCompression=false\r\nvsync=true\r\n";
    assert_eq!(GL_Patcher_Read(off), Some("false"));
    let (on, saved) = GL_Patcher_Plan(off, true, None).unwrap();
    assert_eq!(on, "version=1\r\ntextureCompression=true\r\nvsync=true\r\n");
    assert_eq!(saved.as_deref(), Some("false"));
    assert_eq!(GL_Patcher_Plan(&on, true, Some("false")), None);
    assert_eq!(GL_Patcher_Plan(off, true, Some("false")).unwrap().1.as_deref(), Some("false"));
    assert_eq!(GL_Patcher_Plan(&on, false, Some("false")), Some((off.to_owned(), None)));
    assert_eq!(GL_Patcher_Plan(&on, false, None), None);
    assert_eq!(GL_Patcher_Plan("version=1\n", true, None), None);
    assert_eq!(GL_Patcher_Plan("textureCompressionLevel=false\n", true, None), None);
    assert_eq!(GL_Patcher_Plan("textureCompression=false", true, None).unwrap().0, "textureCompression=true");
}

#[test]
fn GL_Test_Patcher_Apply() {
    let dir = GL_Test_Dir("Patcher");
    let ini = dir.join("options.ini");
    let mut saved: Option<String> = None;
    GL_Patcher_Apply(&ini, true, None, |kept| {
        saved = kept;
        Ok(())
    })
    .unwrap();
    assert!(!ini.exists());

    fs::write(&ini, "textureCompression=false\nvsync=true\n").unwrap();
    GL_Patcher_Apply(&ini, true, None, |kept| {
        saved = kept;
        Ok(())
    })
    .unwrap();
    assert_eq!(fs::read_to_string(&ini).unwrap(), "textureCompression=true\nvsync=true\n");
    assert_eq!(saved.as_deref(), Some("false"));

    assert!(GL_Patcher_Apply(&ini, false, saved.as_deref(), |_| Err("disk".to_owned())).is_err());
    assert_eq!(fs::read_to_string(&ini).unwrap(), "textureCompression=false\nvsync=true\n");

    fs::write(&ini, "textureCompression=true\nvsync=true\n").unwrap();
    let before = saved.clone();
    GL_Patcher_Apply(&ini, false, before.as_deref(), |kept| {
        saved = kept;
        Ok(())
    })
    .unwrap();
    assert_eq!(fs::read_to_string(&ini).unwrap(), "textureCompression=false\nvsync=true\n");
    assert_eq!(saved, None);

    fs::write(&ini, "textureCompression=false\n").unwrap();
    assert!(GL_Patcher_Apply(&ini, true, None, |_| Err("disk".to_owned())).is_err());
    assert_eq!(fs::read_to_string(&ini).unwrap(), "textureCompression=false\n");
    assert!(!dir.join("options.ini.GL_Temp").exists());
    let _ = fs::remove_dir_all(&dir);
}

#[test]
fn GL_Test_Link_Valid() {
    assert!(GL_Link_Valid("https://github.com/RobertWasteLand/Gemini-Launcher"));
    assert!(GL_Link_Valid("https://discord.gg/dZTkKRxcbr"));
    assert!(!GL_Link_Valid("http://example.com"));
    assert!(!GL_Link_Valid("https://"));
    assert!(!GL_Link_Valid("file:///C:/Windows/System32/calc.exe"));
    assert!(!GL_Link_Valid("https://example.com/a b"));
    assert!(!GL_Link_Valid("https://example.com/\"x"));
    let lines = include_str!("../../GL_Live/GL_Links.txt");
    for line in lines.lines().filter(|line| !line.trim().is_empty()) {
        let url = line.split('|').nth(1).map(str::trim).unwrap_or_default();
        assert!(GL_Link_Valid(url), "{line}");
    }
}
