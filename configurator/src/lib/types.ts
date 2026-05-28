export interface JavaStatus {
  found: boolean;
  version: string;
  meets_minimum: boolean;
}

export interface AdoptiumInfo {
  url: string;
  os: string;
  arch: string;
}

export interface JarStatus {
  found: boolean;
  path: string;
  version: string;
}

export interface ReleaseInfo {
  version: string;
  download_url: string;
  checksum_url: string | null;
}

export interface DetectedServer {
  name: string;
  host: string;
  user: string;
  pass: string;
  resources_as_tools: boolean;
}

export interface DetectedConfig {
  tool: string;
  path: string;
  exists: boolean;
  writable: boolean;
  servers: DetectedServer[];
}

export interface ServerConfig {
  name: string;
  host: string;
  user: string;
  pass: string;
  resources_as_tools: boolean;
}
