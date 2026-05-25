use serde::Serialize;

#[derive(Serialize)]
pub struct AdoptiumInfo {
    pub url: String,
    pub os: String,
    pub arch: String,
}

#[tauri::command]
pub fn get_adoptium_url() -> AdoptiumInfo {
    let os = match std::env::consts::OS {
        "macos" => "mac",
        "windows" => "windows",
        "linux" => "linux",
        other => other,
    };

    let arch = match std::env::consts::ARCH {
        "aarch64" => "aarch64",
        "x86_64" => "x64",
        "x86" => "x86",
        other => other,
    };

    let url = format!(
        "https://adoptium.net/temurin/releases/?os={}&arch={}&package=jdk&version=21",
        os, arch
    );

    AdoptiumInfo {
        url,
        os: os.to_string(),
        arch: arch.to_string(),
    }
}
