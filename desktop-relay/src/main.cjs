const { app, BrowserWindow, Menu, Tray, clipboard, dialog, ipcMain, nativeImage, shell } = require('electron');
const crypto = require('node:crypto');
const fs = require('node:fs');
const https = require('node:https');
const http = require('node:http');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const { spawn, spawnSync } = require('node:child_process');
const QRCode = require('qrcode');
const {
  selectReleaseForChannel,
  selectUpdateAsset,
  verifyDownloadedAsset,
} = require('./update-helpers.cjs');

app.disableHardwareAcceleration();

const isDev = !app.isPackaged;
const isSmokeTest = process.argv.includes('--smoke-test');
const rendererDistIndex = path.join(__dirname, 'renderer', 'dist', 'index.html');
const legacyRendererIndex = path.join(__dirname, 'renderer', 'index.html');
const sourceRoot = isDev ? path.resolve(__dirname, '..', '..') : process.resourcesPath;
const sourceRelayDir = isDev ? path.join(sourceRoot, 'agent-relay') : path.join(process.resourcesPath, 'agent-relay');
const configDir = path.join(os.homedir(), '.easycodex');
const configPath = path.join(configDir, 'config.json');
const desktopConfigPath = path.join(configDir, 'desktop-relay.json');
const runtimeRelayDir = path.join(configDir, 'desktop-relay-runtime');
const reposDir = path.join(configDir, 'repos');
const codexGlobalStatePath = path.join(os.homedir(), '.codex', '.codex-global-state.json');
const supportedLanguages = ['system', 'zh', 'en'];
const supportedUpdateChannels = ['stable', 'beta'];
const UPDATE_REQUEST_TIMEOUT_MS = 15000;
const UPDATE_DOWNLOAD_TIMEOUT_MS = 30000;
const UPDATE_NETWORK_RETRIES = 3;
const iconPath = app.isPackaged ? path.join(process.resourcesPath, 'icon.png') : path.join(__dirname, 'assets', 'icon.png');
const windowIconPath = process.platform === 'win32'
  ? (app.isPackaged ? path.join(process.resourcesPath, 'icon.ico') : path.join(__dirname, '..', 'build', 'icon.ico'))
  : iconPath;

const gotSingleInstanceLock = app.requestSingleInstanceLock();
if (!gotSingleInstanceLock) {
  app.exit(0);
}
app.setAppUserModelId('dev.easycodex.desktop-relay');

let mainWindow = null;
let relayProcess = null;
let installProcess = null;
let healthTimer = null;
let tray = null;
let lastHealth = { online: false };
let relayRunning = false;
let allowQuit = false;
let enteringLightMode = false;
let lightModeStartPromise = null;
let relayEventData = null;
let relayOutputBuffer = '';
let relayLogClientIds = new Set();
let codexDetectionCache = null;
let updateState = { checking: false, applying: false, info: null, error: '' };

function commandForShell(command) {
  if (process.platform !== 'win32') return command;
  if (/^[A-Za-z0-9_./:-]+$/.test(command)) return command;
  return `"${command.replace(/"/g, '""')}"`;
}

function cleanExecutablePath(value) {
  return String(value || '').trim().replace(/^"+|"+$/g, '');
}

function pathEntries() {
  return String(process.env.PATH || process.env.Path || '')
    .split(path.delimiter)
    .map((entry) => entry.trim())
    .filter(Boolean);
}

function envPathValue(env) {
  return env.PATH || env.Path || process.env.PATH || process.env.Path || '';
}

function withExtraPath(env, entries) {
  const existing = String(envPathValue(env));
  const seen = new Set(existing.split(path.delimiter).filter(Boolean));
  const extras = entries.filter((entry) => entry && !seen.has(entry));
  return {
    ...env,
    PATH: [...extras, existing].filter(Boolean).join(path.delimiter),
  };
}

function firstExistingFile(candidates) {
  for (const candidate of candidates) {
    const filePath = cleanExecutablePath(candidate);
    if (filePath && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) return filePath;
  }
  return null;
}

function codexCommandCandidates(configuredPath, env) {
  const configured = cleanExecutablePath(configuredPath);
  if (configured) return [configured];
  const envCandidate = cleanExecutablePath(env.CODEX_EXECUTABLE || env.EASY_CODEX_CODEX_PATH);
  const names = process.platform === 'win32'
    ? ['codex.exe', 'codex.cmd', 'codex.bat', 'codex']
    : ['codex'];
  const home = os.homedir();
  const commonDirs = process.platform === 'win32'
    ? [
      path.join(process.env.APPDATA || path.join(home, 'AppData', 'Roaming'), 'npm'),
      path.join(process.env.LOCALAPPDATA || path.join(home, 'AppData', 'Local'), 'Programs'),
    ]
    : [
      '/opt/homebrew/bin',
      '/usr/local/bin',
      '/usr/bin',
      path.join(home, '.npm-global', 'bin'),
      path.join(home, '.local', 'bin'),
    ];
  const pathCandidates = String(envPathValue(env))
    .split(path.delimiter)
    .map((entry) => entry.trim())
    .filter(Boolean)
    .flatMap((entry) => names.map((name) => path.join(entry, name)));
  const commonCandidates = commonDirs.flatMap((entry) => names.map((name) => path.join(entry, name)));
  return [...(envCandidate ? [envCandidate] : []), ...pathCandidates, ...commonCandidates, 'codex'];
}

function codexInvocation(command, args) {
  const cleaned = cleanExecutablePath(command);
  if (process.platform === 'win32' && /\.(cmd|bat)$/i.test(cleaned)) {
    const cmd = cleanExecutablePath(process.env.ComSpec) || windowsSystemCommand('cmd.exe') || 'cmd.exe';
    return {
      command: cmd,
      args: ['/d', '/s', '/c', ['call', cleaned, ...args].map(commandForShell).join(' ')],
      options: { windowsVerbatimArguments: true },
    };
  }
  return { command: cleaned, args, options: {} };
}

function runCodexCheck(command, args, env) {
  const invocation = codexInvocation(command, args);
  return spawnSync(invocation.command, invocation.args, {
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
    encoding: 'utf8',
    timeout: 10000,
    windowsHide: true,
    ...invocation.options,
    env,
  });
}

function verifyCodexExecutable(command, env) {
  const result = runCodexCheck(command, ['--version'], env);
  if (result.error) {
    return { ok: false, error: result.error.message };
  }
  if (result.status !== 0) {
    const output = `${result.stderr || ''}${result.stdout || ''}`.trim();
    return { ok: false, error: output || `codex --version exited with code ${result.status}` };
  }
  const appServerResult = runCodexCheck(command, ['app-server', '--help'], env);
  if (appServerResult.error) {
    return { ok: false, error: appServerResult.error.message };
  }
  if (appServerResult.status !== 0) {
    const output = `${appServerResult.stderr || ''}${appServerResult.stdout || ''}`.trim();
    return { ok: false, error: output || `codex app-server --help exited with code ${appServerResult.status}` };
  }
  return { ok: true, version: String(result.stdout || result.stderr || '').trim() };
}

function shellPathProbes() {
  if (process.platform === 'win32') return [];
  const probes = [];
  const shell = cleanExecutablePath(process.env.SHELL);
  if (shell) probes.push({ shell, args: ['-lc'] });
  for (const candidate of ['/bin/zsh', '/bin/bash', '/bin/sh']) {
    if (fs.existsSync(candidate) && !probes.some((probe) => probe.shell === candidate)) {
      probes.push({ shell: candidate, args: ['-lc'] });
    }
  }
  return probes;
}

function discoverShellCodexEnvironments(baseEnv) {
  const script = 'printf "__EASYC0DEX_PATH__%s\\n" "$PATH"; command -v codex 2>/dev/null | head -n 1';
  const discovered = [];
  for (const probe of shellPathProbes()) {
    const result = spawnSync(probe.shell, [...probe.args, script], {
      stdio: ['ignore', 'pipe', 'pipe'],
      encoding: 'utf8',
      timeout: 10000,
      env: baseEnv,
    });
    if (result.error || result.status !== 0) continue;
    const lines = String(result.stdout || '').split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
    const pathLine = lines.find((line) => line.startsWith('__EASYC0DEX_PATH__'));
    const command = lines.find((line) => !line.startsWith('__EASYC0DEX_PATH__'));
    const shellPath = pathLine ? pathLine.slice('__EASYC0DEX_PATH__'.length) : '';
    if (!command && !shellPath) continue;
    discovered.push({
      command,
      env: shellPath ? { ...baseEnv, PATH: shellPath } : baseEnv,
      source: `${path.basename(probe.shell)} login shell`,
    });
  }
  return discovered;
}

function discoverWindowsCodexCommands(env) {
  if (process.platform !== 'win32') return [];
  const where = windowsSystemCommand('where.exe');
  if (!where) return [];
  const result = spawnSync(where, ['codex'], {
    stdio: ['ignore', 'pipe', 'ignore'],
    encoding: 'utf8',
    timeout: 10000,
    windowsHide: true,
    env,
  });
  if (result.error || result.status !== 0) return [];
  return String(result.stdout || '')
    .split(/\r?\n/)
    .map(cleanExecutablePath)
    .filter(Boolean)
    .map((command) => ({ command, env, source: 'where.exe' }));
}

function detectCodex(configuredPath, options = {}) {
  const key = cleanExecutablePath(configuredPath);
  const now = Date.now();
  if (
    !options.force &&
    codexDetectionCache &&
    codexDetectionCache.key === key &&
    now - codexDetectionCache.checkedAt < 15000
  ) {
    return codexDetectionCache.result;
  }

  let lastError = '';
  const commonPathDirs = process.platform === 'win32'
    ? [
      path.join(process.env.APPDATA || path.join(os.homedir(), 'AppData', 'Roaming'), 'npm'),
      path.join(process.env.ProgramFiles || 'C:\\Program Files', 'nodejs'),
    ]
    : ['/opt/homebrew/bin', '/usr/local/bin', '/usr/bin', path.join(os.homedir(), '.local', 'bin')];
  const baseEnv = withExtraPath(process.env, commonPathDirs);
  const shellDiscoveries = discoverShellCodexEnvironments(baseEnv);
  const commandChecks = key
    ? [
      { command: key, env: baseEnv, source: 'configured' },
      ...shellDiscoveries.map((discovery) => ({ command: key, env: discovery.env, source: `configured with ${discovery.source}` })),
    ]
    : [
      ...shellDiscoveries,
      ...discoverWindowsCodexCommands(baseEnv),
      ...codexCommandCandidates('', baseEnv).map((command) => ({ command, env: baseEnv, source: 'candidate' })),
    ];
  const seen = new Set();

  for (const candidate of commandChecks) {
    const command = cleanExecutablePath(candidate.command);
    if (!command) continue;
    const dedupeKey = `${command}\0${envPathValue(candidate.env)}`;
    if (seen.has(dedupeKey)) continue;
    seen.add(dedupeKey);
    const hasPathSeparator = command.includes('/') || command.includes('\\');
    if (hasPathSeparator && !fs.existsSync(command)) {
      lastError = `Codex executable not found: ${command}`;
      continue;
    }
    if (hasPathSeparator && !fs.statSync(command).isFile()) {
      lastError = `Codex path is not a file: ${command}`;
      continue;
    }
    const check = verifyCodexExecutable(command, candidate.env);
    if (check.ok) {
      const result = {
        installed: true,
        path: command,
        version: check.version,
        source: candidate.source || (key ? 'configured' : (hasPathSeparator ? 'path' : 'command')),
        env: { PATH: envPathValue(candidate.env) },
        error: null,
      };
      codexDetectionCache = { key, checkedAt: now, result };
      return result;
    }
    lastError = check.error;
  }

  const result = {
    installed: false,
    path: key,
    version: '',
    source: key ? 'configured' : 'path',
    error: lastError || 'Codex CLI was not found. Choose the Codex executable path.',
  };
  codexDetectionCache = { key, checkedAt: now, result };
  return result;
}

function windowsSystemCommand(name) {
  const systemRoot = process.env.SystemRoot || 'C:\\Windows';
  return firstExistingFile([
    path.join(systemRoot, 'System32', name),
    path.join(systemRoot, 'Sysnative', name),
    name,
  ]);
}

function npmCommandCandidates() {
  if (process.platform !== 'win32') return ['npm'];
  const pathCandidates = pathEntries().flatMap((entry) => [
    path.join(entry, 'npm.cmd'),
    path.join(entry, 'npm.exe'),
    path.join(entry, 'npm'),
  ]);
  return [
    path.join(process.env.ProgramFiles || 'C:\\Program Files', 'nodejs', 'npm.cmd'),
    path.join(process.env['ProgramFiles(x86)'] || 'C:\\Program Files (x86)', 'nodejs', 'npm.cmd'),
    ...pathCandidates,
    'npm.cmd',
  ];
}

function npmCommandArgs(args) {
  if (process.platform !== 'win32') return { command: 'npm', args };
  const cmd = cleanExecutablePath(process.env.ComSpec) || windowsSystemCommand('cmd.exe') || 'cmd.exe';
  const npm = firstExistingFile(npmCommandCandidates()) || 'npm.cmd';
  return {
    command: cmd,
    args: ['/d', '/s', '/c', ['call', npm, ...args].map(commandForShell).join(' ')],
  };
}

function nodeScriptCommand(scriptPath) {
  const args = [scriptPath];
  const env = {};
  if (process.versions.electron) {
    return {
      command: process.execPath,
      args,
      env: { ELECTRON_RUN_AS_NODE: '1' },
    };
  }
  return { command: process.execPath, args, env };
}

function ensureConfigDir() {
  fs.mkdirSync(configDir, { recursive: true });
}

function readJson(filePath) {
  try {
    if (!fs.existsSync(filePath)) return {};
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch {
    return {};
  }
}

function writeJson(filePath, value) {
  ensureConfigDir();
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function generateApiKey() {
  return crypto.randomBytes(32).toString('hex');
}

function loadApiKey() {
  const config = readJson(configPath);
  if (typeof config.apiKey === 'string' && config.apiKey.trim()) return config.apiKey.trim();
  const apiKey = generateApiKey();
  writeJson(configPath, { apiKey });
  return apiKey;
}

function uniqueResolvedPaths(values) {
  const seen = new Set();
  const result = [];
  for (const value of values) {
    if (typeof value !== 'string' || !value.trim()) continue;
    const resolved = path.resolve(value);
    const key = pathKey(resolved);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(resolved);
  }
  return result;
}

function stringArray(value) {
  return Array.isArray(value) ? value.filter((item) => typeof item === 'string' && item.trim()) : [];
}

function readCodexDesktopState() {
  try {
    if (!fs.existsSync(codexGlobalStatePath)) return null;
    const parsed = JSON.parse(fs.readFileSync(codexGlobalStatePath, 'utf8'));
    return parsed && typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function codexDesktopWorkspaceCandidates() {
  const state = readCodexDesktopState();
  const atomState = state?.['electron-persisted-atom-state'];
  const atom = atomState && typeof atomState === 'object' && !Array.isArray(atomState) ? atomState : {};
  const projectOrder = stringArray(state?.['project-order']).concat(stringArray(atom?.['project-order']));
  const activeRoots = stringArray(state?.['active-workspace-roots']).concat(stringArray(atom?.['active-workspace-roots']));
  const savedRoots = stringArray(state?.['electron-saved-workspace-roots']).concat(stringArray(atom?.['electron-saved-workspace-roots']));
  return uniqueResolvedPaths(projectOrder.length > 0 ? projectOrder : [...activeRoots, ...savedRoots]);
}

function repoWorkspaceCandidates() {
  try {
    if (!fs.existsSync(reposDir)) return [];
    return fs.readdirSync(reposDir, { withFileTypes: true })
      .filter((entry) => entry.isDirectory())
      .map((entry) => path.join(reposDir, entry.name));
  } catch {
    return [];
  }
}

function fallbackWorkspace() {
  const workspace = path.join(reposDir, 'default-workspace');
  fs.mkdirSync(workspace, { recursive: true });
  return workspace;
}

function defaultWorkspace() {
  const candidates = uniqueResolvedPaths([
    process.env.EASYCODEX_WORKSPACE,
    ...(isDev ? [sourceRoot] : []),
    ...codexDesktopWorkspaceCandidates(),
    ...repoWorkspaceCandidates(),
  ]);
  return candidates.map(usableWorkspace).find(Boolean) || fallbackWorkspace();
}

function usableWorkspace(value) {
  if (typeof value !== 'string' || !value.trim()) return '';
  try {
    const workspace = path.resolve(value);
    if (!fs.existsSync(workspace) || !fs.statSync(workspace).isDirectory()) return '';
    const realWorkspace = fs.realpathSync(workspace);
    if (isDisallowedWorkspaceRoot(workspace) || isDisallowedWorkspaceRoot(realWorkspace)) return '';
    return workspace;
  } catch {
    return '';
  }
}

function loadDesktopConfig() {
  const config = readJson(desktopConfigPath);
  return {
    port: Number.isInteger(config.port) ? config.port : 3001,
    workspace: usableWorkspace(config.workspace) || defaultWorkspace(),
    codexPath: typeof config.codexPath === 'string' && config.codexPath.trim() ? config.codexPath.trim() : '',
    languageMode: config.languageMode === 'manual' ? 'manual' : 'follow-phone',
    language: supportedLanguages.includes(config.language) ? config.language : 'system',
    updateChannel: supportedUpdateChannels.includes(config.updateChannel) ? config.updateChannel : 'stable',
    lightMode: config.lightMode === true,
    guideSeen: config.guideSeen === true,
  };
}

function saveDesktopConfig(partial) {
  const config = readJson(desktopConfigPath);
  writeJson(desktopConfigPath, { ...config, ...partial });
}

function updateRepository() {
  return String(process.env.EASYCODEX_UPDATE_REPO || 'Ryan-Laws/easycodex').trim();
}

function normalizeVersion(value) {
  return String(value || '').trim().replace(/^v/i, '');
}

function splitVersion(value) {
  const [main, prerelease = ''] = normalizeVersion(value).split('-', 2);
  return {
    numbers: main.split('.').map((part) => Number.parseInt(part, 10)).map((part) => (Number.isFinite(part) ? part : 0)),
    prerelease: prerelease.split(/[.+]/).filter(Boolean),
  };
}

function compareVersions(a, b) {
  const left = splitVersion(a);
  const right = splitVersion(b);
  const length = Math.max(left.numbers.length, right.numbers.length, 3);
  for (let i = 0; i < length; i += 1) {
    const diff = (left.numbers[i] || 0) - (right.numbers[i] || 0);
    if (diff !== 0) return diff;
  }
  if (left.prerelease.length === 0 && right.prerelease.length > 0) return 1;
  if (left.prerelease.length > 0 && right.prerelease.length === 0) return -1;
  const prereleaseLength = Math.max(left.prerelease.length, right.prerelease.length);
  for (let i = 0; i < prereleaseLength; i += 1) {
    const leftPart = left.prerelease[i];
    const rightPart = right.prerelease[i];
    if (leftPart === rightPart) continue;
    if (leftPart == null) return -1;
    if (rightPart == null) return 1;
    const leftNumber = Number.parseInt(leftPart, 10);
    const rightNumber = Number.parseInt(rightPart, 10);
    const leftNumeric = String(leftNumber) === leftPart;
    const rightNumeric = String(rightNumber) === rightPart;
    if (leftNumeric && rightNumeric) return leftNumber - rightNumber;
    if (leftNumeric) return -1;
    if (rightNumeric) return 1;
    const diff = leftPart.localeCompare(rightPart);
    if (diff !== 0) return diff;
  }
  return 0;
}

function requestJson(url, redirects = 0) {
  return new Promise((resolve, reject) => {
    const req = https.get(url, {
      headers: {
        accept: 'application/vnd.github+json',
        'user-agent': `EasyCodex-Desktop-Relay/${app.getVersion()}`,
      },
      timeout: UPDATE_REQUEST_TIMEOUT_MS,
    }, (res) => {
      const location = res.headers.location;
      if (res.statusCode >= 300 && res.statusCode < 400 && location && redirects < 5) {
        res.resume();
        requestJson(location, redirects + 1).then(resolve, reject);
        return;
      }
      let raw = '';
      res.setEncoding('utf8');
      res.on('data', (chunk) => { raw += chunk; });
      res.on('end', () => {
        if (res.statusCode !== 200) {
          reject(new Error(`GitHub release check failed with HTTP ${res.statusCode}`));
          return;
        }
        try {
          resolve(JSON.parse(raw));
        } catch (error) {
          reject(error);
        }
      });
    });
    req.on('timeout', () => {
      req.destroy(new Error('GitHub release check timed out.'));
    });
    req.on('error', reject);
  });
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function withNetworkRetry(label, task, attempts = UPDATE_NETWORK_RETRIES) {
  let lastError = null;
  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    try {
      return await task();
    } catch (error) {
      lastError = error;
      if (attempt >= attempts) break;
      const waitMs = 700 * attempt;
      appendLog(`${label} failed (${error.message || error}); retrying in ${waitMs}ms...`);
      await delay(waitMs);
    }
  }
  throw lastError;
}

function serializeRelease(release, channel) {
  const currentVersion = app.getVersion();
  const latestVersion = normalizeVersion(release?.tag_name || '');
  const assets = Array.isArray(release?.assets)
    ? release.assets
      .filter((asset) => typeof asset?.name === 'string' && typeof asset?.browser_download_url === 'string')
      .map((asset) => ({
        name: asset.name,
        url: asset.browser_download_url,
        size: typeof asset.size === 'number' ? asset.size : null,
        digest: typeof asset.digest === 'string' ? asset.digest : '',
      }))
    : [];
  return {
    channel,
    currentVersion,
    latestVersion: latestVersion || null,
    updateAvailable: Boolean(latestVersion && compareVersions(latestVersion, currentVersion) > 0),
    checkedAt: new Date().toISOString(),
    releaseUrl: typeof release?.html_url === 'string' ? release.html_url : null,
    releaseName: typeof release?.name === 'string' ? release.name : null,
    publishedAt: typeof release?.published_at === 'string' ? release.published_at : null,
    assets,
  };
}

async function checkForUpdates(reason = 'manual') {
  updateState = { ...updateState, checking: true, error: '' };
  await broadcastState();
  try {
    const channel = loadDesktopConfig().updateChannel;
    const endpoint = channel === 'beta' ? 'releases?per_page=30' : 'releases/latest';
    const release = selectReleaseForChannel(
      await withNetworkRetry('Update check', () => requestJson(`https://api.github.com/repos/${updateRepository()}/${endpoint}`)),
      channel,
    );
    if (!release) throw new Error(channel === 'beta' ? 'No beta release is available.' : 'No stable release is available.');
    updateState = { checking: false, applying: false, info: serializeRelease(release, channel), error: '' };
    appendLog(updateState.info.updateAvailable
      ? `Update available (${channel}): ${updateState.info.currentVersion} -> ${updateState.info.latestVersion}`
      : `EasyCodex Relay is up to date on ${channel} (${updateState.info.currentVersion}).`);
  } catch (error) {
    updateState = { checking: false, applying: false, info: updateState.info, error: error.message || String(error) };
    appendLog(`Update check failed: ${updateState.error}`);
  }
  await broadcastState();
  return updateState;
}

function downloadFileOnce(url, targetPath, redirects = 0) {
  return new Promise((resolve, reject) => {
    fs.mkdirSync(path.dirname(targetPath), { recursive: true });
    const tempPath = `${targetPath}.download`;
    const req = https.get(url, {
      headers: { 'user-agent': `EasyCodex-Desktop-Relay/${app.getVersion()}` },
      timeout: UPDATE_DOWNLOAD_TIMEOUT_MS,
    }, (res) => {
      const location = res.headers.location;
      if (res.statusCode >= 300 && res.statusCode < 400 && location && redirects < 5) {
        res.resume();
        downloadFileOnce(location, targetPath, redirects + 1).then(resolve, reject);
        return;
      }
      if (res.statusCode !== 200) {
        res.resume();
        reject(new Error(`Download failed with HTTP ${res.statusCode}`));
        return;
      }
      const stream = fs.createWriteStream(tempPath);
      res.pipe(stream);
      stream.on('finish', () => stream.close(() => {
        fs.rename(tempPath, targetPath, (error) => {
          if (error) reject(error);
          else resolve(targetPath);
        });
      }));
      stream.on('error', reject);
    });
    req.on('timeout', () => {
      req.destroy(new Error('Download timed out.'));
    });
    req.on('error', reject);
  });
}

async function downloadFile(url, targetPath) {
  return withNetworkRetry('Update download', () => downloadFileOnce(url, targetPath));
}

function runSimpleCommand(args, cwd, onDone) {
  return runCommand(args[0], args.slice(1), cwd, null, onDone);
}

async function updateFromGit() {
  const root = path.resolve(sourceRoot);
  await new Promise((resolve, reject) => {
    installProcess = runSimpleCommand(['git', 'pull', '--ff-only'], root, (error) => {
      installProcess = null;
      if (error) reject(error);
      else resolve();
    });
  });
  await installAndBuild();
}

async function applyDesktopUpdate() {
  if (updateState.applying) throw new Error('Update is already running.');
  if (!updateState.info) await checkForUpdates('apply');
  const info = updateState.info;
  if (!info?.updateAvailable) throw new Error('No update is available.');
  if (!(await confirmDesktopUpdate(info))) return appState();
  updateState = { ...updateState, applying: true, error: '' };
  await broadcastState();
  try {
    if (relayProcess) await stopRelay();
    if (isDev) {
      appendLog('Applying update from git...');
      await updateFromGit();
      appendLog('Update applied. Restart EasyCodex Relay to use the new version.');
    } else {
      const asset = selectUpdateAsset(info);
      if (!asset) throw new Error('No compatible installer asset was found in the latest release.');
      const updatesDir = path.join(configDir, 'updates');
      const targetPath = path.join(updatesDir, asset.name);
      appendLog(`Downloading update: ${asset.name}`);
      await downloadFile(asset.url, targetPath);
      await verifyDownloadedAsset(asset, targetPath, {
        allowUnsigned: process.env.EASYCODEX_ALLOW_UNSIGNED_UPDATES === '1',
        log: appendLog,
      });
      appendLog(`Opening update installer after EasyCodex Relay exits: ${targetPath}`);
      launchInstallerAfterQuit(targetPath);
    }
    updateState = { ...updateState, applying: false, error: '' };
  } catch (error) {
    updateState = { ...updateState, applying: false, error: error.message || String(error) };
    appendLog(`Update failed: ${updateState.error}`);
    throw error;
  } finally {
    await broadcastState();
  }
  return appState();
}

function launchInstallerAfterQuit(targetPath) {
  const resolvedTarget = path.resolve(targetPath);
  if (process.platform === 'win32') {
    const cmd = cleanExecutablePath(process.env.ComSpec) || windowsSystemCommand('cmd.exe') || 'cmd.exe';
    const escaped = resolvedTarget.replace(/"/g, '""');
    const child = spawn(cmd, ['/d', '/s', '/c', `timeout /t 2 /nobreak >nul & start "" "${escaped}"`], {
      detached: true,
      stdio: 'ignore',
      windowsHide: true,
      windowsVerbatimArguments: true,
    });
    child.unref();
  } else {
    if (process.platform === 'linux' && resolvedTarget.toLowerCase().endsWith('.appimage')) {
      try {
        fs.chmodSync(resolvedTarget, 0o755);
      } catch (error) {
        appendLog(`Failed to mark AppImage executable: ${error.message || error}`);
      }
    }
    shell.openPath(resolvedTarget).catch((error) => appendLog(`Failed to open installer: ${error.message || error}`));
  }
  allowQuit = true;
  setTimeout(() => app.quit(), 100);
}

function localIPv4() {
  for (const entries of Object.values(os.networkInterfaces())) {
    for (const iface of entries || []) {
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

function validatePort(input) {
  const port = Number(input);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('Port must be an integer between 1 and 65535.');
  }
  return port;
}

function validateWorkspace(input) {
  const workspace = path.resolve(String(input || ''));
  if (!fs.existsSync(workspace) || !fs.statSync(workspace).isDirectory()) {
    throw new Error('Workspace directory not found.');
  }
  const realWorkspace = fs.realpathSync(workspace);
  if (isDisallowedWorkspaceRoot(workspace) || isDisallowedWorkspaceRoot(realWorkspace)) {
    throw new Error('Choose a specific project folder. EasyCodex will not use your home, Desktop, Documents, Downloads, system, or application data folder as the relay workspace.');
  }
  return workspace;
}

function pathKey(targetPath) {
  const resolved = path.resolve(targetPath);
  return process.platform === 'win32' ? resolved.toLowerCase() : resolved;
}

function isWithinPath(base, targetPath) {
  const resolvedBase = path.resolve(base);
  const resolvedTarget = path.resolve(targetPath);
  const baseKey = pathKey(resolvedBase);
  const targetKey = pathKey(resolvedTarget);
  return targetKey === baseKey || targetKey.startsWith(`${baseKey}${path.sep}`);
}

function isDisallowedWorkspaceRoot(targetPath) {
  const resolved = path.resolve(targetPath);
  const parsed = path.parse(resolved);
  if (pathKey(resolved) === pathKey(parsed.root)) return true;
  const home = path.resolve(os.homedir());
  if (pathKey(resolved) === pathKey(home)) return true;
  const homeBoundaries = ['Desktop', 'Documents', 'Downloads'].map((name) => path.join(home, name));
  if (homeBoundaries.some((root) => pathKey(resolved) === pathKey(root))) return true;
  const disallowed = [
    process.env.SystemRoot,
    process.env.ProgramFiles,
    process.env['ProgramFiles(x86)'],
    process.env.APPDATA,
    process.env.LOCALAPPDATA,
  ].filter((entry) => typeof entry === 'string' && entry.trim());
  return disallowed.some((root) => isWithinPath(root, resolved));
}

function readPackageVersion(packagePath) {
  try {
    const parsed = JSON.parse(fs.readFileSync(packagePath, 'utf8'));
    return typeof parsed.version === 'string' ? parsed.version : '';
  } catch {
    return '';
  }
}

function assertInsidePath(base, targetPath) {
  const resolvedBase = path.resolve(base);
  const resolvedTarget = path.resolve(targetPath);
  const baseKey = pathKey(resolvedBase);
  const targetKey = pathKey(resolvedTarget);
  if (targetKey !== baseKey && !targetKey.startsWith(`${baseKey}${path.sep}`)) {
    throw new Error(`Refusing to manage path outside expected directory: ${resolvedTarget}`);
  }
}

function resetRuntimeRelayDir() {
  assertInsidePath(configDir, runtimeRelayDir);
  if (path.basename(runtimeRelayDir) !== 'desktop-relay-runtime') {
    throw new Error(`Refusing to reset unexpected runtime directory: ${runtimeRelayDir}`);
  }
  fs.rmSync(runtimeRelayDir, { recursive: true, force: true });
  fs.mkdirSync(runtimeRelayDir, { recursive: true });
}

function relayRuntimeReady(dir) {
  return (
    fs.existsSync(path.join(dir, 'package.json')) &&
    fs.existsSync(path.join(dir, 'dist', 'server.js')) &&
    fs.existsSync(path.join(dir, 'node_modules', 'express')) &&
    fs.existsSync(path.join(dir, 'node_modules', 'ws'))
  );
}

function relayDir() {
  if (isDev) return sourceRelayDir;
  const sourcePackage = path.join(sourceRelayDir, 'package.json');
  if (!fs.existsSync(sourcePackage)) throw new Error(`Packaged relay resources not found: ${sourceRelayDir}`);
  if (relayRuntimeReady(sourceRelayDir)) {
    return sourceRelayDir;
  }

  const runtimePackage = path.join(runtimeRelayDir, 'package.json');
  const sourceVersion = readPackageVersion(sourcePackage);
  const runtimeVersion = fs.existsSync(runtimePackage) ? readPackageVersion(runtimePackage) : '';
  if (!relayRuntimeReady(runtimeRelayDir) || (sourceVersion && sourceVersion !== runtimeVersion)) {
    resetRuntimeRelayDir();
    fs.cpSync(sourceRelayDir, runtimeRelayDir, {
      recursive: true,
      force: true,
    });
  }
  if (!relayRuntimeReady(runtimeRelayDir)) {
    throw new Error(`Relay runtime is incomplete after staging: ${runtimeRelayDir}`);
  }
  return runtimeRelayDir;
}

function connectionDetails(port, apiKey) {
  const ip = localIPv4();
  const relayUrl = `ws://${ip}:${port}`;
  const connectUrl = `http://${ip}:${port}/c?k=${encodeURIComponent(apiKey)}`;
  const deepLink = `easycodex://connect?relayUrl=${encodeURIComponent(relayUrl)}&apiKey=${encodeURIComponent(apiKey)}`;
  return { relayUrl, connectUrl, deepLink };
}

function relayReady() {
  try {
    const cwd = relayDir();
    return (
      fs.existsSync(path.join(cwd, 'dist', 'server.js')) &&
      fs.existsSync(path.join(cwd, 'node_modules', 'express')) &&
      fs.existsSync(path.join(cwd, 'node_modules', 'ws'))
    );
  } catch {
    return false;
  }
}

function checkPortAvailable(input) {
  return new Promise((resolve) => {
    const port = validatePort(input);
    const currentPort = loadDesktopConfig().port;
    if (relayRunning && port === currentPort) {
      resolve(true);
      return;
    }
    const server = net.createServer();
    server.once('error', () => resolve(false));
    server.once('listening', () => {
      server.close(() => resolve(true));
    });
    server.listen(port, '0.0.0.0');
  });
}

function looksLikeEasyCodexRelayHealth(health) {
  const data = health?.data;
  return Boolean(
    health?.online &&
    data?.status === 'ok' &&
    typeof data.sessionId === 'string' &&
    Array.isArray(data.allowedWorkspaceRoots) &&
    data.system?.hostname &&
    data.runtime,
  );
}

async function checkPortStatus(input) {
  const port = validatePort(input);
  const available = await checkPortAvailable(port);
  const processes = portProcessDetails(port);
  if (available) {
    return { available: true, reclaimable: false, occupiedByRelay: false, processes };
  }

  const health = await healthRequestHost('127.0.0.1', port, loadApiKey());
  const occupiedByRelay = looksLikeEasyCodexRelayHealth(health);
  return {
    available: false,
    reclaimable: occupiedByRelay,
    occupiedByRelay,
    processes,
    pid: null,
  };
}

function powershellCommand() {
  if (process.platform !== 'win32') return null;
  return firstExistingFile([
    windowsSystemCommand('powershell.exe'),
    windowsSystemCommand('pwsh.exe'),
    'powershell.exe',
  ]);
}

function listeningPidsForPort(port) {
  if (process.platform === 'win32') {
    const powershell = powershellCommand();
    if (!powershell) return [];
    const script = `Get-NetTCPConnection -LocalPort ${port} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique`;
    const result = spawnSync(powershell, ['-NoProfile', '-NonInteractive', '-Command', script], {
      stdio: ['ignore', 'pipe', 'ignore'],
      encoding: 'utf8',
      timeout: 5000,
      windowsHide: true,
    });
    if (result.error || result.status !== 0) return [];
    return String(result.stdout || '')
      .split(/\r?\n/)
      .map((line) => Number.parseInt(line.trim(), 10))
      .filter((pid) => Number.isInteger(pid) && pid > 0);
  }

  const lsofResult = spawnSync('lsof', ['-nP', `-iTCP:${port}`, '-sTCP:LISTEN', '-t'], {
    stdio: ['ignore', 'pipe', 'ignore'],
    encoding: 'utf8',
    timeout: 5000,
  });
  const lsofPids = String(lsofResult.stdout || '')
    .split(/\r?\n/)
    .map((line) => Number.parseInt(line.trim(), 10))
    .filter((pid) => Number.isInteger(pid) && pid > 0);
  if (!lsofResult.error && lsofResult.status === 0 && lsofPids.length > 0) return Array.from(new Set(lsofPids));

  const ssResult = spawnSync('ss', ['-H', '-ltnp', `sport = :${port}`], {
    stdio: ['ignore', 'pipe', 'ignore'],
    encoding: 'utf8',
    timeout: 5000,
  });
  const ssPids = Array.from(String(ssResult.stdout || '').matchAll(/pid=(\d+)/g))
    .map((match) => Number.parseInt(match[1], 10))
    .filter((pid) => Number.isInteger(pid) && pid > 0);
  if (!ssResult.error && ssResult.status === 0 && ssPids.length > 0) return Array.from(new Set(ssPids));

  const netstatResult = spawnSync('netstat', ['-ltnp'], {
    stdio: ['ignore', 'pipe', 'ignore'],
    encoding: 'utf8',
    timeout: 5000,
  });
  const netstatPids = String(netstatResult.stdout || '')
    .split(/\r?\n/)
    .filter((line) => line.includes(`:${port} `) || line.includes(`:${port}\t`))
    .map((line) => Number.parseInt(line.trim().split(/\s+/).at(-1)?.split('/')[0] || '', 10))
    .filter((pid) => Number.isInteger(pid) && pid > 0);
  if (!netstatResult.error && netstatResult.status === 0 && netstatPids.length > 0) return Array.from(new Set(netstatPids));

  return [];
}

function readProcessField(pid, field) {
  const result = spawnSync('ps', ['-p', String(pid), '-o', `${field}=`], {
    stdio: ['ignore', 'pipe', 'ignore'],
    encoding: 'utf8',
    timeout: 5000,
  });
  if (result.error || result.status !== 0) return '';
  return String(result.stdout || '').trim();
}

function linuxProcessExePath(pid) {
  if (process.platform !== 'linux') return '';
  try {
    return fs.readlinkSync(`/proc/${pid}/exe`);
  } catch {
    return '';
  }
}

function processDetailsForPids(pids) {
  const uniquePids = Array.from(new Set((pids || []).filter((pid) => Number.isInteger(pid) && pid > 0)));
  if (uniquePids.length === 0) return [];
  if (process.platform === 'win32') {
    const powershell = powershellCommand();
    if (!powershell) return uniquePids.map((pid) => ({ pid, name: `PID ${pid}`, path: '', commandLine: '' }));
    const idList = uniquePids.join(',');
    const script = `
$ids = @(${idList});
Get-CimInstance Win32_Process | Where-Object { $ids -contains $_.ProcessId } | ForEach-Object {
  [PSCustomObject]@{
    pid = $_.ProcessId
    name = $_.Name
    path = $_.ExecutablePath
    commandLine = $_.CommandLine
  }
} | ConvertTo-Json -Compress
`;
    const result = spawnSync(powershell, ['-NoProfile', '-NonInteractive', '-Command', script], {
      stdio: ['ignore', 'pipe', 'ignore'],
      encoding: 'utf8',
      timeout: 5000,
      windowsHide: true,
    });
    if (result.error || result.status !== 0 || !String(result.stdout || '').trim()) {
      return uniquePids.map((pid) => ({ pid, name: `PID ${pid}`, path: '', commandLine: '' }));
    }
    try {
      const parsed = JSON.parse(String(result.stdout || '').trim());
      return (Array.isArray(parsed) ? parsed : [parsed]).map((entry) => ({
        pid: Number(entry.pid || entry.ProcessId),
        name: String(entry.name || entry.Name || '').trim() || `PID ${entry.pid || entry.ProcessId}`,
        path: String(entry.path || entry.ExecutablePath || '').trim(),
        commandLine: String(entry.commandLine || entry.CommandLine || '').trim(),
      })).filter((entry) => Number.isInteger(entry.pid) && entry.pid > 0);
    } catch {
      return uniquePids.map((pid) => ({ pid, name: `PID ${pid}`, path: '', commandLine: '' }));
    }
  }

  return uniquePids.map((pid) => {
    const commandPath = readProcessField(pid, 'comm');
    const commandLine = readProcessField(pid, 'args');
    const executablePath = linuxProcessExePath(pid) || (path.isAbsolute(commandPath) ? commandPath : '');
    const name = path.basename(commandPath || commandLine.split(/\s+/)[0] || '') || `PID ${pid}`;
    return { pid, name, path: executablePath, commandLine };
  });
}

function portProcessDetails(port) {
  const pids = listeningPidsForPort(port).filter((pid) => pid !== process.pid);
  return processDetailsForPids(pids).map((entry) => {
    const text = `${entry.name} ${entry.path} ${entry.commandLine}`.toLowerCase();
    const isEasyCodexRelay = text.includes('easycodex') || text.includes('agent-relay') || text.includes('desktop-relay');
    return { ...entry, isEasyCodexRelay };
  });
}

async function waitForPortAvailable(port, timeoutMs = 5000) {
  const started = Date.now();
  while (Date.now() - started < timeoutMs) {
    if (await checkPortAvailable(port)) return true;
    await delay(150);
  }
  return false;
}

async function stopExternalRelayOnPort(port) {
  const status = await checkPortStatus(port);
  if (!status.reclaimable) return false;

  const pids = listeningPidsForPort(port).filter((pid) => pid !== process.pid);
  if (pids.length === 0) {
    appendLog(`Port ${port} is used by EasyCodex Relay, but its process id could not be found.`);
    return false;
  }

  appendLog(`Stopping existing EasyCodex Relay on port ${port} (PID ${pids.join(', ')}).`);
  for (const pid of pids) {
    try {
      process.kill(pid);
    } catch (error) {
      appendLog(`Failed to stop PID ${pid}: ${error.message || error}`);
    }
  }

  return waitForPortAvailable(port);
}

async function stopProcessOnPort(input) {
  const port = validatePort(input?.port);
  const pid = Number(input?.pid);
  if (!Number.isInteger(pid) || pid <= 0) throw new Error('Invalid process id.');
  if (pid === process.pid) throw new Error('Cannot stop the desktop app process.');
  if (relayProcess?.pid === pid) throw new Error('Use Stop to stop the current EasyCodex relay.');

  const listening = listeningPidsForPort(port);
  if (!listening.includes(pid)) throw new Error(`PID ${pid} is no longer listening on port ${port}.`);

  appendLog(`Stopping PID ${pid} on port ${port}.`);
  try {
    process.kill(pid);
  } catch (error) {
    throw new Error(`Failed to stop PID ${pid}: ${error.message || error}`);
  }
  await waitForPortAvailable(port, 5000);
  return checkPortStatus(port);
}

function normalizeLanguage(value) {
  return supportedLanguages.includes(value) ? value : 'system';
}

function systemLanguage() {
  const locale = app.getLocale().toLowerCase();
  if (locale.startsWith('zh')) return 'zh';
  return 'en';
}

function effectiveLanguage(config, health) {
  const phoneLanguage = normalizeLanguage(health?.data?.lastClientLanguage);
  if (config.languageMode === 'follow-phone' && phoneLanguage !== 'system') return phoneLanguage;
  if (config.language !== 'system') return config.language;
  return systemLanguage();
}

function closeDialogText() {
  const language = effectiveLanguage(loadDesktopConfig(), lastHealth);
  if (language === 'zh') {
    return {
      buttons: ['最小化到后台', '直接关闭'],
      message: '要让 EasyCodex 中继继续在后台运行吗？',
      detail: '选择“最小化到后台”后，手机仍可继续连接这台电脑上的中继。',
    };
  }
  if (language === 'zh-Hant') {
    return {
      buttons: ['最小化到背景', '直接關閉'],
      message: '要讓 EasyCodex 中繼繼續在背景執行嗎？',
      detail: '選擇「最小化到背景」後，手機仍可繼續連接這台電腦上的中繼。',
    };
  }
  return {
    buttons: ['Minimize to background', 'Quit'],
    message: 'Keep EasyCodex Relay running in the background?',
    detail: 'Choose Minimize to background to keep the relay available for your phone.',
  };
}

function updateConfirmText(version) {
  const language = effectiveLanguage(loadDesktopConfig(), lastHealth);
  if (language === 'zh') {
    return {
      buttons: ['退出并更新', '取消'],
      message: `安装 EasyCodex Relay ${version} 前需要退出当前程序。`,
      detail: '继续后会先停止本机中继，退出桌面端，然后打开安装包。安装过程中手机将暂时无法连接这台电脑。',
    };
  }
  if (language === 'zh-Hant') {
    return {
      buttons: ['退出並更新', '取消'],
      message: `安裝 EasyCodex Relay ${version} 前需要退出目前程式。`,
      detail: '繼續後會先停止本機中繼，退出桌面端，然後開啟安裝包。安裝期間手機將暫時無法連接這台電腦。',
    };
  }
  return {
    buttons: ['Quit and update', 'Cancel'],
    message: `EasyCodex Relay must quit before installing ${version}.`,
    detail: 'Continuing will stop the local relay, quit the desktop app, and then open the installer. Your phone will be disconnected during the update.',
  };
}

async function confirmDesktopUpdate(info) {
  const confirmText = updateConfirmText(info?.latestVersion || '');
  const choice = await dialog.showMessageBox(mainWindow, {
    type: 'warning',
    buttons: confirmText.buttons,
    defaultId: 0,
    cancelId: 1,
    title: 'EasyCodex Relay',
    message: confirmText.message,
    detail: confirmText.detail,
  });
  return choice.response === 0;
}

async function appState() {
  const config = loadDesktopConfig();
  const apiKey = loadApiKey();
  const details = connectionDetails(config.port, apiKey);
  const portStatus = await checkPortStatus(config.port).catch(() => ({
    available: false,
    reclaimable: false,
    occupiedByRelay: false,
  }));
  const isRelayReady = relayReady();
  const codex = detectCodex(config.codexPath);
  return {
    platform: process.platform,
    relayDir: relayDir(),
    configPath,
    desktopConfigPath,
    runtimeRelayDir,
    port: config.port,
    workspace: config.workspace,
    codexPath: config.codexPath,
    codex,
    apiKey,
    relayRunning,
    relayReady: isRelayReady,
    installRunning: Boolean(installProcess),
    update: updateState,
    portAvailable: portStatus.available,
    portReclaimable: portStatus.reclaimable,
    portOccupiedByRelay: portStatus.occupiedByRelay,
    guideVisible: !config.guideSeen,
    health: mergeRelayEventHealth(lastHealth),
    languageMode: config.languageMode,
    language: config.language,
    lightMode: config.lightMode,
    updateChannel: config.updateChannel,
    supportedUpdateChannels,
    effectiveLanguage: effectiveLanguage(config, mergeRelayEventHealth(lastHealth)),
    supportedLanguages,
    qrDataUrl: await QRCode.toDataURL(details.connectUrl, {
      margin: 1,
      width: 360,
      color: { dark: '#1d251f', light: '#ffffff' },
    }),
    ...details,
  };
}

function send(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) mainWindow.webContents.send(channel, payload);
}

async function broadcastState() {
  send('state', await appState());
}

function appendLog(line) {
  send('log', String(line));
}

function ensureBackgroundServices() {
  startHealthPolling();
  if (!updateState.checking && !updateState.info && !updateState.error) {
    setTimeout(() => {
      void checkForUpdates('startup').catch(() => {});
    }, 800);
  }
}

function lightModeStartInput() {
  const config = loadDesktopConfig();
  return {
    port: config.port,
    workspace: config.workspace,
    codexPath: config.codexPath,
  };
}

async function ensureRelayStartedForLightMode(reason) {
  if (relayProcess) return;
  if (lightModeStartPromise) return lightModeStartPromise;
  appendLog(`Lightweight mode requested; starting relay before hiding window (${reason}).`);
  lightModeStartPromise = startRelay(lightModeStartInput(), { skipAutoEnterLightMode: true })
    .finally(() => {
      lightModeStartPromise = null;
    });
  return lightModeStartPromise;
}

async function enterLightMode(reason = 'manual') {
  saveDesktopConfig({ lightMode: true });
  ensureBackgroundServices();
  try {
    await ensureRelayStartedForLightMode(reason);
  } catch (error) {
    appendLog(`Lightweight mode could not start relay: ${error.message || error}`);
    if (tray) tray.setToolTip('EasyCodex Relay - relay not running');
    refreshTrayMenu();
    throw error;
  }
  if (mainWindow && !mainWindow.isDestroyed()) {
    appendLog('Entering lightweight mode: renderer window is closed, relay core stays in tray.');
    enteringLightMode = true;
    mainWindow.destroy();
  }
  if (tray) tray.setToolTip('EasyCodex Relay - lightweight mode');
  refreshTrayMenu();
}

function mergeRelayEventHealth(health) {
  if (!relayRunning || !relayEventData) return health;
  const data = { ...(health?.data || {}), ...relayEventData };
  if (health?.online) return { ...health, data };
  if (relayEventData.status === 'ok') return { online: true, data };
  return health;
}

function updateRelayEventData(data) {
  relayEventData = {
    ...(relayEventData || {}),
    status: 'ok',
    ...data,
  };
  lastHealth = mergeRelayEventHealth(lastHealth);
  send('health', lastHealth);
  void broadcastState();
}

function handleRelayOutputLine(rawLine) {
  const line = String(rawLine || '').trim();
  const eventMatch = line.match(/^\[relay:event\]\s+(\{.*\})$/);
  if (eventMatch) {
    try {
      const event = JSON.parse(eventMatch[1]);
      if (event.type !== 'ready' && event.type !== 'clients') return;
      updateRelayEventData({
        connectedClients: Number.isInteger(event.connectedClients) ? event.connectedClients : relayEventData?.connectedClients,
        notificationClients: Number.isInteger(event.notificationClients) ? event.notificationClients : relayEventData?.notificationClients,
        notificationTokens: Number.isInteger(event.notificationTokens) ? event.notificationTokens : relayEventData?.notificationTokens,
        lastClientLanguage: typeof event.lastClientLanguage === 'string' ? event.lastClientLanguage : relayEventData?.lastClientLanguage,
      });
    } catch {}
    return;
  }

  const authMatch = line.match(/^Authenticated client\s+(\S+)/);
  if (authMatch) {
    relayLogClientIds.add(authMatch[1]);
    updateRelayEventData({ connectedClients: relayLogClientIds.size });
    return;
  }

  const disconnectMatch = line.match(/^Client disconnected \(([^)]+)\)/);
  if (disconnectMatch && disconnectMatch[1] !== 'unauthenticated') {
    relayLogClientIds.delete(disconnectMatch[1]);
    updateRelayEventData({ connectedClients: relayLogClientIds.size });
  }
}

function handleRelayOutput(chunk) {
  const text = chunk.toString();
  appendLog(text);
  relayOutputBuffer += text;
  const lines = relayOutputBuffer.split(/\r?\n/);
  relayOutputBuffer = lines.pop() || '';
  for (const line of lines) handleRelayOutputLine(line);
}

function redactPathList(value) {
  return String(value || '')
    .split(path.delimiter)
    .filter(Boolean)
    .slice(0, 12)
    .join(path.delimiter);
}

function runCommand(command, args, cwd, env, onDone) {
  let finished = false;
  const done = (error) => {
    if (finished) return;
    finished = true;
    onDone(error);
  };
  const commandEnv = { ...process.env, ...(env || {}) };
  appendLog(`> ${command} ${args.join(' ')}`);
  appendLog(`cwd: ${cwd}`);
  let child;
  try {
    child = spawn(command, args, {
      cwd,
      shell: false,
      windowsHide: true,
      windowsVerbatimArguments: process.platform === 'win32' && path.basename(command).toLowerCase() === 'cmd.exe',
      env: commandEnv,
    });
  } catch (error) {
    appendLog(`Failed to spawn process: ${error.message}`);
    appendLog(`PATH head: ${redactPathList(commandEnv.PATH || commandEnv.Path)}`);
    done(error);
    return null;
  }
  child.stdout.on('data', (chunk) => appendLog(chunk.toString()));
  child.stderr.on('data', (chunk) => appendLog(chunk.toString()));
  child.on('error', (error) => {
    appendLog(`Process spawn error: ${error.message}`);
    appendLog(`PATH head: ${redactPathList(commandEnv.PATH || commandEnv.Path)}`);
    done(error);
  });
  child.on('close', (code) => {
    if (code === 0) done(null);
    else done(new Error(`${command} ${args.join(' ')} exited with code ${code ?? 'unknown'}`));
  });
  return child;
}

function runNpmCommand(args, cwd, env, onDone) {
  const commandLine = npmCommandArgs(args);
  return runCommand(commandLine.command, commandLine.args, cwd, env, onDone);
}

function healthRequestHost(host, port, apiKey) {
  return new Promise((resolve) => {
    const req = http.get(
      `http://${host}:${port}/health?key=${encodeURIComponent(apiKey)}`,
      { timeout: 1800 },
      (res) => {
        let raw = '';
        res.setEncoding('utf8');
        res.on('data', (chunk) => { raw += chunk; });
        res.on('end', () => {
          if (res.statusCode !== 200) return resolve({ online: false, statusCode: res.statusCode });
          try {
            resolve({ online: true, data: JSON.parse(raw) });
          } catch {
            resolve({ online: true, data: null });
          }
        });
      },
    );
    req.on('timeout', () => {
      req.destroy();
      resolve({ online: false });
    });
    req.on('error', () => resolve({ online: false }));
  });
}

async function healthRequest(port, apiKey) {
  const hosts = Array.from(new Set(['127.0.0.1', localIPv4()]));
  for (const host of hosts) {
    const health = await healthRequestHost(host, port, apiKey);
    if (health.online) return { ...health, host };
  }
  return { online: false };
}

function startHealthPolling() {
  if (healthTimer) clearInterval(healthTimer);
  healthTimer = setInterval(async () => {
    const config = loadDesktopConfig();
    lastHealth = mergeRelayEventHealth(await healthRequest(config.port, loadApiKey()));
    if (lastHealth.online && config.languageMode === 'follow-phone') {
      const phoneLanguage = normalizeLanguage(lastHealth.data?.lastClientLanguage);
      if (phoneLanguage !== 'system') saveDesktopConfig({ language: phoneLanguage });
    }
    send('health', lastHealth);
    await broadcastState();
  }, 2500);
}

async function installAndBuild() {
  if (installProcess) throw new Error('Install/build is already running.');
  if (relayProcess) throw new Error('Stop the relay before installing or rebuilding it.');
  const cwd = relayDir();
  if (!isDev && relayReady()) {
    appendLog('Packaged relay is already bundled with dependencies and build output.');
    saveDesktopConfig({ guideSeen: true });
    await broadcastState();
    return;
  }
  appendLog('Installing relay dependencies...');
  if (process.platform === 'win32') {
    const commandLine = npmCommandArgs(['--version']);
    appendLog(`Using npm launcher: ${commandLine.command} ${commandLine.args.join(' ')}`);
  }
  await broadcastState();
  await new Promise((resolve, reject) => {
    installProcess = runNpmCommand(['install'], cwd, null, (error) => {
      installProcess = null;
      if (error) reject(error);
      else resolve();
    });
  });
  appendLog('Building relay...');
  await new Promise((resolve, reject) => {
    installProcess = runNpmCommand(['run', 'build'], cwd, null, (error) => {
      installProcess = null;
      if (error) reject(error);
      else resolve();
    });
  });
  appendLog('Relay dependencies and build are ready.');
  saveDesktopConfig({ guideSeen: true });
  await broadcastState();
}

async function startRelay(input, options = {}) {
  if (relayProcess) return;
  const port = validatePort(input?.port);
  const workspace = validateWorkspace(input?.workspace);
  const codexPath = cleanExecutablePath(input?.codexPath ?? loadDesktopConfig().codexPath);
  const codex = detectCodex(codexPath, { force: true });
  if (!codex.installed) {
    appendLog(`Codex detection failed: ${codex.error}`);
    throw new Error(codex.error || 'Codex CLI was not found. Choose the Codex executable path.');
  }
  const portStatus = await checkPortStatus(port);
  if (!portStatus.available) {
    if (!portStatus.reclaimable || !(await stopExternalRelayOnPort(port))) {
      throw new Error('Port is in use. Choose another port.');
    }
    appendLog(`Port ${port} is free after stopping the existing relay.`);
  }
  saveDesktopConfig({ port, workspace, codexPath });
  const cwd = relayDir();
  const builtServer = path.join(cwd, 'dist', 'server.js');
  if (!fs.existsSync(builtServer)) throw new Error('Please run Install/build before starting the relay.');
  const nodeScript = nodeScriptCommand(builtServer);
  const command = nodeScript.command;
  const args = nodeScript.args;
  appendLog('Starting relay...');
  appendLog(`> ${command} ${args.join(' ')}`);
  relayEventData = null;
  relayOutputBuffer = '';
  relayLogClientIds = new Set();

  const relayEnv = {
    ...process.env,
    ...(codex.env || {}),
    ...nodeScript.env,
    PORT: String(port),
    API_KEY: loadApiKey(),
    CODEX_CWD: workspace,
    CODEX_EXECUTABLE: codex.path,
    EASYCODEX_NO_TERMINAL_QR: '1',
    EASYCODEX_UPDATE_CHANNEL: loadDesktopConfig().updateChannel,
  };
  try {
    relayProcess = spawn(command, args, {
      cwd,
      shell: false,
      windowsHide: true,
      windowsVerbatimArguments: false,
      env: relayEnv,
    });
  } catch (error) {
    appendLog(`Relay failed to spawn: ${error.message}`);
    appendLog(`PATH head: ${redactPathList(relayEnv.PATH || relayEnv.Path)}`);
    relayProcess = null;
    relayRunning = false;
    throw error;
  }
  relayRunning = true;
  relayProcess.stdout.on('data', handleRelayOutput);
  relayProcess.stderr.on('data', (chunk) => appendLog(chunk.toString()));
  relayProcess.on('error', async (error) => {
    appendLog(`Relay failed to start: ${error.message}`);
    relayProcess = null;
    relayRunning = false;
    relayEventData = null;
    relayOutputBuffer = '';
    relayLogClientIds = new Set();
    await broadcastState();
  });
  relayProcess.on('close', async (code) => {
    appendLog(`Relay exited with code ${code ?? 'unknown'}.`);
    relayProcess = null;
    relayRunning = false;
    relayEventData = null;
    relayOutputBuffer = '';
    relayLogClientIds = new Set();
    lastHealth = { online: false };
    await broadcastState();
  });
  await broadcastState();
  if (!options.skipAutoEnterLightMode && loadDesktopConfig().lightMode && mainWindow && !mainWindow.isDestroyed()) {
    setTimeout(() => {
      void enterLightMode('relay-started').catch(() => {});
    }, 350);
  }
}

async function stopRelay() {
  if (!relayProcess) {
    relayRunning = false;
    relayEventData = null;
    relayOutputBuffer = '';
    relayLogClientIds = new Set();
    await broadcastState();
    return;
  }
  relayProcess.kill();
  relayProcess = null;
  relayRunning = false;
  relayEventData = null;
  relayOutputBuffer = '';
  relayLogClientIds = new Set();
  lastHealth = { online: false };
  appendLog('Relay stopped.');
  await broadcastState();
}

async function createWindow() {
  Menu.setApplicationMenu(null);
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.show();
    mainWindow.focus();
    return;
  }
  mainWindow = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 940,
    minHeight: 660,
    show: !isSmokeTest,
    frame: false,
    icon: windowIconPath,
    title: 'EasyCodex Relay',
    backgroundColor: '#10120f',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });
  mainWindow.on('close', async (event) => {
    if (allowQuit || isSmokeTest || enteringLightMode) return;
    if (loadDesktopConfig().lightMode) {
      event.preventDefault();
      try {
        await enterLightMode('window-close');
      } catch (error) {
        await broadcastState();
      }
      return;
    }
    event.preventDefault();
    const closeText = closeDialogText();
    const choice = await dialog.showMessageBox(mainWindow, {
      type: 'question',
      buttons: closeText.buttons,
      defaultId: 0,
      cancelId: 0,
      title: 'EasyCodex Relay',
      message: closeText.message,
      detail: closeText.detail,
    });
    if (choice.response === 1) {
      allowQuit = true;
      app.quit();
      return;
    }
    mainWindow.hide();
  });
  mainWindow.on('closed', () => {
    mainWindow = null;
    enteringLightMode = false;
    refreshTrayMenu();
  });
  if (fs.existsSync(rendererDistIndex)) {
    await mainWindow.loadFile(rendererDistIndex);
  } else if (isDev && fs.existsSync(legacyRendererIndex)) {
    appendLog('Renderer dist is missing; loading legacy development renderer.');
    await mainWindow.loadFile(legacyRendererIndex);
  } else {
    throw new Error(`Renderer build output not found: ${rendererDistIndex}`);
  }
  ensureBackgroundServices();
  refreshTrayMenu();
  if (isSmokeTest) setTimeout(() => app.quit(), 1200);
}

function createTray() {
  const icon = nativeImage.createFromPath(iconPath).resize({ width: 16, height: 16 });
  tray = new Tray(icon);
  tray.setToolTip(loadDesktopConfig().lightMode ? 'EasyCodex Relay - lightweight mode' : 'EasyCodex Relay');
  refreshTrayMenu();
  tray.on('click', showMainWindow);
  tray.on('double-click', showMainWindow);
}

function refreshTrayMenu() {
  if (!tray) return;
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Open EasyCodex Relay', click: showMainWindow },
    {
      label: 'Enter lightweight mode',
      enabled: Boolean(mainWindow && !mainWindow.isDestroyed()),
      click: () => {
        void enterLightMode('tray-menu').catch(() => {});
      },
    },
    { type: 'separator' },
    {
      label: 'Quit',
      click: () => {
        allowQuit = true;
        app.quit();
      },
    },
  ]));
}

function showMainWindow() {
  if (tray) tray.setToolTip('EasyCodex Relay');
  if (!mainWindow) {
    void createWindow();
    return;
  }
  mainWindow.show();
  mainWindow.focus();
  refreshTrayMenu();
}

app.whenReady().then(() => {
  Menu.setApplicationMenu(null);
  createTray();
  if (loadDesktopConfig().lightMode && !isSmokeTest) {
    ensureBackgroundServices();
    void ensureRelayStartedForLightMode('startup').catch(async (error) => {
      console.error('[desktop-relay] Lightweight startup failed:', error);
      await createWindow();
      appendLog(`Lightweight startup failed: ${error.message || error}`);
    });
  } else {
    createWindow();
  }
});

app.on('second-instance', () => {
  showMainWindow();
});

app.on('window-all-closed', () => {
  if (enteringLightMode || loadDesktopConfig().lightMode) return;
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});

app.on('before-quit', () => {
  allowQuit = true;
  if (healthTimer) clearInterval(healthTimer);
  if (relayProcess) relayProcess.kill();
  if (installProcess) installProcess.kill();
});

ipcMain.handle('get-state', appState);
ipcMain.handle('install-build', async () => {
  await installAndBuild();
  return appState();
});
ipcMain.handle('check-update', async () => {
  await checkForUpdates('manual');
  return appState();
});
ipcMain.handle('apply-update', async () => applyDesktopUpdate());
ipcMain.handle('start-relay', async (_event, input) => {
  await startRelay(input || {});
  return appState();
});
ipcMain.handle('stop-relay', async () => {
  await stopRelay();
  return appState();
});
ipcMain.handle('enter-light-mode', async () => {
  await enterLightMode('ipc');
  return appState();
});
ipcMain.handle('refresh-api-key', async () => {
  writeJson(configPath, { apiKey: generateApiKey() });
  appendLog('Generated a new relay API key.');
  return appState();
});
ipcMain.handle('save-config', async (_event, input) => {
  const next = {};
  if (Object.prototype.hasOwnProperty.call(input || {}, 'port')) {
    const port = validatePort(input.port);
    const portStatus = await checkPortStatus(port);
    if (!portStatus.available && !portStatus.reclaimable) throw new Error('Port is in use. Choose another port.');
    next.port = port;
  }
  if (Object.prototype.hasOwnProperty.call(input || {}, 'workspace')) next.workspace = validateWorkspace(input.workspace);
  if (Object.prototype.hasOwnProperty.call(input || {}, 'codexPath')) {
    const codexPath = cleanExecutablePath(input.codexPath);
    const codex = detectCodex(codexPath, { force: true });
    if (!codex.installed) throw new Error(codex.error || 'Codex CLI was not found.');
    next.codexPath = codexPath;
  }
  if (input?.languageMode === 'manual' || input?.languageMode === 'follow-phone') next.languageMode = input.languageMode;
  if (supportedLanguages.includes(input?.language)) next.language = input.language;
  if (Object.prototype.hasOwnProperty.call(input || {}, 'lightMode')) next.lightMode = input.lightMode === true;
  if (supportedUpdateChannels.includes(input?.updateChannel)) {
    next.updateChannel = input.updateChannel;
    updateState = { checking: false, applying: false, info: null, error: '' };
  }
  saveDesktopConfig(next);
  return appState();
});
ipcMain.handle('preview-port', async (_event, input) => {
  const port = validatePort(input?.port);
  const apiKey = loadApiKey();
  const details = connectionDetails(port, apiKey);
  const portStatus = await checkPortStatus(port);
  return {
    port,
    portAvailable: portStatus.available,
    portReclaimable: portStatus.reclaimable,
    portOccupiedByRelay: portStatus.occupiedByRelay,
    processes: portStatus.processes,
    qrDataUrl: await QRCode.toDataURL(details.connectUrl, {
      margin: 1,
      width: 360,
      color: { dark: '#1d251f', light: '#ffffff' },
    }),
    ...details,
  };
});
ipcMain.handle('stop-port-process', async (_event, input) => {
  const status = await stopProcessOnPort(input || {});
  await broadcastState();
  return {
    portAvailable: status.available,
    portReclaimable: status.reclaimable,
    portOccupiedByRelay: status.occupiedByRelay,
    processes: status.processes,
  };
});
ipcMain.handle('window-minimize', () => {
  if (mainWindow) mainWindow.minimize();
});
ipcMain.handle('window-hide', () => {
  if (mainWindow) mainWindow.hide();
});
ipcMain.handle('window-close', () => {
  if (mainWindow) mainWindow.close();
});
ipcMain.handle('browse-workspace', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Choose the default Codex workspace',
    properties: ['openDirectory'],
  });
  if (result.canceled || !result.filePaths[0]) return null;
  return result.filePaths[0];
});
ipcMain.handle('browse-codex', async () => {
  const result = await dialog.showOpenDialog(mainWindow, {
    title: 'Choose the Codex executable',
    properties: ['openFile'],
  });
  if (result.canceled || !result.filePaths[0]) return null;
  const codexPath = result.filePaths[0];
  const codex = detectCodex(codexPath, { force: true });
  if (!codex.installed) throw new Error(codex.error || 'Selected file is not a working Codex executable.');
  saveDesktopConfig({ codexPath });
  return appState();
});
ipcMain.handle('copy-text', (_event, text) => {
  clipboard.writeText(String(text || ''));
});
ipcMain.handle('open-external', (_event, url) => {
  const target = new URL(String(url || ''));
  if (!['https:', 'http:', 'mailto:'].includes(target.protocol)) {
    throw new Error('Unsupported external URL scheme.');
  }
  shell.openExternal(target.toString());
});
