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
    setupEyebrow: 'Setup',
    setupTitle: 'Relay setup and logs',
    setupSubtitle: 'Start the relay and connect your phone first. The task workbench is below for later use.',
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
    firstUseBody: 'Use these steps to get your phone connected.',
    firstUseStepInstall: 'Click Install/build once to prepare the relay dependencies.',
    firstUseStepStart: 'Click Start relay and wait until the status is online or ready for phone connection.',
    firstUseStepConnect: 'Scan the QR code with your phone camera to import the relay URL and API Key, then create a task on your phone.',
    portAvailable: 'Port is available',
    portBusy: 'Port is in use',
    portRelayBusy: 'Existing EasyCodex relay will be restarted',
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
    updateChannel: 'Update channel',
    stableChannel: 'Stable',
    betaChannel: 'Beta',
    updateChecking: 'Checking for updates',
    updateAvailable: 'Update available',
    updateNotice: 'Update notice',
    updateReady: (current, latest) => `Version ${latest} is available. Current version: ${current}.`,
    updateCurrent: (current) => `EasyCodex Relay is up to date (${current}).`,
    updateFailed: (message) => `Update check failed: ${message}`,
    updating: 'Updating',
  },
  zh: {
    eyebrow: '电脑端中继',
    title: 'EasyCodex 中继',
    subtitle: '启动本机中继，让手机连接这台电脑上的 Codex。',
    setupEyebrow: '设置',
    setupTitle: '中继设置和日志',
    setupSubtitle: '先启动中继并扫码连接；任务台在下面，连接后再使用。',
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
    firstUseBody: '按这 3 步把手机连上来。',
    firstUseStepInstall: '第一次使用先点“安装/构建”，完成依赖准备。',
    firstUseStepStart: '点“启动中继”，等右上角状态变成在线或等待手机连接。',
    firstUseStepConnect: '用手机相机扫右侧二维码导入地址和 API Key，然后在手机里创建任务。',
    portAvailable: '端口可用',
    portBusy: '端口已被占用',
    portRelayBusy: '已存在 EasyCodex 中继，启动时会自动重启',
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
    updateChannel: '更新通道',
    stableChannel: '正式版',
    betaChannel: 'Beta 版',
    updateChecking: '正在检测更新',
    updateAvailable: '发现新版本',
    updateNotice: '更新提醒',
    updateReady: (current, latest) => `可更新到 ${latest}，当前版本 ${current}。`,
    updateCurrent: (current) => `EasyCodex 中继已是最新版本（${current}）。`,
    updateFailed: (message) => `检测更新失败：${message}`,
    updating: '正在更新',
  },
  'zh-Hant': {
    eyebrow: '電腦端中繼',
    title: 'EasyCodex 中繼',
    subtitle: '啟動本機中繼，讓手機連接這台電腦上的 Codex。',
    setupEyebrow: '設定',
    setupTitle: '中繼設定和日誌',
    setupSubtitle: '先啟動中繼並掃碼連線；任務台在下方，連線後再使用。',
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
    firstUseBody: '照這 3 步把手機連上來。',
    firstUseStepInstall: '第一次使用先點「安裝/建置」，完成依賴準備。',
    firstUseStepStart: '點「啟動中繼」，等右上角狀態變成線上或等待手機連線。',
    firstUseStepConnect: '用手機相機掃右側 QR Code 匯入位址和 API Key，然後在手機裡建立任務。',
    portAvailable: '連接埠可用',
    portBusy: '連接埠已被占用',
    portRelayBusy: '已存在 EasyCodex 中繼，啟動時會自動重啟',
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
    updateChannel: '更新通道',
    stableChannel: '正式版',
    betaChannel: 'Beta 版',
    updateChecking: '正在檢測更新',
    updateAvailable: '發現新版本',
    updateNotice: '更新提醒',
    updateReady: (current, latest) => `可更新到 ${latest}，目前版本 ${current}。`,
    updateCurrent: (current) => `EasyCodex 中繼已是最新版本（${current}）。`,
    updateFailed: (message) => `檢測更新失敗：${message}`,
    updating: '正在更新',
  },
  ja: {
    eyebrow: 'デスクトップリレー',
    title: 'EasyCodex リレー',
    subtitle: 'ローカルリレーを起動して、このコンピューターの Codex にスマートフォンを接続します。',
    setupEyebrow: 'セットアップ',
    setupTitle: 'リレー設定とログ',
    setupSubtitle: '先にリレーを起動してスマートフォンを接続します。タスクワークベンチは下にあります。',
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
    firstUseBody: '次の 3 ステップでスマートフォンを接続します。',
    firstUseStepInstall: '初回はインストール/ビルドを実行してリレー依存関係を準備します。',
    firstUseStepStart: 'リレーを起動し、状態がオンラインまたは接続待機になるまで待ちます。',
    firstUseStepConnect: 'スマートフォンのカメラで QR コードを読み取り、URL と API Key を取り込んでからタスクを作成します。',
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
    setupEyebrow: '설정',
    setupTitle: '릴레이 설정 및 로그',
    setupSubtitle: '먼저 릴레이를 시작하고 휴대폰을 연결하세요. 작업대는 아래에 있습니다.',
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
    firstUseBody: '아래 3단계로 휴대폰을 연결하세요.',
    firstUseStepInstall: '처음에는 설치/빌드를 한 번 실행해 릴레이 의존성을 준비합니다.',
    firstUseStepStart: '릴레이 시작을 누르고 상태가 온라인 또는 휴대폰 연결 대기가 될 때까지 기다립니다.',
    firstUseStepConnect: '휴대폰 카메라로 QR 코드를 스캔해 URL과 API Key를 가져온 뒤 휴대폰에서 작업을 만듭니다.',
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
    setupEyebrow: 'Configuración',
    setupTitle: 'Configuración y registros del relay',
    setupSubtitle: 'Primero inicia el relay y conecta el teléfono. El panel de tareas queda abajo.',
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
    firstUseBody: 'Sigue estos 3 pasos para conectar el teléfono.',
    firstUseStepInstall: 'Haz clic en Instalar/compilar una vez para preparar las dependencias del relay.',
    firstUseStepStart: 'Haz clic en Iniciar relay y espera hasta que el estado esté en línea o listo para el teléfono.',
    firstUseStepConnect: 'Escanea el código QR con la cámara del teléfono para importar la URL y la API Key, y crea una tarea en el teléfono.',
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
    setupEyebrow: 'Configuration',
    setupTitle: 'Configuration du relais et journaux',
    setupSubtitle: 'Démarrez le relais et connectez le téléphone en premier. Le plan de travail est en dessous.',
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
    firstUseBody: 'Suivez ces 3 étapes pour connecter le téléphone.',
    firstUseStepInstall: 'Cliquez sur Installer/compiler une fois pour préparer les dépendances du relais.',
    firstUseStepStart: 'Cliquez sur Démarrer le relais et attendez que l’état soit en ligne ou prêt pour le téléphone.',
    firstUseStepConnect: 'Scannez le QR code avec la caméra du téléphone pour importer l’URL et l’API Key, puis créez une tâche sur le téléphone.',
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
    setupEyebrow: 'Einrichtung',
    setupTitle: 'Relay-Einstellungen und Logs',
    setupSubtitle: 'Starte zuerst das Relay und verbinde dein Telefon. Die Aufgabenansicht steht darunter.',
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
    firstUseBody: 'Verbinde dein Telefon in diesen 3 Schritten.',
    firstUseStepInstall: 'Klicke einmal auf Installieren/builden, um die Relay-Abhängigkeiten vorzubereiten.',
    firstUseStepStart: 'Klicke auf Relay starten und warte, bis der Status online oder bereit für die Telefonverbindung ist.',
    firstUseStepConnect: 'Scanne den QR-Code mit der Telefonkamera, um URL und API Key zu importieren, und erstelle dann eine Aufgabe auf dem Telefon.',
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
  updateChannelSelect: document.getElementById('updateChannelSelect'),
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
  desktopSocketStatus: document.getElementById('desktopSocketStatus'),
  refreshAgentsButton: document.getElementById('refreshAgentsButton'),
  taskSearchInput: document.getElementById('taskSearchInput'),
  taskList: document.getElementById('taskList'),
  selectedTaskMeta: document.getElementById('selectedTaskMeta'),
  selectedTaskTitle: document.getElementById('selectedTaskTitle'),
  selectedTaskSubtitle: document.getElementById('selectedTaskSubtitle'),
  messageStream: document.getElementById('messageStream'),
  agentComposer: document.getElementById('agentComposer'),
  agentPromptInput: document.getElementById('agentPromptInput'),
  sendPromptButton: document.getElementById('sendPromptButton'),
  composerHint: document.getElementById('composerHint'),
  stopAgentButton: document.getElementById('stopAgentButton'),
  resumeThreadButton: document.getElementById('resumeThreadButton'),
  approvalPanel: document.getElementById('approvalPanel'),
  taskDetails: document.getElementById('taskDetails'),
  refreshGitButton: document.getElementById('refreshGitButton'),
  gitSummaryText: document.getElementById('gitSummaryText'),
  changedFiles: document.getElementById('changedFiles'),
  diffPreview: document.getElementById('diffPreview'),
  workbenchConnectionText: document.getElementById('workbenchConnectionText'),
  workbenchQrImage: document.getElementById('workbenchQrImage'),
  copyWorkbenchDeepLinkButton: document.getElementById('copyWorkbenchDeepLinkButton'),
};

let currentState = null;
let currentLanguage = 'en';
let portPreviewTimer = null;
let pendingAction = null;
let relaySocket = null;
let relaySocketState = 'offline';
let relayRequestId = 1;
let selectedAgentId = null;
let selectedThreadId = null;
let taskFilter = 'active';
let taskSearch = '';
let agents = [];
let activeThreads = [];
let historyThreads = [];
let pendingRequestsByAgent = new Map();
const gitContextByCwd = new Map();
const loadingGitCwds = new Set();
const pendingRelayRequests = new Map();
const refreshTimers = new Map();
const nonBlockingPendingActions = new Set(['updateChecking']);

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

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function shortPath(value) {
  const raw = String(value || '');
  if (!raw) return '';
  const parts = raw.split(/[\\/]/).filter(Boolean);
  if (parts.length <= 2) return raw;
  return `${parts.at(-2)} / ${parts.at(-1)}`;
}

function formatTime(value) {
  const timestamp = Number(value || 0);
  if (!timestamp) return '';
  return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

function statusLabel(status) {
  const normalized = String(status || '').toLowerCase();
  if (normalized === 'working' || normalized === 'running') return '执行中';
  if (normalized === 'ready') return '空闲';
  if (normalized === 'error') return '错误';
  if (normalized === 'stopped') return '已停止';
  if (normalized === 'initializing') return '初始化';
  return status || '未知';
}

function messageLabel(type, role) {
  if (role === 'user' || type === 'user') return 'You';
  switch (type) {
    case 'thinking': return 'Reasoning';
    case 'plan': return 'Plan';
    case 'command': return 'Command';
    case 'command_output': return 'Output';
    case 'file_change': return 'Files';
    case 'sub_agent': return 'Sub-agent';
    case 'status': return 'Status';
    default: return 'Codex';
  }
}

function taskPreview(agent) {
  const last = [...(agent.messages || [])].reverse().find((message) => String(message.text || '').trim());
  return String(agent.activityLabel || agent.activity || last?.text || '等待新消息')
    .replace(/\s+/g, ' ')
    .trim()
    .slice(0, 120);
}

function taskSearchText(item, isHistory) {
  return [
    item.name,
    item.preview,
    item.status,
    item.model,
    item.cwd,
    item.projectRoot,
    isHistory ? '' : taskPreview(item),
  ].filter(Boolean).join(' ').toLowerCase();
}

function filterTasks(items, isHistory) {
  const query = taskSearch.trim().toLowerCase();
  if (!query) return items;
  return items.filter((item) => taskSearchText(item, isHistory).includes(query));
}

function taskNameFromPrompt(text) {
  const clean = String(text || '').replace(/\s+/g, ' ').trim();
  if (!clean) return 'EasyCodex';
  return clean.length > 42 ? `${clean.slice(0, 42).trimEnd()}...` : clean;
}

function activeAgent() {
  return agents.find((agent) => agent.id === selectedAgentId) || null;
}

function isAgentBusy(agent) {
  const status = String(agent?.status || '').toLowerCase();
  return status === 'working' || status === 'running';
}

function selectedThread() {
  return activeThreads.find((thread) => thread.id === selectedThreadId)
    || historyThreads.find((thread) => thread.id === selectedThreadId)
    || null;
}

function mergeThreadById(list, thread) {
  if (!thread?.id) return list;
  let found = false;
  const next = list.map((entry) => {
    if (entry.id !== thread.id) return entry;
    found = true;
    return { ...entry, ...thread };
  });
  if (!found) next.unshift(thread);
  return next;
}

function isActiveThreadStatus(status) {
  return [
    'initializing',
    'resuming',
    'working',
    'running',
    'active',
    'in_progress',
    'inprogress',
    'in-progress',
    'pending',
    'processing',
    'queued',
    'starting',
    'streaming',
  ].includes(String(status || '').trim().toLowerCase());
}

function shouldShowActiveThread(thread) {
  return isActiveThreadStatus(thread?.status) || Number(thread?.queuedFollowUpCount || 0) > 0;
}

function agentThreadIds() {
  return new Set(agents.map((agent) => agent.codexThreadId || agent.threadId).filter(Boolean));
}

function visibleActiveItems() {
  const runningThreadIds = agentThreadIds();
  return [
    ...agents.map((agent) => ({ ...agent, __kind: 'agent' })),
    ...activeThreads
      .filter((thread) => !runningThreadIds.has(thread.id))
      .map((thread) => ({ ...thread, __kind: 'thread' })),
  ];
}

function diffSummary(diff) {
  const files = new Set();
  let additions = 0;
  let deletions = 0;
  for (const line of String(diff || '').split(/\r?\n/)) {
    if (line.startsWith('+') && !line.startsWith('+++')) additions += 1;
    if (line.startsWith('-') && !line.startsWith('---')) deletions += 1;
    const file = line.match(/^diff --git a\/(.+?) b\/(.+)$/)?.[2]
      || line.match(/^\+\+\+ b\/(.+)$/)?.[1]
      || line.match(/^--- a\/(.+)$/)?.[1];
    if (file && file !== '/dev/null') files.add(file.replace(/\\/g, '/'));
  }
  return { files: Array.from(files), additions, deletions };
}

function gitStatusFiles(status) {
  if (!status) return [];
  return [
    ...(status.modified || []),
    ...(status.created || []),
    ...(status.deleted || []),
    ...(status.notAdded || []),
    ...((status.renamed || []).map((entry) => entry.to || entry.from).filter(Boolean)),
    ...(status.conflicted || []),
  ].filter(Boolean);
}

async function refreshGitContext(agent, force = false) {
  if (!agent?.cwd || relaySocketState !== 'online') return;
  const cwd = agent.cwd;
  if (!force && (gitContextByCwd.has(cwd) || loadingGitCwds.has(cwd))) return;
  loadingGitCwds.add(cwd);
  renderGitContext(agent);
  try {
    const [status, diff] = await Promise.all([
      relaySend('git_status', { cwd }),
      relaySend('git_diff', { cwd }),
    ]);
    gitContextByCwd.set(cwd, {
      status,
      diff: diff?.diff || '',
      loadedAt: Date.now(),
      error: '',
    });
  } catch (error) {
    gitContextByCwd.set(cwd, {
      status: null,
      diff: '',
      loadedAt: Date.now(),
      error: error.message || String(error),
    });
  } finally {
    loadingGitCwds.delete(cwd);
    if (activeAgent()?.cwd === cwd) renderGitContext(activeAgent());
  }
}

function renderGitContext(agent) {
  if (!agent?.cwd) {
    elements.gitSummaryText.textContent = '选择运行中的任务后显示 Git 状态。';
    elements.changedFiles.innerHTML = '';
    elements.diffPreview.textContent = '';
    elements.refreshGitButton.disabled = true;
    return;
  }

  elements.refreshGitButton.disabled = relaySocketState !== 'online';
  const cwd = agent.cwd;
  const context = gitContextByCwd.get(cwd);
  if (loadingGitCwds.has(cwd) && !context) {
    elements.gitSummaryText.textContent = '正在读取 Git 状态...';
    elements.changedFiles.innerHTML = '';
    elements.diffPreview.textContent = '';
    return;
  }

  if (!context) {
    elements.gitSummaryText.textContent = '等待读取 Git 状态...';
    elements.changedFiles.innerHTML = '';
    elements.diffPreview.textContent = '';
    refreshGitContext(agent).catch((error) => appendLog(`Git refresh failed: ${error.message || error}`));
    return;
  }

  if (context.error) {
    elements.gitSummaryText.textContent = `Git 状态不可用：${context.error}`;
    elements.changedFiles.innerHTML = '';
    elements.diffPreview.textContent = '';
    return;
  }

  const summary = diffSummary(context.diff);
  const statusFiles = gitStatusFiles(context.status);
  const files = Array.from(new Set([...summary.files, ...statusFiles]));
  const branch = context.status?.branch || 'unknown';
  elements.gitSummaryText.textContent = files.length
    ? `${branch} / ${files.length} 个文件 / +${summary.additions} -${summary.deletions}`
    : `${branch} / 工作区干净`;
  elements.changedFiles.innerHTML = files.length
    ? files.slice(0, 18).map((file) => `<span class="file-chip">${escapeHtml(file)}</span>`).join('')
    : '<span class="muted-chip">没有文件变更</span>';
  elements.diffPreview.textContent = context.diff
    ? context.diff.slice(0, 5000)
    : '';
}

function setSocketStatus(state, text) {
  relaySocketState = state;
  elements.desktopSocketStatus.dataset.state = state;
  elements.desktopSocketStatus.textContent = text;
  elements.workbenchConnectionText.textContent = text;
}

function relaySend(action, params = {}) {
  if (!relaySocket || relaySocket.readyState !== WebSocket.OPEN || relaySocketState !== 'online') {
    return Promise.reject(new Error('Relay WebSocket is not connected.'));
  }
  const requestId = `desktop_${relayRequestId++}`;
  relaySocket.send(JSON.stringify({ action, params, requestId }));
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      pendingRelayRequests.delete(requestId);
      reject(new Error(`${action} timed out`));
    }, 30000);
    pendingRelayRequests.set(requestId, { resolve, reject, timer });
  });
}

function handleRelayResponse(message) {
  const pending = pendingRelayRequests.get(message.requestId);
  if (!pending) return;
  clearTimeout(pending.timer);
  pendingRelayRequests.delete(message.requestId);
  if (message.type === 'error') pending.reject(new Error(message.error || 'Relay request failed'));
  else pending.resolve(message.data);
}

function scheduleAgentRefresh(agentId) {
  if (!agentId || agentId === 'system') return;
  clearTimeout(refreshTimers.get(agentId));
  refreshTimers.set(agentId, setTimeout(() => {
    refreshTimers.delete(agentId);
    refreshAgent(agentId).catch((error) => appendLog(`Task refresh failed: ${error.message || error}`));
  }, 120));
}

function rememberPendingRequest(agentId, data) {
  if (!agentId || !data?.requestId) return;
  const requests = pendingRequestsByAgent.get(agentId) || [];
  const next = requests.filter((request) => request.requestId !== data.requestId);
  next.push(data);
  pendingRequestsByAgent.set(agentId, next);
}

function resolvePendingRequest(agentId, requestId) {
  const requests = pendingRequestsByAgent.get(agentId) || [];
  pendingRequestsByAgent.set(agentId, requests.filter((request) => request.requestId !== requestId));
}

function handleRelayStream(entry) {
  if (!entry || entry.type !== 'stream') return;
  if (entry.event === 'agents/changed' || entry.event === 'codex/threads_changed') {
    refreshTasks().catch((error) => appendLog(`Task list refresh failed: ${error.message || error}`));
  }
  if (entry.event === 'codex/threads_changed' && selectedThreadId) {
    refreshSelectedThread().catch((error) => appendLog(`Thread refresh failed: ${error.message || error}`));
  }
  if (entry.event === 'agent/requested') rememberPendingRequest(entry.agentId, entry.data);
  if (entry.event === 'agent/request_resolved') resolvePendingRequest(entry.agentId, entry.data?.requestId);
  scheduleAgentRefresh(entry.agentId);
}

function connectRelaySocket() {
  if (!currentState?.relayRunning || !currentState?.relayUrl || !currentState?.apiKey) {
    if (relaySocket) relaySocket.close();
    relaySocket = null;
    setSocketStatus('offline', '等待中继启动');
    renderWorkbench();
    return;
  }
  if (relaySocket && [WebSocket.CONNECTING, WebSocket.OPEN].includes(relaySocket.readyState)) return;

  setSocketStatus('offline', '正在连接任务流...');
  relaySocket = new WebSocket(currentState.relayUrl);
  relaySocket.addEventListener('open', () => {
    relaySocket.send(JSON.stringify({
      action: 'auth',
      params: { key: currentState.apiKey, clientId: 'easycodex-desktop-workbench' },
      requestId: `desktop_auth_${Date.now()}`,
    }));
  });
  relaySocket.addEventListener('message', (event) => {
    let message = null;
    try {
      message = JSON.parse(event.data);
    } catch {
      return;
    }
    if (message.type === 'response' && message.action === 'auth') {
      setSocketStatus('online', '任务流已连接');
      refreshTasks().catch((error) => appendLog(`Task load failed: ${error.message || error}`));
      return;
    }
    if (message.type === 'response' || message.type === 'error') {
      handleRelayResponse(message);
      return;
    }
    handleRelayStream(message);
  });
  relaySocket.addEventListener('close', () => {
    if (relaySocketState !== 'offline') setSocketStatus('offline', '任务流已断开');
    renderWorkbench();
  });
  relaySocket.addEventListener('error', () => {
    setSocketStatus('error', '任务流连接失败');
    renderWorkbench();
  });
}

async function refreshAgent(agentId) {
  const agent = await relaySend('get_agent', { agentId });
  agents = agents.map((entry) => (entry.id === agent.id ? agent : entry));
  if (!agents.some((entry) => entry.id === agent.id)) agents.unshift(agent);
  if (Array.isArray(agent.pendingRequests)) pendingRequestsByAgent.set(agent.id, agent.pendingRequests);
  renderWorkbench();
}

async function refreshSelectedThread() {
  const threadId = selectedThreadId;
  if (!threadId || relaySocketState !== 'online') return;
  const detail = await relaySend('read_codex_thread', { threadId });
  const update = (entry) => (entry.id === threadId ? { ...entry, ...detail } : entry);
  activeThreads = activeThreads.map(update);
  historyThreads = historyThreads.map(update);
  if (!activeThreads.some((entry) => entry.id === threadId) && !historyThreads.some((entry) => entry.id === threadId)) {
    activeThreads.unshift(detail);
  }
  renderWorkbench();
}

async function refreshTasks() {
  if (relaySocketState !== 'online') {
    renderWorkbench();
    return;
  }
  const selectedThreadSnapshot = selectedThread();
  const [nextAgents, activeResult] = await Promise.all([
    relaySend('list_agents'),
    relaySend('list_codex_threads', { all: true, limit: 80, activeOnly: true }).catch((error) => {
      appendLog(`Active thread load failed: ${error.message || error}`);
      return { data: [] };
    }),
  ]);
  agents = nextAgents;
  for (const agent of agents) {
    if (Array.isArray(agent.pendingRequests)) pendingRequestsByAgent.set(agent.id, agent.pendingRequests);
  }
  const runningThreadIds = agentThreadIds();
  activeThreads = (activeResult?.data || [])
    .filter((thread) => thread?.id && !runningThreadIds.has(thread.id) && shouldShowActiveThread(thread))
    .sort((left, right) => Number(right.updatedAt || 0) - Number(left.updatedAt || 0));
  if (taskFilter === 'history') await refreshHistory();
  if (selectedAgentId && !agents.some((agent) => agent.id === selectedAgentId)) selectedAgentId = agents[0]?.id || null;
  if (selectedThreadId && !selectedThread() && selectedThreadSnapshot) {
    historyThreads = mergeThreadById(historyThreads, selectedThreadSnapshot);
  }
  if (selectedThreadId && !selectedThread()) selectedThreadId = null;
  if (!selectedAgentId && !selectedThreadId && agents[0]) selectedAgentId = agents[0].id;
  if (!selectedAgentId && !selectedThreadId && activeThreads[0]) selectedThreadId = activeThreads[0].id;
  renderWorkbench();
  if (selectedThreadId && !(selectedThread()?.messages?.length)) {
    refreshSelectedThread().catch((error) => appendLog(`Thread detail load failed: ${error.message || error}`));
  }
}

async function refreshHistory() {
  const selectedThreadSnapshot = selectedThread();
  const result = await relaySend('list_codex_threads', { all: true, limit: 80 });
  const runningThreadIds = new Set([
    ...agents.map((agent) => agent.codexThreadId).filter(Boolean),
    ...activeThreads.map((thread) => thread.id).filter(Boolean),
  ]);
  historyThreads = (result?.data || []).filter((thread) => !runningThreadIds.has(thread.id));
  if (selectedThreadId && selectedThreadSnapshot && !historyThreads.some((thread) => thread.id === selectedThreadId)) {
    historyThreads = mergeThreadById(historyThreads, selectedThreadSnapshot);
  }
}

function renderTaskList() {
  const isHistory = taskFilter === 'history';
  const items = filterTasks(isHistory ? historyThreads.map((thread) => ({ ...thread, __kind: 'history' })) : visibleActiveItems(), isHistory);
  if (!items.length) {
    elements.taskList.innerHTML = `<div class="empty-state">${taskSearch ? '没有匹配的任务' : `暂无${isHistory ? '历史任务' : '运行中任务'}`}</div>`;
    return;
  }
  elements.taskList.innerHTML = items.map((item) => {
    const id = item.id;
    const kind = item.__kind || (isHistory ? 'history' : 'agent');
    const active = kind === 'agent' ? selectedAgentId === id : selectedThreadId === id;
    const title = item.name || item.preview || 'Codex task';
    const status = item.queuedFollowUpCount > 0 ? 'pending' : String(item.status || '');
    const preview = kind === 'agent' ? taskPreview(item) : String(item.activityLabel || item.preview || (kind === 'history' ? '可恢复历史任务' : 'Codex 正在更新'));
    const meta = shortPath(item.cwd || item.projectRoot);
    return `
      <button class="task-card ${active ? 'active' : ''}" data-id="${escapeHtml(id)}" data-kind="${escapeHtml(kind)}" type="button">
        <span class="task-title-line">
          <span class="task-title">${escapeHtml(title)}</span>
          <span class="status-pill ${escapeHtml(status)}">${escapeHtml(isHistory ? statusLabel(item.status) : statusLabel(item.status))}</span>
        </span>
        <span class="task-preview">${escapeHtml(preview)}</span>
        <span class="task-meta-line">
          <span class="task-path">${escapeHtml(meta)}</span>
          <span class="task-path">${escapeHtml(formatTime(item.updatedAt || item.createdAt || item.messages?.at(-1)?.timestamp))}</span>
        </span>
      </button>
    `;
  }).join('');
}

function renderMessages(messages) {
  if (!messages?.length) {
    elements.messageStream.innerHTML = '<div class="empty-state">这个任务还没有消息。手机或电脑发一句话后，执行流会出现在这里。</div>';
    return;
  }
  elements.messageStream.innerHTML = messages.map((message) => {
    const role = message.role === 'user' || message.type === 'user' ? 'user' : 'agent';
    const type = String(message.type || role);
    return `
      <article class="message ${escapeHtml(role)} ${escapeHtml(type)}">
        <div class="message-shell">
          <div class="message-label">
            <span>${escapeHtml(messageLabel(type, role))}</span>
            <span>${escapeHtml(formatTime(message.timestamp))}</span>
          </div>
          <div class="message-text">${escapeHtml(message.text || '')}</div>
        </div>
      </article>
    `;
  }).join('');
  elements.messageStream.scrollTop = elements.messageStream.scrollHeight;
}

function renderApproval(agent) {
  const requests = pendingRequestsByAgent.get(agent?.id) || [];
  if (!agent || requests.length === 0) {
    elements.approvalPanel.hidden = true;
    elements.approvalPanel.innerHTML = '';
    return;
  }
  const request = requests[0];
  const text = request.text || request.params?.command || request.method || 'Codex 请求批准操作';
  elements.approvalPanel.hidden = false;
  elements.approvalPanel.innerHTML = `
    <h3>等待审批</h3>
    <p>Codex 正在请求电脑或手机确认这个操作。</p>
    <pre>${escapeHtml(text)}</pre>
    <div class="approval-actions">
      <button class="primary" data-approval="approve" data-request-id="${escapeHtml(request.requestId)}" type="button">批准</button>
      <button class="secondary danger" data-approval="deny" data-request-id="${escapeHtml(request.requestId)}" type="button">拒绝</button>
    </div>
  `;
}

function renderDetails(agent, thread) {
  const rows = agent ? [
    ['状态', statusLabel(agent.status)],
    ['模型', agent.model || '-'],
    ['项目', agent.cwd || '-'],
    ['Thread', agent.codexThreadId || agent.threadId || '-'],
    ['审批策略', agent.approvalPolicy || '-'],
    ['运行状态', agent.activityLabel || agent.activity || '-'],
  ] : thread ? [
    ['状态', statusLabel(thread.status)],
    ['项目', thread.cwd || thread.projectRoot || '-'],
    ['Thread', thread.id],
    ['最近更新', thread.updatedAt ? new Date(thread.updatedAt).toLocaleString() : '-'],
    ['队列', String(thread.queuedFollowUpCount || 0)],
  ] : [
    ['状态', relaySocketState === 'online' ? '已连接' : '未连接'],
    ['提示', '从左侧选择任务'],
  ];
  elements.taskDetails.innerHTML = rows.map(([key, value]) => `
    <div>
      <dt>${escapeHtml(key)}</dt>
      <dd>${escapeHtml(value)}</dd>
    </div>
  `).join('');
}

function renderSelectedTask() {
  const agent = activeAgent();
  const thread = selectedThread();
  elements.resumeThreadButton.hidden = !thread || Boolean(agent);
  elements.stopAgentButton.disabled = !agent || agent.status === 'stopped';
  elements.sendPromptButton.disabled = !agent || relaySocketState !== 'online';
  elements.agentPromptInput.disabled = !agent || relaySocketState !== 'online';
  elements.composerHint.textContent = agent
    ? isAgentBusy(agent)
      ? `当前任务运行中，发送将新建并行任务 / ${shortPath(agent.cwd)}`
      : `发送到 ${shortPath(agent.cwd)}`
    : thread
      ? '先恢复历史任务，再继续发送消息。'
      : '选择运行中的任务后可以发送。';

  if (agent) {
    elements.selectedTaskMeta.textContent = `${statusLabel(agent.status)} / ${shortPath(agent.cwd)}`;
    elements.selectedTaskTitle.textContent = agent.name || 'Codex task';
    elements.selectedTaskSubtitle.textContent = agent.activityLabel || agent.activity || agent.codexThreadId || '电脑和手机正在共享这个 relay 任务。';
    renderApproval(agent);
    renderMessages(agent.messages || []);
    renderDetails(agent, null);
    renderGitContext(agent);
    return;
  }

  if (thread) {
    elements.selectedTaskMeta.textContent = `${statusLabel(thread.status)} / ${shortPath(thread.cwd)}`;
    elements.selectedTaskTitle.textContent = thread.name || thread.preview || '历史任务';
    elements.selectedTaskSubtitle.textContent = taskFilter === 'active'
      ? (thread.activityLabel || '这是正在变化的 Codex 线程，恢复后可以从工作台继续对话。')
      : '这是 Codex 历史线程，恢复后可以继续对话。';
    renderApproval(null);
    renderMessages(thread.messages || []);
    renderDetails(null, thread);
    renderGitContext(null);
    return;
  }

  elements.selectedTaskMeta.textContent = relaySocketState === 'online' ? 'Workbench ready' : 'No relay connection';
  elements.selectedTaskTitle.textContent = relaySocketState === 'online' ? '选择一个任务' : '启动中继后显示任务';
  elements.selectedTaskSubtitle.textContent = '手机发起的任务会出现在这里，电脑端可以继续对话和审批。';
  renderApproval(null);
  renderMessages([]);
  renderDetails(null, null);
  renderGitContext(null);
}

function renderWorkbench() {
  renderTaskList();
  renderSelectedTask();
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
  const canUsePort = currentState?.portAvailable || currentState?.portReclaimable;
  elements.installButton.disabled = isBusy || currentState?.installRunning || currentState?.relayRunning;
  elements.checkUpdateButton.disabled = isBusy || currentState?.update?.checking || currentState?.update?.applying;
  elements.applyUpdateButton.disabled = isBusy || currentState?.update?.checking || currentState?.update?.applying || !currentState?.update?.info?.updateAvailable;
  elements.startButton.disabled = isBusy || currentState?.installRunning || currentState?.relayRunning || !currentState?.relayReady || !canUsePort || !currentState?.codex?.installed;
  elements.stopButton.disabled = isBusy || !currentState?.relayRunning;
  elements.refreshKeyButton.disabled = isBusy || currentState?.relayRunning;
  elements.portInput.disabled = isBusy || currentState?.relayRunning;
  elements.updateChannelSelect.disabled = isBusy;
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
    elements.updateTitle.textContent = t('updateNotice');
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
  setControlsBusy(!nonBlockingPendingActions.has(pendingAction));
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
  elements.updateChannelSelect.value = state.updateChannel || 'stable';
  elements.workspaceInput.value = state.workspace;
  elements.codexPathInput.value = state.codexPath || state.codex?.path || '';
  elements.relayUrlInput.value = state.relayUrl;
  elements.apiKeyInput.value = state.apiKey;
  elements.qrImage.src = state.qrDataUrl;
  elements.workbenchQrImage.src = state.qrDataUrl;
  elements.workbenchConnectionText.textContent = state.relayRunning
    ? `Relay: ${state.relayUrl}`
    : '启动中继后，电脑端任务台会连接本机任务流。';
  renderUpdate(state.update);
  elements.statusCard.dataset.busy = 'false';
  elements.statusCard.dataset.state = state.relayRunning ? 'starting' : 'offline';
  elements.statusText.textContent = state.relayRunning ? t('starting') : t('offline');
  setControlsBusy(false);
  if (state.portAvailable) setPortStatus('ok', t('portAvailable'));
  else if (state.portReclaimable) setPortStatus('ok', t('portRelayBusy'));
  else setPortStatus('error', t('portBusy'));
  setCodexStatus(state);
  renderHealth(state.health);
  renderPendingStatus();
  connectRelaySocket();
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
      if (!preview.portAvailable && !preview.portReclaimable) {
        setPortStatus('error', t('portBusy'));
        elements.startButton.disabled = true;
        return;
      }
      setPortStatus('ok', preview.portReclaimable ? t('portRelayBusy') : t('portAvailable'));
      const state = await window.easyCodexRelay.saveConfig({ port });
      renderState(state);
    } catch (error) {
      setPortStatus('error', error.message || t('invalidPort'));
      elements.startButton.disabled = true;
    }
  }, 250);
});

elements.updateChannelSelect.addEventListener('change', () => {
  runAction(elements.updateChannelSelect, () => window.easyCodexRelay.saveConfig({
    updateChannel: elements.updateChannelSelect.value,
  }));
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

elements.copyWorkbenchDeepLinkButton.addEventListener('click', () => {
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

elements.refreshAgentsButton.addEventListener('click', () => {
  refreshTasks().catch((error) => appendLog(`Task refresh failed: ${error.message || error}`));
});

elements.taskSearchInput.addEventListener('input', () => {
  taskSearch = elements.taskSearchInput.value;
  renderWorkbench();
});

elements.refreshGitButton.addEventListener('click', () => {
  const agent = activeAgent();
  if (!agent) return;
  refreshGitContext(agent, true).catch((error) => appendLog(`Git refresh failed: ${error.message || error}`));
});

document.querySelectorAll('.rail-tab').forEach((button) => {
  button.addEventListener('click', async () => {
    taskFilter = button.dataset.filter || 'active';
    document.querySelectorAll('.rail-tab').forEach((tab) => tab.classList.toggle('active', tab === button));
    selectedAgentId = taskFilter === 'active' ? (selectedAgentId || agents[0]?.id || null) : null;
    selectedThreadId = null;
    try {
      if (taskFilter === 'history') await refreshHistory();
    } catch (error) {
      appendLog(`History load failed: ${error.message || error}`);
    }
    renderWorkbench();
  });
});

elements.taskList.addEventListener('click', async (event) => {
  const target = event.target instanceof Element ? event.target : null;
  const card = target?.closest('.task-card');
  if (!card) return;
  const id = card.dataset.id;
  if (card.dataset.kind === 'history' || card.dataset.kind === 'thread') {
    selectedAgentId = null;
    selectedThreadId = id;
    const thread = selectedThread();
    if (thread && !thread.messages) {
      try {
        const detail = await relaySend('read_codex_thread', { threadId: id });
        if (activeThreads.some((entry) => entry.id === id)) {
          activeThreads = mergeThreadById(activeThreads, detail);
        } else {
          historyThreads = mergeThreadById(historyThreads, detail);
        }
      } catch (error) {
        appendLog(`Thread read failed: ${error.message || error}`);
      }
    }
  } else {
    selectedThreadId = null;
    selectedAgentId = id;
    refreshAgent(id).catch((error) => appendLog(`Task read failed: ${error.message || error}`));
  }
  renderWorkbench();
});

elements.agentComposer.addEventListener('submit', async (event) => {
  event.preventDefault();
  const text = elements.agentPromptInput.value.trim();
  const agent = activeAgent();
  if (!text || !agent) return;
  elements.sendPromptButton.disabled = true;
  try {
    let targetAgent = agent;
    if (isAgentBusy(agent)) {
      targetAgent = await relaySend('create_agent', {
        name: taskNameFromPrompt(text),
        model: agent.model || 'gpt-5.5',
        cwd: agent.cwd || currentState?.workspace,
        approvalPolicy: agent.approvalPolicy || 'never',
        serviceTier: agent.serviceTier,
        reasoningEffort: agent.reasoningEffort,
      });
      agents.unshift(targetAgent);
      selectedAgentId = targetAgent.id;
      selectedThreadId = null;
      taskFilter = 'active';
    }
    await relaySend('send_message', { agentId: targetAgent.id, text });
    elements.agentPromptInput.value = '';
    await refreshAgent(targetAgent.id);
  } catch (error) {
    appendLog(`Send failed: ${error.message || error}`);
  } finally {
    renderWorkbench();
  }
});

elements.agentPromptInput.addEventListener('keydown', (event) => {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault();
    elements.agentComposer.requestSubmit();
  }
});

elements.stopAgentButton.addEventListener('click', async () => {
  const agent = activeAgent();
  if (!agent) return;
  try {
    await relaySend('stop_agent', { agentId: agent.id });
    selectedAgentId = null;
    await refreshTasks();
  } catch (error) {
    appendLog(`Stop failed: ${error.message || error}`);
  }
});

elements.resumeThreadButton.addEventListener('click', async () => {
  const thread = selectedThread();
  if (!thread) return;
  try {
    const agent = await relaySend('create_agent', {
      name: thread.name || thread.preview || 'Resumed Codex task',
      model: thread.model || 'gpt-5.5',
      cwd: thread.cwd || thread.projectRoot || currentState?.workspace,
      approvalPolicy: thread.approvalPolicy || 'never',
      serviceTier: thread.serviceTier,
      reasoningEffort: thread.reasoningEffort,
      codexThreadId: thread.id,
    });
    selectedThreadId = null;
    selectedAgentId = agent.id;
    taskFilter = 'active';
    document.querySelectorAll('.rail-tab').forEach((tab) => tab.classList.toggle('active', tab.dataset.filter === 'active'));
    await refreshTasks();
  } catch (error) {
    appendLog(`Resume failed: ${error.message || error}`);
  }
});

elements.approvalPanel.addEventListener('click', async (event) => {
  const target = event.target instanceof Element ? event.target : null;
  const button = target?.closest('[data-approval]');
  const agent = activeAgent();
  if (!button || !agent) return;
  button.disabled = true;
  const approved = button.dataset.approval === 'approve';
  const requestId = button.dataset.requestId;
  try {
    await relaySend('respond_agent_request', {
      agentId: agent.id,
      requestId,
      approved,
      reason: approved ? 'Approved from EasyCodex desktop' : 'Denied from EasyCodex desktop',
    });
    resolvePendingRequest(agent.id, requestId);
    await refreshAgent(agent.id);
  } catch (error) {
    appendLog(`Approval failed: ${error.message || error}`);
  }
});

window.easyCodexRelay.onState(renderState);
window.easyCodexRelay.onHealth(renderHealth);
window.easyCodexRelay.onLog(appendLog);

window.easyCodexRelay.getState()
  .then(renderState)
  .catch((error) => appendLog(`Error: ${error.message || error}`));
