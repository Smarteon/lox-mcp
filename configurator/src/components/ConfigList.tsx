import type { DetectedConfig, DetectedServer } from "../lib/types";
import ServerTree from "./ServerTree";

interface Props {
  configs: DetectedConfig[];
  onAdd: (tool: string, path: string) => void;
  onEdit: (tool: string, path: string, server: DetectedServer) => void;
  onDelete: (tool: string, path: string, serverName: string) => void;
}

export default function ConfigList({ configs, onAdd, onEdit, onDelete }: Props) {
  const sorted = [...configs].sort((a, b) => {
    const aHas = a.servers.length > 0;
    const bHas = b.servers.length > 0;
    if (aHas !== bHas) return aHas ? -1 : 1;
    return a.tool.localeCompare(b.tool);
  });
  return (
    <div className="space-y-3">
      {sorted.map((cfg) => (
        <div key={cfg.tool} className="bg-gray-800 rounded-lg border border-gray-700">
          <div className="flex items-center justify-between px-4 py-3">
            <div>
              <span className="font-medium">{cfg.tool}</span>
              <span className="ml-3 text-xs text-gray-500 font-mono">
                {cfg.exists ? cfg.path : `${cfg.path} (will create)`}
              </span>
            </div>
            <button
              onClick={() => onAdd(cfg.tool, cfg.path)}
              className="px-3 py-1 text-xs bg-green-700 hover:bg-green-600 rounded transition-colors"
            >
              + Add
            </button>
          </div>
          {cfg.servers.length > 0 ? (
            <ServerTree
              servers={cfg.servers}
              tool={cfg.tool}
              path={cfg.path}
              onEdit={onEdit}
              onDelete={onDelete}
            />
          ) : (
            <div className="px-4 pb-3 text-sm text-gray-500 italic">
              No loxone servers configured
            </div>
          )}
        </div>
      ))}
    </div>
  );
}
