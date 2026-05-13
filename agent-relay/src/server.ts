import express from 'express';
import http from 'http';
import { WebSocketServer, WebSocket } from 'ws';
import os from 'os';
import fs from 'fs';
import path from 'path';
import crypto from 'crypto';
import { execFileSync } from 'child_process';
import { promises as fsPromises } from 'fs';
import simpleGit from 'simple-git';
import qrcode from 'qrcode-terminal';
import { SessionOrchestrator } from './session-orchestrator';
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

interface StreamHistoryEntry {
  type: 'stream';
  sessionId: string;
  seq: number;
  timestamp: number;
  agentId: string;
  event: string;
  data: unknown;
}

function generateApiKey(): string {
  return crypto.randomBytes(32).toString('hex');
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
  console.log(`[auth] API key: ${apiKey}\n`);
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

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
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
    ...discoverRelayGitWorktrees(),
  ]);
}

function resolveWorkspaceCwd(cwd?: string): string {
  const requested = resolveSafePath(cwd || getPrimaryWorkspaceRoot());
  const allowedRoots = getAllowedWorkspaceRoots();
  if (allowedRoots.some((root) => isWithinBase(root, requested))) {
    const stat = fs.statSync(requested);
    if (!stat.isDirectory()) throw new Error(`Path is not a directory: ${requested}`);
    return requested;
  }
  throw new Error('Path is outside the allowed EasyCodex workspace roots.');
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
  const requested = targetPath?.trim() ? resolveWorkspaceCwd(targetPath) : allowedRoots[0];
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

const config = loadOrCreateConfig();
const relaySessionId = crypto.randomUUID();

const app = express();
const server = http.createServer(app);
const wss = new WebSocketServer({ server });
const clients = new Map<WebSocket, ClientSession>();
const STREAM_HISTORY_LIMIT = Number(process.env.STREAM_HISTORY_LIMIT || 5000);
const CODEX_WATCH_DEBOUNCE_MS = Number(process.env.CODEX_WATCH_DEBOUNCE_MS || 150);
let nextStreamSeq = 1;
const streamHistory: StreamHistoryEntry[] = [];
let codexWatchTimer: ReturnType<typeof setTimeout> | null = null;
const codexWatchers: fs.FSWatcher[] = [];

function getConnectedAuthenticatedCount(): number {
  let count = 0;
  for (const session of clients.values()) {
    if (session.authenticated) count += 1;
  }
  return count;
}

function rememberStreamEvent(agentId: string, event: string, data: unknown): StreamHistoryEntry {
  const entry: StreamHistoryEntry = {
    type: 'stream',
    sessionId: relaySessionId,
    seq: nextStreamSeq++,
    timestamp: Date.now(),
    agentId,
    event,
    data,
  };
  streamHistory.push(entry);
  if (streamHistory.length > STREAM_HISTORY_LIMIT) {
    streamHistory.splice(0, streamHistory.length - STREAM_HISTORY_LIMIT);
  }
  return entry;
}

function broadcast(agentId: string, event: string, data: unknown) {
  const entry = rememberStreamEvent(agentId, event, data);
  const message = JSON.stringify(entry);
  for (const session of clients.values()) {
    if (!session.authenticated) continue;
    if (session.ws.readyState === WebSocket.OPEN) {
      session.ws.send(message);
    }
  }
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
      reply({ ok: true, clientId: session.clientId });
      return;
    }

    try {
      switch (action) {
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
          } = params as {
            name: string;
            model: string;
            cwd: string;
            approvalPolicy?: string;
            systemPrompt?: string;
            agentId?: string;
            serviceTier?: string;
            reasoningEffort?: string;
            codexThreadId?: string;
          };
          const resolvedCwd = resolveWorkspaceCwd(cwd);
          await maybeAutoPullRepo(resolvedCwd);
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
            },
          );
          reply(agent);
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
          const safeAfterSeq = typeof afterSeq === 'number' && Number.isFinite(afterSeq) ? afterSeq : 0;
          const safeLimit = Math.min(Math.max(typeof limit === 'number' ? limit : 1000, 1), STREAM_HISTORY_LIMIT);
          const sameSession = sessionId === relaySessionId;
          const events = streamHistory
            .filter((entry) => !sameSession || entry.seq > safeAfterSeq)
            .slice(-safeLimit);
          reply({
            sessionId: relaySessionId,
            events,
            latestSeq: streamHistory[streamHistory.length - 1]?.seq || safeAfterSeq,
            truncated: sameSession && streamHistory.length > events.length && events[0]?.seq > safeAfterSeq + 1,
          });
          break;
        }

        case 'send_message': {
          const { agentId, text } = params as { agentId: string; text: string };
          await manager.sendMessage(agentId, text);
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

        case 'stop_agent': {
          const { agentId } = params as { agentId: string };
          manager.stopAgent(agentId);
          reply({ ok: true });
          broadcast(agentId, 'agents/changed', { reason: 'stopped', agentId, timestamp: Date.now() });
          broadcastCodexThreadsChanged('agent_stopped', { agentId });
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
          const { limit, cursor, cwd, includeGlobal, all } = params as {
            limit?: number;
            cursor?: string;
            cwd?: string;
            includeGlobal?: boolean;
            all?: boolean;
          };
          const requestedCwd = typeof cwd === 'string' && cwd.trim() ? resolveWorkspaceCwd(cwd) : undefined;
          const resolvedCwd = requestedCwd
            || (includeGlobal === false ? resolveWorkspaceCwd(getPrimaryWorkspaceRoot()) : undefined);
          const result = await manager.listCodexThreads({ limit, cursor, cwd: resolvedCwd, all: all === true });
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
  });
});

server.listen(PORT, '0.0.0.0', () => {
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
  console.log(`  API key: ${config.apiKey}\n`);
  if (process.env.EASYCODEX_NO_TERMINAL_QR !== '1') {
    console.log('  Scan this QR with your phone camera to open EasyCodex and import the WebSocket URL and API key:');
    qrcode.generate(qrPayload, { small: true });
  }
  console.log(`\n  QR payload: ${qrPayload}\n`);
  console.log(`  Deep link: ${deepLink}\n`);
  startCodexStateWatcher();
});
