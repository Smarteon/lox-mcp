import { useState } from "react";
import { invoke } from "@tauri-apps/api/core";
import type { DetectedServer, ServerConfig } from "../lib/types";

interface Props {
  tool: string;
  path: string;
  editingServer?: DetectedServer;
  onDone: () => void;
  onCancel: () => void;
}

export default function ServerForm({ tool, path, editingServer, onDone, onCancel }: Props) {
  const isEditing = !!editingServer;
  const defaultName = editingServer?.name.replace(/^loxone-?/, "") || "";

  const [name, setName] = useState(defaultName);
  const [host, setHost] = useState(editingServer?.host ?? "");
  const [user, setUser] = useState(editingServer?.user ?? "");
  const [pass, setPass] = useState(editingServer?.pass ?? "");
  const [showPass, setShowPass] = useState(false);
  const [resourcesAsTools, setResourcesAsTools] = useState(editingServer?.resources_as_tools ?? true);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [testResult, setTestResult] = useState<{ ok: boolean; msg: string } | null>(null);
  const [error, setError] = useState("");

  const handleTest = async () => {
    if (!host.trim()) { setTestResult({ ok: false, msg: "Host is required" }); return; }
    setTesting(true);
    setTestResult(null);
    try {
      const msg = await invoke<string>("verify_connection", {
        host: host.trim(),
        user: user.trim(),
        pass: pass.trim(),
      });
      setTestResult({ ok: true, msg });
    } catch (err: any) {
      setTestResult({ ok: false, msg: String(err) });
    } finally {
      setTesting(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim()) { setError("Server name is required"); return; }
    if (!host.trim()) { setError("Host is required"); return; }

    setSaving(true);
    setError("");

    const config: ServerConfig = {
      name: name.trim(),
      host: host.trim(),
      user: user.trim(),
      pass: pass.trim(),
      resources_as_tools: resourcesAsTools,
    };

    try {
      if (isEditing) {
        await invoke("edit_server", { tool, path, original_name: editingServer!.name, config });
      } else {
        await invoke("add_server", { tool, path, config });
      }
      onDone();
    } catch (err: any) {
      console.error("save error:", err);
      setError(String(err));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="flex flex-col items-center justify-center min-h-screen p-8">
      <form onSubmit={handleSubmit} className="w-full max-w-md space-y-4">
        <h2 className="text-xl font-bold mb-2">
          {isEditing ? "Edit" : "Add"} Server — {tool}
        </h2>
        <p className="text-xs text-gray-500 font-mono mb-4 break-all">{path}</p>

        {error && (
          <div className="bg-red-900/70 border border-red-500 rounded p-3 text-sm text-red-200 break-all">
            {error}
          </div>
        )}

        <div>
          <label className="block text-sm text-gray-400 mb-1">Server Name</label>
          <div className="flex items-center">
            <span className="px-3 py-2 bg-gray-700 border border-r-0 border-gray-600 rounded-l text-gray-400 text-sm">
              loxone-
            </span>
            <input
              type="text"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="home"
              className="flex-1 px-3 py-2 bg-gray-800 border border-gray-600 rounded-r text-sm focus:outline-none focus:border-blue-500"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm text-gray-400 mb-1">Loxone Host</label>
          <input
            type="text"
            value={host}
            onChange={(e) => setHost(e.target.value)}
            placeholder="http://192.168.1.100"
            className="w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded text-sm focus:outline-none focus:border-blue-500"
          />
        </div>

        <div>
          <label className="block text-sm text-gray-400 mb-1">Username</label>
          <input
            type="text"
            value={user}
            onChange={(e) => setUser(e.target.value)}
            placeholder="admin"
            className="w-full px-3 py-2 bg-gray-800 border border-gray-600 rounded text-sm focus:outline-none focus:border-blue-500"
          />
        </div>

        <div>
          <label className="block text-sm text-gray-400 mb-1">Password</label>
          <div className="flex items-center">
            <input
              type={showPass ? "text" : "password"}
              value={pass}
              onChange={(e) => setPass(e.target.value)}
              placeholder="password"
              className="flex-1 px-3 py-2 bg-gray-800 border border-r-0 border-gray-600 rounded-l text-sm focus:outline-none focus:border-blue-500"
            />
            <button
              type="button"
              onClick={() => setShowPass((v) => !v)}
              className="px-3 py-2 bg-gray-700 border border-gray-600 rounded-r text-gray-400 hover:text-gray-200 text-sm transition-colors select-none"
            >
              {showPass ? "Hide" : "Show"}
            </button>
          </div>
          <p className="text-xs text-yellow-600 mt-1">
            Stored in plain text (same as all MCP clients).
          </p>
        </div>

        <div>
          <button
            type="button"
            onClick={handleTest}
            disabled={testing || saving}
            className="w-full px-4 py-2 bg-gray-700 hover:bg-gray-600 disabled:bg-gray-800 disabled:text-gray-500 rounded text-sm font-medium transition-colors"
          >
            {testing ? "Testing connection..." : "Test Connection"}
          </button>
          {testResult && (
            <div className={`mt-2 px-3 py-2 rounded text-sm ${testResult.ok ? "bg-green-900/50 border border-green-700 text-green-300" : "bg-red-900/50 border border-red-700 text-red-300"}`}>
              {testResult.ok ? "✓ " : "✗ "}{testResult.msg}

            </div>
          )}
        </div>

        <div className="flex items-center gap-2">
          <input
            type="checkbox"
            id="resources-as-tools"
            checked={resourcesAsTools}
            onChange={(e) => setResourcesAsTools(e.target.checked)}
            className="rounded"
          />
          <label htmlFor="resources-as-tools" className="text-sm text-gray-300">
            Enable --resources-as-tools
          </label>
        </div>

        <div className="flex gap-3 pt-4">
          <button
            type="submit"
            disabled={saving}
            className="flex-1 px-4 py-2 bg-blue-600 hover:bg-blue-700 disabled:bg-gray-700 rounded font-medium transition-colors"
          >
            {saving ? "Saving..." : isEditing ? "Update" : "Add Server"}
          </button>
          <button
            type="button"
            onClick={onCancel}
            className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded transition-colors"
          >
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
