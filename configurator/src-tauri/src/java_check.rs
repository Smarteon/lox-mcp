use serde::Serialize;
use std::process::Command;
use regex::Regex;

#[derive(Serialize)]
pub struct JavaStatus {
    pub found: bool,
    pub version: String,
    pub meets_minimum: bool,
}

#[tauri::command]
pub fn check_java() -> JavaStatus {
    let output = Command::new("java")
        .arg("-version")
        .output();

    match output {
        Ok(out) => {
            // java -version outputs to stderr
            let stderr = String::from_utf8_lossy(&out.stderr).to_string();
            let stdout = String::from_utf8_lossy(&out.stdout).to_string();
            let combined = format!("{}{}", stderr, stdout);

            let re = Regex::new(r#"(?:java|openjdk) version "(\d+)(?:\.(\d+))?(?:\.(\d+))?"#).unwrap();
            if let Some(caps) = re.captures(&combined) {
                let major: u32 = caps.get(1).map_or(0, |m| m.as_str().parse().unwrap_or(0));
                let minor: u32 = caps.get(2).map_or(0, |m| m.as_str().parse().unwrap_or(0));
                let patch: u32 = caps.get(3).map_or(0, |m| m.as_str().parse().unwrap_or(0));
                let version = if minor > 0 || patch > 0 {
                    format!("{}.{}.{}", major, minor, patch)
                } else {
                    format!("{}", major)
                };
                JavaStatus {
                    found: true,
                    version,
                    meets_minimum: major >= 21,
                }
            } else {
                // Try alternate format: "21.0.3" without quotes prefix
                let re2 = Regex::new(r#"(\d+)\.(\d+)\.(\d+)"#).unwrap();
                if let Some(caps) = re2.captures(&combined) {
                    let major: u32 = caps.get(1).map_or(0, |m| m.as_str().parse().unwrap_or(0));
                    let minor: u32 = caps.get(2).map_or(0, |m| m.as_str().parse().unwrap_or(0));
                    let patch: u32 = caps.get(3).map_or(0, |m| m.as_str().parse().unwrap_or(0));
                    JavaStatus {
                        found: true,
                        version: format!("{}.{}.{}", major, minor, patch),
                        meets_minimum: major >= 21,
                    }
                } else {
                    // Final fallback: bare major-version output like "java 21" (some distributions
                    // omit minor/patch and quotes entirely, e.g. certain Alpine/GraalVM builds)
                    let re3 = Regex::new(r#"(?:java|openjdk)\s+(\d+)"#).unwrap();
                    if let Some(caps) = re3.captures(&combined) {
                        let major: u32 = caps.get(1).map_or(0, |m| m.as_str().parse().unwrap_or(0));
                        JavaStatus {
                            found: true,
                            version: format!("{}", major),
                            meets_minimum: major >= 21,
                        }
                    } else {
                        JavaStatus {
                            found: true,
                            version: "unknown".to_string(),
                            meets_minimum: false,
                        }
                    }
                }
            }
        }
        Err(_) => JavaStatus {
            found: false,
            version: String::new(),
            meets_minimum: false,
        },
    }
}
