use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use sha2::{Digest, Sha256};

#[derive(Serialize)]
pub struct JarStatus {
    pub found: bool,
    pub path: String,
    pub version: String,
}

#[derive(Serialize, Deserialize)]
pub struct ReleaseInfo {
    pub version: String,
    pub download_url: String,
    pub checksum_url: Option<String>,
}

fn get_jar_dir() -> PathBuf {
    dirs::home_dir().unwrap_or_default().join(".lox-mcp")
}

fn get_jar_path() -> PathBuf {
    get_jar_dir().join("lox-mcp.jar")
}

fn get_version_path() -> PathBuf {
    get_jar_dir().join("version")
}

#[tauri::command]
pub fn check_jar() -> JarStatus {
    let jar = get_jar_path();
    if jar.exists() {
        let version = fs::read_to_string(get_version_path())
            .unwrap_or_default()
            .trim()
            .to_string();
        JarStatus {
            found: true,
            path: jar.to_string_lossy().to_string(),
            version: if version.is_empty() { "unknown".to_string() } else { version },
        }
    } else {
        JarStatus {
            found: false,
            path: jar.to_string_lossy().to_string(),
            version: String::new(),
        }
    }
}

#[tauri::command]
pub async fn get_latest_release() -> Result<ReleaseInfo, String> {
    let client = reqwest::Client::builder()
        .user_agent("lox-mcp-configurator")
        .build()
        .map_err(|e| e.to_string())?;

    let resp: serde_json::Value = client
        .get("https://api.github.com/repos/Smarteon/lox-mcp/releases/latest")
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;

    let tag = resp["tag_name"]
        .as_str()
        .unwrap_or("unknown")
        .trim_start_matches('v')
        .to_string();

    let download_url = resp["assets"]
        .as_array()
        .and_then(|assets| {
            assets.iter().find(|a| {
                a["name"]
                    .as_str()
                    .map_or(false, |n| n.ends_with("-all.jar"))
            })
        })
        .and_then(|a| a["browser_download_url"].as_str())
        .unwrap_or("")
        .to_string();

    // Optional: companion SHA256 checksum file (e.g. lox-mcp-1.2.3-all.jar.sha256)
    let checksum_url = resp["assets"]
        .as_array()
        .and_then(|assets| {
            assets.iter().find(|a| {
                a["name"]
                    .as_str()
                    .map_or(false, |n| n.ends_with("-all.jar.sha256"))
            })
        })
        .and_then(|a| a["browser_download_url"].as_str())
        .map(|s| s.to_string());

    Ok(ReleaseInfo {
        version: tag,
        download_url,
        checksum_url,
    })
}

#[tauri::command]
pub async fn download_jar(url: String, checksum_url: Option<String>) -> Result<String, String> {
    let dir = get_jar_dir();
    fs::create_dir_all(&dir).map_err(|e| e.to_string())?;

    // Extract version from URL filename (e.g. lox-mcp-1.2.3-all.jar)
    let version = url
        .split('/')
        .last()
        .and_then(|n| n.strip_prefix("lox-mcp-"))
        .and_then(|n| n.strip_suffix("-all.jar"))
        .unwrap_or("unknown")
        .to_string();

    let client = reqwest::Client::builder()
        .user_agent("lox-mcp-configurator")
        .build()
        .map_err(|e| e.to_string())?;

    // Download JAR
    let resp = client
        .get(&url)
        .send()
        .await
        .map_err(|e| e.to_string())?;

    if !resp.status().is_success() {
        return Err(format!("Download failed: HTTP {}", resp.status()));
    }

    let bytes = resp.bytes().await.map_err(|e| e.to_string())?;

    // Verify SHA256 checksum if available
    if let Some(csum_url) = checksum_url {
        let csum_resp = client
            .get(&csum_url)
            .send()
            .await
            .map_err(|e| e.to_string())?;

        let csum_status = csum_resp.status();
        if csum_status.is_success() {
            let csum_text = csum_resp.text().await.map_err(|e| e.to_string())?;
            // Checksum file format: "<hex>  filename" or just "<hex>"
            let expected = csum_text.split_whitespace().next().unwrap_or("").to_lowercase();
            if !expected.is_empty() {
                let mut hasher = Sha256::new();
                hasher.update(&bytes);
                let actual = format!("{:x}", hasher.finalize());
                if actual != expected {
                    return Err(format!(
                        "SHA256 checksum mismatch — download may be corrupted.\nExpected: {}\nGot:      {}",
                        expected, actual
                    ));
                }
            }
        } else {
            // Checksum URL was advertised in the release but is now unreachable.
            // Proceeding without verification would silently accept a potentially
            // corrupted or tampered download, so we fail loudly instead.
            return Err(format!(
                "Checksum verification failed: could not fetch checksum file (HTTP {}).\n\
                 The download has not been saved. Please retry or download manually.",
                csum_status
            ));
        }
    }

    // Save to stable unversioned path — configs never need updating on upgrade
    let dest = get_jar_path();
    fs::write(&dest, &bytes).map_err(|e| e.to_string())?;

    // Store version separately
    fs::write(get_version_path(), &version).map_err(|e| e.to_string())?;

    Ok(dest.to_string_lossy().to_string())
}
