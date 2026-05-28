import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import StatusBar from "./components/StatusBar";
import JavaSetup from "./components/JavaSetup";
import ConfigList from "./components/ConfigList";
import ServerForm from "./components/ServerForm";
import type {
  JavaStatus,
  JarStatus,
  ReleaseInfo,
  DetectedConfig,
  DetectedServer,
} from "./lib/types";

type Screen = "loading" | "java-setup" | "jar-setup" | "overview" | "server-form";

interface FormContext {
  tool: string;
  path: string;
  editingServer?: DetectedServer; // full server object if editing
}

function App() {
  const [screen, setScreen] = useState<Screen>("loading");
  const [javaStatus, setJavaStatus] = useState<JavaStatus | null>(null);
  const [jarStatus, setJarStatus] = useState<JarStatus | null>(null);
  const [latestRelease, setLatestRelease] = useState<ReleaseInfo | null>(null);
  const [configs, setConfigs] = useState<DetectedConfig[]>([]);
  const [formContext, setFormContext] = useState<FormContext | null>(null);
  const [downloading, setDownloading] = useState(false);

  const runChecks = async () => {
    setScreen("loading");
    try {
      const [java, jar, release, scanned] = await Promise.all([
        invoke<JavaStatus>("check_java"),
        invoke<JarStatus>("check_jar"),
        invoke<ReleaseInfo>("get_latest_release").catch(() => null),
        invoke<DetectedConfig[]>("scan_configs"),
      ]);
      setJavaStatus(java);
      setJarStatus(jar);
      setLatestRelease(release);
      setConfigs(scanned);

      if (!java.found || !java.meets_minimum) {
        setScreen("java-setup");
      } else if (!jar.found) {
        setScreen("jar-setup");
      } else {
        setScreen("overview");
      }
    } catch (e) {
      console.error("Check failed:", e);
      setScreen("overview");
    }
  };

  useEffect(() => {
    runChecks();
  }, []);

  const handleDownloadJar = async () => {
    if (!latestRelease?.download_url) return;
    setDownloading(true);
    try {
      await invoke("download_jar", { url: latestRelease.download_url, checksum_url: latestRelease.checksum_url ?? null });
      await runChecks();
    } catch (e) {
      console.error("Download failed:", e);
    } finally {
      setDownloading(false);
    }
  };

  const handleAddServer = (tool: string, path: string) => {
    setFormContext({ tool, path });
    setScreen("server-form");
  };

  const handleEditServer = (tool: string, path: string, server: DetectedServer) => {
    setFormContext({ tool, path, editingServer: server });
    setScreen("server-form");
  };

  const handleDeleteServer = async (tool: string, path: string, serverName: string) => {
    await invoke("delete_server", { tool, path, server_name: serverName });
    const scanned = await invoke<DetectedConfig[]>("scan_configs");
    setConfigs(scanned);
  };

  const handleFormDone = async () => {
    const scanned = await invoke<DetectedConfig[]>("scan_configs");
    setConfigs(scanned);
    setScreen("overview");
  };

  if (screen === "loading") {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-lg text-gray-400">Checking prerequisites...</div>
      </div>
    );
  }

  if (screen === "java-setup") {
    const handleJavaSkip = () => {
      // If jar is also missing, go to jar-setup next instead of skipping straight to overview
      if (!jarStatus?.found) {
        setScreen("jar-setup");
      } else {
        setScreen("overview");
      }
    };
    return <JavaSetup javaStatus={javaStatus} onRecheck={runChecks} onSkip={handleJavaSkip} />;
  }

  if (screen === "jar-setup") {
    return (
      <div className="flex flex-col items-center justify-center h-screen gap-6 p-8">
        <h1 className="text-2xl font-bold">lox-mcp JAR Not Found</h1>
        <p className="text-gray-400 text-center max-w-md">
          The lox-mcp JAR was not found at <code className="text-sm bg-gray-800 px-1 rounded">~/.lox-mcp/</code>.
          {latestRelease && (
            <span> Latest version: <strong>v{latestRelease.version}</strong></span>
          )}
        </p>
        <button
          onClick={handleDownloadJar}
          disabled={downloading || !latestRelease?.download_url}
          className="px-6 py-3 bg-green-600 hover:bg-green-700 disabled:bg-gray-700 rounded-lg font-medium transition-colors"
        >
          {downloading ? "Downloading..." : "Download & Install"}
        </button>
        <button onClick={() => setScreen("overview")} className="text-sm text-gray-500 hover:text-gray-300">
          Skip for now
        </button>
      </div>
    );
  }

  if (screen === "server-form" && formContext) {
    return (
      <ServerForm
        tool={formContext.tool}
        path={formContext.path}
        editingServer={formContext.editingServer}
        onDone={handleFormDone}
        onCancel={() => setScreen("overview")}
      />
    );
  }

  // Overview
  return (
    <div className="flex flex-col h-screen">
      <StatusBar
        javaStatus={javaStatus}
        jarStatus={jarStatus}
        latestRelease={latestRelease}
        onUpgrade={handleDownloadJar}
        downloading={downloading}
        onJavaClick={() => setScreen("java-setup")}
        onJarClick={() => setScreen("jar-setup")}
      />
      <div className="flex-1 overflow-y-auto p-6">
        <h2 className="text-xl font-semibold mb-4">Detected Configurations</h2>
        <ConfigList
          configs={configs}
          onAdd={handleAddServer}
          onEdit={handleEditServer}
          onDelete={handleDeleteServer}
        />
      </div>
    </div>
  );
}

export default App;
