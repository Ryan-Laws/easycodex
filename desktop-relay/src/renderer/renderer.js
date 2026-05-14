const languageOptions = [
  ['system', 'System / 跟随手机'],
  ['zh', '简体中文'],
  ['zh-Hant', '繁體中文'],
  ['en', 'English'],
  ['ja', '日本語'],
  ['ko', '한국어'],
  ['es', 'Español'],
  ['fr', 'Français'],
  ['de', 'Deutsch'],
];

const dictionaries = {
  en: {
    eyebrow: 'Desktop Relay',
    title: 'EasyCodex Relay',
    subtitle: 'Start the local relay so your phone can control Codex on this computer.',
    launch: 'Launch',
    language: 'Language',
    port: 'Port',
    workspace: 'Default workspace',
    codexPath: 'Codex executable',
    codexFound: (value) => `Codex detected${value ? `: ${value}` : ''}`,
    codexMissing: 'Codex not found. Choose the Codex executable.',
    relayUrl: 'Relay URL',
    apiKey: 'API Key',
    copy: 'Copy',
    refresh: 'Refresh',
    installBuild: 'Install/build',
    startRelay: 'Start relay',
    stop: 'Stop',
    quickConnect: 'Quick connect',
    qrHint: 'Scan with your phone camera to import URL and key.',
    copyDeepLink: 'Copy deep link',
    logs: 'Logs',
    clear: 'Clear',
    windowTitle: 'EasyCodex Relay',
    firstUseTitle: 'First time?',
    firstUseBody: 'Run Install/build once before starting the relay.',
    portAvailable: 'Port is available',
    portBusy: 'Port is in use',
    invalidPort: 'Invalid port',
    offline: 'Offline',
    starting: 'Starting',
    installing: 'Installing relay',
    launching: 'Starting relay',
    stopping: 'Stopping relay',
    online: 'Online',
    waiting: 'Waiting for relay',
    healthPending: 'Health check pending',
    ready: 'Ready for phone connection',
    phoneConnected: (count) => `Phone connected (${count})`,
    checkUpdate: 'Check',
    quickUpdate: 'Update',
    updateChecking: 'Checking for updates',
    updateAvailable: 'Update available',
    updateReady: (current, latest) => `Version ${latest} is available. Current version: ${current}.`,
    updateCurrent: (current) => `EasyCodex Relay is up to date (${current}).`,
    updateFailed: (message) => `Update check failed: ${message}`,
    updating: 'Updating',
  },
  zh: {
    eyebrow: '电脑端中继',
    title: 'EasyCodex 中继',
    subtitle: '启动本机中继，让手机连接这台电脑上的 Codex。',
    launch: '启动',
    language: '语言',
    port: '端口',
    workspace: '默认工作区',
    codexPath: 'Codex 可执行文件',
    codexFound: (value) => `已检测到 Codex${value ? `：${value}` : ''}`,
    codexMissing: '未检测到 Codex，请选择 Codex 可执行文件。',
    relayUrl: '中继地址',
    apiKey: 'API Key',
    copy: '复制',
    refresh: '刷新',
    installBuild: '安装/构建',
    startRelay: '启动中继',
    stop: '停止',
    quickConnect: '快速连接',
    qrHint: '用手机相机扫码，自动导入地址和密钥。',
    copyDeepLink: '复制深链',
    logs: '日志',
    clear: '清空',
    windowTitle: 'EasyCodex 中继',
    firstUseTitle: '第一次使用？',
    firstUseBody: '请先点击“安装/构建”，准备好中继依赖后再启动。',
    portAvailable: '端口可用',
    portBusy: '端口已被占用',
    invalidPort: '端口无效',
    offline: '未启动',
    starting: '启动中',
    installing: '正在安装/构建',
    launching: '正在启动中继',
    stopping: '正在停止中继',
    online: '在线',
    waiting: '等待中继',
    healthPending: '等待健康检查',
    ready: '等待手机连接',
    phoneConnected: (count) => `手机已连接（${count}）`,
    checkUpdate: '检查',
    quickUpdate: '更新',
    updateChecking: '正在检测更新',
    updateAvailable: '发现新版本',
    updateReady: (current, latest) => `可更新到 ${latest}，当前版本 ${current}。`,
    updateCurrent: (current) => `EasyCodex 中继已是最新版本（${current}）。`,
    updateFailed: (message) => `检测更新失败：${message}`,
    updating: '正在更新',
  },
  'zh-Hant': {
    eyebrow: '電腦端中繼',
    title: 'EasyCodex 中繼',
    subtitle: '啟動本機中繼，讓手機連接這台電腦上的 Codex。',
    launch: '啟動',
    language: '語言',
    port: '連接埠',
    workspace: '預設工作區',
    codexPath: 'Codex 可執行檔',
    codexFound: (value) => `已偵測到 Codex${value ? `：${value}` : ''}`,
    codexMissing: '未偵測到 Codex，請選擇 Codex 可執行檔。',
    relayUrl: '中繼位址',
    apiKey: 'API Key',
    copy: '複製',
    refresh: '刷新',
    installBuild: '安裝/建置',
    startRelay: '啟動中繼',
    stop: '停止',
    quickConnect: '快速連線',
    qrHint: '用手機相機掃碼，自動匯入位址和密鑰。',
    copyDeepLink: '複製深層連結',
    logs: '日誌',
    clear: '清空',
    windowTitle: 'EasyCodex 中繼',
    firstUseTitle: '第一次使用？',
    firstUseBody: '請先點擊「安裝/建置」，準備好中繼依賴後再啟動。',
    portAvailable: '連接埠可用',
    portBusy: '連接埠已被占用',
    invalidPort: '連接埠無效',
    offline: '未啟動',
    starting: '啟動中',
    installing: '正在安裝/建置',
    launching: '正在啟動中繼',
    stopping: '正在停止中繼',
    online: '線上',
    waiting: '等待中繼',
    healthPending: '等待健康檢查',
    ready: '等待手機連線',
    phoneConnected: (count) => `手機已連線（${count}）`,
    checkUpdate: '檢查',
    quickUpdate: '更新',
    updateChecking: '正在檢測更新',
    updateAvailable: '發現新版本',
    updateReady: (current, latest) => `可更新到 ${latest}，目前版本 ${current}。`,
    updateCurrent: (current) => `EasyCodex 中繼已是最新版本（${current}）。`,
    updateFailed: (message) => `檢測更新失敗：${message}`,
    updating: '正在更新',
  },
  ja: {
    eyebrow: 'デスクトップリレー',
    title: 'EasyCodex リレー',
    subtitle: 'ローカルリレーを起動して、このコンピューターの Codex にスマートフォンを接続します。',
    launch: '起動',
    language: '言語',
    port: 'ポート',
    workspace: '既定の作業フォルダー',
    relayUrl: 'リレー URL',
    apiKey: 'API Key',
    copy: 'コピー',
    refresh: '更新',
    installBuild: 'インストール/ビルド',
    startRelay: 'リレーを起動',
    stop: '停止',
    quickConnect: 'クイック接続',
    qrHint: 'スマートフォンのカメラでスキャンして URL とキーを取り込みます。',
    copyDeepLink: 'ディープリンクをコピー',
    logs: 'ログ',
    clear: '消去',
    windowTitle: 'EasyCodex リレー',
    firstUseTitle: '初めてですか?',
    firstUseBody: 'リレーを起動する前に、まずインストール/ビルドを実行してください。',
    portAvailable: 'ポートは利用可能です',
    portBusy: 'ポートは使用中です',
    invalidPort: '無効なポート',
    offline: 'オフライン',
    starting: '起動中',
    online: 'オンライン',
    waiting: 'リレー待機中',
    healthPending: 'ヘルスチェック待機中',
    ready: 'スマートフォン接続待機中',
    phoneConnected: (count) => `スマートフォン接続済み (${count})`,
  },
  ko: {
    eyebrow: '데스크톱 릴레이',
    title: 'EasyCodex 릴레이',
    subtitle: '로컬 릴레이를 시작해 휴대폰이 이 컴퓨터의 Codex에 연결되도록 합니다.',
    launch: '시작',
    language: '언어',
    port: '포트',
    workspace: '기본 작업 폴더',
    relayUrl: '릴레이 URL',
    apiKey: 'API Key',
    copy: '복사',
    refresh: '새로 고침',
    installBuild: '설치/빌드',
    startRelay: '릴레이 시작',
    stop: '중지',
    quickConnect: '빠른 연결',
    qrHint: '휴대폰 카메라로 스캔해 URL과 키를 가져옵니다.',
    copyDeepLink: '딥 링크 복사',
    logs: '로그',
    clear: '지우기',
    windowTitle: 'EasyCodex 릴레이',
    firstUseTitle: '처음 사용하시나요?',
    firstUseBody: '릴레이를 시작하기 전에 설치/빌드를 한 번 실행하세요.',
    portAvailable: '포트를 사용할 수 있습니다',
    portBusy: '포트가 사용 중입니다',
    invalidPort: '잘못된 포트',
    offline: '오프라인',
    starting: '시작 중',
    online: '온라인',
    waiting: '릴레이 대기 중',
    healthPending: '상태 확인 대기 중',
    ready: '휴대폰 연결 대기 중',
    phoneConnected: (count) => `휴대폰 연결됨 (${count})`,
  },
  es: {
    eyebrow: 'Relay de escritorio',
    title: 'EasyCodex Relay',
    subtitle: 'Inicia el relay local para que tu teléfono controle Codex en este ordenador.',
    launch: 'Inicio',
    language: 'Idioma',
    port: 'Puerto',
    workspace: 'Espacio de trabajo predeterminado',
    relayUrl: 'URL del relay',
    apiKey: 'API Key',
    copy: 'Copiar',
    refresh: 'Actualizar',
    installBuild: 'Instalar/compilar',
    startRelay: 'Iniciar relay',
    stop: 'Detener',
    quickConnect: 'Conexión rápida',
    qrHint: 'Escanea con la cámara del teléfono para importar la URL y la clave.',
    copyDeepLink: 'Copiar enlace profundo',
    logs: 'Registros',
    clear: 'Limpiar',
    windowTitle: 'EasyCodex Relay',
    firstUseTitle: '¿Primera vez?',
    firstUseBody: 'Ejecuta Instalar/compilar una vez antes de iniciar el relay.',
    portAvailable: 'El puerto está disponible',
    portBusy: 'El puerto está en uso',
    invalidPort: 'Puerto no válido',
    offline: 'Sin conexión',
    starting: 'Iniciando',
    online: 'En línea',
    waiting: 'Esperando relay',
    healthPending: 'Esperando comprobación',
    ready: 'Listo para el teléfono',
    phoneConnected: (count) => `Teléfono conectado (${count})`,
  },
  fr: {
    eyebrow: 'Relais bureau',
    title: 'Relais EasyCodex',
    subtitle: 'Démarrez le relais local pour connecter votre téléphone au Codex de cet ordinateur.',
    launch: 'Lancement',
    language: 'Langue',
    port: 'Port',
    workspace: 'Espace de travail par défaut',
    relayUrl: 'URL du relais',
    apiKey: 'API Key',
    copy: 'Copier',
    refresh: 'Actualiser',
    installBuild: 'Installer/compiler',
    startRelay: 'Démarrer le relais',
    stop: 'Arrêter',
    quickConnect: 'Connexion rapide',
    qrHint: 'Scannez avec la caméra du téléphone pour importer URL et clé.',
    copyDeepLink: 'Copier le lien profond',
    logs: 'Journaux',
    clear: 'Effacer',
    windowTitle: 'Relais EasyCodex',
    firstUseTitle: 'Première utilisation ?',
    firstUseBody: 'Exécutez Installer/compiler une fois avant de démarrer le relais.',
    portAvailable: 'Le port est disponible',
    portBusy: 'Le port est utilisé',
    invalidPort: 'Port invalide',
    offline: 'Hors ligne',
    starting: 'Démarrage',
    online: 'En ligne',
    waiting: 'En attente du relais',
    healthPending: 'Contrôle de santé en attente',
    ready: 'Prêt pour le téléphone',
    phoneConnected: (count) => `Téléphone connecté (${count})`,
  },
  de: {
    eyebrow: 'Desktop-Relay',
    title: 'EasyCodex Relay',
    subtitle: 'Starte das lokale Relay, damit dein Telefon Codex auf diesem Computer steuern kann.',
    launch: 'Start',
    language: 'Sprache',
    port: 'Port',
    workspace: 'Standard-Arbeitsordner',
    relayUrl: 'Relay-URL',
    apiKey: 'API Key',
    copy: 'Kopieren',
    refresh: 'Aktualisieren',
    installBuild: 'Installieren/builden',
    startRelay: 'Relay starten',
    stop: 'Stoppen',
    quickConnect: 'Schnellverbindung',
    qrHint: 'Mit der Telefonkamera scannen, um URL und Key zu importieren.',
    copyDeepLink: 'Deep Link kopieren',
    logs: 'Logs',
    clear: 'Leeren',
    windowTitle: 'EasyCodex Relay',
    firstUseTitle: 'Erste Verwendung?',
    firstUseBody: 'Führe Installieren/builden einmal aus, bevor du das Relay startest.',
    portAvailable: 'Port ist verfügbar',
    portBusy: 'Port wird bereits verwendet',
    invalidPort: 'Ungültiger Port',
    offline: 'Offline',
    starting: 'Startet',
    online: 'Online',
    waiting: 'Warte auf Relay',
    healthPending: 'Warte auf Health Check',
    ready: 'Bereit für Telefonverbindung',
    phoneConnected: (count) => `Telefon verbunden (${count})`,
  },
};

const elements = {
  statusCard: document.getElementById('statusCard'),
  statusText: document.getElementById('statusText'),
  healthText: document.getElementById('healthText'),
  firstUseGuide: document.getElementById('firstUseGuide'),
  updateBox: document.getElementById('updateBox'),
  updateTitle: document.getElementById('updateTitle'),
  updateText: document.getElementById('updateText'),
  relayPath: document.getElementById('relayPath'),
  languageSelect: document.getElementById('languageSelect'),
  portInput: document.getElementById('portInput'),
  portStatus: document.getElementById('portStatus'),
  workspaceInput: document.getElementById('workspaceInput'),
  codexPathInput: document.getElementById('codexPathInput'),
  codexStatus: document.getElementById('codexStatus'),
  relayUrlInput: document.getElementById('relayUrlInput'),
  apiKeyInput: document.getElementById('apiKeyInput'),
  qrImage: document.getElementById('qrImage'),
  logOutput: document.getElementById('logOutput'),
  browseButton: document.getElementById('browseButton'),
  browseCodexButton: document.getElementById('browseCodexButton'),
  copyConnectionButton: document.getElementById('copyConnectionButton'),
  copyKeyButton: document.getElementById('copyKeyButton'),
  refreshKeyButton: document.getElementById('refreshKeyButton'),
  installButton: document.getElementById('installButton'),
  checkUpdateButton: document.getElementById('checkUpdateButton'),
  applyUpdateButton: document.getElementById('applyUpdateButton'),
  startButton: document.getElementById('startButton'),
  stopButton: document.getElementById('stopButton'),
  copyDeepLinkButton: document.getElementById('copyDeepLinkButton'),
  clearLogsButton: document.getElementById('clearLogsButton'),
  windowMinimizeButton: document.getElementById('windowMinimizeButton'),
  windowCloseButton: document.getElementById('windowCloseButton'),
};

let currentState = null;
let currentLanguage = 'en';
let portPreviewTimer = null;
let pendingAction = null;

function t(key, ...args) {
  const dict = dictionaries[currentLanguage] || dictionaries.en;
  const value = dict[key] ?? dictionaries.en[key] ?? key;
  return typeof value === 'function' ? value(...args) : value;
}

function applyLanguage(language) {
  currentLanguage = dictionaries[language] ? language : 'en';
  document.documentElement.lang = currentLanguage;
  document.querySelectorAll('[data-i18n]').forEach((node) => {
    node.textContent = t(node.dataset.i18n);
  });
}

function appendLog(line) {
  const text = String(line || '').trimEnd();
  if (!text) return;
  elements.logOutput.textContent += `${text}\n`;
  elements.logOutput.scrollTop = elements.logOutput.scrollHeight;
}

function connectionText(state) {
  return `Relay URL: ${state.relayUrl}\nAPI Key: ${state.apiKey}\nDeep link: ${state.deepLink}`;
}

function setPortStatus(kind, message) {
  elements.portStatus.dataset.state = kind;
  elements.portStatus.textContent = message;
}

function setCodexStatus(state) {
  const codex = state?.codex;
  if (codex?.installed) {
    elements.codexStatus.dataset.state = 'ok';
    elements.codexStatus.textContent = t('codexFound', codex.version || codex.path);
    return;
  }
  elements.codexStatus.dataset.state = 'error';
  elements.codexStatus.textContent = codex?.error || t('codexMissing');
}

function setControlsBusy(isBusy) {
  elements.installButton.disabled = isBusy || currentState?.installRunning || currentState?.relayRunning;
  elements.checkUpdateButton.disabled = isBusy || currentState?.update?.checking || currentState?.update?.applying;
  elements.applyUpdateButton.disabled = isBusy || currentState?.update?.checking || currentState?.update?.applying || !currentState?.update?.info?.updateAvailable;
  elements.startButton.disabled = isBusy || currentState?.installRunning || currentState?.relayRunning || !currentState?.relayReady || !currentState?.portAvailable || !currentState?.codex?.installed;
  elements.stopButton.disabled = isBusy || !currentState?.relayRunning;
  elements.refreshKeyButton.disabled = isBusy || currentState?.relayRunning;
  elements.portInput.disabled = isBusy || currentState?.relayRunning;
  elements.browseButton.disabled = isBusy;
  elements.codexPathInput.disabled = isBusy || currentState?.relayRunning;
  elements.browseCodexButton.disabled = isBusy || currentState?.relayRunning;
}

function renderUpdate(update) {
  const info = update?.info;
  const isActive = update?.checking || update?.applying;
  const shouldShow = Boolean(isActive || update?.error || info);
  elements.updateBox.hidden = !shouldShow;
  if (!shouldShow) return;

  if (update?.applying) {
    elements.updateTitle.textContent = t('updating');
    elements.updateText.textContent = info?.latestVersion ? t('updateReady', info.currentVersion, info.latestVersion) : '';
  } else if (update?.checking) {
    elements.updateTitle.textContent = t('updateChecking');
    elements.updateText.textContent = '';
  } else if (update?.error) {
    elements.updateTitle.textContent = t('updateChecking');
    elements.updateText.textContent = t('updateFailed', update.error);
  } else if (info?.updateAvailable) {
    elements.updateTitle.textContent = t('updateAvailable');
    elements.updateText.textContent = t('updateReady', info.currentVersion, info.latestVersion);
  } else {
    elements.updateTitle.textContent = t('checkUpdate');
    elements.updateText.textContent = t('updateCurrent', info?.currentVersion || '');
  }
}

function renderPendingStatus() {
  if (!pendingAction) return;
  elements.statusCard.dataset.state = 'starting';
  elements.statusCard.dataset.busy = 'true';
  elements.statusText.textContent = t(pendingAction);
  elements.healthText.textContent = t('healthPending');
  setControlsBusy(true);
}

function renderLanguageOptions(state) {
  if (elements.languageSelect.childElementCount === 0) {
    for (const [value, label] of languageOptions) {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = label;
      elements.languageSelect.appendChild(option);
    }
  }
  elements.languageSelect.value = state.languageMode === 'follow-phone' ? 'system' : state.language;
}

function renderState(state) {
  currentState = state;
  applyLanguage(state.effectiveLanguage);
  renderLanguageOptions(state);
  elements.relayPath.textContent = state.relayDir;
  elements.portInput.value = state.port;
  elements.workspaceInput.value = state.workspace;
  elements.codexPathInput.value = state.codexPath || state.codex?.path || '';
  elements.relayUrlInput.value = state.relayUrl;
  elements.apiKeyInput.value = state.apiKey;
  elements.qrImage.src = state.qrDataUrl;
  elements.firstUseGuide.hidden = !state.guideVisible;
  renderUpdate(state.update);
  elements.statusCard.dataset.busy = 'false';
  elements.statusCard.dataset.state = state.relayRunning ? 'starting' : 'offline';
  elements.statusText.textContent = state.relayRunning ? t('starting') : t('offline');
  setControlsBusy(false);
  if (state.portAvailable) setPortStatus('ok', t('portAvailable'));
  else setPortStatus('error', t('portBusy'));
  setCodexStatus(state);
  renderHealth(state.health);
  renderPendingStatus();
}

function renderHealth(health) {
  if (pendingAction) {
    renderPendingStatus();
    return;
  }
  if (!currentState?.relayRunning) {
    elements.statusCard.dataset.busy = 'false';
    elements.statusCard.dataset.state = 'offline';
    elements.statusText.textContent = t('offline');
    elements.healthText.textContent = t('waiting');
    return;
  }
  if (!health?.online) {
    elements.statusCard.dataset.busy = currentState?.relayRunning ? 'true' : 'false';
    elements.statusCard.dataset.state = 'starting';
    elements.statusText.textContent = t('starting');
    elements.healthText.textContent = t('healthPending');
    return;
  }
  const clients = health.data?.connectedClients ?? 0;
  elements.statusCard.dataset.busy = 'false';
  elements.statusCard.dataset.state = 'online';
  elements.statusText.textContent = t('online');
  elements.healthText.textContent = clients > 0 ? t('phoneConnected', clients) : t('ready');
}

async function runAction(button, action, pendingKey) {
  button.disabled = true;
  pendingAction = pendingKey;
  renderPendingStatus();
  let nextState = null;
  try {
    nextState = await action();
  } catch (error) {
    appendLog(`Error: ${error.message || error}`);
  } finally {
    pendingAction = null;
    if (nextState) renderState(nextState);
    else {
      if (currentState) renderState(currentState);
      else button.disabled = false;
    }
  }
}

elements.installButton.addEventListener('click', () => {
  runAction(elements.installButton, () => window.easyCodexRelay.installAndBuild(), 'installing');
});

elements.checkUpdateButton.addEventListener('click', () => {
  runAction(elements.checkUpdateButton, () => window.easyCodexRelay.checkUpdate(), 'updateChecking');
});

elements.applyUpdateButton.addEventListener('click', () => {
  runAction(elements.applyUpdateButton, () => window.easyCodexRelay.applyUpdate(), 'updating');
});

elements.startButton.addEventListener('click', () => {
  runAction(elements.startButton, () => window.easyCodexRelay.startRelay({
    port: elements.portInput.value,
    workspace: elements.workspaceInput.value,
    codexPath: elements.codexPathInput.value,
  }), 'launching');
});

elements.stopButton.addEventListener('click', () => {
  runAction(elements.stopButton, () => window.easyCodexRelay.stopRelay(), 'stopping');
});

elements.browseButton.addEventListener('click', async () => {
  const workspace = await window.easyCodexRelay.browseWorkspace();
  if (workspace) elements.workspaceInput.value = workspace;
});

elements.browseCodexButton.addEventListener('click', () => {
  runAction(elements.browseCodexButton, () => window.easyCodexRelay.browseCodex());
});

elements.codexPathInput.addEventListener('change', () => {
  runAction(elements.browseCodexButton, () => window.easyCodexRelay.saveConfig({
    codexPath: elements.codexPathInput.value,
  }));
});

elements.portInput.addEventListener('input', () => {
  clearTimeout(portPreviewTimer);
  const rawPort = elements.portInput.value.trim();
  const port = Number(rawPort);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    setPortStatus('error', t('invalidPort'));
    elements.startButton.disabled = true;
    return;
  }

  portPreviewTimer = setTimeout(async () => {
    try {
      const preview = await window.easyCodexRelay.previewPort({ port });
      elements.relayUrlInput.value = preview.relayUrl;
      elements.qrImage.src = preview.qrDataUrl;
      currentState = { ...currentState, ...preview };
      if (!preview.portAvailable) {
        setPortStatus('error', t('portBusy'));
        elements.startButton.disabled = true;
        return;
      }
      setPortStatus('ok', t('portAvailable'));
      const state = await window.easyCodexRelay.saveConfig({ port });
      renderState(state);
    } catch (error) {
      setPortStatus('error', error.message || t('invalidPort'));
      elements.startButton.disabled = true;
    }
  }, 250);
});

elements.copyConnectionButton.addEventListener('click', () => {
  if (currentState) window.easyCodexRelay.copyText(connectionText(currentState));
});

elements.copyKeyButton.addEventListener('click', () => {
  if (currentState) window.easyCodexRelay.copyText(currentState.apiKey);
});

elements.copyDeepLinkButton.addEventListener('click', () => {
  if (currentState) window.easyCodexRelay.copyText(currentState.deepLink);
});

elements.refreshKeyButton.addEventListener('click', () => {
  runAction(elements.refreshKeyButton, () => window.easyCodexRelay.refreshApiKey());
});

elements.languageSelect.addEventListener('change', () => {
  const language = elements.languageSelect.value;
  const languageMode = language === 'system' ? 'follow-phone' : 'manual';
  runAction(elements.languageSelect, () => window.easyCodexRelay.saveConfig({ language, languageMode }));
});

elements.clearLogsButton.addEventListener('click', () => {
  elements.logOutput.textContent = '';
});

elements.windowMinimizeButton.addEventListener('click', () => {
  window.easyCodexRelay.minimizeWindow();
});

elements.windowCloseButton.addEventListener('click', () => {
  window.easyCodexRelay.closeWindow();
});

window.easyCodexRelay.onState(renderState);
window.easyCodexRelay.onHealth(renderHealth);
window.easyCodexRelay.onLog(appendLog);

window.easyCodexRelay.getState()
  .then(renderState)
  .catch((error) => appendLog(`Error: ${error.message || error}`));
