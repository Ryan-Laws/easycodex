import express from 'express';
import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import os from 'os';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import net from 'net';
import { execFileSync, spawn, type ChildProcess, type SpawnOptions } from 'child_process';
import { promises as fsPromises } from 'fs';
import simpleGit from 'simple-git';
import qrcode from 'qrcode-terminal';
import { SessionOrchestrator, codexDesktopVisibleWorkspaceRoots, type MessageAttachmentInput } from './session-orchestrator';
import { applyUpdate, checkForUpdates, type UpdateInfo } from './updater';
import {
  registerNotificationToken,
  updateClientLanguage,
  getRegisteredTokenCount,
  getRegisteredClientCount,
  getNotificationPreferences,
  updateNotificationPreference,
  getNotificationHistory,
  type NotificationLevel,
} from './notifier';
import {
  buildCliArgs,
  cleanCliSandboxMode,
  replayStreamHistory,
  type CliRunOptions,
  type StreamHistoryEntry,
} from './cli-helpers';

const PORT = Number(process.env.PORT || 3001);
const CONFIG_DIR = path.join(os.homedir(), '.easycodex');
const CONFIG_PATH = path.join(CONFIG_DIR, 'config.json');

interface RelayConfig {
  apiKey: string;
}

interface ClientSession {
  ws: WebSocket;
  authenticated: boolean;
  clientId: string | null;
  connectedAt: number;
  remoteAddress: string;
}

interface PendingStreamBatch {
  agentId: string;
  event: string;
  data: Record<string, unknown>;
  textKey: 'delta' | 'chunk';
  textLength: number;
  timer: ReturnType<typeof setTimeout> | null;
}

interface CliRun {
  id: string;
  windowId: string;
  cwd: string;
  commandLine: string;
  process: ChildProcess;
  startedAt: number;
  jsonOutput: boolean;
  jsonBuffer: string;
}

interface RelayWarning {
  code: string;
  message: string;
  recommendation?: string;
}

function generateApiKey(): string {
  return crypto.randomBytes(32).toString('hex');
}

function maskSecret(value: string): string {
  const clean = value.trim();
  if (clean.length <= 12) return 'configured';
  return `${clean.slice(0, 6)}...${clean.slice(-4)}`;
}

function loadOrCreateConfig(): RelayConfig {
  if (process.env.API_KEY && process.env.API_KEY.trim()) {
    console.log('[auth] Using API key from API_KEY environment variable.');
    return { apiKey: process.env.API_KEY.trim() };
  }

  try {
    if (fs.existsSync(CONFIG_PATH)) {
      const raw = fs.readFileSync(CONFIG_PATH, 'utf8');
      const parsed = JSON.parse(raw) as Partial<RelayConfig>;
      if (typeof parsed.apiKey === 'string' && parsed.apiKey.length > 0) {
        return { apiKey: parsed.apiKey };
      }
    }
  } catch (err) {
    console.warn('[config] Failed to read existing config, regenerating:', err);
  }

  const apiKey = generateApiKey();
  fs.mkdirSync(CONFIG_DIR, { recursive: true });
  fs.writeFileSync(CONFIG_PATH, JSON.stringify({ apiKey }, null, 2), { encoding: 'utf8', mode: 0o600 });
  console.log('\n[auth] Generated relay API key for first start.');
  console.log(`[auth] Saved to ${CONFIG_PATH}`);
  console.log(`[auth] API key: ${maskSecret(apiKey)}\n`);
  return { apiKey };
}

function getLocalIP(): string {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] ?? []) {
      if (
        iface.family === 'IPv4' &&
        !iface.internal &&
        !iface.address.startsWith('169.254.') &&
        !iface.address.startsWith('198.18.') &&
        !iface.address.startsWith('198.19.')
      ) {
        return iface.address;
      }
    }
  }
  return '127.0.0.1';
}

function extractAuthKey(req: express.Request): string | null {
  const header = req.header('authorization');
  if (header?.startsWith('Bearer ')) {
    return header.slice(7).trim();
  }
  const queryKey = req.query.key;
  if (typeof queryKey === 'string' && queryKey.trim()) {
    return queryKey.trim();
  }
  return null;
}

function buildConnectDeepLink(relayUrl: string, apiKey: string): string {
  return `easycodex://connect?relayUrl=${encodeURIComponent(relayUrl)}&apiKey=${encodeURIComponent(apiKey)}`;
}

function buildConnectHttpUrl(networkUrl: string, apiKey: string): string {
  const httpUrl = networkUrl.replace(/^ws:\/\//i, 'http://').replace(/^wss:\/\//i, 'https://');
  return `${httpUrl}/c?k=${encodeURIComponent(apiKey)}`;
}

function shouldLogConnectSecrets(): boolean {
  return parseBooleanEnv(process.env.EASYCODEX_LOG_CONNECT_SECRETS);
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function cleanExecutablePath(value: unknown): string {
  return String(value || '').trim().replace(/^"+|"+$/g, '');
}

function commandForCmd(value: string): string {
  return `"${value.replace(/"/g, '""')}"`;
}

function codexCliInvocation(args: string[]): { command: string; args: string[]; options: SpawnOptions } {
  const configured = cleanExecutablePath(process.env.CODEX_EXECUTABLE || process.env.EASY_CODEX_CODEX_PATH);
  const command = configured || 'codex';
  if (process.platform === 'win32') {
    const executable = configured ? commandForCmd(command) : command;
    return {
      command: process.env.ComSpec || 'cmd.exe',
      args: ['/d', '/s', '/c', `call ${executable} ${args.map(commandForCmd).join(' ')}`],
      options: { windowsVerbatimArguments: true },
    };
  }
  return { command, args, options: {} };
}

function quoteCommandArg(value: string): string {
  if (/^[A-Za-z0-9_./:=+-]+$/.test(value)) return value;
  return `"${value.replace(/"/g, '\\"')}"`;
}

function codexCliCommandLine(args: string[]): string {
  const configured = cleanExecutablePath(process.env.CODEX_EXECUTABLE || process.env.EASY_CODEX_CODEX_PATH);
  const executable = configured || 'codex';
  return [quoteCommandArg(executable), ...args.map(quoteCommandArg)].join(' ');
}

function resolveWithinCwd(cwd: string, relativePath?: string): string {
  const safeCwd = path.resolve(cwd || process.cwd());
  const resolved = path.resolve(safeCwd, relativePath || '.');
  if (isWithinBase(safeCwd, resolved)) {
    return resolved;
  }
  throw new Error('Path escapes cwd');
}

function isWithinBase(base: string, targetPath: string): boolean {
  const resolvedBaseRaw = path.resolve(base);
  const resolvedRaw = path.resolve(targetPath);
  const resolvedBase = process.platform === 'win32' ? resolvedBaseRaw.toLowerCase() : resolvedBaseRaw;
  const resolved = process.platform === 'win32' ? resolvedRaw.toLowerCase() : resolvedRaw;
  return resolved === resolvedBase || resolved.startsWith(`${resolvedBase}${path.sep}`);
}

function getReposRoot(): string {
  const root = process.env.REPOS_DIR
    ? path.resolve(process.env.REPOS_DIR)
    : path.join(os.homedir(), '.easycodex', 'repos');
  fs.mkdirSync(root, { recursive: true });
  return root;
}

function resolveWithinBase(base: string, targetPath: string): string {
  const resolvedBase = path.resolve(base);
  const resolved = path.resolve(targetPath);
  if (isWithinBase(resolvedBase, resolved)) {
    return resolved;
  }
  throw new Error('Path escapes repository root');
}

function resolveSafePath(targetPath?: string): string {
  const raw = typeof targetPath === 'string' ? targetPath.trim() : '';
  if (raw.includes('\0')) throw new Error('Path contains invalid characters');
  return path.resolve(raw || getPrimaryWorkspaceRoot());
}

function resolveExistingDirectory(targetPath?: string): string {
  const resolved = resolveSafePath(targetPath);
  const stat = fs.statSync(resolved);
  if (!stat.isDirectory()) throw new Error(`Path is not a directory: ${resolved}`);
  return resolved;
}

function getPrimaryWorkspaceRoot(): string {
  return path.resolve(process.env.CODEX_CWD || process.cwd());
}

type GitWorktreeInfo = {
  path: string;
  name: string;
  branch?: string;
  bare: boolean;
  detached: boolean;
  locked: boolean;
};

function parseGitWorktreePorcelain(output: string): string[] {
  return parseGitWorktreeEntries(output).map((entry) => entry.path);
}

function parseGitWorktreeEntries(output: string): GitWorktreeInfo[] {
  const worktrees: GitWorktreeInfo[] = [];
  let current: Partial<GitWorktreeInfo> | null = null;

  const finish = () => {
    if (!current?.path) return;
    const resolvedPath = path.resolve(current.path);
    worktrees.push({
      path: resolvedPath,
      name: directoryLabel(resolvedPath),
      branch: current.branch,
      bare: current.bare === true,
      detached: current.detached === true,
      locked: current.locked === true,
    });
  };

  for (const rawLine of output.split(/\r?\n/)) {
    const line = rawLine.trim();
    if (!line) continue;
    if (line.startsWith('worktree ')) {
      finish();
      const worktreePath = line.slice('worktree '.length).trim();
      current = { path: worktreePath };
      continue;
    }
    if (!current) continue;
    if (line.startsWith('branch ')) {
      current.branch = line
        .slice('branch '.length)
        .trim()
        .replace(/^refs\/heads\//, '');
    } else if (line === 'bare') {
      current.bare = true;
    } else if (line === 'detached') {
      current.detached = true;
    } else if (line.startsWith('locked')) {
      current.locked = true;
    }
  }

  finish();
  return worktrees;
}

function gitWorktreeRootsForRepoSync(repoPath: string): string[] {
  try {
    if (!fs.existsSync(path.join(repoPath, '.git'))) return [];
    const output = execFileSync('git', ['-C', repoPath, 'worktree', 'list', '--porcelain'], {
      encoding: 'utf8',
      windowsHide: true,
    });
    return parseGitWorktreePorcelain(output).map((entry) => path.resolve(entry));
  } catch {
    return [];
  }
}

function discoverRelayGitWorktrees(): string[] {
  const roots = [getPrimaryWorkspaceRoot()];
  const reposRoot = getReposRoot();
  try {
    const entries = fs.readdirSync(reposRoot, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.isDirectory()) roots.push(path.join(reposRoot, entry.name));
    }
  } catch {}
  return uniqueResolvedPaths(roots.flatMap((root) => gitWorktreeRootsForRepoSync(root)));
}

function getAllowedWorkspaceRoots(): string[] {
  return uniqueResolvedPaths([
    getPrimaryWorkspaceRoot(),
    getReposRoot(),
    ...Array.from(customWorkspaceRoots),
    ...codexDesktopVisibleWorkspaceRoots(),
    ...discoverRelayGitWorktrees(),
  ]);
}

function isDisallowedCustomWorkspaceRoot(targetPath: string): boolean {
  const resolved = path.resolve(targetPath);
  const parsed = path.parse(resolved);
  if (resolved === parsed.root) return true;
  const home = path.resolve(os.homedir());
  if (normalizePathKey(resolved) === normalizePathKey(home)) return true;
  const homeBoundaries = ['Desktop', 'Documents', 'Downloads'].map((name) => path.join(home, name));
  if (homeBoundaries.some((root) => normalizePathKey(resolved) === normalizePathKey(root))) return true;
  const disallowed = [
    process.env.SystemRoot,
    process.env.ProgramFiles,
    process.env['ProgramFiles(x86)'],
    process.env.APPDATA,
    process.env.LOCALAPPDATA,
  ].filter((entry): entry is string => typeof entry === 'string' && entry.trim() !== '');
  return disallowed.some((root) => isWithinBase(root, resolved));
}

function trustCustomWorkspaceRoot(targetPath: string): string {
  const resolved = resolveExistingDirectory(targetPath);
  if (isDisallowedCustomWorkspaceRoot(resolved)) {
    throw new Error('Refusing to use a system, profile, or application data directory as a project workspace.');
  }
  customWorkspaceRoots.add(resolved);
  return resolved;
}

function resolveWorkspaceCwd(cwd?: string): string {
  const requested = resolveSafePath(cwd || getPrimaryWorkspaceRoot());
  const allowedRoots = getAllowedWorkspaceRoots();
  if (allowedRoots.some((root) => isWithinBase(root, requested))) {
    const stat = fs.statSync(requested);
    if (!stat.isDirectory()) throw new Error(`Path is not a directory: ${requested}`);
    return requested;
  }
  throw new Error('Path is outside the allowed EasyCodex workspace roots. Use trust_workspace_root before accessing a new project directory.');
}

function resolveWithinWorkspace(cwd: string | undefined, relativePath?: string): { cwd: string; target: string } {
  const safeCwd = resolveWorkspaceCwd(cwd);
  return {
    cwd: safeCwd,
    target: resolveWithinCwd(safeCwd, relativePath),
  };
}

function sanitizeAttachmentName(name: string): string {
  const base = path.basename((name || 'attachment').replace(/\0/g, ''));
  const normalized = base.replace(/[<>:"/\\|?*\x00-\x1F]/g, '_').replace(/\s+/g, ' ').trim();
  return normalized.slice(0, 160) || 'attachment';
}

function dateStampForAttachment(): string {
  return new Date().toISOString().replace(/[-:]/g, '').replace(/\.\d{3}Z$/, 'Z');
}

function repoNameFromUrl(url: string): string {
  const normalized = url.replace(/\/$/, '');
  const base = normalized.split('/').pop() || 'repo';
  return base.replace(/\.git$/i, '') || 'repo';
}

function parseBooleanEnv(value: string | undefined): boolean {
  if (!value) return false;
  const normalized = value.trim().toLowerCase();
  return normalized === '1' || normalized === 'true' || normalized === 'yes' || normalized === 'on';
}

function addRelayWarning(warning: RelayWarning): void {
  if (relayWarnings.some((entry) => entry.code === warning.code)) return;
  relayWarnings.push(warning);
}

function probeListen(host: string, port: number): Promise<{ ok: boolean; errorCode?: string }> {
  return new Promise((resolve) => {
    const probe = net.createServer();
    let settled = false;
    const finish = (result: { ok: boolean; errorCode?: string }) => {
      if (settled) return;
      settled = true;
      resolve(result);
    };
    probe.once('error', (err: NodeJS.ErrnoException) => {
      finish({ ok: false, errorCode: err.code });
    });
    probe.once('listening', () => {
      probe.close(() => finish({ ok: true }));
    });
    probe.listen(port, host);
  });
}

async function detectPortWarnings(): Promise<void> {
  const loopbackProbe = await probeListen('127.0.0.1', PORT);
  if (!loopbackProbe.ok) {
    addRelayWarning({
      code: 'loopback_port_unavailable',
      message: `127.0.0.1:${PORT} is already in use, so localhost health checks or desktop clients may reach a different process.`,
      recommendation: `Use the printed network URL, free 127.0.0.1:${PORT}, or restart the relay with another PORT value.`,
    });
  }
}

function uniqueResolvedPaths(paths: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const entry of paths) {
    const resolved = path.resolve(entry);
    const key = process.platform === 'win32' ? resolved.toLowerCase() : resolved;
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(resolved);
  }
  return result;
}

function normalizePathKey(targetPath: string): string {
  const resolved = path.resolve(targetPath);
  return process.platform === 'win32' ? resolved.toLowerCase() : resolved;
}

function directoryLabel(targetPath: string): string {
  const parsed = path.parse(targetPath);
  const trimmed = targetPath.replace(/[\\/]+$/, '');
  return path.basename(trimmed) || parsed.root || targetPath;
}

async function browseDirectories(targetPath?: string) {
  const allowedRoots = getAllowedWorkspaceRoots();
  const requested = targetPath?.trim() ? resolveSafePath(targetPath) : allowedRoots[0];
  if (!allowedRoots.some((root) => isWithinBase(root, requested))) {
    throw new Error('Path is outside the allowed EasyCodex workspace roots. Use trust_workspace_root before browsing a new project directory.');
  }
  const current = resolveExistingDirectory(requested);
  const roots = uniqueResolvedPaths(allowedRoots).map((root) => ({
    name: directoryLabel(root),
    path: root,
  }));
  let worktrees: GitWorktreeInfo[] = [];
  try {
    const output = await simpleGit({ baseDir: current }).raw(['worktree', 'list', '--porcelain']);
    worktrees = parseGitWorktreeEntries(output).map((entry) => ({
      ...entry,
      current: normalizePathKey(entry.path) === normalizePathKey(current),
    })) as Array<GitWorktreeInfo & { current: boolean }>;
  } catch {}
  const entries = await fsPromises.readdir(current, { withFileTypes: true });
  const directories = entries
    .filter((entry) => entry.isDirectory())
    .map((entry) => ({
      name: entry.name,
      path: path.join(current, entry.name),
    }))
    .sort((a, b) => a.name.localeCompare(b.name));
  const parent = path.dirname(current);
  const safeParent = parent !== current && allowedRoots.some((root) => isWithinBase(root, parent)) ? parent : null;
  return {
    path: current,
    parent: safeParent,
    roots,
    worktrees,
    entries: directories,
  };
}

function normalizeGitCommitFiles(cwd: string, files: unknown): string[] {
  if (!Array.isArray(files) || files.length === 0) {
    throw new Error('files is required; choose the exact files to commit.');
  }
  const normalized = files.map((file) => {
    if (typeof file !== 'string' || !file.trim()) {
      throw new Error('files must contain non-empty path strings.');
    }
    const target = resolveWithinCwd(cwd, file.trim());
    return path.relative(cwd, target).split(path.sep).join('/');
  });
  return Array.from(new Set(normalized));
}

async function maybeAutoPullRepo(cwd: string): Promise<void> {
  if (!parseBooleanEnv(process.env.AUTO_PULL_REPOS)) return;

  const reposRoot = getReposRoot();
  let safePath: string;
  try {
    safePath = resolveWithinBase(reposRoot, cwd);
  } catch {
    return;
  }

  if (!fs.existsSync(path.join(safePath, '.git'))) return;

  try {
    await simpleGit({ baseDir: safePath }).pull();
    console.log(`[relay] Auto-pulled repo before agent start: ${safePath}`);
  } catch (err) {
    console.warn(`[relay] Auto-pull failed for ${safePath}:`, err);
  }
}

function gitForCwd(cwd: string) {
  const safeCwd = resolveWorkspaceCwd(cwd);
  return simpleGit({
    baseDir: safeCwd,
  });
}

function codexCliVersion(): string {
  try {
    const invocation = codexCliInvocation(['--version']);
    return execFileSync(invocation.command, invocation.args, {
      encoding: 'utf8',
      windowsHide: true,
      timeout: 5000,
      ...invocation.options,
    }).trim();
  } catch {
    return '';
  }
}

function cliJsonText(value: unknown): string {
  if (typeof value === 'string') return value;
  if (!value || typeof value !== 'object') return '';
  const record = value as Record<string, unknown>;
  for (const key of ['text', 'delta', 'message', 'content', 'summary', 'preview']) {
    const text = cliJsonText(record[key]);
    if (text) return text;
  }
  for (const key of ['item', 'payload', 'data', 'msg']) {
    const text = cliJsonText(record[key]);
    if (text) return text;
  }
  return '';
}

function cliJsonTitle(value: Record<string, unknown>): string {
  for (const key of ['type', 'event', 'kind', 'role']) {
    const text = typeof value[key] === 'string' ? value[key].trim() : '';
    if (text) return text;
  }
  return 'event';
}

function handleCliJsonLine(run: CliRun, rawLine: string) {
  const line = rawLine.trim();
  if (!line) return;
  try {
    const parsed = JSON.parse(line) as Record<string, unknown>;
    const text = cliJsonText(parsed);
    const title = cliJsonTitle(parsed);
    const isFinal = /final|completed|done|last/i.test(title);
    broadcast('cli', isFinal ? 'cli/final' : text ? 'cli/output' : 'cli/status', {
      windowId: run.windowId,
      runId: run.id,
      cwd: run.cwd,
      stream: 'stdout',
      chunk: text || line,
      finalText: isFinal ? text : undefined,
      kind: title,
      title,
      structured: true,
      timestamp: Date.now(),
    });
  } catch {
    broadcast('cli', 'cli/output', {
      windowId: run.windowId,
      runId: run.id,
      stream: 'stdout',
      chunk: `${rawLine}\n`,
      structured: false,
      timestamp: Date.now(),
    });
  }
}

function handleCliJsonChunk(run: CliRun, chunk: Buffer) {
  run.jsonBuffer += chunk.toString('utf8');
  const lines = run.jsonBuffer.split(/\r?\n/);
  run.jsonBuffer = lines.pop() || '';
  lines.forEach((line) => handleCliJsonLine(run, line));
}

function startCliRun(options: CliRunOptions): CliRun {
  const windowId = options.windowId;
  const cwd = options.cwd;
  const prompt = options.prompt;
  const safeWindowId = windowId.trim() || crypto.randomUUID();
  if (activeCliRuns.has(safeWindowId)) {
    throw new Error('This Codex CLI window is already running. Stop it or wait for it to finish.');
  }
  const trimmedPrompt = prompt.trim();
  const safeCwd = resolveWorkspaceCwd(cwd || getPrimaryWorkspaceRoot());
  const runId = crypto.randomUUID();
  const { args, mode } = buildCliArgs(
    { ...options, prompt: trimmedPrompt },
    safeCwd,
    (image) => resolveWithinCwd(safeCwd, image),
    (dir) => resolveWorkspaceCwd(dir),
  );
  const cleanModel = typeof options.model === 'string' ? options.model.trim() : '';
  const cleanReasoning = typeof options.reasoningEffort === 'string' ? options.reasoningEffort.trim() : '';
  const cleanSandbox = cleanCliSandboxMode(options.sandboxMode);
  const invocation = codexCliInvocation(args);
  const commandLine = codexCliCommandLine(args);
  const child = spawn(invocation.command, invocation.args, {
    cwd: safeCwd,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: { ...process.env },
    windowsHide: true,
    ...invocation.options,
  });
  const run: CliRun = {
    id: runId,
    windowId: safeWindowId,
    cwd: safeCwd,
    commandLine,
    process: child,
    startedAt: Date.now(),
    jsonOutput: options.jsonOutput === true,
    jsonBuffer: '',
  };
  activeCliRuns.set(safeWindowId, run);
  broadcast('cli', 'cli/started', {
    windowId: safeWindowId,
    runId,
    cwd: safeCwd,
    command: commandLine,
    mode,
    model: cleanModel,
    reasoningEffort: cleanReasoning,
    sandboxMode: cleanSandbox,
    skipGitRepoCheck: options.skipGitRepoCheck !== false,
    structured: run.jsonOutput,
    timestamp: run.startedAt,
  });
  child.stdout?.on('data', (chunk: Buffer) => {
    if (run.jsonOutput) {
      handleCliJsonChunk(run, chunk);
      return;
    }
    broadcast('cli', 'cli/output', {
      windowId: safeWindowId,
      runId,
      stream: 'stdout',
      chunk: chunk.toString('utf8'),
      timestamp: Date.now(),
    });
  });
  child.stderr?.on('data', (chunk: Buffer) => {
    broadcast('cli', 'cli/output', {
      windowId: safeWindowId,
      runId,
      stream: 'stderr',
      chunk: chunk.toString('utf8'),
      timestamp: Date.now(),
    });
  });
  child.on('error', (err) => {
    if (activeCliRuns.get(safeWindowId)?.id === runId) activeCliRuns.delete(safeWindowId);
    broadcast('cli', 'cli/failed', {
      windowId: safeWindowId,
      runId,
      cwd: safeCwd,
      error: err.message,
      timestamp: Date.now(),
    });
  });
  child.on('close', (code, signal) => {
    if (run.jsonOutput && run.jsonBuffer.trim()) {
      handleCliJsonLine(run, run.jsonBuffer);
      run.jsonBuffer = '';
    }
    if (activeCliRuns.get(safeWindowId)?.id === runId) activeCliRuns.delete(safeWindowId);
    broadcast('cli', 'cli/exited', {
      windowId: safeWindowId,
      runId,
      cwd: safeCwd,
      code,
      signal,
      durationMs: Date.now() - run.startedAt,
      timestamp: Date.now(),
    });
  });
  return run;
}

function stopCliRun(windowId?: string): boolean {
  const run = windowId?.trim()
    ? activeCliRuns.get(windowId.trim())
    : activeCliRuns.values().next().value;
  if (!run) return false;
  run.process.kill();
  return true;
}

const config = loadOrCreateConfig();
const relaySessionId = crypto.randomUUID();

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server });
const clients = new Map<WebSocket, ClientSession>();
const STREAM_HISTORY_LIMIT = Number(process.env.STREAM_HISTORY_LIMIT || 5000);
const CODEX_WATCH_DEBOUNCE_MS = Number(process.env.CODEX_WATCH_DEBOUNCE_MS || 150);
const CODEX_THREAD_POLL_INTERVAL_MS = Number(process.env.CODEX_THREAD_POLL_INTERVAL_MS || 5000);
const MOBILE_STREAM_TEXT_LIMIT = Number(process.env.EASY_CODEX_MOBILE_STREAM_TEXT_LIMIT || 12000);
const STREAM_BATCH_FLUSH_MS = Number(process.env.EASY_CODEX_STREAM_BATCH_FLUSH_MS || 120);
const STREAM_BATCH_MAX_CHARS = Number(process.env.EASY_CODEX_STREAM_BATCH_MAX_CHARS || 4000);
const MOBILE_STREAM_TRUNCATED_NOTICE = '\n\n[EasyCodex mobile truncated this long output. Use the desktop relay/Codex session for the full text.]';
let nextStreamSeq = 1;
const streamHistory: StreamHistoryEntry[] = [];
let codexWatchTimer: ReturnType<typeof setTimeout> | null = null;
let codexThreadPollTimer: ReturnType<typeof setInterval> | null = null;
let codexThreadPollInFlight = false;
let lastCodexThreadSignature: string | null = null;
const codexWatchers: fs.FSWatcher[] = [];
let lastUpdateCheck: UpdateInfo | null = null;
const activeCliRuns = new Map<string, CliRun>();
const customWorkspaceRoots = new Set<string>();
const relayWarnings: RelayWarning[] = [];
const pendingStreamBatches = new Map<string, PendingStreamBatch>();

function cliWindowRecentEvents(windowId: string, limit = 120): StreamHistoryEntry[] {
  const cleanWindowId = windowId.trim();
  if (!cleanWindowId) return [];
  flushStreamBatches('cli');
  return streamHistory
    .filter((entry) => {
      if (entry.agentId !== 'cli' || !entry.event.startsWith('cli/')) return false;
      const data = entry.data as Record<string, unknown> | null;
      return data != null && typeof data === 'object' && data.windowId === cleanWindowId;
    })
    .slice(-Math.max(1, Math.min(limit, 500)));
}

function getConnectedAuthenticatedCount(): number {
  let count = 0;
  for (const session of clients.values()) {
    if (session.authenticated) count += 1;
  }
  return count;
}

function emitDesktopRelayEvent(type: string, data: Record<string, unknown> = {}) {
  console.log(`[relay:event] ${JSON.stringify({ type, timestamp: Date.now(), ...data })}`);
}

function emitClientState(reason: string) {
  emitDesktopRelayEvent('clients', {
    reason,
    connectedClients: getConnectedAuthenticatedCount(),
    notificationClients: getRegisteredClientCount(),
    notificationTokens: getRegisteredTokenCount(),
    lastClientLanguage,
  });
}

async function runUpdateCheck(reason: string): Promise<UpdateInfo> {
  lastUpdateCheck = await checkForUpdates();
  emitDesktopRelayEvent('update', {
    reason,
    update: lastUpdateCheck,
  });
  if (lastUpdateCheck.updateAvailable) {
    broadcast('system', 'relay/update_available', {
      reason,
      update: lastUpdateCheck,
      timestamp: Date.now(),
    });
  }
  return lastUpdateCheck;
}

function compactStreamText(value: string, fallback = '详细内容已省略。'): string {
  if (!value.trim()) return value;
  if (value.length <= MOBILE_STREAM_TEXT_LIMIT && !value.includes('\n')) return value;
  return fallback;
}

function capStreamDelta(value: string): string {
  if (value.length <= MOBILE_STREAM_TEXT_LIMIT) return value;
  return `${value.slice(0, MOBILE_STREAM_TEXT_LIMIT).trimEnd()}${MOBILE_STREAM_TRUNCATED_NOTICE}`;
}

function streamDiffSummary(text: string): { files: string[]; additions: number; deletions: number } {
  const files = new Set<string>();
  let additions = 0;
  let deletions = 0;
  for (const line of text.split(/\r?\n/)) {
    if (line.startsWith('+') && !line.startsWith('+++')) additions += 1;
    if (line.startsWith('-') && !line.startsWith('---')) deletions += 1;
    for (const candidate of [
      line.match(/^diff --git a\/(.+?) b\/(.+)$/)?.[2],
      line.match(/^\+\+\+ b\/(.+)$/)?.[1],
      line.match(/^--- a\/(.+)$/)?.[1],
    ]) {
      if (candidate && candidate !== '/dev/null') files.add(candidate.replace(/\\/g, '/'));
    }
  }
  return { files: Array.from(files), additions, deletions };
}

function summarizeFileStreamText(value: string): string {
  const summary = streamDiffSummary(value);
  if (summary.files.length === 0) return '文件已修改。';
  const stats = summary.additions + summary.deletions > 0 ? ` (+${summary.additions} -${summary.deletions})` : '';
  return `文件改动\n${summary.files.slice(0, 8).map((filePath) => `- ${filePath}${stats}`).join('\n')}`;
}

function sanitizeStreamData(value: unknown, event = ''): unknown {
  if (typeof value === 'string') {
    if (event.includes('commandOutput')) return '命令输出已省略。';
    if (event.includes('fileChange') || event.includes('diff')) return summarizeFileStreamText(value);
    return compactStreamText(value);
  }
  if (!value || typeof value !== 'object') return value;
  if (Array.isArray(value)) return value.map((item) => sanitizeStreamData(item, event));

  const result: Record<string, unknown> = {};
  for (const [key, child] of Object.entries(value as Record<string, unknown>)) {
    const heavyText = [
      'text',
      'output',
      'aggregatedOutput',
      'aggregated_output',
      'stdout',
      'stderr',
      'diff',
      'patch',
      'content',
      'command',
      'input',
      'arguments',
    ].includes(key);
    if (typeof child === 'string' && key === 'delta') {
      result[key] = capStreamDelta(child);
    } else if (typeof child === 'string' && heavyText) {
      if (key === 'command') {
        result[key] = '正在运行命令。';
      } else if (key === 'input' || key === 'arguments') {
        result[key] = '参数已省略。';
      } else if (event.includes('commandOutput') || key === 'stdout' || key === 'stderr' || key === 'aggregated_output' || key === 'aggregatedOutput' || key === 'output') {
        result[key] = '命令输出已省略。';
      } else if (event.includes('fileChange') || event.includes('diff') || key === 'diff' || key === 'patch') {
        result[key] = summarizeFileStreamText(child);
      } else {
        result[key] = compactStreamText(child);
      }
    } else {
      result[key] = sanitizeStreamData(child, event);
    }
  }
  return result;
}

function rememberStreamEvent(agentId: string, event: string, data: unknown): StreamHistoryEntry {
  const entry: StreamHistoryEntry = {
    type: 'stream',
    sessionId: relaySessionId,
    seq: nextStreamSeq++,
    timestamp: Date.now(),
    agentId,
    event,
    data: event.startsWith('cli/') ? data : sanitizeStreamData(data, event),
  };
  streamHistory.push(entry);
  if (streamHistory.length > STREAM_HISTORY_LIMIT) {
    streamHistory.splice(0, streamHistory.length - STREAM_HISTORY_LIMIT);
  }
  return entry;
}

function sendStreamEvent(agentId: string, event: string, data: unknown) {
  const entry = rememberStreamEvent(agentId, event, data);
  const message = JSON.stringify(entry);
  for (const session of clients.values()) {
    if (!session.authenticated) continue;
    if (session.ws.readyState === WebSocket.OPEN) {
      session.ws.send(message);
    }
  }
}

function streamBatchTextKey(event: string, data: unknown): 'delta' | 'chunk' | null {
  if (!data || typeof data !== 'object' || Array.isArray(data)) return null;
  const record = data as Record<string, unknown>;
  if (event === 'cli/output' && typeof record.chunk === 'string') return 'chunk';
  if (event.includes('/delta') && typeof record.delta === 'string') return 'delta';
  return null;
}

function streamBatchKey(agentId: string, event: string, data: Record<string, unknown>, textKey: 'delta' | 'chunk'): string {
  if (event === 'cli/output') {
    const windowId = typeof data.windowId === 'string' ? data.windowId : '';
    const stream = typeof data.stream === 'string' ? data.stream : '';
    return [agentId, event, windowId, stream, textKey].join('\u0000');
  }
  const item = data.item && typeof data.item === 'object' && !Array.isArray(data.item)
    ? data.item as Record<string, unknown>
    : null;
  const itemId = typeof data.itemId === 'string'
    ? data.itemId
    : typeof item?.id === 'string'
      ? item.id
      : '';
  return [agentId, event, itemId, textKey].join('\u0000');
}

function flushStreamBatch(key: string) {
  const batch = pendingStreamBatches.get(key);
  if (!batch) return;
  pendingStreamBatches.delete(key);
  if (batch.timer) clearTimeout(batch.timer);
  sendStreamEvent(batch.agentId, batch.event, batch.data);
}

function flushStreamBatches(agentId?: string) {
  for (const [key, batch] of Array.from(pendingStreamBatches.entries())) {
    if (!agentId || batch.agentId === agentId) flushStreamBatch(key);
  }
}

function enqueueStreamBatch(agentId: string, event: string, data: unknown): boolean {
  const textKey = streamBatchTextKey(event, data);
  if (!textKey) return false;
  const record = data as Record<string, unknown>;
  const text = record[textKey];
  if (typeof text !== 'string' || text.length === 0) return false;
  const key = streamBatchKey(agentId, event, record, textKey);
  const existing = pendingStreamBatches.get(key);
  if (existing) {
    existing.data[textKey] = `${existing.data[textKey] || ''}${text}`;
    existing.data.timestamp = Date.now();
    existing.textLength += text.length;
    if (existing.textLength >= Math.max(1, STREAM_BATCH_MAX_CHARS)) flushStreamBatch(key);
    return true;
  }
  const batch: PendingStreamBatch = {
    agentId,
    event,
    data: { ...record, timestamp: Date.now() },
    textKey,
    textLength: text.length,
    timer: null,
  };
  batch.timer = setTimeout(() => flushStreamBatch(key), Math.max(0, STREAM_BATCH_FLUSH_MS));
  pendingStreamBatches.set(key, batch);
  if (batch.textLength >= Math.max(1, STREAM_BATCH_MAX_CHARS)) flushStreamBatch(key);
  return true;
}

function broadcast(agentId: string, event: string, data: unknown) {
  if (enqueueStreamBatch(agentId, event, data)) return;
  flushStreamBatches(agentId);
  sendStreamEvent(agentId, event, data);
}

function broadcastCodexThreadsChanged(reason: string, detail: Record<string, unknown> = {}) {
  if (codexWatchTimer) clearTimeout(codexWatchTimer);
  codexWatchTimer = setTimeout(() => {
    codexWatchTimer = null;
    broadcast('system', 'codex/threads_changed', {
      reason,
      timestamp: Date.now(),
      ...detail,
    });
  }, Math.max(0, CODEX_WATCH_DEBOUNCE_MS));
}

function shouldNotifyCodexFileChange(filename: string | Buffer | null): boolean {
  if (!filename) return true;
  const normalized = filename.toString().replace(/\\/g, '/').toLowerCase();
  if (!normalized) return true;
  if (normalized.includes('/log') || normalized.includes('/cache')) return false;
  return normalized.includes('session')
    || normalized.includes('thread')
    || normalized.endsWith('.json')
    || normalized.endsWith('.jsonl');
}

function startCodexStateWatcher() {
  const roots = Array.from(new Set([
    path.join(os.homedir(), '.codex'),
    path.join(os.homedir(), '.codex', 'sessions'),
  ].map((entry) => path.resolve(entry))));

  for (const root of roots) {
    if (!fs.existsSync(root)) continue;
    try {
      const watcher = fs.watch(root, { recursive: process.platform === 'win32' }, (eventType, filename) => {
        if (!shouldNotifyCodexFileChange(filename)) return;
        broadcastCodexThreadsChanged('codex_state_changed', {
          eventType,
          path: filename?.toString() || root,
        });
      });
      codexWatchers.push(watcher);
      console.log(`[relay] Watching Codex state for live sync: ${root}`);
    } catch (err) {
      console.warn(`[relay] Failed to watch Codex state at ${root}:`, err);
    }
  }
}

function codexThreadSignature(threads: Array<{ id?: unknown; name?: unknown; preview?: unknown; updatedAt?: unknown; status?: unknown; pinned?: unknown }>): string {
  return threads
    .map((thread) => {
      const id = typeof thread.id === 'string' ? thread.id : '';
      const name = typeof thread.name === 'string' ? thread.name : '';
      const preview = typeof thread.preview === 'string' ? thread.preview : '';
      const updatedAt = typeof thread.updatedAt === 'number' || typeof thread.updatedAt === 'string'
        ? String(thread.updatedAt)
        : '';
      const status = typeof thread.status === 'string' ? thread.status : '';
      const pinned = thread.pinned === true ? 'pinned' : '';
      return `${id}:${name}:${preview}:${updatedAt}:${status}:${pinned}`;
    })
    .filter(Boolean)
    .sort()
    .join('|');
}

async function pollCodexThreadsForChanges(reason: string) {
  if (codexThreadPollInFlight || getConnectedAuthenticatedCount() === 0) return;
  codexThreadPollInFlight = true;
  try {
    const result = await manager.listCodexThreads({ limit: 100, all: true });
    const threads = Array.isArray(result.data) ? result.data : [];
    const signature = codexThreadSignature(threads);
    if (lastCodexThreadSignature !== null && signature !== lastCodexThreadSignature) {
      broadcastCodexThreadsChanged(reason, { source: 'poll' });
    }
    lastCodexThreadSignature = signature;
  } catch (err) {
    console.warn('[relay] Failed to poll Codex threads:', err);
  } finally {
    codexThreadPollInFlight = false;
  }
}

function updateCodexThreadPoller() {
  if (getConnectedAuthenticatedCount() === 0) {
    if (codexThreadPollTimer) clearInterval(codexThreadPollTimer);
    codexThreadPollTimer = null;
    return;
  }
  if (codexThreadPollTimer) return;
  void pollCodexThreadsForChanges('codex_thread_poll_started');
  codexThreadPollTimer = setInterval(() => {
    void pollCodexThreadsForChanges('codex_thread_poll_changed');
  }, Math.max(1000, CODEX_THREAD_POLL_INTERVAL_MS));
}

const manager = new SessionOrchestrator(broadcast);
const startedAt = Date.now();
let lastClientLanguage: string | null = null;

app.get('/health', (req, res) => {
  const key = extractAuthKey(req);
  if (!key || key !== config.apiKey) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  res.json({
    status: 'ok',
    sessionId: relaySessionId,
    workspaceRoot: getPrimaryWorkspaceRoot(),
    reposRoot: getReposRoot(),
    allowedWorkspaceRoots: getAllowedWorkspaceRoots(),
    uptimeMs: Date.now() - startedAt,
    agents: manager.listAgents().length,
    connectedClients: getConnectedAuthenticatedCount(),
    notificationClients: getRegisteredClientCount(),
    notificationTokens: getRegisteredTokenCount(),
    lastClientLanguage,
    warnings: relayWarnings,
    update: lastUpdateCheck,
    runtime: manager.getRuntimeCapabilities(),
    system: {
      hostname: os.hostname(),
      platform: process.platform,
      arch: process.arch,
      nodeVersion: process.version,
      cpus: os.cpus().length,
      totalMemory: os.totalmem(),
      freeMemory: os.freemem(),
    },
  });
});

function imageContentType(filePath: string): string | null {
  switch (path.extname(filePath).toLowerCase()) {
    case '.png':
      return 'image/png';
    case '.jpg':
    case '.jpeg':
      return 'image/jpeg';
    case '.gif':
      return 'image/gif';
    case '.webp':
      return 'image/webp';
    case '.bmp':
      return 'image/bmp';
    default:
      return null;
  }
}

app.get('/media/image', async (req, res) => {
  const key = extractAuthKey(req);
  if (!key || key !== config.apiKey) {
    res.status(401).json({ error: 'Unauthorized' });
    return;
  }

  const rawPath = typeof req.query.path === 'string' ? req.query.path.trim() : '';
  if (!rawPath) {
    res.status(400).json({ error: 'path is required' });
    return;
  }

  try {
    const target = path.resolve(rawPath);
    if (!getAllowedWorkspaceRoots().some((root) => isWithinBase(root, target))) {
      res.status(403).json({ error: 'Path is outside the allowed EasyCodex workspace roots.' });
      return;
    }
    const stat = await fsPromises.stat(target);
    if (!stat.isFile()) {
      res.status(404).json({ error: 'Image not found' });
      return;
    }
    const contentType = imageContentType(target);
    if (!contentType) {
      res.status(415).json({ error: 'Unsupported image type' });
      return;
    }
    res.setHeader('Content-Type', contentType);
    res.setHeader('Cache-Control', 'private, max-age=60');
    fs.createReadStream(target).pipe(res);
  } catch (err) {
    res.status(404).json({ error: err instanceof Error ? err.message : String(err) });
  }
});

function handleConnectPage(req: express.Request, res: express.Response) {
  const host = req.get('host') || `127.0.0.1:${PORT}`;
  const relayUrlFromHost = `${req.protocol === 'https' ? 'wss' : 'ws'}://${host}`;
  const relayUrl = typeof req.query.relayUrl === 'string' && req.query.relayUrl.trim()
    ? req.query.relayUrl.trim()
    : relayUrlFromHost;
  const apiKey =
    (typeof req.query.apiKey === 'string' && req.query.apiKey.trim() ? req.query.apiKey.trim() : '') ||
    (typeof req.query.k === 'string' && req.query.k.trim() ? req.query.k.trim() : '');
  if (!relayUrl || !apiKey) {
    res.status(400).type('text/plain').send('Missing relayUrl or apiKey.');
    return;
  }

  const deepLink = buildConnectDeepLink(relayUrl, apiKey);
  const escapedDeepLink = escapeHtml(deepLink);
  res.type('html').send(`<!doctype html>
<html lang="zh-CN">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Open EasyCodex</title>
  <meta http-equiv="refresh" content="0; url=${escapedDeepLink}">
  <style>
    body { font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 28px; line-height: 1.5; }
    a { display: inline-block; padding: 12px 16px; background: #258965; color: white; text-decoration: none; font-weight: 700; }
    code { word-break: break-all; }
  </style>
</head>
<body>
  <h1>Open EasyCodex</h1>
  <p>如果没有自动打开 EasyCodex，请点下面的按钮。</p>
  <p><a href="${escapedDeepLink}">打开 EasyCodex</a></p>
  <p><code>${escapedDeepLink}</code></p>
  <script>window.location.href = ${JSON.stringify(deepLink)};</script>
</body>
</html>`);
}

app.get('/connect', handleConnectPage);
app.get('/c', handleConnectPage);

wss.on('connection', (ws, req) => {
  const remoteAddress = req.socket.remoteAddress || 'unknown';
  const session: ClientSession = {
    ws,
    authenticated: false,
    clientId: null,
    connectedAt: Date.now(),
    remoteAddress,
  };
  clients.set(ws, session);
  console.log(`Client connected from ${remoteAddress}`);

  const authTimeout = setTimeout(() => {
    if (!session.authenticated && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'error', error: 'Authentication required' }));
      ws.close(4001, 'Authentication required');
    }
  }, 10000);

  ws.on('message', async (raw) => {
    let msg: { action: string; params?: Record<string, unknown>; requestId?: string };
    try {
      msg = JSON.parse(raw.toString());
    } catch {
      ws.send(JSON.stringify({ type: 'error', error: 'Invalid JSON' }));
      return;
    }

    const { action, params = {}, requestId } = msg;

    const reply = (data: unknown) => {
      ws.send(JSON.stringify({ type: 'response', action, requestId, data }));
    };

    const replyError = (error: string) => {
      ws.send(JSON.stringify({ type: 'error', action, requestId, error }));
    };

    if (!session.authenticated) {
      if (action !== 'auth') {
        replyError('Unauthenticated: first message must be auth');
        ws.close(4001, 'Unauthenticated');
        return;
      }

      const key = typeof params.key === 'string' ? params.key : '';
      const requestedClientId = typeof params.clientId === 'string' ? params.clientId.trim() : '';
      if (!key || key !== config.apiKey) {
        replyError('Invalid API key');
        ws.close(4001, 'Invalid API key');
        return;
      }

      session.authenticated = true;
      session.clientId = requestedClientId || crypto.randomUUID();
      clearTimeout(authTimeout);
      console.log(`Authenticated client ${session.clientId} (${remoteAddress})`);
      emitClientState('authenticated');
      updateCodexThreadPoller();
      reply({ ok: true, clientId: session.clientId });
      return;
    }

    try {
      switch (action) {
        case 'cli_start': {
          const { windowId, cwd, model, reasoningEffort, sandboxMode, skipGitRepoCheck } = params as {
            windowId?: string;
            cwd?: string;
            model?: string;
            reasoningEffort?: string;
            sandboxMode?: string;
            skipGitRepoCheck?: boolean;
          };
          const safeCwd = resolveWorkspaceCwd(cwd || getPrimaryWorkspaceRoot());
          const running = typeof windowId === 'string' && windowId.trim()
            ? activeCliRuns.get(windowId.trim()) || null
            : null;
          reply({
            ok: true,
            windowId: windowId || null,
            cwd: safeCwd,
            model: typeof model === 'string' ? model.trim() : '',
            reasoningEffort: typeof reasoningEffort === 'string' ? reasoningEffort.trim() : '',
            sandboxMode: cleanCliSandboxMode(sandboxMode),
            skipGitRepoCheck: skipGitRepoCheck !== false,
            version: codexCliVersion(),
            running: running != null,
            runId: running?.id || null,
            command: running?.commandLine || null,
            events: running && typeof windowId === 'string' ? cliWindowRecentEvents(windowId, 160) : [],
          });
          break;
        }

        case 'cli_run': {
          const {
            windowId,
            cwd,
            prompt,
            model,
            reasoningEffort,
            sandboxMode,
            skipGitRepoCheck,
            mode,
            sessionId,
            reviewTarget,
            profile,
            images,
            addDirs,
            jsonOutput,
            ephemeral,
            ignoreRules,
          } = params as {
            windowId?: string;
            cwd?: string;
            prompt?: string;
            model?: string;
            reasoningEffort?: string;
            sandboxMode?: string;
            skipGitRepoCheck?: boolean;
            mode?: string;
            sessionId?: string;
            reviewTarget?: string;
            profile?: string;
            images?: unknown;
            addDirs?: unknown;
            jsonOutput?: boolean;
            ephemeral?: boolean;
            ignoreRules?: boolean;
          };
          const run = startCliRun({
            windowId: windowId || '',
            cwd: cwd || getPrimaryWorkspaceRoot(),
            prompt: prompt || '',
            model,
            reasoningEffort,
            sandboxMode,
            skipGitRepoCheck: skipGitRepoCheck !== false,
            mode,
            sessionId,
            reviewTarget,
            profile,
            images,
            addDirs,
            jsonOutput,
            ephemeral,
            ignoreRules,
          });
          reply({ ok: true, windowId: run.windowId, runId: run.id, cwd: run.cwd });
          break;
        }

        case 'cli_stop': {
          const { windowId } = params as { windowId?: string };
          const stopped = stopCliRun(windowId);
          reply({ ok: true, stopped });
          break;
        }

        case 'create_agent': {
          const {
            name,
            model,
            cwd,
            approvalPolicy,
            systemPrompt,
            agentId,
            serviceTier,
            reasoningEffort,
            codexThreadId,
            firstMessage,
            attachments,
            projectless,
          } = params as {
            name: string;
            model: string;
            cwd?: string;
            approvalPolicy?: string;
            systemPrompt?: string;
            agentId?: string;
            serviceTier?: string;
            reasoningEffort?: string;
            codexThreadId?: string;
            firstMessage?: string;
            attachments?: MessageAttachmentInput[];
            projectless?: boolean;
          };
          const isProjectless = projectless === true;
          const resolvedCwd = resolveWorkspaceCwd(isProjectless ? getPrimaryWorkspaceRoot() : cwd);
          if (!isProjectless) await maybeAutoPullRepo(resolvedCwd);
          const agent = await manager.createAgent(
            name || 'Agent',
            model || 'gpt-5.5',
            resolvedCwd,
            approvalPolicy || 'never',
            systemPrompt || '',
            agentId,
            {
              serviceTier,
              reasoningEffort,
              codexThreadId,
              projectless: isProjectless,
            },
          );
          if (typeof firstMessage === 'string' && firstMessage.trim()) {
            await manager.sendMessage(agent.id, firstMessage, Array.isArray(attachments) ? attachments : []);
          }
          reply(manager.getAgent(agent.id) || agent);
          broadcast(agent.id, 'agents/changed', { reason: 'created', agent, timestamp: Date.now() });
          broadcastCodexThreadsChanged('agent_created', { agentId: agent.id });
          break;
        }

        case 'list_agents': {
          reply(await manager.listVisibleAgents());
          break;
        }

        case 'replay_stream': {
          const { afterSeq, limit, sessionId } = params as { afterSeq?: number; limit?: number; sessionId?: string };
          flushStreamBatches();
          reply(replayStreamHistory(streamHistory, relaySessionId, { afterSeq, limit, sessionId }, STREAM_HISTORY_LIMIT));
          break;
        }

        case 'send_message': {
          const { agentId, text, attachments } = params as {
            agentId: string;
            text: string;
            attachments?: MessageAttachmentInput[];
          };
          await manager.sendMessage(agentId, text, Array.isArray(attachments) ? attachments : []);
          reply({ ok: true });
          broadcastCodexThreadsChanged('message_sent', { agentId });
          break;
        }

        case 'upload_attachments': {
          const { cwd, files } = params as {
            cwd?: string;
            files?: Array<{
              name?: string;
              mimeType?: string | null;
              size?: number | null;
              base64?: string;
            }>;
          };
          if (!Array.isArray(files) || files.length === 0) {
            throw new Error('files is required');
          }
          if (files.length > 12) {
            throw new Error('Too many attachments; send at most 12 files at once.');
          }
          const safeCwd = resolveWorkspaceCwd(cwd);
          const attachmentRoot = resolveWithinCwd(safeCwd, '.easycodex-attachments');
          await fsPromises.mkdir(attachmentRoot, { recursive: true });

          const uploaded: Array<{ name: string; path: string; size: number; mimeType: string | null }> = [];
          for (const file of files) {
            const encoded = typeof file?.base64 === 'string' ? file.base64 : '';
            if (!encoded) throw new Error('Attachment data is missing');
            const buffer = Buffer.from(encoded, 'base64');
            if (buffer.length > 12 * 1024 * 1024) {
              throw new Error(`${file.name || 'Attachment'} exceeds the 12 MB per-file limit.`);
            }
            const safeName = sanitizeAttachmentName(file.name || 'attachment');
            const stampedName = `${dateStampForAttachment()}-${crypto.randomBytes(3).toString('hex')}-${safeName}`;
            const relativePath = path.join('.easycodex-attachments', stampedName);
            const target = resolveWithinCwd(safeCwd, relativePath);
            await fsPromises.writeFile(target, buffer);
            uploaded.push({
              name: safeName,
              path: relativePath.split(path.sep).join('/'),
              size: buffer.length,
              mimeType: typeof file.mimeType === 'string' ? file.mimeType : null,
            });
          }
          reply({ cwd: safeCwd, files: uploaded });
          break;
        }

        case 'interrupt': {
          const { agentId } = params as { agentId: string };
          await manager.interruptAgent(agentId);
          reply({ ok: true });
          break;
        }

        case 'respond_agent_request': {
          const { agentId, requestId, approved, reason } = params as {
            agentId: string;
            requestId: string;
            approved?: boolean;
            reason?: string;
          };
          manager.respondAgentRequest(agentId, requestId, approved === true, reason || '');
          reply({ ok: true });
          break;
        }

        case 'respond_agent_user_input': {
          const { agentId, requestId, answers } = params as {
            agentId: string;
            requestId: string;
            answers?: Record<string, unknown>;
          };
          manager.respondAgentUserInput(agentId, requestId, answers || {});
          reply({ ok: true });
          break;
        }

        case 'stop_agent': {
          const { agentId } = params as { agentId: string };
          manager.stopAgent(agentId);
          reply({ ok: true });
          broadcast(agentId, 'agents/changed', { reason: 'stopped', agentId, timestamp: Date.now() });
          broadcastCodexThreadsChanged('agent_stopped', { agentId });
          break;
        }

        case 'archive_codex_thread': {
          const { threadId, agentId } = params as { threadId?: string; agentId?: string };
          if (!threadId?.trim()) throw new Error('threadId is required');
          await manager.archiveCodexThread(threadId.trim(), agentId);
          reply({ ok: true });
          if (agentId?.trim()) {
            broadcast(agentId.trim(), 'agents/changed', { reason: 'archived', agentId: agentId.trim(), timestamp: Date.now() });
          }
          broadcastCodexThreadsChanged('thread_archived', { threadId: threadId.trim(), agentId });
          break;
        }

        case 'update_agent_model': {
          const { agentId, model } = params as { agentId: string; model: string };
          manager.updateModel(agentId, model);
          reply({ ok: true });
          break;
        }

        case 'update_agent_config': {
          const {
            agentId,
            model,
            cwd,
            approvalPolicy,
            systemPrompt,
            serviceTier,
            reasoningEffort,
          } = params as {
            agentId: string;
            model?: string;
            cwd?: string;
            approvalPolicy?: string;
            systemPrompt?: string;
            serviceTier?: string;
            reasoningEffort?: string;
          };
          manager.updateConfig(agentId, {
            model,
            cwd: cwd ? resolveWorkspaceCwd(cwd) : undefined,
            approvalPolicy,
            systemPrompt,
            serviceTier,
            reasoningEffort,
          });
          reply({ ok: true });
          break;
        }

        case 'list_codex_threads': {
          const { limit, cursor, cwd, includeGlobal, all, activeOnly } = params as {
            limit?: number;
            cursor?: string;
            cwd?: string;
            includeGlobal?: boolean;
            all?: boolean;
            activeOnly?: boolean;
          };
          const requestedCwd = typeof cwd === 'string' && cwd.trim() ? resolveWorkspaceCwd(cwd) : undefined;
          const resolvedCwd = requestedCwd
            || (includeGlobal === false ? resolveWorkspaceCwd(getPrimaryWorkspaceRoot()) : undefined);
          const result = await manager.listCodexThreads({
            limit,
            cursor,
            cwd: resolvedCwd,
            all: all === true,
            activeOnly: activeOnly === true,
          });
          reply(result);
          break;
        }

        case 'read_codex_thread': {
          const { threadId } = params as { threadId: string };
          if (!threadId?.trim()) throw new Error('threadId is required');
          const result = await manager.readCodexThread(threadId.trim());
          reply(result);
          break;
        }

        case 'list_codex_models': {
          const { includeHidden } = params as { includeHidden?: boolean };
          const result = await manager.listCodexModels(includeHidden !== false);
          reply(result);
          break;
        }

        case 'runtime_capabilities': {
          reply(manager.getRuntimeCapabilities());
          break;
        }

        case 'check_update': {
          reply(await runUpdateCheck('client_request'));
          break;
        }

        case 'apply_update': {
          const result = await applyUpdate();
          reply(result);
          broadcast('system', 'relay/update_applied', {
            result,
            timestamp: Date.now(),
          });
          break;
        }

        case 'register_notification_token': {
          const { token, language } = params as { token: string; language?: string };
          if (!session.clientId) {
            replyError('Client is missing an id');
            break;
          }
          registerNotificationToken(session.clientId, token, language);
          reply({ ok: true });
          break;
        }

        case 'update_client_language': {
          const { language } = params as { language?: string };
          if (!session.clientId) {
            replyError('Client is missing an id');
            break;
          }
          updateClientLanguage(session.clientId, language);
          if (typeof language === 'string' && language.trim()) {
            lastClientLanguage = language.trim();
            emitClientState('language_changed');
          }
          reply({ ok: true, language });
          break;
        }

        case 'update_notification_prefs': {
          const { agentId, level } = params as { agentId: string; level: NotificationLevel };
          if (!agentId?.trim()) throw new Error('agentId is required');
          const normalizedLevel = (level || 'all').trim().toLowerCase();
          if (!['all', 'errors', 'muted'].includes(normalizedLevel)) {
            throw new Error('level must be one of: all, errors, muted');
          }
          updateNotificationPreference(agentId.trim(), normalizedLevel as NotificationLevel);
          reply({ ok: true, agentId: agentId.trim(), level: normalizedLevel });
          break;
        }

        case 'get_notification_prefs': {
          reply(getNotificationPreferences());
          break;
        }

        case 'list_notification_history': {
          const { limit } = params as { limit?: number };
          reply(getNotificationHistory(typeof limit === 'number' ? limit : 100));
          break;
        }

        case 'list_files': {
          const { cwd, path: relativePath } = params as { cwd: string; path?: string };
          const resolved = resolveWithinWorkspace(cwd, relativePath);
          const target = resolved.target;
          const entries = await fsPromises.readdir(target, { withFileTypes: true });
          const serialized = entries
            .map((entry) => ({
              name: entry.name,
              path: path.join(relativePath || '.', entry.name),
              type: entry.isDirectory() ? 'directory' : 'file',
            }))
            .sort((a, b) => {
              if (a.type !== b.type) return a.type === 'directory' ? -1 : 1;
              return a.name.localeCompare(b.name);
            });
          reply({
            cwd: resolved.cwd,
            path: relativePath || '.',
            entries: serialized,
          });
          break;
        }

        case 'list_directories': {
          const { cwd, path: relativePath } = params as { cwd: string; path?: string };
          const resolved = resolveWithinWorkspace(cwd, relativePath);
          const target = resolved.target;
          const entries = await fsPromises.readdir(target, { withFileTypes: true });
          const directories = entries
            .filter((entry) => entry.isDirectory())
            .map((entry) => ({
              name: entry.name,
              path: path.join(relativePath || '.', entry.name),
              type: 'directory',
            }))
            .sort((a, b) => a.name.localeCompare(b.name));
          reply({
            cwd: resolved.cwd,
            path: relativePath || '.',
            entries: directories,
          });
          break;
        }

        case 'browse_directories': {
          const { path: targetPath } = params as { path?: string };
          reply(await browseDirectories(targetPath));
          break;
        }

        case 'trust_workspace_root': {
          const { path: targetPath } = params as { path?: string };
          if (!targetPath?.trim()) throw new Error('path is required');
          const trustedPath = trustCustomWorkspaceRoot(targetPath);
          reply({
            ok: true,
            path: trustedPath,
            allowedWorkspaceRoots: getAllowedWorkspaceRoots(),
          });
          break;
        }

        case 'read_file': {
          const { cwd, path: relativePath } = params as { cwd: string; path: string };
          if (!relativePath) throw new Error('path is required');
          const resolved = resolveWithinWorkspace(cwd, relativePath);
          const target = resolved.target;
          const stat = await fsPromises.stat(target);
          if (!stat.isFile()) throw new Error('Target is not a file');
          const content = await fsPromises.readFile(target, 'utf8');
          reply({
            cwd: resolved.cwd,
            path: relativePath,
            content,
          });
          break;
        }

        case 'git_status': {
          const { cwd } = params as { cwd: string };
          const git = gitForCwd(cwd || getPrimaryWorkspaceRoot());
          const status = await git.status();
          reply({
            branch: status.current,
            isClean: status.isClean(),
            ahead: status.ahead,
            behind: status.behind,
            modified: status.modified,
            created: status.created,
            deleted: status.deleted,
            renamed: status.renamed,
            notAdded: status.not_added,
            conflicted: status.conflicted,
          });
          break;
        }

        case 'git_log': {
          const { cwd, limit } = params as { cwd: string; limit?: number };
          const git = gitForCwd(cwd || getPrimaryWorkspaceRoot());
          const history = await git.log({ maxCount: Math.min(Math.max(limit || 20, 1), 100) });
          reply(history.all);
          break;
        }

        case 'git_diff': {
          const { cwd, file } = params as { cwd: string; file?: string };
          const safeCwd = resolveWorkspaceCwd(cwd || getPrimaryWorkspaceRoot());
          const git = gitForCwd(safeCwd);
          const safeFile = file ? path.relative(safeCwd, resolveWithinCwd(safeCwd, file)) : '';
          const diff = safeFile ? await git.diff([safeFile]) : await git.diff();
          reply({ diff });
          break;
        }

        case 'git_commit': {
          const { cwd, message, files } = params as { cwd: string; message: string; files?: unknown };
          const safeCwd = resolveWorkspaceCwd(cwd || getPrimaryWorkspaceRoot());
          const git = gitForCwd(safeCwd);
          const safeFiles = normalizeGitCommitFiles(safeCwd, files);
          await git.add(safeFiles);
          const result = await git.commit(message || 'chore: update via EasyCodex mobile');
          reply(result);
          break;
        }

        case 'git_branches': {
          const { cwd } = params as { cwd: string };
          const git = gitForCwd(cwd || getPrimaryWorkspaceRoot());
          const branches = await git.branchLocal();
          reply({
            current: branches.current,
            all: branches.all,
          });
          break;
        }

        case 'git_worktrees': {
          const { cwd } = params as { cwd?: string };
          const safeCwd = resolveWorkspaceCwd(cwd || getPrimaryWorkspaceRoot());
          const output = await simpleGit({ baseDir: safeCwd }).raw(['worktree', 'list', '--porcelain']);
          const worktrees = parseGitWorktreeEntries(output).map((worktree) => ({
            ...worktree,
            current: normalizePathKey(worktree.path) === normalizePathKey(safeCwd),
          }));
          reply(worktrees);
          break;
        }

        case 'git_checkout': {
          const { cwd, branch } = params as { cwd: string; branch: string };
          if (!branch) throw new Error('branch is required');
          const git = gitForCwd(cwd || getPrimaryWorkspaceRoot());
          await git.checkout(branch);
          reply({ ok: true });
          break;
        }

        case 'clone_repo': {
          const { url } = params as { url: string };
          if (!url?.trim()) throw new Error('url is required');
          const reposRoot = getReposRoot();
          const baseName = repoNameFromUrl(url.trim());
          let targetPath = path.join(reposRoot, baseName);
          let suffix = 1;
          while (fs.existsSync(targetPath)) {
            targetPath = path.join(reposRoot, `${baseName}-${suffix}`);
            suffix += 1;
          }
          await simpleGit().clone(url.trim(), targetPath);
          reply({ ok: true, path: targetPath, name: path.basename(targetPath) });
          break;
        }

        case 'list_repos': {
          const reposRoot = getReposRoot();
          const entries = await fsPromises.readdir(reposRoot, { withFileTypes: true });
          const repos = await Promise.all(
            entries
              .filter((entry) => entry.isDirectory())
              .map(async (entry) => {
                const repoPath = path.join(reposRoot, entry.name);
                if (!fs.existsSync(path.join(repoPath, '.git'))) return null;
                let remote = '';
                try {
                  const remoteOutput = await simpleGit({ baseDir: repoPath }).remote(['get-url', 'origin']);
                  remote = typeof remoteOutput === 'string' ? remoteOutput.trim() : '';
                } catch {}
                return {
                  name: entry.name,
                  path: repoPath,
                  remote,
                };
              }),
          );
          reply((repos.filter(Boolean) as Array<{ name: string; path: string; remote: string }>).sort((a, b) => a.name.localeCompare(b.name)));
          break;
        }

        case 'pull_repo': {
          const { path: repoPath } = params as { path: string };
          if (!repoPath?.trim()) throw new Error('path is required');
          const reposRoot = getReposRoot();
          const safePath = resolveWithinBase(reposRoot, repoPath.trim());
          const git = simpleGit({ baseDir: safePath });
          const result = await git.pull();
          reply({ ok: true, summary: result.summary });
          break;
        }

        case 'get_agent': {
          const { agentId } = params as { agentId: string };
          const agent = manager.getAgent(agentId);
          if (agent) reply(agent);
          else replyError('Agent not found');
          break;
        }

        default:
          replyError(`Unknown action: ${action}`);
      }
    } catch (err) {
      replyError(err instanceof Error ? err.message : String(err));
    }
  });

  ws.on('close', (code, reason) => {
    clearTimeout(authTimeout);
    clients.delete(ws);
    const reasonText = reason.length > 0 ? ` reason="${reason.toString('utf8')}"` : '';
    console.log(`Client disconnected (${session.clientId || 'unauthenticated'}) code=${code}${reasonText} remote=${remoteAddress}`);
    if (session.authenticated) {
      emitClientState('disconnected');
      updateCodexThreadPoller();
    }
  });
});

async function startRelayServer(): Promise<void> {
  await detectPortWarnings();
  await new Promise<void>((resolve, reject) => {
    const onError = (err: Error) => reject(err);
    server.once('error', onError);
    server.listen(PORT, '0.0.0.0', () => {
      server.off('error', onError);
      const ip = getLocalIP();
      const networkUrl = `ws://${ip}:${PORT}`;
      const qrPayload = buildConnectHttpUrl(networkUrl, config.apiKey);
      const deepLink = buildConnectDeepLink(networkUrl, config.apiKey);
      console.log('\n  Codex Agent Relay running');
      console.log(`  Local:   ws://localhost:${PORT}`);
      console.log(`  Network: ${networkUrl}`);
      console.log(`  Config:  ${CONFIG_PATH}`);
      console.log(`  Workspace: ${getPrimaryWorkspaceRoot()}`);
      console.log(`  Repos:     ${getReposRoot()}`);
      console.log(`  API key: ${maskSecret(config.apiKey)}\n`);
      for (const warning of relayWarnings) {
        console.warn(`  Warning [${warning.code}]: ${warning.message}`);
        if (warning.recommendation) console.warn(`  Recommendation: ${warning.recommendation}`);
      }
      if (process.env.EASYCODEX_NO_TERMINAL_QR !== '1') {
        console.log('  Scan this QR with your phone camera to open EasyCodex and import the WebSocket URL and API key:');
        qrcode.generate(qrPayload, { small: true });
      }
      if (shouldLogConnectSecrets()) {
        console.log(`\n  QR payload: ${qrPayload}\n`);
        console.log(`  Deep link: ${deepLink}\n`);
      } else {
        console.log('\n  QR payload and deep link are hidden because they contain the relay API key.');
        console.log('  Set EASYCODEX_LOG_CONNECT_SECRETS=1 only for temporary troubleshooting.\n');
      }
      emitDesktopRelayEvent('ready', {
        status: 'ok',
        sessionId: relaySessionId,
        workspaceRoot: getPrimaryWorkspaceRoot(),
        reposRoot: getReposRoot(),
        connectedClients: getConnectedAuthenticatedCount(),
        notificationClients: getRegisteredClientCount(),
        notificationTokens: getRegisteredTokenCount(),
        lastClientLanguage,
        warnings: relayWarnings,
      });
      startCodexStateWatcher();
      void runUpdateCheck('startup').catch((err) => {
        console.warn('[update] Startup update check failed:', err);
      });
      resolve();
    });
  });
}

if (require.main === module) {
  void startRelayServer().catch((err) => {
    console.error('[relay] Failed to start:', err);
    process.exitCode = 1;
  });
}
