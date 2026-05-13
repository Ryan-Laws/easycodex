const { app, BrowserWindow, Menu, Tray, clipboard, dialog, ipcMain, nativeImage, shell } = require('electron');
const crypto = require('node:crypto');
const fs = require('node:fs');
const http = require('node:http');
const net = require('node:net');
const os = require('node:os');
const path = require('node:path');
const { spawn } = require('node:child_process');
const QRCode = require('qrcode');

const isDev = !app.isPackaged;
const isSmokeTest = process.argv.includes('--smoke-test');
const sourceRoot = isDev ? path.resolve(__dirname, '..', '..') : process.resourcesPath;
const sourceRelayDir = isDev ? path.join(sourceRoot, 'agent-relay') : path.join(process.resourcesPath, 'agent-relay');
const configDir = path.join(os.homedir(), '.easycodex');
const configPath = path.join(configDir, 'config.json');
const desktopConfigPath = path.join(configDir, 'desktop-relay.json');
const runtimeRelayDir = path.join(configDir, 'desktop-relay-runtime');
const supportedLanguages = ['system', 'zh', 'zh-Hant', 'en', 'ja', 'ko', 'es', 'fr', 'de'];
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
let relayEventData = null;
let relayOutputBuffer = '';
let relayLogClientIds = new Set();

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

function firstExistingFile(candidates) {
  for (const candidate of candidates) {
    const filePath = cleanExecutablePath(candidate);
    if (filePath && fs.existsSync(filePath) && fs.statSync(filePath).isFile()) return filePath;
  }
  return null;
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

function loadDesktopConfig() {
  const config = readJson(desktopConfigPath);
  return {
    port: Number.isInteger(config.port) ? config.port : 3001,
    workspace: typeof config.workspace === 'string' && config.workspace.trim() ? config.workspace : os.homedir(),
    languageMode: config.languageMode === 'manual' ? 'manual' : 'follow-phone',
    language: supportedLanguages.includes(config.language) ? config.language : 'system',
    guideSeen: config.guideSeen === true,
  };
}

function saveDesktopConfig(partial) {
  writeJson(desktopConfigPath, { ...loadDesktopConfig(), ...partial });
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
  return workspace;
}

function relayDir() {
  if (isDev) return sourceRelayDir;
  const sourcePackage = path.join(sourceRelayDir, 'package.json');
  if (!fs.existsSync(sourcePackage)) throw new Error(`Packaged relay resources not found: ${sourceRelayDir}`);
  if (
    fs.existsSync(path.join(sourceRelayDir, 'dist', 'server.js')) &&
    fs.existsSync(path.join(sourceRelayDir, 'node_modules', 'express')) &&
    fs.existsSync(path.join(sourceRelayDir, 'node_modules', 'ws'))
  ) {
    return sourceRelayDir;
  }

  const runtimePackage = path.join(runtimeRelayDir, 'package.json');
  if (!fs.existsSync(runtimePackage)) {
    fs.mkdirSync(runtimeRelayDir, { recursive: true });
    fs.cpSync(sourceRelayDir, runtimeRelayDir, {
      recursive: true,
      force: true,
    });
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
  const port = validatePort(input);
  const currentPort = loadDesktopConfig().port;
  if (relayRunning && port === currentPort) return Promise.resolve(true);

  return new Promise((resolve) => {
    const server = net.createServer();
    server.once('error', () => resolve(false));
    server.once('listening', () => {
      server.close(() => resolve(true));
    });
    server.listen(port, '0.0.0.0');
  });
}

function normalizeLanguage(value) {
  return supportedLanguages.includes(value) ? value : 'system';
}

function systemLanguage() {
  const locale = app.getLocale().toLowerCase();
  if (locale.startsWith('zh-tw') || locale.startsWith('zh-hk') || locale.includes('hant')) return 'zh-Hant';
  if (locale.startsWith('zh')) return 'zh';
  if (locale.startsWith('ja')) return 'ja';
  if (locale.startsWith('ko')) return 'ko';
  if (locale.startsWith('es')) return 'es';
  if (locale.startsWith('fr')) return 'fr';
  if (locale.startsWith('de')) return 'de';
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

async function appState() {
  const config = loadDesktopConfig();
  const apiKey = loadApiKey();
  const details = connectionDetails(config.port, apiKey);
  const portAvailable = await checkPortAvailable(config.port).catch(() => false);
  const isRelayReady = relayReady();
  return {
    platform: process.platform,
    relayDir: relayDir(),
    configPath,
    desktopConfigPath,
    runtimeRelayDir,
    port: config.port,
    workspace: config.workspace,
    apiKey,
    relayRunning,
    relayReady: isRelayReady,
    installRunning: Boolean(installProcess),
    portAvailable,
    guideVisible: !config.guideSeen,
    health: mergeRelayEventHealth(lastHealth),
    languageMode: config.languageMode,
    language: config.language,
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

async function startRelay(input) {
  if (relayProcess) return;
  const port = validatePort(input?.port);
  const workspace = validateWorkspace(input?.workspace);
  const portAvailable = await checkPortAvailable(port);
  if (!portAvailable) throw new Error('Port is in use. Choose another port.');
  saveDesktopConfig({ port, workspace });
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
    ...nodeScript.env,
    PORT: String(port),
    API_KEY: loadApiKey(),
    CODEX_CWD: workspace,
    EASYCODEX_NO_TERMINAL_QR: '1',
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
  mainWindow = new BrowserWindow({
    width: 1100,
    height: 760,
    minWidth: 940,
    minHeight: 660,
    show: !isSmokeTest,
    frame: false,
    icon: windowIconPath,
    title: 'EasyCodex Relay',
    backgroundColor: '#f7f4ec',
    webPreferences: {
      preload: path.join(__dirname, 'preload.cjs'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false,
    },
  });
  mainWindow.on('close', async (event) => {
    if (allowQuit || isSmokeTest) return;
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
  mainWindow.on('closed', () => { mainWindow = null; });
  await mainWindow.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  startHealthPolling();
  if (isSmokeTest) setTimeout(() => app.quit(), 1200);
}

function createTray() {
  const icon = nativeImage.createFromPath(iconPath).resize({ width: 16, height: 16 });
  tray = new Tray(icon);
  tray.setToolTip('EasyCodex Relay');
  tray.setContextMenu(Menu.buildFromTemplate([
    { label: 'Open EasyCodex Relay', click: showMainWindow },
    { type: 'separator' },
    {
      label: 'Quit',
      click: () => {
        allowQuit = true;
        app.quit();
      },
    },
  ]));
  tray.on('double-click', showMainWindow);
}

function showMainWindow() {
  if (!mainWindow) {
    createWindow();
    return;
  }
  mainWindow.show();
  mainWindow.focus();
}

app.whenReady().then(() => {
  createTray();
  createWindow();
});

app.on('second-instance', () => {
  showMainWindow();
});

app.on('window-all-closed', () => {
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
ipcMain.handle('start-relay', async (_event, input) => {
  await startRelay(input || {});
  return appState();
});
ipcMain.handle('stop-relay', async () => {
  await stopRelay();
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
    if (!(await checkPortAvailable(port))) throw new Error('Port is in use. Choose another port.');
    next.port = port;
  }
  if (Object.prototype.hasOwnProperty.call(input || {}, 'workspace')) next.workspace = validateWorkspace(input.workspace);
  if (input?.languageMode === 'manual' || input?.languageMode === 'follow-phone') next.languageMode = input.languageMode;
  if (supportedLanguages.includes(input?.language)) next.language = input.language;
  saveDesktopConfig(next);
  return appState();
});
ipcMain.handle('preview-port', async (_event, input) => {
  const port = validatePort(input?.port);
  const apiKey = loadApiKey();
  const details = connectionDetails(port, apiKey);
  return {
    port,
    portAvailable: await checkPortAvailable(port),
    qrDataUrl: await QRCode.toDataURL(details.connectUrl, {
      margin: 1,
      width: 360,
      color: { dark: '#1d251f', light: '#ffffff' },
    }),
    ...details,
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
ipcMain.handle('copy-text', (_event, text) => {
  clipboard.writeText(String(text || ''));
});
ipcMain.handle('open-external', (_event, url) => {
  shell.openExternal(String(url || ''));
});
