use serde::Deserialize;
use std::fs;
use std::path::PathBuf;

#[derive(Deserialize)]
pub struct ServerConfig {
    pub name: String,
    pub host: String,
    pub user: String,
    pub pass: String,
    pub resources_as_tools: bool,
}

/// Stable JAR path — never changes between versions
fn get_jar_path() -> String {
    dirs::home_dir()
        .unwrap_or_default()
        .join(".lox-mcp/lox-mcp.jar")
        .to_string_lossy()
        .to_string()
}

/// Build the standard MCP entry for tools using mcpServers/servers format
fn build_standard_entry(config: &ServerConfig) -> serde_json::Value {
    let jar_path = get_jar_path();
    let mut args = vec![
        serde_json::Value::String("-jar".to_string()),
        serde_json::Value::String(jar_path),
        serde_json::Value::String("--stdio".to_string()),
    ];
    if config.resources_as_tools {
        args.push(serde_json::Value::String("--resources-as-tools".to_string()));
    }

    serde_json::json!({
        "command": "java",
        "args": args,
        "env": {
            "LOXONE_HOST": config.host,
            "LOXONE_USER": config.user,
            "LOXONE_PASS": config.pass
        }
    })
}

/// Build the OpenCode-specific MCP entry
fn build_opencode_entry(config: &ServerConfig) -> serde_json::Value {
    let jar_path = get_jar_path();
    let mut command = vec!["java".to_string(), "-jar".to_string(), jar_path, "--stdio".to_string()];
    if config.resources_as_tools {
        command.push("--resources-as-tools".to_string());
    }

    serde_json::json!({
        "type": "local",
        "command": command,
        "environment": {
            "LOXONE_HOST": config.host,
            "LOXONE_USER": config.user,
            "LOXONE_PASS": config.pass
        }
    })
}

/// Determine the config key and entry builder based on tool name
fn tool_config(tool: &str) -> (&str, fn(&ServerConfig) -> serde_json::Value) {
    match tool {
        "OpenCode" => ("mcp", build_opencode_entry as fn(&ServerConfig) -> serde_json::Value),
        "Zed" => ("context_servers", build_standard_entry as fn(&ServerConfig) -> serde_json::Value),
        "VS Code / Copilot" => ("servers", build_standard_entry as fn(&ServerConfig) -> serde_json::Value),
        _ => ("mcpServers", build_standard_entry as fn(&ServerConfig) -> serde_json::Value),
    }
}

/// Read or create the config file, returning parsed JSON
fn read_or_create_config(path: &PathBuf) -> Result<serde_json::Value, String> {
    if path.exists() {
        let content = fs::read_to_string(path).map_err(|e| e.to_string())?;
        let trimmed = content.trim();
        if trimmed.is_empty() {
            return Ok(serde_json::json!({}));
        }
        serde_json::from_str(trimmed).map_err(|e| format!("Failed to parse {}: {}", path.display(), e))
    } else {
        if let Some(parent) = path.parent() {
            fs::create_dir_all(parent).map_err(|e| e.to_string())?;
        }
        Ok(serde_json::json!({}))
    }
}

/// Write config back to file, creating a .bak backup first
fn write_config(path: &PathBuf, value: &serde_json::Value) -> Result<(), String> {
    if path.exists() {
        let backup = path.with_extension("json.bak");
        fs::copy(path, &backup).map_err(|e| format!("Failed to create backup: {}", e))?;
    }
    let content = serde_json::to_string_pretty(value).map_err(|e| e.to_string())?;
    fs::write(path, content).map_err(|e| e.to_string())
}

#[tauri::command]
pub fn add_server(tool: String, path: String, config: ServerConfig) -> Result<(), String> {
    let file_path = PathBuf::from(&path);
    let (key, builder) = tool_config(&tool);

    let mut json = read_or_create_config(&file_path)?;

    // Ensure the section exists and is an object
    match json.get(key) {
        None => { json[key] = serde_json::json!({}); }
        Some(v) if !v.is_object() => {
            return Err(format!("Config section '{}' exists but is not an object", key));
        }
        _ => {}
    }

    let server_name = format!("loxone-{}", config.name);
    let entry = builder(&config);
    json[key][&server_name] = entry;

    // For OpenCode, preserve $schema if not present
    if tool == "OpenCode" && json.get("$schema").is_none() {
        json["$schema"] = serde_json::Value::String("https://opencode.ai/config.json".to_string());
    }

    write_config(&file_path, &json)
}

#[tauri::command]
pub fn edit_server(tool: String, path: String, original_name: String, config: ServerConfig) -> Result<(), String> {
    let file_path = PathBuf::from(&path);
    let (key, builder) = tool_config(&tool);

    let mut json = read_or_create_config(&file_path)?;

    // Validate the section before touching anything, so we never partially mutate the file
    // and then bail out with an error (which would be confusing and leave state inconsistent).
    match json.get(key) {
        None => { json[key] = serde_json::json!({}); }
        Some(v) if !v.is_object() => {
            return Err(format!("Config section '{}' exists but is not an object", key));
        }
        _ => {}
    }

    // Remove old entry if name changed (safe: section is guaranteed to be an object above)
    let new_name = format!("loxone-{}", config.name);
    if original_name != new_name {
        if let Some(section) = json.get_mut(key).and_then(|v| v.as_object_mut()) {
            section.remove(&original_name);
        }
    }

    let entry = builder(&config);
    json[key][&new_name] = entry;

    write_config(&file_path, &json)
}

#[tauri::command]
pub fn delete_server(tool: String, path: String, server_name: String) -> Result<(), String> {
    let file_path = PathBuf::from(&path);
    let (key, _) = tool_config(&tool);

    let mut json = read_or_create_config(&file_path)?;

    if let Some(section) = json.get_mut(key).and_then(|v| v.as_object_mut()) {
        section.remove(&server_name);
    }

    write_config(&file_path, &json)
}
