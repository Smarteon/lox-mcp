import { useState, useEffect } from "react";
import { invoke } from "@tauri-apps/api/core";
import { open } from "@tauri-apps/plugin-shell";
import type { JavaStatus, AdoptiumInfo } from "../lib/types";

interface Props {
  javaStatus: JavaStatus | null;
  onRecheck: () => void;
  onSkip: () => void;
}

export default function JavaSetup({ javaStatus, onRecheck, onSkip }: Props) {
  const [adoptium, setAdoptium] = useState<AdoptiumInfo | null>(null);

  useEffect(() => {
    invoke<AdoptiumInfo>("get_adoptium_url").then(setAdoptium);
  }, []);

  return (
    <div className="flex flex-col items-center justify-center h-screen gap-6 p-8">
      <div className="text-red-400 text-5xl mb-2">!</div>
      <h1 className="text-2xl font-bold">Java 21+ Required</h1>
      <p className="text-gray-400 text-center max-w-md">
        {javaStatus?.found
          ? `Java ${javaStatus.version} was detected, but version 21 or higher is required.`
          : "Java was not found on your system. Please install Java 21 or higher."}
      </p>
      {adoptium && (
        <button
          onClick={() => open(adoptium.url)}
          className="px-6 py-3 bg-blue-600 hover:bg-blue-700 rounded-lg font-medium transition-colors"
        >
          Get Java 21 for {adoptium.os} ({adoptium.arch}) ↗
        </button>
      )}
      <div className="flex gap-3">
        <button
          onClick={onRecheck}
          className="px-4 py-2 bg-gray-700 hover:bg-gray-600 rounded-lg text-sm transition-colors"
        >
          Re-check
        </button>
        <button
          onClick={onSkip}
          className="px-4 py-2 bg-gray-800 hover:bg-gray-700 rounded-lg text-sm text-gray-400 transition-colors"
        >
          Skip for now
        </button>
      </div>
    </div>
  );
}
