import type { DetectedServer } from "../lib/types";

interface Props {
  servers: DetectedServer[];
  tool: string;
  path: string;
  onEdit: (tool: string, path: string, server: DetectedServer) => void;
  onDelete: (tool: string, path: string, serverName: string) => void;
}

export default function ServerTree({ servers, tool, path, onEdit, onDelete }: Props) {
  return (
    <div className="px-4 pb-3 space-y-1">
      {servers.map((server) => (
        <div
          key={server.name}
          className="flex items-center justify-between py-1.5 px-3 bg-gray-750 rounded text-sm"
        >
          <div className="flex items-center gap-3">
            <span className="text-gray-400">└──</span>
            <span className="font-mono">{server.name}</span>
            {server.host && (
              <span className="text-xs text-gray-500">{server.host}</span>
            )}
          </div>
          <div className="flex gap-2">
            <button
              onClick={() => onEdit(tool, path, server)}
              className="px-2 py-0.5 text-xs bg-gray-700 hover:bg-gray-600 rounded transition-colors"
            >
              Edit
            </button>
            <button
              onClick={() => onDelete(tool, path, server.name)}
              className="px-2 py-0.5 text-xs bg-red-900 hover:bg-red-800 rounded transition-colors"
            >
              Delete
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
