import fs from 'fs';
import path from 'path';
import { execFile } from 'child_process';
import { promisify } from 'util';

const execFileAsync = promisify(execFile);
const DEFAULT_REPO = 'Ryan-Laws/easycodex';
const GITHUB_API = 'https://api.github.com';

type ReleaseAsset = {
  name?: unknown;
  browser_download_url?: unknown;
  size?: unknown;
};

type GitHubRelease = {
  tag_name?: unknown;
  html_url?: unknown;
  name?: unknown;
  published_at?: unknown;
  body?: unknown;
  assets?: unknown;
};

export type UpdateInfo = {
  currentVersion: string;
  latestVersion: string | null;
  updateAvailable: boolean;
  checkedAt: string;
  releaseUrl: string | null;
  releaseName: string | null;
  publishedAt: string | null;
  assets: Array<{ name: string; url: string; size: number | null }>;
  error?: string;
};

export type ApplyUpdateResult = {
  ok: boolean;
  before: string;
  after?: string;
  updated: boolean;
  restartRequired: boolean;
  output: string;
};

function repository(): string {
  return (process.env.EASYCODEX_UPDATE_REPO || DEFAULT_REPO).trim();
}

function readCurrentVersion(): string {
  for (const candidate of [
    path.join(__dirname, '..', 'package.json'),
    path.join(process.cwd(), 'package.json'),
    path.join(process.cwd(), '..', 'package.json'),
  ]) {
    try {
      if (!fs.existsSync(candidate)) continue;
      const parsed = JSON.parse(fs.readFileSync(candidate, 'utf8')) as { version?: unknown };
      if (typeof parsed.version === 'string' && parsed.version.trim()) return parsed.version.trim();
    } catch {}
  }
  return '0.0.0';
}

function normalizeVersion(value: string): string {
  return value.trim().replace(/^v/i, '');
}

function versionParts(value: string): number[] {
  return normalizeVersion(value)
    .split(/[.+-]/)
    .map((part) => Number.parseInt(part, 10))
    .map((part) => (Number.isFinite(part) ? part : 0));
}

function compareVersions(a: string, b: string): number {
  const left = versionParts(a);
  const right = versionParts(b);
  const length = Math.max(left.length, right.length, 3);
  for (let i = 0; i < length; i += 1) {
    const diff = (left[i] || 0) - (right[i] || 0);
    if (diff !== 0) return diff;
  }
  return 0;
}

function serializeAsset(asset: ReleaseAsset): { name: string; url: string; size: number | null } | null {
  if (typeof asset.name !== 'string' || typeof asset.browser_download_url !== 'string') return null;
  return {
    name: asset.name,
    url: asset.browser_download_url,
    size: typeof asset.size === 'number' ? asset.size : null,
  };
}

export async function checkForUpdates(): Promise<UpdateInfo> {
  const currentVersion = readCurrentVersion();
  const checkedAt = new Date().toISOString();
  try {
    const response = await fetch(`${GITHUB_API}/repos/${repository()}/releases/latest`, {
      headers: {
        accept: 'application/vnd.github+json',
        'user-agent': `EasyCodex-Agent-Relay/${currentVersion}`,
      },
    });
    if (!response.ok) {
      throw new Error(`GitHub release check failed with HTTP ${response.status}`);
    }
    const release = await response.json() as GitHubRelease;
    const tag = typeof release.tag_name === 'string' ? release.tag_name : '';
    const latestVersion = tag ? normalizeVersion(tag) : null;
    const assets = Array.isArray(release.assets)
      ? release.assets.map((asset) => serializeAsset(asset as ReleaseAsset)).filter((asset): asset is NonNullable<typeof asset> => Boolean(asset))
      : [];
    return {
      currentVersion,
      latestVersion,
      updateAvailable: Boolean(latestVersion && compareVersions(latestVersion, currentVersion) > 0),
      checkedAt,
      releaseUrl: typeof release.html_url === 'string' ? release.html_url : null,
      releaseName: typeof release.name === 'string' ? release.name : null,
      publishedAt: typeof release.published_at === 'string' ? release.published_at : null,
      assets,
    };
  } catch (err) {
    return {
      currentVersion,
      latestVersion: null,
      updateAvailable: false,
      checkedAt,
      releaseUrl: null,
      releaseName: null,
      publishedAt: null,
      assets: [],
      error: err instanceof Error ? err.message : String(err),
    };
  }
}

async function execGit(args: string[], cwd: string): Promise<string> {
  const result = await execFileAsync('git', args, {
    cwd,
    windowsHide: true,
    maxBuffer: 1024 * 1024 * 8,
  });
  return `${result.stdout || ''}${result.stderr || ''}`.trim();
}

async function execNpm(args: string[], cwd: string): Promise<string> {
  const command = process.platform === 'win32' ? 'npm.cmd' : 'npm';
  const result = await execFileAsync(command, args, {
    cwd,
    windowsHide: true,
    maxBuffer: 1024 * 1024 * 8,
  });
  return `${result.stdout || ''}${result.stderr || ''}`.trim();
}

async function gitRoot(start: string): Promise<string> {
  const root = await execGit(['rev-parse', '--show-toplevel'], start);
  return path.resolve(root.split(/\r?\n/)[0] || start);
}

export async function applyUpdate(): Promise<ApplyUpdateResult> {
  const before = readCurrentVersion();
  const root = await gitRoot(process.cwd());
  const relayDir = path.join(root, 'agent-relay');
  if (!fs.existsSync(path.join(root, '.git')) || !fs.existsSync(path.join(relayDir, 'package.json'))) {
    throw new Error('Quick update is only available when the relay is running from the EasyCodex git repository.');
  }

  const output: string[] = [];
  output.push(await execGit(['fetch', '--tags', 'origin'], root));
  output.push(await execGit(['pull', '--ff-only'], root));
  output.push(await execNpm(['install'], relayDir));
  output.push(await execNpm(['run', 'build'], relayDir));

  const after = readCurrentVersion();
  return {
    ok: true,
    before,
    after,
    updated: compareVersions(after, before) > 0,
    restartRequired: true,
    output: output.filter(Boolean).join('\n\n'),
  };
}
