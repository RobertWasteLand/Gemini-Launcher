import { createHash } from "node:crypto";
import { execFileSync } from "node:child_process";
import { copyFileSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const GL_Root = dirname(dirname(fileURLToPath(import.meta.url)));
const GL_Source = join(GL_Root, "GL_Java");
const GL_Bundle = join(GL_Root, "src-tauri", "GL_Bundle");
const GL_Addons = join(GL_Bundle, "GL_Java");
const GL_Work = join(GL_Root, "src-tauri", "target", "GL_Java");
const GL_Release = "17";
const GL_License = "ZombieBuddy_LICENSE.txt";
const GL_Date = "2026-01-01T00:00:00Z";

const GL_Pinned = [
  {
    name: "ZombieBuddy.jar",
    url: "https://github.com/zed-0xff/ZombieBuddy/releases/download/v2.3.4/ZombieBuddy.jar",
    sha256: "eb79b9876332010733a8e0d7ce4fe0377846059a9e28859029e2a0fb449f6cf2",
  },
  {
    name: "zbNative.dll",
    url: "https://github.com/zed-0xff/ZombieBuddy/releases/download/v2.3.3/zbNative.dll",
    sha256: "c2ae9335e717ee24b2f4a40d1a3bf77f1519762a72a0459e766a2bbafc077f6c",
  },
];

function GL_Hash_Get(bytes) {
  return createHash("sha256").update(bytes).digest("hex");
}

function GL_Tool_Path(name) {
  const home = process.env.JAVA_HOME;
  const exe = process.platform === "win32" ? `${name}.exe` : name;
  if (home && existsSync(join(home, "bin", exe))) {
    return join(home, "bin", exe);
  }
  return name;
}

async function GL_Pinned_Fetch(file) {
  const target = join(GL_Bundle, file.name);
  if (existsSync(target) && GL_Hash_Get(readFileSync(target)) === file.sha256) {
    console.log(`${file.name}: cached`);
    return;
  }
  const response = await fetch(file.url, { redirect: "follow" });
  if (!response.ok) {
    throw new Error(`${file.name}: download failed with HTTP ${response.status}`);
  }
  const bytes = Buffer.from(await response.arrayBuffer());
  const hash = GL_Hash_Get(bytes);
  if (hash !== file.sha256) {
    throw new Error(`${file.name}: sha256 ${hash} does not match ${file.sha256}`);
  }
  writeFileSync(target, bytes);
  console.log(`${file.name}: downloaded and verified`);
}

function GL_Sources_List(dir) {
  const out = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const path = join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...GL_Sources_List(path));
    } else if (entry.name.endsWith(".java")) {
      out.push(path);
    }
  }
  return out;
}

function GL_Addon_Build(name) {
  const sources = GL_Sources_List(join(GL_Source, name));
  if (!sources.length) {
    throw new Error(`${name}: no .java files`);
  }
  const classes = join(GL_Work, name);
  rmSync(classes, { recursive: true, force: true });
  mkdirSync(classes, { recursive: true });
  execFileSync(
    GL_Tool_Path("javac"),
    ["--release", GL_Release, "-encoding", "UTF-8", "-nowarn", "-cp", join(GL_Bundle, "ZombieBuddy.jar"), "-d", classes, ...sources],
    { stdio: "inherit" },
  );
  const jar = join(GL_Addons, `${name}.jar`);
  execFileSync(GL_Tool_Path("jar"), ["--create", "--date", GL_Date, "--file", jar, "-C", classes, "."], { stdio: "inherit" });
  console.log(`${name}.jar: built (${GL_Hash_Get(readFileSync(jar))})`);
}

mkdirSync(GL_Bundle, { recursive: true });
for (const file of GL_Pinned) {
  await GL_Pinned_Fetch(file);
}
copyFileSync(join(GL_Source, GL_License), join(GL_Bundle, GL_License));
rmSync(GL_Addons, { recursive: true, force: true });
mkdirSync(GL_Addons, { recursive: true });
const GL_Names = readdirSync(GL_Source, { withFileTypes: true })
  .filter((entry) => entry.isDirectory() && entry.name.startsWith("GL_"))
  .map((entry) => entry.name)
  .sort();
for (const name of GL_Names) {
  GL_Addon_Build(name);
}
