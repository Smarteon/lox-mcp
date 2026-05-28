import type { JavaStatus, JarStatus, ReleaseInfo } from "../lib/types";

interface Props {
  javaStatus: JavaStatus | null;
  jarStatus: JarStatus | null;
  latestRelease: ReleaseInfo | null;
  onUpgrade: () => void;
  downloading: boolean;
  onJavaClick: () => void;
  onJarClick: () => void;
}

export default function StatusBar({ javaStatus, jarStatus, latestRelease, onUpgrade, downloading, onJavaClick, onJarClick }: Props) {
  const javaOk = javaStatus?.found && javaStatus.meets_minimum;
  const jarOk = jarStatus?.found;
  const outdated = jarOk && latestRelease && jarStatus.version !== latestRelease.version && jarStatus.version !== "unknown";

  return (
    <div className="flex items-center gap-6 px-6 py-3 bg-gray-800 border-b border-gray-700 text-sm">
      <button
        onClick={!javaOk ? onJavaClick : undefined}
        className={`flex items-center gap-2 ${!javaOk ? "cursor-pointer hover:text-white" : "cursor-default"} transition-colors`}
      >
        <span className={javaOk ? "text-green-400" : "text-red-400"}>
          {javaOk ? "✓" : "✗"}
        </span>
        <span>Java {javaStatus?.found ? javaStatus.version : "not found"}</span>
        {!javaOk && <span className="text-xs text-red-400 underline">fix</span>}
      </button>

      <button
        onClick={!jarOk ? onJarClick : undefined}
        className={`flex items-center gap-2 ${!jarOk ? "cursor-pointer hover:text-white" : "cursor-default"} transition-colors`}
      >
        <span className={jarOk ? "text-green-400" : "text-red-400"}>
          {jarOk ? "✓" : "✗"}
        </span>
        <span>lox-mcp {jarOk ? `v${jarStatus.version}` : "not installed"}</span>
        {!jarOk && <span className="text-xs text-red-400 underline">fix</span>}
      </button>

      {outdated && (
        <button
          onClick={onUpgrade}
          disabled={downloading}
          className="ml-2 px-2 py-0.5 text-xs bg-yellow-600 hover:bg-yellow-700 disabled:bg-gray-600 rounded transition-colors"
        >
          {downloading ? "..." : `Update to v${latestRelease!.version}`}
        </button>
      )}
    </div>
  );
}
