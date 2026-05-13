import fs from 'fs';
import os from 'os';
import path from 'path';

const MOBILE_NOTIFICATION_ENDPOINT = 'https://exp.host/--/api/v2/push/send';
const CONFIG_DIR = path.join(os.homedir(), '.easycodex');
const NOTIFICATION_PREFS_PATH = path.join(CONFIG_DIR, 'notification-prefs.json');
const MAX_HISTORY = 200;

export type NotificationLevel = 'all' | 'errors' | 'muted';
type NotificationSeverity = 'info' | 'error';
type NotificationLanguage = 'zh' | 'zh-Hant' | 'en' | 'ja' | 'ko' | 'es' | 'fr' | 'de';
type NotificationKind = 'agent_crashed' | 'turn_started' | 'turn_completed' | 'turn_failed' | 'file_changed';

interface ClientPushRegistration {
  token: string;
  language: NotificationLanguage;
}

interface OutboundNotificationPayload {
  to: string;
  title: string;
  body: string;
  subtitle?: string;
  sound?: 'default' | null;
  categoryId?: string;
  channelId?: string;
  priority?: 'default' | 'normal' | 'high';
  data?: Record<string, unknown>;
}

const clientNotificationTokens = new Map<string, Map<string, ClientPushRegistration>>();
const clientLanguages = new Map<string, NotificationLanguage>();
const notificationPrefs = new Map<string, NotificationLevel>();
const notificationHistory: Array<{
  id: string;
  timestamp: number;
  agentId: string;
  title: string;
  body: string;
  severity: NotificationSeverity;
  status: 'sent' | 'muted' | 'no_tokens' | 'error';
  deliveredCount: number;
}> = [];

function normalizeLevel(input: string): NotificationLevel {
  if (input === 'errors' || input === 'muted') return input;
  return 'all';
}

function loadNotificationPrefs() {
  try {
    if (!fs.existsSync(NOTIFICATION_PREFS_PATH)) return;
    const raw = fs.readFileSync(NOTIFICATION_PREFS_PATH, 'utf8');
    const parsed = JSON.parse(raw) as Record<string, string>;
    for (const [agentId, level] of Object.entries(parsed || {})) {
      if (!agentId.trim()) continue;
      notificationPrefs.set(agentId, normalizeLevel(level));
    }
  } catch (err) {
    console.warn('[notify] Failed to load notification prefs:', err);
  }
}

function saveNotificationPrefs() {
  try {
    fs.mkdirSync(CONFIG_DIR, { recursive: true });
    const payload: Record<string, NotificationLevel> = {};
    for (const [agentId, level] of notificationPrefs.entries()) {
      payload[agentId] = level;
    }
    fs.writeFileSync(NOTIFICATION_PREFS_PATH, JSON.stringify(payload, null, 2), 'utf8');
  } catch (err) {
    console.warn('[notify] Failed to save notification prefs:', err);
  }
}

function appendNotificationHistory(entry: {
  agentId: string;
  title: string;
  body: string;
  severity: NotificationSeverity;
  status: 'sent' | 'muted' | 'no_tokens' | 'error';
  deliveredCount: number;
}) {
  notificationHistory.push({
    id: `notif_${Date.now()}_${Math.random().toString(36).slice(2, 8)}`,
    timestamp: Date.now(),
    ...entry,
  });
  if (notificationHistory.length > MAX_HISTORY) {
    notificationHistory.splice(0, notificationHistory.length - MAX_HISTORY);
  }
}

function shouldSendByLevel(level: NotificationLevel, severity: NotificationSeverity): boolean {
  if (level === 'muted') return false;
  if (level === 'errors' && severity !== 'error') return false;
  return true;
}

export function updateNotificationPreference(agentId: string, level: NotificationLevel) {
  if (!agentId.trim()) return;
  notificationPrefs.set(agentId.trim(), level);
  saveNotificationPrefs();
}

export function getNotificationPreferences(): Record<string, NotificationLevel> {
  const result: Record<string, NotificationLevel> = {};
  for (const [agentId, level] of notificationPrefs.entries()) {
    result[agentId] = level;
  }
  return result;
}

export function getNotificationLevel(agentId: string): NotificationLevel {
  return notificationPrefs.get(agentId) || 'all';
}

export function getNotificationHistory(limit = 100) {
  const safeLimit = Math.min(Math.max(limit, 1), MAX_HISTORY);
  return notificationHistory.slice(-safeLimit).reverse();
}

function normalizeLanguage(input?: string): NotificationLanguage {
  const value = String(input || '').trim().toLowerCase();
  if (value === 'zh-hant' || value === 'zh-tw' || value === 'zh-hk' || value === 'zh-mo') return 'zh-Hant';
  if (value === 'zh' || value === 'zh-cn' || value === 'zh-hans') return 'zh';
  if (value === 'ja' || value === 'ja-jp') return 'ja';
  if (value === 'ko' || value === 'ko-kr') return 'ko';
  if (value === 'es' || value.startsWith('es-')) return 'es';
  if (value === 'fr' || value.startsWith('fr-')) return 'fr';
  if (value === 'de' || value.startsWith('de-')) return 'de';
  return 'en';
}

export function updateClientLanguage(clientId: string, language?: string) {
  if (!clientId) return;
  const normalized = normalizeLanguage(language);
  clientLanguages.set(clientId, normalized);
  const tokens = clientNotificationTokens.get(clientId);
  if (!tokens) return;
  for (const registration of tokens.values()) {
    registration.language = normalized;
  }
}

loadNotificationPrefs();

export function registerNotificationToken(clientId: string, token: string, language?: string) {
  if (!clientId) return;
  if (token && (token.startsWith('ExponentPushToken[') || token.startsWith('ExpoPushToken['))) {
    const normalizedLanguage = normalizeLanguage(language || clientLanguages.get(clientId));
    clientLanguages.set(clientId, normalizedLanguage);
    const existing = clientNotificationTokens.get(clientId) || new Map<string, ClientPushRegistration>();
    existing.set(token, { token, language: normalizedLanguage });
    clientNotificationTokens.set(clientId, existing);
    console.log(`  📱 Notification token registered for ${clientId.slice(0, 8)}: ${token.slice(0, 30)}...`);
  }
}

export function removePushToken(clientId: string, token: string) {
  const existing = clientNotificationTokens.get(clientId);
  if (!existing) return;
  existing.delete(token);
  if (existing.size === 0) {
    clientNotificationTokens.delete(clientId);
  }
}

export function getRegisteredTokenCount(): number {
  let total = 0;
  for (const tokens of clientNotificationTokens.values()) {
    total += tokens.size;
  }
  return total;
}

export function getRegisteredClientCount(): number {
  return clientNotificationTokens.size;
}

function localizedPushText(language: NotificationLanguage, opts: {
  kind?: NotificationKind;
  title: string;
  body: string;
  subtitle?: string;
  agentName?: string;
  exitCode?: number | string;
  preview?: string;
  path?: string;
  errorMessage?: string;
}) {
  const agent = opts.agentName || opts.title.replace(/\s+[—-].*$/, '').trim() || 'EasyCodex';
  const dictionaries: Record<NotificationLanguage, {
    error: string;
    workingTitle: (name: string) => string;
    workingBody: string;
    finishedTitle: (name: string) => string;
    completedBody: (name: string) => string;
    failedBody: string;
    crashedBody: (code: string) => string;
    fileChangedTitle: (name: string) => string;
    fileChangedBody: string;
    tapToOpen: string;
    holdToReply: string;
    holdToFollowUp: string;
  }> = {
    zh: {
      error: '错误',
      workingTitle: (name) => `${name} 正在处理`,
      workingBody: '智能体已开始处理你的请求。',
      finishedTitle: (name) => `${name} 已完成`,
      completedBody: (name) => `${name} 已完成任务。`,
      failedBody: '智能体任务失败。',
      crashedBody: (code) => `智能体崩溃，退出码 ${code}。`,
      fileChangedTitle: (name) => `${name} — 文件已修改`,
      fileChangedBody: '智能体修改了一个文件。',
      tapToOpen: '点按打开',
      holdToReply: '长按回复',
      holdToFollowUp: '长按继续跟进',
    },
    'zh-Hant': {
      error: '錯誤',
      workingTitle: (name) => `${name} 正在處理`,
      workingBody: '智能體已開始處理你的請求。',
      finishedTitle: (name) => `${name} 已完成`,
      completedBody: (name) => `${name} 已完成任務。`,
      failedBody: '智能體任務失敗。',
      crashedBody: (code) => `智能體崩潰，退出碼 ${code}。`,
      fileChangedTitle: (name) => `${name} — 檔案已修改`,
      fileChangedBody: '智能體修改了一個檔案。',
      tapToOpen: '點按開啟',
      holdToReply: '長按回覆',
      holdToFollowUp: '長按繼續跟進',
    },
    en: {
      error: 'Error',
      workingTitle: (name) => `${name} is working`,
      workingBody: 'Agent started processing your request.',
      finishedTitle: (name) => `${name} finished`,
      completedBody: (name) => `${name} completed the task.`,
      failedBody: 'Agent turn failed.',
      crashedBody: (code) => `Agent crashed with exit code ${code}.`,
      fileChangedTitle: (name) => `${name} — File changed`,
      fileChangedBody: 'Agent modified a file.',
      tapToOpen: 'Tap to open',
      holdToReply: 'Hold to reply',
      holdToFollowUp: 'Hold to follow up',
    },
    ja: {
      error: 'エラー',
      workingTitle: (name) => `${name} が処理中です`,
      workingBody: 'エージェントがリクエストの処理を開始しました。',
      finishedTitle: (name) => `${name} が完了しました`,
      completedBody: (name) => `${name} がタスクを完了しました。`,
      failedBody: 'エージェントの処理に失敗しました。',
      crashedBody: (code) => `エージェントが終了コード ${code} でクラッシュしました。`,
      fileChangedTitle: (name) => `${name} — ファイルを変更しました`,
      fileChangedBody: 'エージェントがファイルを変更しました。',
      tapToOpen: 'タップして開く',
      holdToReply: '長押しして返信',
      holdToFollowUp: '長押しして続ける',
    },
    ko: {
      error: '오류',
      workingTitle: (name) => `${name} 처리 중`,
      workingBody: '에이전트가 요청 처리를 시작했습니다.',
      finishedTitle: (name) => `${name} 완료됨`,
      completedBody: (name) => `${name} 작업을 완료했습니다.`,
      failedBody: '에이전트 작업이 실패했습니다.',
      crashedBody: (code) => `에이전트가 종료 코드 ${code}로 중단되었습니다.`,
      fileChangedTitle: (name) => `${name} — 파일 변경됨`,
      fileChangedBody: '에이전트가 파일을 수정했습니다.',
      tapToOpen: '탭하여 열기',
      holdToReply: '길게 눌러 답장',
      holdToFollowUp: '길게 눌러 후속 작업',
    },
    es: {
      error: 'Error',
      workingTitle: (name) => `${name} está trabajando`,
      workingBody: 'El agente empezó a procesar tu solicitud.',
      finishedTitle: (name) => `${name} terminó`,
      completedBody: (name) => `${name} completó la tarea.`,
      failedBody: 'La tarea del agente falló.',
      crashedBody: (code) => `El agente se cerró con el código ${code}.`,
      fileChangedTitle: (name) => `${name} — Archivo modificado`,
      fileChangedBody: 'El agente modificó un archivo.',
      tapToOpen: 'Toca para abrir',
      holdToReply: 'Mantén pulsado para responder',
      holdToFollowUp: 'Mantén pulsado para continuar',
    },
    fr: {
      error: 'Erreur',
      workingTitle: (name) => `${name} travaille`,
      workingBody: "L'agent a commencé à traiter votre demande.",
      finishedTitle: (name) => `${name} a terminé`,
      completedBody: (name) => `${name} a terminé la tâche.`,
      failedBody: "La tâche de l'agent a échoué.",
      crashedBody: (code) => `L'agent s'est arrêté avec le code ${code}.`,
      fileChangedTitle: (name) => `${name} — Fichier modifié`,
      fileChangedBody: "L'agent a modifié un fichier.",
      tapToOpen: 'Touchez pour ouvrir',
      holdToReply: 'Appui long pour répondre',
      holdToFollowUp: 'Appui long pour continuer',
    },
    de: {
      error: 'Fehler',
      workingTitle: (name) => `${name} arbeitet`,
      workingBody: 'Der Agent hat mit der Verarbeitung deiner Anfrage begonnen.',
      finishedTitle: (name) => `${name} ist fertig`,
      completedBody: (name) => `${name} hat die Aufgabe abgeschlossen.`,
      failedBody: 'Der Agentenlauf ist fehlgeschlagen.',
      crashedBody: (code) => `Der Agent ist mit Exit-Code ${code} abgestürzt.`,
      fileChangedTitle: (name) => `${name} — Datei geändert`,
      fileChangedBody: 'Der Agent hat eine Datei geändert.',
      tapToOpen: 'Zum Öffnen tippen',
      holdToReply: 'Zum Antworten halten',
      holdToFollowUp: 'Zum Fortfahren halten',
    },
  };
  const t = dictionaries[language] || dictionaries.en;
  switch (opts.kind) {
    case 'agent_crashed':
      return { title: `${agent} — ${t.error}`, body: t.crashedBody(String(opts.exitCode || 'unknown')), subtitle: t.tapToOpen, replyHint: t.holdToReply };
    case 'turn_started':
      return { title: t.workingTitle(agent), body: t.workingBody, subtitle: t.tapToOpen, replyHint: t.holdToReply };
    case 'turn_completed':
      return { title: t.finishedTitle(agent), body: opts.preview || t.completedBody(agent), subtitle: t.holdToReply, replyHint: t.holdToReply };
    case 'turn_failed':
      return { title: `${agent} — ${t.error}`, body: opts.errorMessage || opts.body || t.failedBody, subtitle: t.tapToOpen, replyHint: t.holdToReply };
    case 'file_changed':
      return { title: t.fileChangedTitle(agent), body: opts.path || t.fileChangedBody, subtitle: t.holdToFollowUp, replyHint: t.holdToFollowUp };
    default:
      return { title: opts.title, body: opts.body, subtitle: opts.subtitle, replyHint: t.holdToReply };
  }
}

export async function notifyMobileClients(opts: {
  title: string;
  body: string;
  subtitle?: string;
  kind?: NotificationKind;
  agentName?: string;
  exitCode?: number | string;
  preview?: string;
  path?: string;
  errorMessage?: string;
  agentId?: string;
  categoryId?: string;
  channelId?: string;
  priority?: 'default' | 'normal' | 'high';
  replyHint?: string;
  severity?: NotificationSeverity;
}) {
  const severity: NotificationSeverity = opts.severity || 'info';
  const agentId = opts.agentId || '';
  if (agentId) {
    const level = getNotificationLevel(agentId);
    if (!shouldSendByLevel(level, severity)) {
      appendNotificationHistory({
        agentId,
        title: opts.title,
        body: opts.body,
        severity,
        status: 'muted',
        deliveredCount: 0,
      });
      return;
    }
  }

  if (clientNotificationTokens.size === 0) {
    appendNotificationHistory({
      agentId,
      title: opts.title,
      body: opts.body,
      severity,
      status: 'no_tokens',
      deliveredCount: 0,
    });
    return;
  }

  const messages: OutboundNotificationPayload[] = [];
  for (const tokens of clientNotificationTokens.values()) {
    for (const registration of tokens.values()) {
      const localized = localizedPushText(registration.language, opts);
      messages.push({
        to: registration.token,
        title: localized.title,
        body: localized.body,
        subtitle: localized.subtitle,
        sound: 'default',
        categoryId: opts.categoryId || 'thread-reply',
        channelId: opts.channelId || 'thread-updates',
        priority: opts.priority || 'high',
        data: {
          kind: opts.kind || 'thread_update',
          agentId: opts.agentId || '',
          canReply: true,
          replyHint: opts.replyHint || localized.replyHint,
        },
      });
    }
  }

  try {
    const res = await fetch(MOBILE_NOTIFICATION_ENDPOINT, {
      method: 'POST',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(messages),
    });

    if (!res.ok) {
      console.error(`  Notification send failed: ${res.status} ${res.statusText}`);
      appendNotificationHistory({
        agentId,
        title: opts.title,
        body: opts.body,
        severity,
        status: 'error',
        deliveredCount: 0,
      });
      return;
    }
    appendNotificationHistory({
      agentId,
      title: opts.title,
      body: opts.body,
      severity,
      status: 'sent',
      deliveredCount: messages.length,
    });
  } catch (err) {
    console.error('  Notification send error:', err);
    appendNotificationHistory({
      agentId,
      title: opts.title,
      body: opts.body,
      severity,
      status: 'error',
      deliveredCount: 0,
    });
  }
}
