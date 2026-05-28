use serde::Serialize;
use std::fs;
use std::path::PathBuf;

/// Represents a single detected loxone MCP server entry.
///
/// # Security note
/// `pass` is included here because the edit flow needs to pre-populate the form with the
/// existing credentials. This struct is serialized into every `scan_configs` Tauri IPC
/// response, so passwords are visible to the frontend JS context. The hardened CSP
/// (`default-src 'self'`) prevents exfiltration to external origins, which makes this an
/// acceptable tradeoff. Do not relax the CSP without reconsidering this design.
#[derive(Serialize, Clone)]
pub struct DetectedServer {
    pub name: String,
    pub host: String,
    pub user: String,
    pub pass: String,
    pub resources_as_tools: bool,
}

/// Represents a detected config file and its loxone servers
#[derive(Serialize)]
pub struct DetectedConfig {
    pub tool: String,
    pub path: String,
    pub exists: bool,
    pub writable: bool,
    pub servers: Vec<DetectedServer>,
}

fn home() -> PathBuf {
    dirs::home_dir().unwrap_or_default()
}

fn claude_desktop_path() -> Option<PathBuf> {
    match std::env::consts::OS {
        "macos" => Some(home().join("Library/Application Support/Claude/claude_desktop_config.json")),
        "windows" => std::env::var("APPDATA").ok().map(|p| PathBuf::from(p).join("Claude/claude_desktop_config.json")),
        "linux" => Some(home().join(".config/claude/claude_desktop_config.json")),
        _ => None,
    }
}

fn cursor_path() -> Option<PathBuf> {
    Some(home().join(".cursor/mcp.json"))
}

fn vscode_path() -> Option<PathBuf> {
    Some(home().join(".vscode/mcp.json"))
}

fn opencode_path() -> Option<PathBuf> {
    match std::env::consts::OS {
        "macos" | "linux" => Some(home().join(".config/opencode/opencode.json")),
        "windows" => std::env::var("APPDATA").ok().map(|p| PathBuf::from(p).join("opencode/opencode.json")),
        _ => None,
    }
}

fn zed_path() -> Option<PathBuf> {
    Some(home().join(".config/zed/settings.json"))
}

fn continue_path() -> Option<PathBuf> {
    Some(home().join(".continue/mcpServers/loxone.json"))
}

fn windsurf_path() -> Option<PathBuf> {
    match std::env::consts::OS {
        "macos" | "linux" => Some(home().join(".codeium/windsurf/mcp_config.json")),
        "windows" => std::env::var("APPDATA").ok().map(|p| PathBuf::from(p).join("codeium/windsurf/mcp_config.json")),
        _ => None,
    }
}

fn antigravity_path() -> Option<PathBuf> {
    Some(home().join(".gemini/antigravity/mcp_config.json"))
}

const TOOLS: &[(&str, fn() -> Option<PathBuf>, &str)] = &[
    ("Claude Desktop", claude_desktop_path, "mcpServers"),
    ("Cursor", cursor_path, "mcpServers"),
    ("VS Code / Copilot", vscode_path, "servers"),
    ("OpenCode", opencode_path, "mcp"),
    ("Zed", zed_path, "context_servers"),
    ("Continue.dev", continue_path, "mcpServers"),
    ("Windsurf", windsurf_path, "mcpServers"),
    ("Antigravity", antigravity_path, "mcpServers"),
];

/// Extract loxone-* servers from a JSON value given the config key
fn extract_servers(json: &serde_json::Value, key: &str) -> Vec<DetectedServer> {
    let mut servers = Vec::new();
    let section = &json[key];
    if let Some(obj) = section.as_object() {
        for (name, config) in obj {
            if name.starts_with("loxone") {
                let env = config.get("env").or_else(|| config.get("environment"));
                let host = env
                    .and_then(|e| e["LOXONE_HOST"].as_str())
                    .unwrap_or("")
                    .to_string();
                let user = env
                    .and_then(|e| e["LOXONE_USER"].as_str())
                    .unwrap_or("")
                    .to_string();
                let pass = env
                    .and_then(|e| e["LOXONE_PASS"].as_str())
                    .unwrap_or("")
                    .to_string();
                // Detect --resources-as-tools in args array (standard tools) or command array (OpenCode)
                let resources_as_tools = config["args"]
                    .as_array()
                    .or_else(|| config["command"].as_array())
                    .map(|arr| arr.iter().any(|a| a.as_str() == Some("--resources-as-tools")))
                    .unwrap_or(false);
                servers.push(DetectedServer {
                    name: name.clone(),
                    host,
                    user,
                    pass,
                    resources_as_tools,
                });
            }
        }
    }
    servers
}

#[tauri::command]
pub fn scan_configs() -> Vec<DetectedConfig> {
    TOOLS
        .iter()
        .filter_map(|(tool, path_fn, key)| {
            let path = path_fn()?;
            let exists = path.exists();
            let writable = if exists {
                fs::metadata(&path).map(|m| !m.permissions().readonly()).unwrap_or(false)
            } else {
                // The write path (add_server) uses fs::create_dir_all, which succeeds even when
                // multiple intermediate directories are missing. Mirror that: walk up the ancestry
                // until we find the first component that actually exists, then check whether it is
                // writable. This avoids falsely reporting non-writable when only the immediate
                // parent doesn't exist yet.
                let mut ancestor = path.parent();
                let writable = loop {
                    match ancestor {
                        None => break false,
                        Some(p) if p.exists() => {
                            break fs::metadata(p).map(|m| !m.permissions().readonly()).unwrap_or(false);
                        }
                        Some(p) => ancestor = p.parent(),
                    }
                };
                writable
            };

            let servers = if exists {
                fs::read_to_string(&path)
                    .ok()
                    .and_then(|content| serde_json::from_str::<serde_json::Value>(&content).ok())
                    .map(|json| extract_servers(&json, key))
                    .unwrap_or_default()
            } else {
                Vec::new()
            };

            Some(DetectedConfig {
                tool: tool.to_string(),
                path: path.to_string_lossy().to_string(),
                exists,
                writable,
                servers,
            })
        })
        .collect()
}
