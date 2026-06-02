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

#[derive(Deserialize)]
struct GithubAsset {
    name: String,
    browser_download_url: String,
}

#[derive(Deserialize)]
struct GithubRelease {
    tag_name: String,
    draft: bool,
    prerelease: bool,
    assets: Vec<GithubAsset>,
}

fn extract_release_info(release: &GithubRelease) -> Option<ReleaseInfo> {
    let download_url = release
        .assets
        .iter()
        .find(|asset| asset.name.ends_with("-all.jar"))
        .map(|asset| asset.browser_download_url.clone())?;

    let checksum_url = release
        .assets
        .iter()
        .find(|asset| asset.name.ends_with("-all.jar.sha256"))
        .map(|asset| asset.browser_download_url.clone());

    Some(ReleaseInfo {
        version: release.tag_name.trim_start_matches('v').to_string(),
        download_url,
        checksum_url,
    })
}

fn is_mcp_release_tag(tag: &str) -> bool {
    tag.starts_with('v')
}

fn select_latest_mcp_release(releases: &[GithubRelease]) -> Option<ReleaseInfo> {
    releases
        .iter()
        .filter(|release| !release.draft && !release.prerelease)
        .filter(|release| is_mcp_release_tag(&release.tag_name))
        .find_map(extract_release_info)
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

    let resp: Vec<GithubRelease> = client
        .get("https://api.github.com/repos/Smarteon/lox-mcp/releases")
        .send()
        .await
        .map_err(|e| e.to_string())?
        .json()
        .await
        .map_err(|e| e.to_string())?;

    select_latest_mcp_release(&resp)
        .ok_or_else(|| "No stable MCP release with a -all.jar asset found".to_string())
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

#[cfg(test)]
mod tests {
    use super::{select_latest_mcp_release, GithubAsset, GithubRelease};

    fn asset(name: &str) -> GithubAsset {
        GithubAsset {
            name: name.to_string(),
            browser_download_url: format!("https://example.com/{name}"),
        }
    }

    #[test]
    fn selects_first_stable_release_with_mcp_jar_asset() {
        let releases = vec![
            GithubRelease {
                tag_name: "configurator-v1.2.3".to_string(),
                draft: false,
                prerelease: false,
                assets: vec![asset("configurator-macos.dmg")],
            },
            GithubRelease {
                tag_name: "v1.2.2".to_string(),
                draft: false,
                prerelease: false,
                assets: vec![
                    asset("lox-mcp-1.2.2-all.jar"),
                    asset("lox-mcp-1.2.2-all.jar.sha256"),
                ],
            },
        ];

        let selected = select_latest_mcp_release(&releases).expect("release should be selected");

        assert_eq!(selected.version, "1.2.2");
        assert_eq!(
            selected.download_url,
            "https://example.com/lox-mcp-1.2.2-all.jar"
        );
        assert_eq!(
            selected.checksum_url,
            Some("https://example.com/lox-mcp-1.2.2-all.jar.sha256".to_string())
        );
    }

    #[test]
    fn returns_none_when_no_release_contains_mcp_jar_asset() {
        let releases = vec![GithubRelease {
            tag_name: "configurator-v1.2.3".to_string(),
            draft: false,
            prerelease: false,
            assets: vec![asset("configurator-macos.dmg")],
        }];

        assert!(select_latest_mcp_release(&releases).is_none());
    }

    #[test]
    fn ignores_configurator_tag_even_with_mcp_like_asset_name() {
        let releases = vec![
            GithubRelease {
                tag_name: "configurator-v1.2.3".to_string(),
                draft: false,
                prerelease: false,
                assets: vec![asset("lox-mcp-1.2.3-all.jar")],
            },
            GithubRelease {
                tag_name: "v1.2.2".to_string(),
                draft: false,
                prerelease: false,
                assets: vec![asset("lox-mcp-1.2.2-all.jar")],
            },
        ];

        let selected = select_latest_mcp_release(&releases).expect("release should be selected");
        assert_eq!(selected.version, "1.2.2");
        assert_eq!(
            selected.download_url,
            "https://example.com/lox-mcp-1.2.2-all.jar"
        );
    }
}
