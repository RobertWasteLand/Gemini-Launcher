use sysinfo::System;

pub const GL_Memory_Sizes: &[u64] = &[3, 4, 6, 8, 10, 12, 16];
const GL_Memory_Floor: u64 = 3;
const GL_Memory_Share: f64 = 0.6;
const GL_Bytes_Gb: f64 = 1_073_741_824.0;
const GL_Memory_Tolerance: f64 = 0.05;

pub fn GL_Memory_Total() -> u64 {
    let mut sys = System::new();
    sys.refresh_memory();
    (sys.total_memory() as f64 / GL_Bytes_Gb).round() as u64
}

pub fn GL_Memory_Limit(total: u64) -> u64 {
    (((total as f64) * GL_Memory_Share).floor() as u64).max(GL_Memory_Floor)
}

pub fn GL_Memory_Auto(total: u64) -> u64 {
    let pick = match total {
        0..=8 => 3,
        9..=12 => 4,
        13..=16 => 6,
        17..=24 => 8,
        25..=32 => 10,
        _ => 12,
    };
    pick.min(GL_Memory_Limit(total))
}

pub fn GL_Memory_Choices(total: u64) -> Vec<u64> {
    let limit = GL_Memory_Limit(total);
    GL_Memory_Sizes.iter().copied().filter(|size| *size <= limit).collect()
}

pub fn GL_Memory_Resolve(value: &str, total: u64) -> u64 {
    match value.parse::<u64>() {
        Ok(size) if GL_Memory_Choices(total).contains(&size) => size,
        _ => GL_Memory_Auto(total),
    }
}

pub fn GL_Memory_Match(mb: u64, requested: u64) -> bool {
    let wanted = (requested * 1024) as f64;
    wanted > 0.0 && ((mb as f64) - wanted).abs() / wanted <= GL_Memory_Tolerance
}

pub fn GL_Memory_Arg(size: u64) -> String {
    format!("-Xmx{size}g")
}

pub fn GL_Memory_Parse(console: &str) -> Option<u64> {
    console.lines().rev().find_map(|line| {
        let rest = &line[line.find("JVM (free:")?..];
        let max = &rest[rest.find("max:")? + 4..];
        let digits: String = max
            .trim_start()
            .chars()
            .take_while(|c| c.is_ascii_digit())
            .collect();
        digits.parse().ok()
    })
}
