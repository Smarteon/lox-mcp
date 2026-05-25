mod java_check;
mod jar_manager;
mod config_scanner;
mod config_editor;
mod platform;
mod connection_verifier;

use config_editor::{add_server, edit_server, delete_server};
use config_scanner::scan_configs;
use jar_manager::{check_jar, get_latest_release, download_jar};
use java_check::check_java;
use platform::get_adoptium_url;
use connection_verifier::{verify_connection, verify_connection_trust_cert};

fn main() {
    tauri::Builder::default()
        .plugin(tauri_plugin_shell::init())
        .invoke_handler(tauri::generate_handler![
            check_java,
            get_adoptium_url,
            check_jar,
            get_latest_release,
            download_jar,
            scan_configs,
            add_server,
            edit_server,
            delete_server,
            verify_connection,
            verify_connection_trust_cert,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}
