use serde::Serialize;

#[derive(Serialize)]
pub struct VerifyResult {
    /// "ok" | "auth_error" | "access_denied" | "cert_error" | "unreachable" | "unexpected"
    pub status: String,
    pub message: String,
}

fn map_status(http_status: u16) -> VerifyResult {
    match http_status {
        200 => VerifyResult {
            status: "ok".to_string(),
            message: "Connected to Miniserver successfully".to_string(),
        },
        401 => VerifyResult {
            status: "auth_error".to_string(),
            message: "Invalid username or password".to_string(),
        },
        403 => VerifyResult {
            status: "access_denied".to_string(),
            message: "Access denied".to_string(),
        },
        s => VerifyResult {
            status: "unexpected".to_string(),
            message: format!("Unexpected response from Miniserver: HTTP {}", s),
        },
    }
}

/// Verify Loxone Miniserver connectivity by calling the lightweight `jdev/cfg/api` endpoint
/// with HTTP Basic auth. No JAR required.
///
/// TLS strategy: attempt with full certificate validation first. If the connection fails due
/// to a certificate error (self-signed certs are common on Miniservers), return a distinct
/// `cert_error` status so the frontend can prompt the user to explicitly trust the cert before
/// retrying via `verify_connection_trust_cert`. This avoids silently disabling TLS validation
/// for all connections.
#[tauri::command]
pub async fn verify_connection(host: String, user: String, pass: String) -> Result<VerifyResult, String> {
    let base = host.trim_end_matches('/');
    let url = format!("{}/jdev/cfg/api", base);

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(8))
        .build()
        .map_err(|e| e.to_string())?;

    match client.get(&url).basic_auth(&user, Some(&pass)).send().await {
        Ok(resp) => Ok(map_status(resp.status().as_u16())),
        Err(e) => {
            // Detect TLS certificate errors and surface them distinctly
            if e.is_connect() && format!("{}", e).to_lowercase().contains("certificate") {
                Ok(VerifyResult {
                    status: "cert_error".to_string(),
                    message: "The Miniserver's TLS certificate could not be verified. \
                              This is common with self-signed certificates. \
                              Click \"Trust certificate\" to connect anyway."
                        .to_string(),
                })
            } else {
                Ok(VerifyResult {
                    status: "unreachable".to_string(),
                    message: format!("Cannot reach Miniserver: {}", e),
                })
            }
        }
    }
}

/// Same as `verify_connection` but with TLS certificate validation disabled.
/// Only called after the user has explicitly acknowledged the certificate warning.
#[tauri::command]
pub async fn verify_connection_trust_cert(host: String, user: String, pass: String) -> Result<VerifyResult, String> {
    let base = host.trim_end_matches('/');
    let url = format!("{}/jdev/cfg/api", base);

    let client = reqwest::Client::builder()
        .timeout(std::time::Duration::from_secs(8))
        .danger_accept_invalid_certs(true)
        .build()
        .map_err(|e| e.to_string())?;

    match client.get(&url).basic_auth(&user, Some(&pass)).send().await {
        Ok(resp) => Ok(map_status(resp.status().as_u16())),
        Err(e) => Ok(VerifyResult {
            status: "unreachable".to_string(),
            message: format!("Cannot reach Miniserver: {}", e),
        }),
    }
}
