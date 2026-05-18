import { render } from 'preact';
import { useCallback, useEffect, useMemo, useRef, useState } from 'preact/hooks';
import './styles.css';

const languageOptions = [
  ['system', 'System / 跟随手机'],
  ['zh', '简体中文'],
  ['en', 'English'],
];

const activeStatuses = new Set([
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
]);

const detailMessageTypes = new Set(['command', 'command_output', 'file_change', 'sub_agent', 'tool', 'tool_call']);
const detailGroupTypes = new Set(['command', 'command_output', 'file_change']);
const longDetailTextLimit = 6000;

const initialWorkbench = {
  relaySocketState: 'offline',
  relaySocketText: '等待中继启动',
  agents: [],
  threadsById: {},
  activeThreadIds: [],
  historyThreadIds: [],
  selectedTarget: null,
  taskFilter: 'active',
  taskSearch: '',
  pendingRequestsByAgent: {},
  gitContextByCwd: {},
  loadingGitCwds: {},
  refreshingTasks: false,
  loadingDetails: {},
  threadErrors: {},
};

function compact(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
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
  if (normalized === 'queued' || normalized === 'pending') return '排队中';
  if (normalized === 'resuming') return '恢复中';
  return status || '可恢复';
}

function permissionModeForTask(task) {
  if (task?.permissionMode) return task.permissionMode;
  if (task?.approvalPolicy === 'never' || task?.sandboxMode === 'danger-full-access' || task?.sandbox === 'danger-full-access') return 'full-access';
  if (task?.approvalsReviewer === 'auto_review') return 'auto-review';
  return 'default-review';
}

function messageLabel(type, role) {
  if (role === 'user' || type === 'user') return '你';
  switch (type) {
    case 'thinking': return '思考';
    case 'plan': return '计划';
    case 'command': return '命令';
    case 'command_output': return '命令输出';
    case 'file_change': return '文件改动';
    case 'sub_agent': return '子代理';
    case 'status': return '状态';
    default: return 'Codex';
  }
}

function isAgentBusy(agent) {
  return activeStatuses.has(String(agent?.status || '').trim().toLowerCase());
}

function isActiveThread(thread) {
  return activeStatuses.has(String(thread?.status || '').trim().toLowerCase())
    || Number(thread?.queuedFollowUpCount || 0) > 0;
}

function taskNameFromPrompt(text) {
  const clean = compact(text);
  if (!clean) return 'EasyCodex';
  return clean.length > 42 ? `${clean.slice(0, 42).trimEnd()}...` : clean;
}

function taskPreview(agent) {
  const last = [...(agent?.messages || [])].reverse().find((message) => compact(message.text));
  return compact(agent?.activityLabel || agent?.activity || last?.text || agent?.preview || '等待新消息').slice(0, 120);
}

function threadPreview(thread) {
  return compact(thread?.activityLabel || thread?.preview || '可恢复历史任务').slice(0, 140);
}

function targetKey(kind, id) {
  return id ? `${kind}:${id}` : null;
}

function parseTarget(target) {
  const [kind, ...rest] = String(target || '').split(':');
  return { kind, id: rest.join(':') };
}

function mergeThreadsById(current, threads) {
  const next = { ...current };
  for (const thread of threads || []) {
    if (!thread?.id) continue;
    next[thread.id] = { ...(next[thread.id] || {}), ...thread };
  }
  return next;
}

function removeThreadFromWorkbench(current, threadId) {
  if (!threadId) return current;
  const threadsById = { ...current.threadsById };
  delete threadsById[threadId];
  const activeThreadIds = current.activeThreadIds.filter((id) => id !== threadId);
  const historyThreadIds = current.historyThreadIds.filter((id) => id !== threadId);
  const loadingDetails = { ...current.loadingDetails };
  delete loadingDetails[threadId];
  const threadErrors = { ...current.threadErrors };
  delete threadErrors[threadId];
  let selectedTarget = current.selectedTarget;
  const selected = parseTarget(selectedTarget);
  if (selected.kind === 'thread' && selected.id === threadId) {
    selectedTarget = current.agents[0]?.id
      ? targetKey('agent', current.agents[0].id)
      : activeThreadIds[0]
        ? targetKey('thread', activeThreadIds[0])
        : historyThreadIds[0]
          ? targetKey('thread', historyThreadIds[0])
          : null;
  }
  return {
    ...current,
    threadsById,
    activeThreadIds,
    historyThreadIds,
    loadingDetails,
    threadErrors,
    selectedTarget,
  };
}

function unique(values) {
  return Array.from(new Set(values.filter(Boolean)));
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

function connectionText(state) {
  return `Relay URL: ${state?.relayUrl || ''}\nAPI Key: ${state?.apiKey || ''}\nDeep link: ${state?.deepLink || ''}`;
}

function updateInfoText(state) {
  const update = state?.update;
  const info = update?.info;
  if (update?.applying) return '正在更新';
  if (update?.checking) return '正在检查更新';
  if (update?.error) return `更新检查失败：${update.error}`;
  if (info?.updateAvailable) return `可更新到 ${info.latestVersion}`;
  if (info?.currentVersion) return `当前 ${info.currentVersion}`;
  return '未检查';
}

function stableMessageKey(message, fallback = 0) {
  return message?.itemId || `${message?.timestamp || 0}_${message?.role || ''}_${message?.type || ''}_${fallback}`;
}

function isDetailMessage(message) {
  return detailMessageTypes.has(String(message?.type || ''));
}

function isDetailGroupCandidate(message) {
  const role = message?.role === 'user' || message?.type === 'user' ? 'user' : 'agent';
  return role === 'agent' && detailGroupTypes.has(String(message?.type || ''));
}

function isInternalStatusMessage(message) {
  if (message?.type !== 'status') return false;
  const text = String(message?.text || '').trimStart();
  return String(message?.itemId || '').startsWith('tokens_')
    || String(message?.itemId || '').startsWith('queued_followups_')
    || text.startsWith('Token usage')
    || text === 'Token usage updated.'
    || (text.startsWith('已排队 ') && text.includes('个后续任务'));
}

function isPrimaryConversationVisible(message) {
  const text = String(message?.text || '').trim();
  if (!text) return false;
  if (text === '已加载项目上下文。') return false;
  if (isInternalStatusMessage(message)) return false;
  return true;
}

function detailGroupKind(messages) {
  const hasCommand = messages.some((message) => message.type === 'command' || message.type === 'command_output');
  const hasFileChange = messages.some((message) => message.type === 'file_change');
  if (hasCommand && hasFileChange) return 'mixed';
  if (hasFileChange) return 'file_change';
  return 'command';
}

function conversationListItems(messages) {
  const visible = (messages || []).filter(isPrimaryConversationVisible);
  const items = [];
  let index = 0;
  while (index < visible.length) {
    const message = visible[index];
    if (!isDetailGroupCandidate(message)) {
      items.push({ kind: 'message', message });
      index += 1;
      continue;
    }

    const group = [];
    while (index < visible.length && isDetailGroupCandidate(visible[index])) {
      group.push(visible[index]);
      index += 1;
    }

    if (group.length >= 2) {
      items.push({ kind: 'detail_group', messages: group, groupKind: detailGroupKind(group) });
    } else {
      items.push({ kind: 'message', message: group[0] });
    }
  }
  return items;
}

function formatDurationToken(raw) {
  const value = String(raw || '').trim();
  const millis = Number(value.replace(/ms$/i, ''));
  if (!Number.isFinite(millis) || !/^\d+ms$/i.test(value)) return value;
  if (millis < 1000) return `${millis}ms`;
  const seconds = Math.floor(millis / 1000);
  const minutes = Math.floor(seconds / 60);
  const restSeconds = seconds % 60;
  return minutes > 0 ? `${minutes}m ${restSeconds}s` : `${seconds}s`;
}

function compactDetailTitle(value, limit = 96) {
  const text = compact(value);
  return text.length <= limit ? text : `${text.slice(0, limit).trimEnd()}...`;
}

function commandDisplay(raw, isOutput) {
  let status = '';
  let exit = '';
  let duration = '';
  let command = '';
  for (const line of String(raw || '').split(/\r?\n/).slice(0, 80)) {
    const trimmed = line.trim();
    if (!status && trimmed.toLowerCase().startsWith('status:')) status = trimmed.slice(trimmed.indexOf(':') + 1).trim();
    else if (!exit && trimmed.toLowerCase().startsWith('exit:')) exit = trimmed.slice(trimmed.indexOf(':') + 1).trim();
    else if (!duration && trimmed.toLowerCase().startsWith('duration:')) duration = formatDurationToken(trimmed.slice(trimmed.indexOf(':') + 1).trim());
    else if (
      !command
      && trimmed
      && !['运行命令', '命令已完成', '正在运行命令。', '命令执行完成。', '命令已完成，输出已省略。'].includes(trimmed)
      && !/^cwd:/i.test(trimmed)
      && !/^status:/i.test(trimmed)
      && !/^exit:/i.test(trimmed)
      && !/^duration:/i.test(trimmed)
    ) {
      command = trimmed;
    }
  }
  const title = compactDetailTitle(
    isOutput && duration ? `已处理 ${duration}` : isOutput ? status || '已处理' : command ? `已运行 ${command}` : '命令',
  );
  const subtitle = (isOutput ? [command, exit ? `exit ${exit}` : '', status] : [status || '已开始', duration])
    .filter(Boolean)
    .join(' · ');
  return { label: isOutput ? '命令输出' : '命令', title, subtitle, body: String(raw || ''), additions: 0, deletions: 0, files: [], fileEntries: [] };
}

function parseFileSummaryLine(trimmed) {
  const bullet = trimmed.match(/^[-•]\s+(.+?)(?:\s+\(?\+(\d+)\s+-(\d+)\)?)?$/);
  const edited = trimmed.match(/^(?:已编辑|已修改|修改|edited|modified|updated)\s+(.+?)(?:\s+\(?\+(\d+)\s+-(\d+)\)?)?$/i);
  const match = bullet || edited;
  if (!match) return null;
  const path = String(match[1] || '').trim().replace(/^a\//, '').replace(/^b\//, '');
  if (!path || (!path.includes('/') && !path.includes('\\') && !path.includes('.'))) return null;
  return { path, additions: Number(match[2] || 0), deletions: Number(match[3] || 0) };
}

function parseInlineFileChangeStats(trimmed) {
  const match = trimmed.match(/\+\s*(\d+)\s+-\s*(\d+)/);
  if (!match) return null;
  return [Number(match[1] || 0), Number(match[2] || 0)];
}

function fileChangeStats(raw) {
  const paths = new Set();
  const entryStats = new Map();
  let additions = 0;
  let deletions = 0;
  const lines = String(raw || '').split(/\r?\n/);
  const rawLooksLikeDiff = lines.some((line) => line.startsWith('diff --git ') || line.startsWith('@@'));

  for (const line of lines) {
    const trimmed = line.trim();
    const summaryEntry = parseFileSummaryLine(trimmed);
    if (summaryEntry) {
      paths.add(summaryEntry.path);
      const existing = entryStats.get(summaryEntry.path);
      entryStats.set(summaryEntry.path, existing
        ? { ...existing, additions: existing.additions + summaryEntry.additions, deletions: existing.deletions + summaryEntry.deletions }
        : summaryEntry);
      continue;
    }

    const inlineStats = parseInlineFileChangeStats(trimmed);
    if (inlineStats) {
      additions += inlineStats[0];
      deletions += inlineStats[1];
    }
    if (rawLooksLikeDiff && line.startsWith('+') && !line.startsWith('+++')) additions += 1;
    if (rawLooksLikeDiff && line.startsWith('-') && !line.startsWith('---')) deletions += 1;

    const diffPath = trimmed.match(/^diff --git a\/(.+?) b\/(.+)$/)?.[2];
    const newPath = trimmed.match(/^\+\+\+ b\/(.+)$/)?.[1];
    const oldPath = trimmed.match(/^--- a\/(.+)$/)?.[1];
    const plainPath = trimmed
      && trimmed !== '文件改动'
      && trimmed !== 'Files:'
      && !/^Files:/i.test(trimmed)
      && !trimmed.startsWith('@@')
      && !trimmed.startsWith('+')
      && !trimmed.startsWith('-')
      && !/^status:/i.test(trimmed)
      && (trimmed.includes('/') || trimmed.includes('\\') || (trimmed.includes('.') && trimmed.split('.').at(-1)?.length <= 5))
      ? trimmed
      : '';
    [diffPath, newPath, oldPath, plainPath]
      .filter(Boolean)
      .map((path) => path.trim().replace(/^a\//, '').replace(/^b\//, ''))
      .filter((path) => path && path !== '/dev/null')
      .forEach((path) => paths.add(path));
  }

  const fileEntries = entryStats.size
    ? Array.from(entryStats.values())
    : Array.from(paths).map((path) => ({ path, additions: 0, deletions: 0 }));
  const summaryAdditions = fileEntries.reduce((sum, entry) => sum + Number(entry.additions || 0), 0);
  const summaryDeletions = fileEntries.reduce((sum, entry) => sum + Number(entry.deletions || 0), 0);
  return {
    files: Array.from(paths),
    additions: summaryAdditions || summaryDeletions ? summaryAdditions : additions,
    deletions: summaryAdditions || summaryDeletions ? summaryDeletions : deletions,
    fileEntries,
  };
}

function fileChangeDisplay(raw) {
  const stats = fileChangeStats(raw);
  const status = String(raw || '')
    .split(/\r?\n/)
    .find((line) => line.toLowerCase().startsWith('status:'))
    ?.split(':')
    .slice(1)
    .join(':')
    .trim() || '';
  const title = compactDetailTitle(
    stats.files.length === 1
      ? stats.files[0].split(/[\\/]/).at(-1)
      : stats.files.length > 1
        ? `${stats.files.length} 个文件改动`
        : '文件改动',
  );
  const subtitle = [
    status || '已处理',
    stats.additions + stats.deletions > 0 ? `+${stats.additions} -${stats.deletions}` : '',
    stats.files[0] || '',
  ].filter(Boolean).join(' · ');
  return { label: '文件改动', title, subtitle, body: String(raw || ''), ...stats };
}

function cleanFileChangeBody(raw) {
  const noiseLine = /^(success\s+)?(update|updated|modify|modified|edit|edited)\b.*\b(following\s+files?|files?)\b.*$/i;
  return String(raw || '')
    .split(/\r?\n/)
    .filter((line) => {
      const trimmed = line.trim();
      return !trimmed.match(/^Files:$/i) && !noiseLine.test(trimmed);
    })
    .join('\n')
    .trim();
}

function detailDisplay(message) {
  const type = String(message?.type || '');
  if (type === 'file_change') return fileChangeDisplay(message?.text || '');
  if (type === 'sub_agent') return { ...commandDisplay(message?.text || '', true), label: '子代理' };
  if (type === 'command') return commandDisplay(message?.text || '', false);
  if (type === 'command_output') return commandDisplay(message?.text || '', true);
  const title = String(message?.text || '').split(/\r?\n/).find((line) => line.trim()) || messageLabel(type, 'agent');
  return { label: messageLabel(type, 'agent'), title, subtitle: '', body: String(message?.text || ''), additions: 0, deletions: 0, files: [], fileEntries: [] };
}

function useRelayWorkbench(api, appendLog, currentState) {
  const [workbench, setWorkbench] = useState(initialWorkbench);
  const [socketEpoch, setSocketEpoch] = useState(0);
  const socketRef = useRef(null);
  const requestIdRef = useRef(1);
  const pendingRequestsRef = useRef(new Map());
  const taskRefreshTimerRef = useRef(null);
  const agentRefreshTimersRef = useRef(new Map());
  const reconnectTimerRef = useRef(null);
  const workbenchRef = useRef(workbench);

  useEffect(() => {
    workbenchRef.current = workbench;
  }, [workbench]);

  const relaySend = useCallback((action, params = {}) => {
    const socket = socketRef.current;
    if (!socket || socket.readyState !== WebSocket.OPEN || workbenchRef.current.relaySocketState !== 'online') {
      return Promise.reject(new Error('Relay WebSocket is not connected.'));
    }
    const requestId = `desktop_${requestIdRef.current++}`;
    socket.send(JSON.stringify({ action, params, requestId }));
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        pendingRequestsRef.current.delete(requestId);
        reject(new Error(`${action} timed out`));
      }, 30000);
      pendingRequestsRef.current.set(requestId, { resolve, reject, timer });
    });
  }, []);

  const rejectPendingRelayRequests = useCallback((error) => {
    const pending = Array.from(pendingRequestsRef.current.values());
    pendingRequestsRef.current.clear();
    pending.forEach((request) => {
      clearTimeout(request.timer);
      request.reject(error);
    });
  }, []);

  const setSocketStatus = useCallback((state, text) => {
    setWorkbench((current) => ({ ...current, relaySocketState: state, relaySocketText: text }));
  }, []);

  const rememberPendingRequest = useCallback((agentId, data) => {
    if (!agentId || !data?.requestId) return;
    setWorkbench((current) => {
      const requests = current.pendingRequestsByAgent[agentId] || [];
      return {
        ...current,
        pendingRequestsByAgent: {
          ...current.pendingRequestsByAgent,
          [agentId]: [...requests.filter((entry) => entry.requestId !== data.requestId), data],
        },
      };
    });
  }, []);

  const resolvePendingRequest = useCallback((agentId, requestId) => {
    setWorkbench((current) => ({
      ...current,
      pendingRequestsByAgent: {
        ...current.pendingRequestsByAgent,
        [agentId]: (current.pendingRequestsByAgent[agentId] || []).filter((entry) => entry.requestId !== requestId),
      },
    }));
  }, []);

  const refreshAgent = useCallback(async (agentId) => {
    if (!agentId) return;
    const agent = await relaySend('get_agent', { agentId });
    setWorkbench((current) => {
      const agents = current.agents.some((entry) => entry.id === agent.id)
        ? current.agents.map((entry) => (entry.id === agent.id ? agent : entry))
        : [agent, ...current.agents];
      return {
        ...current,
        agents,
        pendingRequestsByAgent: Array.isArray(agent.pendingRequests)
          ? { ...current.pendingRequestsByAgent, [agent.id]: agent.pendingRequests }
          : current.pendingRequestsByAgent,
      };
    });
  }, [relaySend]);

  const refreshSelectedThread = useCallback(async (threadId) => {
    const targetId = threadId || parseTarget(workbenchRef.current.selectedTarget).id;
    if (!targetId || workbenchRef.current.relaySocketState !== 'online') return;
    setWorkbench((current) => ({
      ...current,
      loadingDetails: { ...current.loadingDetails, [targetId]: true },
      threadErrors: { ...current.threadErrors, [targetId]: '' },
    }));
    try {
      const detail = await relaySend('read_codex_thread', { threadId: targetId });
      setWorkbench((current) => ({
        ...current,
        threadsById: mergeThreadsById(current.threadsById, [detail]),
        historyThreadIds: current.historyThreadIds.includes(targetId)
          ? current.historyThreadIds
          : unique([targetId, ...current.historyThreadIds]),
        loadingDetails: { ...current.loadingDetails, [targetId]: false },
      }));
    } catch (error) {
      setWorkbench((current) => ({
        ...current,
        loadingDetails: { ...current.loadingDetails, [targetId]: false },
        threadErrors: { ...current.threadErrors, [targetId]: error.message || String(error) },
      }));
      appendLog(`Thread detail load failed: ${error.message || error}`);
    }
  }, [appendLog, relaySend]);

  const refreshHistory = useCallback(async () => {
    const result = await relaySend('list_codex_threads', { all: true, limit: 80 });
    const threads = (result?.data || []).filter((thread) => thread?.id);
    setWorkbench((current) => ({
      ...current,
      threadsById: mergeThreadsById(current.threadsById, threads),
      historyThreadIds: threads
        .map((thread) => thread.id)
        .filter((id) => !current.activeThreadIds.includes(id))
        .filter((id) => !current.agents.some((agent) => (agent.codexThreadId || agent.threadId) === id)),
    }));
  }, [relaySend]);

  const refreshTasks = useCallback(async (opts = {}) => {
    if (workbenchRef.current.relaySocketState !== 'online') return;
    setWorkbench((current) => ({ ...current, refreshingTasks: true }));
    try {
      const [agents, activeResult] = await Promise.all([
        relaySend('list_agents'),
        relaySend('list_codex_threads', { all: true, limit: 80, activeOnly: true }).catch((error) => {
          appendLog(`Active thread load failed: ${error.message || error}`);
          return { data: [] };
        }),
      ]);
      const activeThreads = (activeResult?.data || []).filter((thread) => thread?.id && isActiveThread(thread));
      setWorkbench((current) => {
        const agentThreadIds = new Set(agents.map((agent) => agent.codexThreadId || agent.threadId).filter(Boolean));
        const activeThreadIds = activeThreads
          .filter((thread) => !agentThreadIds.has(thread.id))
          .sort((left, right) => Number(right.updatedAt || 0) - Number(left.updatedAt || 0))
          .map((thread) => thread.id);
        let selectedTarget = current.selectedTarget;
        if (!selectedTarget && agents[0]) selectedTarget = targetKey('agent', agents[0].id);
        if (!selectedTarget && activeThreadIds[0]) selectedTarget = targetKey('thread', activeThreadIds[0]);
        return {
          ...current,
          agents,
          activeThreadIds,
          selectedTarget,
          threadsById: mergeThreadsById(current.threadsById, activeThreads),
          pendingRequestsByAgent: agents.reduce((acc, agent) => {
            if (Array.isArray(agent.pendingRequests)) acc[agent.id] = agent.pendingRequests;
            return acc;
          }, { ...current.pendingRequestsByAgent }),
          refreshingTasks: false,
        };
      });
      if (opts.includeHistory || workbenchRef.current.taskFilter === 'history') {
        await refreshHistory();
      }
    } catch (error) {
      appendLog(`Task refresh failed: ${error.message || error}`);
      setWorkbench((current) => ({ ...current, refreshingTasks: false }));
    }
  }, [appendLog, refreshHistory, relaySend]);

  const scheduleTaskRefresh = useCallback(() => {
    clearTimeout(taskRefreshTimerRef.current);
    taskRefreshTimerRef.current = setTimeout(() => {
      refreshTasks().catch((error) => appendLog(`Task refresh failed: ${error.message || error}`));
    }, 160);
  }, [appendLog, refreshTasks]);

  const scheduleAgentRefresh = useCallback((agentId) => {
    if (!agentId || agentId === 'system') return;
    const timers = agentRefreshTimersRef.current;
    clearTimeout(timers.get(agentId));
    timers.set(agentId, setTimeout(() => {
      timers.delete(agentId);
      refreshAgent(agentId).catch((error) => appendLog(`Task read failed: ${error.message || error}`));
    }, 140));
  }, [appendLog, refreshAgent]);

  const handleRelayResponse = useCallback((message) => {
    const pending = pendingRequestsRef.current.get(message.requestId);
    if (!pending) return;
    clearTimeout(pending.timer);
    pendingRequestsRef.current.delete(message.requestId);
    if (message.type === 'error') pending.reject(new Error(message.error || 'Relay request failed'));
    else pending.resolve(message.data);
  }, []);

  const handleRelayStream = useCallback((entry) => {
    if (!entry || entry.type !== 'stream') return;
    const archivedThreadId = entry.event === 'codex/threads_changed' && entry.data?.reason === 'thread_archived'
      ? entry.data?.threadId
      : '';
    if (archivedThreadId) {
      setWorkbench((current) => removeThreadFromWorkbench(current, archivedThreadId));
    }
    if (entry.event === 'agents/changed' || entry.event === 'codex/threads_changed') {
      scheduleTaskRefresh();
    }
    if (entry.event === 'codex/threads_changed') {
      const parsed = parseTarget(workbenchRef.current.selectedTarget);
      if (parsed.kind === 'thread' && parsed.id && parsed.id !== archivedThreadId) {
        refreshSelectedThread(parsed.id).catch((error) => appendLog(`Thread refresh failed: ${error.message || error}`));
      }
    }
    if (entry.event === 'agent/requested') rememberPendingRequest(entry.agentId, entry.data);
    if (entry.event === 'agent/request_resolved') resolvePendingRequest(entry.agentId, entry.data?.requestId);
    scheduleAgentRefresh(entry.agentId);
  }, [appendLog, refreshSelectedThread, rememberPendingRequest, resolvePendingRequest, scheduleAgentRefresh, scheduleTaskRefresh]);

  useEffect(() => {
    let disposed = false;
    if (reconnectTimerRef.current) {
      clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = null;
    }
    if (!currentState?.relayRunning || !currentState?.relayUrl || !currentState?.apiKey) {
      if (socketRef.current) socketRef.current.close();
      socketRef.current = null;
      rejectPendingRelayRequests(new Error('Relay WebSocket is not connected.'));
      setSocketStatus('offline', '等待中继启动');
      return;
    }
    const existing = socketRef.current;
    if (existing && [WebSocket.CONNECTING, WebSocket.OPEN].includes(existing.readyState)) return;

    const scheduleReconnect = () => {
      if (disposed || !currentState?.relayRunning || !currentState?.relayUrl || !currentState?.apiKey) return;
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      reconnectTimerRef.current = setTimeout(() => {
        reconnectTimerRef.current = null;
        setSocketEpoch((value) => value + 1);
      }, 1500);
    };

    setSocketStatus('offline', '正在连接任务流...');
    const socket = new WebSocket(currentState.relayUrl);
    socketRef.current = socket;
    socket.addEventListener('open', () => {
      socket.send(JSON.stringify({
        action: 'auth',
        params: { key: currentState.apiKey, clientId: 'easycodex-desktop-workbench' },
        requestId: `desktop_auth_${Date.now()}`,
      }));
    });
    socket.addEventListener('message', (event) => {
      let message = null;
      try {
        message = JSON.parse(event.data);
      } catch {
        return;
      }
      if (message.type === 'response' && message.action === 'auth') {
        setSocketStatus('online', '任务流已连接');
        setTimeout(() => refreshTasks({ includeHistory: true }), 0);
        return;
      }
      if (message.type === 'response' || message.type === 'error') {
        handleRelayResponse(message);
        return;
      }
      handleRelayStream(message);
    });
    socket.addEventListener('close', () => {
      if (disposed) return;
      if (socketRef.current === socket) socketRef.current = null;
      rejectPendingRelayRequests(new Error('Relay WebSocket closed.'));
      setSocketStatus('offline', '任务流已断开，保留上次任务列表');
      scheduleReconnect();
    });
    socket.addEventListener('error', () => {
      if (disposed) return;
      rejectPendingRelayRequests(new Error('Relay WebSocket connection failed.'));
      setSocketStatus('error', '任务流连接失败');
      scheduleReconnect();
    });
    return () => {
      disposed = true;
      if (reconnectTimerRef.current) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      rejectPendingRelayRequests(new Error('Relay WebSocket closed.'));
      socket.close();
    };
  }, [currentState?.apiKey, currentState?.relayRunning, currentState?.relayUrl, handleRelayResponse, handleRelayStream, refreshTasks, rejectPendingRelayRequests, setSocketStatus, socketEpoch]);

  const setTaskFilter = useCallback((filter) => {
    setWorkbench((current) => ({
      ...current,
      taskFilter: filter,
      selectedTarget: filter === 'active'
        ? current.selectedTarget
        : current.selectedTarget?.startsWith('thread:')
          ? current.selectedTarget
          : null,
    }));
    if (filter === 'history') refreshHistory().catch((error) => appendLog(`History load failed: ${error.message || error}`));
  }, [appendLog, refreshHistory]);

  const selectTarget = useCallback((target) => {
    setWorkbench((current) => ({ ...current, selectedTarget: target }));
    const parsed = parseTarget(target);
    if (parsed.kind === 'thread') refreshSelectedThread(parsed.id);
    if (parsed.kind === 'agent') refreshAgent(parsed.id).catch((error) => appendLog(`Task read failed: ${error.message || error}`));
  }, [appendLog, refreshAgent, refreshSelectedThread]);

  const refreshGitContext = useCallback(async (agent, force = false) => {
    if (!agent?.cwd || workbenchRef.current.relaySocketState !== 'online') return;
    const cwd = agent.cwd;
    if (!force && (workbenchRef.current.gitContextByCwd[cwd] || workbenchRef.current.loadingGitCwds[cwd])) return;
    setWorkbench((current) => ({
      ...current,
      loadingGitCwds: { ...current.loadingGitCwds, [cwd]: true },
    }));
    try {
      const [status, diff] = await Promise.all([
        relaySend('git_status', { cwd }),
        relaySend('git_diff', { cwd }),
      ]);
      setWorkbench((current) => ({
        ...current,
        gitContextByCwd: {
          ...current.gitContextByCwd,
          [cwd]: { status, diff: diff?.diff || '', loadedAt: Date.now(), error: '' },
        },
        loadingGitCwds: { ...current.loadingGitCwds, [cwd]: false },
      }));
    } catch (error) {
      setWorkbench((current) => ({
        ...current,
        gitContextByCwd: {
          ...current.gitContextByCwd,
          [cwd]: { status: null, diff: '', loadedAt: Date.now(), error: error.message || String(error) },
        },
        loadingGitCwds: { ...current.loadingGitCwds, [cwd]: false },
      }));
    }
  }, [relaySend]);

  return {
    workbench,
    relaySend,
    refreshTasks,
    refreshHistory,
    refreshAgent,
    refreshSelectedThread,
    refreshGitContext,
    resolvePendingRequest,
    setTaskFilter,
    selectTarget,
    setWorkbench,
  };
}

function App() {
  const api = window.easyCodexRelay;
  const [appState, setAppState] = useState(null);
  const [health, setHealth] = useState(null);
  const [logs, setLogs] = useState([]);
  const [pendingAction, setPendingAction] = useState('');
  const [setupOpen, setSetupOpen] = useState(false);
  const [quickOpen, setQuickOpen] = useState(true);
  const [logsOpen, setLogsOpen] = useState(false);
  const [activeSection, setActiveSection] = useState('config');
  const [draftPort, setDraftPort] = useState('');
  const [draftWorkspace, setDraftWorkspace] = useState('');
  const [draftCodexPath, setDraftCodexPath] = useState('');
  const [composerText, setComposerText] = useState('');
  const [portPreview, setPortPreview] = useState(null);
  const draftDirtyRef = useRef({ port: false, workspace: false, codexPath: false });
  const portPreviewSeqRef = useRef(0);

  const appendLog = useCallback((line) => {
    const text = String(line || '').trimEnd();
    if (!text) return;
    setLogs((current) => [...current.slice(-500), text]);
  }, []);

  const {
    workbench,
    relaySend,
    refreshTasks,
    refreshAgent,
    refreshGitContext,
    resolvePendingRequest,
    setTaskFilter,
    selectTarget,
    setWorkbench,
  } = useRelayWorkbench(api, appendLog, appState);

  const syncDraftsFromState = useCallback((state, force = false) => {
    if (force || !draftDirtyRef.current.port) setDraftPort(String(state.port || ''));
    if (force || !draftDirtyRef.current.workspace) setDraftWorkspace(state.workspace || '');
    if (force || !draftDirtyRef.current.codexPath) setDraftCodexPath(state.codexPath || state.codex?.path || '');
  }, []);

  const updateDraftPort = useCallback((value) => {
    draftDirtyRef.current.port = true;
    setDraftPort(value);
  }, []);

  const updateDraftWorkspace = useCallback((value) => {
    draftDirtyRef.current.workspace = true;
    setDraftWorkspace(value);
  }, []);

  const updateDraftCodexPath = useCallback((value) => {
    draftDirtyRef.current.codexPath = true;
    setDraftCodexPath(value);
  }, []);

  useEffect(() => {
    api.onState((state) => {
      setAppState(state);
      syncDraftsFromState(state);
    });
    api.onHealth((nextHealth) => setHealth(nextHealth));
    api.onLog(appendLog);
    api.getState().then((state) => {
      setAppState(state);
      setHealth(state.health || null);
      syncDraftsFromState(state, true);
    }).catch((error) => appendLog(`Error: ${error.message || error}`));
  }, [api, appendLog, syncDraftsFromState]);

  useEffect(() => {
    if (!appState || !draftPort) return;
    const requestSeq = ++portPreviewSeqRef.current;
    const requestedPort = String(draftPort).trim();
    const timer = setTimeout(() => {
      api.previewPort({ port: requestedPort })
        .then((preview) => {
          if (requestSeq === portPreviewSeqRef.current && requestedPort === String(draftPort).trim()) setPortPreview(preview);
        })
        .catch((error) => {
          if (requestSeq === portPreviewSeqRef.current && requestedPort === String(draftPort).trim()) {
            setPortPreview({ port: Number(requestedPort), portAvailable: false, portReclaimable: false, processes: [], message: error.message || String(error) });
          }
        });
    }, 260);
    return () => clearTimeout(timer);
  }, [api, appState, draftPort]);

  const runAction = useCallback(async (key, action, options = {}) => {
    setPendingAction(key);
    try {
      const nextState = await action();
      if (nextState) {
        setAppState(nextState);
        const savedDrafts = options.savedDrafts || [];
        if (savedDrafts.length > 0) {
          draftDirtyRef.current = {
            ...draftDirtyRef.current,
            ...savedDrafts.reduce((acc, field) => ({ ...acc, [field]: false }), {}),
          };
        }
        syncDraftsFromState(nextState);
      }
    } catch (error) {
      appendLog(`Error: ${error.message || error}`);
    } finally {
      setPendingAction('');
    }
  }, [appendLog, syncDraftsFromState]);

  const activeAgent = useMemo(() => {
    const parsed = parseTarget(workbench.selectedTarget);
    if (parsed.kind !== 'agent') return null;
    return workbench.agents.find((agent) => agent.id === parsed.id) || null;
  }, [workbench.agents, workbench.selectedTarget]);

  const selectedThread = useMemo(() => {
    const parsed = parseTarget(workbench.selectedTarget);
    if (parsed.kind !== 'thread') return null;
    return workbench.threadsById[parsed.id] || null;
  }, [workbench.selectedTarget, workbench.threadsById]);

  const activeItems = useMemo(() => {
    const agentThreadIds = new Set(workbench.agents.map((agent) => agent.codexThreadId || agent.threadId).filter(Boolean));
    const threadItems = workbench.activeThreadIds
      .filter((id) => !agentThreadIds.has(id))
      .map((id) => workbench.threadsById[id])
      .filter(Boolean)
      .map((thread) => ({ ...thread, __kind: 'thread' }));
    return [
      ...workbench.agents.map((agent) => ({ ...agent, __kind: 'agent' })),
      ...threadItems,
    ];
  }, [workbench.activeThreadIds, workbench.agents, workbench.threadsById]);

  const historyItems = useMemo(() => {
    const agentThreadIds = new Set(workbench.agents.map((agent) => agent.codexThreadId || agent.threadId).filter(Boolean));
    const activeThreadIds = new Set(workbench.activeThreadIds);
    return workbench.historyThreadIds
      .map((id) => workbench.threadsById[id])
      .filter(Boolean)
      .filter((thread) => !agentThreadIds.has(thread.id))
      .filter((thread) => !activeThreadIds.has(thread.id) || targetKey('thread', thread.id) === workbench.selectedTarget)
      .sort((left, right) => Number(right.updatedAt || 0) - Number(left.updatedAt || 0))
      .map((thread) => ({ ...thread, __kind: 'history' }));
  }, [workbench.activeThreadIds, workbench.agents, workbench.historyThreadIds, workbench.selectedTarget, workbench.threadsById]);

  const visibleTasks = useMemo(() => {
    const source = workbench.taskFilter === 'history' ? historyItems : activeItems;
    const query = compact(workbench.taskSearch).toLowerCase();
    if (!query) return source;
    return source.filter((item) => [
      item.name,
      item.preview,
      item.status,
      item.model,
      item.cwd,
      item.projectRoot,
      item.__kind === 'agent' ? taskPreview(item) : threadPreview(item),
    ].filter(Boolean).join(' ').toLowerCase().includes(query));
  }, [activeItems, historyItems, workbench.taskFilter, workbench.taskSearch]);

  const previewMatchesDraft = portPreview && String(portPreview.port || '') === String(draftPort).trim();
  const draftPortDirty = draftDirtyRef.current.port;
  const canUsePort = previewMatchesDraft
    ? portPreview?.portAvailable || portPreview?.portReclaimable
    : draftPortDirty
      ? false
    : appState?.portAvailable || appState?.portReclaimable;
  const isBusy = Boolean(pendingAction || appState?.installRunning || appState?.update?.checking || appState?.update?.applying);
  const connectedClients = health?.data?.connectedClients ?? appState?.health?.data?.connectedClients ?? 0;
  const relayOnline = Boolean(appState?.relayRunning && (health?.online || appState?.health?.online));
  const canStartRelay = Boolean(appState?.relayReady && canUsePort && appState?.codex?.installed && !appState?.relayRunning);
  const startRelay = useCallback(() => {
    runAction('launching', () => api.startRelay({ port: draftPort, workspace: draftWorkspace, codexPath: draftCodexPath }));
  }, [api, draftCodexPath, draftPort, draftWorkspace, runAction]);
  const stopRelay = useCallback(() => {
    runAction('stopping', () => api.stopRelay());
  }, [api, runAction]);

  const handleSend = useCallback(async (event) => {
    event.preventDefault();
    const text = composerText.trim();
    if (!text || !activeAgent) return;
    try {
      let targetAgent = activeAgent;
      if (isAgentBusy(activeAgent)) {
        targetAgent = await relaySend('create_agent', {
          name: taskNameFromPrompt(text),
          model: activeAgent.model || 'gpt-5.5',
          cwd: activeAgent.cwd || appState?.workspace,
          permissionMode: permissionModeForTask(activeAgent),
          serviceTier: activeAgent.serviceTier,
          reasoningEffort: activeAgent.reasoningEffort,
        });
        setWorkbench((current) => ({ ...current, agents: [targetAgent, ...current.agents], selectedTarget: targetKey('agent', targetAgent.id), taskFilter: 'active' }));
      }
      await relaySend('send_message', { agentId: targetAgent.id, text });
      setComposerText('');
      await refreshAgent(targetAgent.id);
    } catch (error) {
      appendLog(`Send failed: ${error.message || error}`);
    }
  }, [activeAgent, appState?.workspace, appendLog, composerText, refreshAgent, relaySend, setWorkbench]);

  const handleResumeThread = useCallback(async () => {
    if (!selectedThread) return;
    try {
      const agent = await relaySend('create_agent', {
        name: selectedThread.name || selectedThread.preview || 'Resumed Codex task',
        model: selectedThread.model || 'gpt-5.5',
        cwd: selectedThread.cwd || selectedThread.projectRoot || appState?.workspace,
        permissionMode: permissionModeForTask(selectedThread),
        serviceTier: selectedThread.serviceTier,
        reasoningEffort: selectedThread.reasoningEffort,
        codexThreadId: selectedThread.id,
      });
      setWorkbench((current) => ({
        ...current,
        agents: [agent, ...current.agents.filter((entry) => entry.id !== agent.id)],
        selectedTarget: targetKey('agent', agent.id),
        taskFilter: 'active',
      }));
      await refreshTasks();
    } catch (error) {
      appendLog(`Resume failed: ${error.message || error}`);
    }
  }, [appState?.workspace, appendLog, refreshTasks, relaySend, selectedThread, setWorkbench]);

  const handleStopAgent = useCallback(async () => {
    if (!activeAgent) return;
    try {
      await relaySend('stop_agent', { agentId: activeAgent.id });
      await refreshTasks();
    } catch (error) {
      appendLog(`Stop failed: ${error.message || error}`);
    }
  }, [activeAgent, appendLog, refreshTasks, relaySend]);

  useEffect(() => {
    if (activeAgent?.cwd) refreshGitContext(activeAgent).catch((error) => appendLog(`Git refresh failed: ${error.message || error}`));
  }, [activeAgent?.cwd, appendLog, refreshGitContext]);

  return (
    <div className="app-shell">
      <Titlebar api={api} />
      <main className="page">
        <AppToolbar
          appState={appState}
          relayOnline={relayOnline}
          connectedClients={connectedClients}
          pendingAction={pendingAction}
          activeSection={activeSection}
          onSectionChange={setActiveSection}
          isBusy={isBusy}
          canStart={canStartRelay}
          onStart={startRelay}
          onStop={stopRelay}
        />

        {activeSection === 'config' ? (
          <ConfigScreen
            appState={appState}
            api={api}
            workbench={workbench}
            logs={logs}
            onClearLogs={() => setLogs([])}
            isBusy={isBusy}
            portPreview={portPreview}
            setPortPreview={setPortPreview}
            draftPort={draftPort}
            draftWorkspace={draftWorkspace}
            draftCodexPath={draftCodexPath}
            setDraftPort={updateDraftPort}
            setDraftWorkspace={updateDraftWorkspace}
            setDraftCodexPath={updateDraftCodexPath}
            runAction={runAction}
            updateText={updateInfoText(appState)}
          />
        ) : (
          <TasksScreen
            activeAgent={activeAgent}
            selectedThread={selectedThread}
            workbench={workbench}
            activeItems={activeItems}
            historyItems={historyItems}
            visibleTasks={visibleTasks}
            composerText={composerText}
            setComposerText={setComposerText}
            appState={appState}
            api={api}
            connectedClients={connectedClients}
            refreshTasks={refreshTasks}
            setTaskFilter={setTaskFilter}
            setWorkbench={setWorkbench}
            selectTarget={selectTarget}
            handleSend={handleSend}
            handleResumeThread={handleResumeThread}
            handleStopAgent={handleStopAgent}
            refreshGitContext={refreshGitContext}
            relaySend={relaySend}
            resolvePendingRequest={resolvePendingRequest}
            refreshAgent={refreshAgent}
            appendLog={appendLog}
          />
        )}
      </main>
    </div>
  );
}

function Titlebar({ api }) {
  return (
    <header className="titlebar">
      <div className="titlebar-drag">
        <img className="app-mark" src="../../assets/icon.png" alt="" />
        <span>EasyCodex Relay</span>
      </div>
      <div className="window-actions">
        <button type="button" title="Minimize" onClick={() => api.minimizeWindow()}>-</button>
        <button type="button" title="Close" onClick={() => api.closeWindow()}>x</button>
      </div>
    </header>
  );
}

function AppToolbar({ appState, relayOnline, connectedClients, pendingAction, activeSection, onSectionChange, isBusy, canStart, onStart, onStop }) {
  const statusText = pendingAction
    ? ({ launching: '正在启动', stopping: '正在停止', installing: '正在安装', updating: '正在更新', saving: '正在保存', checking: '正在检查' }[pendingAction] || '处理中')
    : relayOnline
      ? '在线'
      : appState?.relayRunning
        ? '启动中'
        : '未启动';
  const healthText = relayOnline
    ? connectedClients > 0 ? `手机 ${connectedClients}` : '等待手机'
    : appState?.relayRunning ? '健康检查中' : '中继未运行';
  return (
    <header className="app-toolbar">
      <div className="toolbar-brand">
        <strong>EasyCodex</strong>
        <span>{statusText} · {healthText}</span>
      </div>
      <nav className="main-tabs" aria-label="主菜单">
        <button type="button" className={activeSection === 'config' ? 'active' : ''} onClick={() => onSectionChange('config')}>
          配置
        </button>
        <button type="button" className={activeSection === 'tasks' ? 'active' : ''} onClick={() => onSectionChange('tasks')}>
          任务
        </button>
      </nav>
      <div className="toolbar-status">
        <span className={`status-dot ${relayOnline ? 'online' : appState?.relayRunning ? 'starting' : ''}`} />
        <span>{appState?.port || '-'} 端口</span>
        {appState?.relayRunning
          ? <button className="secondary danger" type="button" disabled={isBusy} onClick={onStop}>停止</button>
          : <button className="primary" type="button" disabled={isBusy || !canStart} onClick={onStart}>启动中继</button>}
      </div>
    </header>
  );
}

function ConfigScreen({
  appState,
  api,
  workbench,
  logs,
  onClearLogs,
  isBusy,
  portPreview,
  setPortPreview,
  draftPort,
  draftWorkspace,
  draftCodexPath,
  setDraftPort,
  setDraftWorkspace,
  setDraftCodexPath,
  runAction,
  updateText,
}) {
  return (
    <section className="config-view">
      <div className="config-hero">
        <div>
          <p className="eyebrow">Desktop Relay</p>
          <h1>配置总控</h1>
          <p>先把连接、Codex 路径和更新状态放在这里；任务页只负责执行和对话。</p>
        </div>
        <div className="config-status-card">
          <span className={`status-dot ${workbench.relaySocketState}`} />
          <strong>{workbench.relaySocketText}</strong>
          <small>{updateText}</small>
        </div>
      </div>
      <div className="config-grid">
        <section className="config-card quick-card">
          <div className="card-heading">
            <h2>快速连接</h2>
            <span>{appState?.relayRunning ? '可扫码' : '等待启动'}</span>
          </div>
          <QuickConnect state={appState} api={api} portPreview={portPreview} draftPort={draftPort} />
        </section>
        <section className="config-card settings-card">
          <div className="card-heading">
            <h2>设置</h2>
            <span>{appState?.codex?.installed ? 'Codex 已就绪' : '需要配置'}</span>
          </div>
          <SetupPanel
            api={api}
            appState={appState}
            isBusy={isBusy}
            portPreview={portPreview}
            setPortPreview={setPortPreview}
            draftPort={draftPort}
            draftWorkspace={draftWorkspace}
            draftCodexPath={draftCodexPath}
            setDraftPort={setDraftPort}
            setDraftWorkspace={setDraftWorkspace}
            setDraftCodexPath={setDraftCodexPath}
            runAction={runAction}
          />
        </section>
        <section className="config-card log-card">
          <div className="card-heading">
            <h2>日志</h2>
            <span>{logs.length} 行</span>
          </div>
          <LogPanel logs={logs} onClear={onClearLogs} />
        </section>
      </div>
    </section>
  );
}

function TasksScreen({
  activeAgent,
  selectedThread,
  workbench,
  activeItems,
  historyItems,
  visibleTasks,
  composerText,
  setComposerText,
  appState,
  api,
  connectedClients,
  refreshTasks,
  setTaskFilter,
  setWorkbench,
  selectTarget,
  handleSend,
  handleResumeThread,
  handleStopAgent,
  refreshGitContext,
  relaySend,
  resolvePendingRequest,
  refreshAgent,
  appendLog,
}) {
  return (
    <section className="workbench">
      <TaskRail
        workbench={workbench}
        activeCount={activeItems.length}
        historyCount={historyItems.length}
        visibleTasks={visibleTasks}
        onRefresh={() => refreshTasks({ includeHistory: workbench.taskFilter === 'history' })}
        onFilter={setTaskFilter}
        onSearch={(taskSearch) => setWorkbench((current) => ({ ...current, taskSearch }))}
        onSelect={selectTarget}
      />
      <ConversationPane
        agent={activeAgent}
        thread={selectedThread}
        workbench={workbench}
        composerText={composerText}
        setComposerText={setComposerText}
        onSend={handleSend}
        onResumeThread={handleResumeThread}
        onStopAgent={handleStopAgent}
      />
      <ContextPane
        agent={activeAgent}
        thread={selectedThread}
        workbench={workbench}
        state={appState}
        api={api}
        updateText={updateInfoText(appState)}
        connectedClients={connectedClients}
        onRefreshGit={() => activeAgent && refreshGitContext(activeAgent, true)}
        onApprove={async (request, approved) => {
          if (!activeAgent || !request?.requestId) return;
          try {
            await relaySend('respond_agent_request', {
              agentId: activeAgent.id,
              requestId: request.requestId,
              approved,
              reason: approved ? 'Approved from EasyCodex desktop' : 'Denied from EasyCodex desktop',
            });
            resolvePendingRequest(activeAgent.id, request.requestId);
            await refreshAgent(activeAgent.id);
          } catch (error) {
            appendLog(`Approval failed: ${error.message || error}`);
          }
        }}
      />
    </section>
  );
}

function TopSummary({ appState, health, relayOnline, connectedClients, pendingAction, isBusy, canStart, onStart, onStop }) {
  const statusText = pendingAction
    ? ({ launching: '正在启动', stopping: '正在停止', installing: '正在安装', updating: '正在更新' }[pendingAction] || '处理中')
    : relayOnline
      ? '在线'
      : appState?.relayRunning
        ? '启动中'
        : '未启动';
  const healthText = relayOnline
    ? connectedClients > 0 ? `手机已连接 ${connectedClients}` : '等待手机连接'
    : appState?.relayRunning ? '等待健康检查' : '本机中继未运行';
  return (
    <section className="top-summary">
      <div className="brand-block">
        <p className="eyebrow">Desktop Relay</p>
        <h1>EasyCodex 中继站</h1>
        <p>连接手机、管理 Codex 任务，并在电脑端稳定恢复历史线程。</p>
      </div>
      <div className="summary-cards">
        <div className={`summary-card ${relayOnline ? 'online' : appState?.relayRunning ? 'starting' : 'offline'}`}>
          <span className="status-dot" />
          <div>
            <strong>{statusText}</strong>
            <small>{healthText}</small>
          </div>
        </div>
        <div className="summary-card">
          <strong>{appState?.port || '-'}</strong>
          <small>端口</small>
        </div>
        <div className="summary-card">
          <strong>{appState?.updateChannel || 'stable'}</strong>
          <small>{updateInfoText(appState)}</small>
        </div>
        <div className="summary-actions">
          {appState?.relayRunning
            ? <button className="secondary danger" type="button" disabled={isBusy} onClick={onStop}>停止</button>
            : <button className="primary" type="button" disabled={isBusy || !canStart} onClick={onStart}>启动中继</button>}
        </div>
      </div>
    </section>
  );
}

function Disclosure({ title, badge, open, onToggle, children }) {
  return (
    <section className={`disclosure ${open ? 'open' : ''}`}>
      <button className="disclosure-header" type="button" onClick={onToggle}>
        <span>{title}</span>
        <small>{badge}</small>
      </button>
      {open && <div className="disclosure-body">{children}</div>}
    </section>
  );
}

function QuickConnect({ state, api, portPreview, draftPort }) {
  const previewMatchesDraft = portPreview && String(portPreview.port || '') === String(draftPort || '').trim();
  const displayState = previewMatchesDraft ? { ...state, ...portPreview } : state;
  return (
    <div className="quick-connect">
      <div className="qr-frame">
        {displayState?.qrDataUrl ? <img src={displayState.qrDataUrl} alt="EasyCodex relay QR" /> : <span>等待二维码</span>}
      </div>
      <div className="quick-copy">
        <p>{displayState?.relayUrl ? `Relay: ${displayState.relayUrl}` : '启动中继后，手机扫码即可导入地址和 API Key。'}</p>
        <div className="button-row">
          <button className="secondary" type="button" disabled={!displayState} onClick={() => api.copyText(connectionText(displayState))}>复制连接</button>
          <button className="secondary" type="button" disabled={!displayState?.deepLink} onClick={() => api.copyText(displayState.deepLink)}>复制深链</button>
        </div>
      </div>
    </div>
  );
}

function SetupPanel({
  api,
  appState,
  isBusy,
  portPreview,
  setPortPreview,
  draftPort,
  draftWorkspace,
  draftCodexPath,
  setDraftPort,
  setDraftWorkspace,
  setDraftCodexPath,
  runAction,
}) {
  const portStatus = portPreview
    ? portPreview.portAvailable ? '端口可用' : portPreview.portReclaimable ? '可重启已有中继' : '端口被占用'
    : appState?.portAvailable ? '端口可用' : appState?.portReclaimable ? '可重启已有中继' : '端口被占用';
  return (
    <div className="setup-grid">
      <label>
        <span>语言</span>
        <select
          value={appState?.languageMode === 'follow-phone' ? 'system' : appState?.language || 'system'}
          disabled={isBusy}
          onChange={(event) => {
            const language = event.currentTarget.value;
            const languageMode = language === 'system' ? 'follow-phone' : 'manual';
            runAction('saving', () => api.saveConfig({ language, languageMode }));
          }}
        >
          {languageOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}
        </select>
      </label>
      <label>
        <span>更新通道</span>
        <select
          value={appState?.updateChannel || 'stable'}
          disabled={isBusy}
          onChange={(event) => runAction('saving', () => api.saveConfig({ updateChannel: event.currentTarget.value }))}
        >
          <option value="stable">Stable</option>
          <option value="beta">Beta</option>
        </select>
      </label>
      <label>
        <span>端口</span>
        <input value={draftPort} disabled={isBusy || appState?.relayRunning} onInput={(event) => setDraftPort(event.currentTarget.value)} />
        <small className={portStatus.includes('占用') ? 'danger-text' : 'ok-text'}>{portStatus}</small>
      </label>
      <div className="wide">
        <LightModePanel
          api={api}
          appState={appState}
          isBusy={isBusy}
          runAction={runAction}
        />
      </div>
      <div className="wide">
        <PortInspector
          api={api}
          isBusy={isBusy}
          port={draftPort}
          preview={portPreview}
          setPreview={setPortPreview}
        />
      </div>
      <label className="wide">
        <span>默认工作区</span>
        <div className="input-row">
          <input value={draftWorkspace} disabled={isBusy} onInput={(event) => setDraftWorkspace(event.currentTarget.value)} />
          <button className="icon-button" type="button" disabled={isBusy} onClick={async () => {
            const workspace = await api.browseWorkspace();
            if (workspace) setDraftWorkspace(workspace);
          }}>...</button>
        </div>
      </label>
      <label className="wide">
        <span>Codex 可执行文件</span>
        <div className="input-row">
          <input value={draftCodexPath} disabled={isBusy || appState?.relayRunning} onInput={(event) => setDraftCodexPath(event.currentTarget.value)} placeholder="codex" />
          <button className="icon-button" type="button" disabled={isBusy || appState?.relayRunning} onClick={async () => {
            const browseResult = await api.browseCodex();
            const codexPath = typeof browseResult === 'string'
              ? browseResult
              : browseResult?.codexPath || browseResult?.codex?.path || '';
            if (codexPath) {
              setDraftCodexPath(codexPath);
              runAction(
                'saving',
                () => (typeof browseResult === 'string' ? api.saveConfig({ codexPath }) : Promise.resolve(browseResult)),
                { savedDrafts: ['codexPath'] },
              );
            }
          }}>...</button>
        </div>
        <small className={appState?.codex?.installed ? 'ok-text' : 'danger-text'}>
          {appState?.codex?.installed ? `已检测到 Codex：${appState.codex.version || appState.codex.path}` : appState?.codex?.error || '未检测到 Codex'}
        </small>
      </label>
      <div className="wide button-row">
        <button className="secondary" type="button" disabled={isBusy || appState?.relayRunning} onClick={() => runAction('installing', () => api.installAndBuild())}>安装/构建</button>
        <button className="secondary" type="button" disabled={isBusy} onClick={() => runAction('saving', () => api.saveConfig({ port: draftPort, workspace: draftWorkspace, codexPath: draftCodexPath }), { savedDrafts: ['port', 'workspace', 'codexPath'] })}>保存设置</button>
        <button className="secondary" type="button" disabled={isBusy} onClick={() => runAction('checking', () => api.checkUpdate())}>检查更新</button>
        <button className="primary" type="button" disabled={isBusy || appState?.update?.applying || !appState?.update?.info?.updateAvailable} onClick={() => runAction('updating', () => api.applyUpdate())}>应用更新</button>
      </div>
    </div>
  );
}

function LightModePanel({ api, appState, isBusy, runAction }) {
  const enabled = appState?.lightMode === true;
  return (
    <section className={`light-mode-panel ${enabled ? 'enabled' : ''}`}>
      <label className="switch-row">
        <input
          type="checkbox"
          checked={enabled}
          disabled={isBusy}
          onChange={(event) => runAction('saving', () => api.saveConfig({ lightMode: event.currentTarget.checked }))}
        />
        <span className="switch-track" aria-hidden="true" />
        <span>
          <strong>轻量模式</strong>
          <small>启动中继后自动收纳到托盘，仅保留 relay 内核运行。</small>
        </span>
      </label>
      <button
        className="secondary"
        type="button"
        disabled={isBusy}
        onClick={() => runAction('launching', () => api.enterLightMode())}
      >
        立即收纳到托盘
      </button>
    </section>
  );
}

function PortInspector({ api, isBusy, port, preview, setPreview }) {
  const [busyPid, setBusyPid] = useState(null);
  const [error, setError] = useState('');
  const processes = preview?.processes || [];
  const portNumber = Number(port);
  const validPort = Number.isInteger(portNumber) && portNumber >= 1 && portNumber <= 65535;
  const statusText = !validPort
    ? '请输入 1-65535 的端口'
    : preview?.message
      ? preview.message
      : preview?.portAvailable
        ? '端口空闲，可以使用'
        : preview?.portReclaimable
          ? '检测到已有 EasyCodex 中继占用，可启动时自动接管'
          : processes.length
            ? '端口被下列进程占用'
            : '端口被占用，但没有读取到监听进程';
  const statusKind = preview?.portAvailable ? 'ok' : validPort && processes.length ? 'busy' : 'neutral';

  async function refreshPort() {
    if (!validPort) return;
    setError('');
    try {
      setPreview(await api.previewPort({ port: portNumber }));
    } catch (err) {
      setPreview({ portAvailable: false, portReclaimable: false, processes: [], message: err.message || String(err) });
    }
  }

  async function stopProcess(processInfo) {
    if (!validPort || !processInfo?.pid) return;
    const confirmed = window.confirm(`结束 PID ${processInfo.pid}（${processInfo.name || '未知进程'}）并释放端口 ${portNumber}？`);
    if (!confirmed) return;
    setBusyPid(processInfo.pid);
    setError('');
    try {
      await api.stopPortProcess({ port: portNumber, pid: processInfo.pid });
      await refreshPort();
    } catch (err) {
      setError(err.message || String(err));
      await refreshPort();
    } finally {
      setBusyPid(null);
    }
  }

  return (
    <section className={`port-inspector ${statusKind}`}>
      <div className="port-inspector-head">
        <div>
          <strong>端口检测器</strong>
          <span>{statusText}</span>
        </div>
        <button className="text-button" type="button" disabled={!validPort || isBusy || busyPid !== null} onClick={refreshPort}>
          重新检测
        </button>
      </div>
      {error && <p className="port-error">{error}</p>}
      {!preview?.portAvailable && processes.length > 0 && (
        <div className="port-process-list">
          {processes.map((entry) => (
            <div className="port-process" key={entry.pid}>
              <div>
                <strong>{entry.name || `PID ${entry.pid}`}</strong>
                <span>PID {entry.pid}{entry.isEasyCodexRelay ? ' · EasyCodex Relay' : ''}</span>
                {(entry.path || entry.commandLine) && <small>{entry.path || entry.commandLine}</small>}
              </div>
              <button
                className="secondary danger"
                type="button"
                disabled={isBusy || busyPid !== null}
                onClick={() => stopProcess(entry)}
              >
                {busyPid === entry.pid ? '结束中' : '结束进程'}
              </button>
            </div>
          ))}
        </div>
      )}
    </section>
  );
}

function LogPanel({ logs, onClear }) {
  return (
    <div className="log-panel">
      <div className="log-actions">
        <span>最近 {logs.length} 行</span>
        <button className="text-button" type="button" onClick={onClear}>清空</button>
      </div>
      <pre>{logs.join('\n')}</pre>
    </div>
  );
}

function TaskRail({ workbench, activeCount, historyCount, visibleTasks, onRefresh, onFilter, onSearch, onSelect }) {
  return (
    <aside className="task-rail">
      <div className="rail-top">
        <div>
          <p className="eyebrow">Tasks</p>
          <h2>任务台</h2>
        </div>
        <button className={`icon-button ${workbench.refreshingTasks ? 'spinning' : ''}`} type="button" title="刷新任务" onClick={onRefresh}>↻</button>
      </div>
      <div className="rail-tabs">
        <button type="button" className={workbench.taskFilter === 'active' ? 'active' : ''} onClick={() => onFilter('active')}>运行中 <span>{activeCount}</span></button>
        <button type="button" className={workbench.taskFilter === 'history' ? 'active' : ''} onClick={() => onFilter('history')}>历史 <span>{historyCount}</span></button>
      </div>
      <label className="task-search">
        <span>搜索</span>
        <input value={workbench.taskSearch} placeholder="任务、项目、状态" onInput={(event) => onSearch(event.currentTarget.value)} />
      </label>
      <div className={`socket-banner ${workbench.relaySocketState}`}>{workbench.relaySocketText}</div>
      <div className="task-list">
        {visibleTasks.length === 0
          ? <div className="empty-state">{workbench.taskSearch ? '没有匹配的任务' : workbench.taskFilter === 'history' ? '暂无历史任务' : '暂无运行中任务'}</div>
          : visibleTasks.map((item) => (
            <TaskCard
              key={`${item.__kind}:${item.id}`}
              item={item}
              active={workbench.selectedTarget === targetKey(item.__kind === 'agent' ? 'agent' : 'thread', item.id)}
              onClick={() => onSelect(targetKey(item.__kind === 'agent' ? 'agent' : 'thread', item.id))}
            />
          ))}
      </div>
    </aside>
  );
}

function TaskCard({ item, active, onClick }) {
  const isAgent = item.__kind === 'agent';
  const status = item.queuedFollowUpCount > 0 ? 'pending' : String(item.status || '');
  return (
    <button className={`task-card ${active ? 'active' : ''}`} type="button" onClick={onClick}>
      <span className="task-title-line">
        <span className="task-title">{item.name || item.preview || 'Codex task'}</span>
        <span className={`status-pill ${status}`}>{statusLabel(status)}</span>
      </span>
      <span className="task-preview">{isAgent ? taskPreview(item) : threadPreview(item)}</span>
      <span className="task-meta-line">
        <span>{shortPath(item.cwd || item.projectRoot)}</span>
        <span>{formatTime(item.updatedAt || item.createdAt || item.messages?.at?.(-1)?.timestamp)}</span>
      </span>
    </button>
  );
}

function ConversationPane({ agent, thread, workbench, composerText, setComposerText, onSend, onResumeThread, onStopAgent }) {
  const messages = agent?.messages || thread?.messages || [];
  const conversationItems = useMemo(() => conversationListItems(messages), [messages]);
  const selectedThreadId = thread?.id;
  const threadError = selectedThreadId ? workbench.threadErrors[selectedThreadId] : '';
  const loadingThread = selectedThreadId ? workbench.loadingDetails[selectedThreadId] : false;
  return (
    <section className="conversation-pane">
      <header className="conversation-top">
        <div>
          <p className="eyebrow">{agent ? `${statusLabel(agent.status)} / ${shortPath(agent.cwd)}` : thread ? `${statusLabel(thread.status)} / ${shortPath(thread.cwd || thread.projectRoot)}` : 'Workbench ready'}</p>
          <h2>{agent?.name || thread?.name || thread?.preview || '选择一个任务'}</h2>
          <p>{agent ? agent.activityLabel || agent.activity || agent.codexThreadId || '电脑和手机正在共享这个 relay 任务。' : thread ? '这是 Codex 历史线程，恢复后可以继续对话。' : '手机发起的任务会出现在这里，电脑端可以继续对话和审批。'}</p>
        </div>
        <div className="conversation-actions">
          {thread && <button className="secondary" type="button" onClick={onResumeThread}>恢复</button>}
          <button className="secondary danger" type="button" disabled={!agent || agent.status === 'stopped'} onClick={onStopAgent}>停止</button>
        </div>
      </header>
      <div className="conversation-notices">
        {loadingThread && <div className="inline-note">正在同步历史详情，当前摘要会保持显示。</div>}
        {threadError && <div className="inline-note error">详情读取失败：{threadError}</div>}
      </div>
      <div className="message-stream">
        {conversationItems.length === 0
          ? <div className="empty-state">这个任务还没有消息。手机或电脑发一句话后，执行流会出现在这里。</div>
          : conversationItems.map((item, index) => item.kind === 'detail_group'
            ? <DetailGroupBubble key={`detail_group_${stableMessageKey(item.messages[0], index)}_${stableMessageKey(item.messages.at(-1), index)}_${item.messages.length}`} item={item} />
            : <MessageBubble key={stableMessageKey(item.message, index)} message={item.message} />)}
      </div>
      <form className="composer" onSubmit={onSend}>
        <textarea
          rows={3}
          value={composerText}
          disabled={!agent || workbench.relaySocketState !== 'online'}
          placeholder={thread ? '先恢复历史任务，再继续发送消息。' : '给当前任务继续发送消息...'}
          onInput={(event) => setComposerText(event.currentTarget.value)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' && !event.shiftKey) {
              event.preventDefault();
              event.currentTarget.form?.requestSubmit();
            }
          }}
        />
        <div className="composer-actions">
          <span>{agent ? isAgentBusy(agent) ? `当前任务运行中，发送将新建并行任务 / ${shortPath(agent.cwd)}` : `发送到 ${shortPath(agent.cwd)}` : thread ? '恢复历史任务后可以发送。' : '选择运行中的任务后可以发送。'}</span>
          <button className="primary" type="submit" disabled={!agent || !composerText.trim() || workbench.relaySocketState !== 'online'}>发送</button>
        </div>
      </form>
    </section>
  );
}

function MessageBubble({ message }) {
  const role = message.role === 'user' || message.type === 'user' ? 'user' : 'agent';
  const type = String(message.type || role);
  if (role === 'agent' && isDetailMessage(message)) {
    return <DetailMessageCard message={message} />;
  }
  return (
    <article className={`message ${role} ${type}`}>
      <div className="message-shell">
        <div className="message-label">
          <span>{messageLabel(type, role)}</span>
          <span>{formatTime(message.timestamp)}</span>
        </div>
        <div className="message-text">{message.text || ''}</div>
      </div>
    </article>
  );
}

function DetailGroupBubble({ item }) {
  const [expanded, setExpanded] = useState(false);
  const messages = item.messages || [];
  const running = messages.some((message) => message.streaming);
  const commandCount = messages.filter((message) => message.type === 'command').length || messages.length;
  const fileChangeCount = messages.filter((message) => message.type === 'file_change').length;
  const fileCount = Math.max(messages.reduce((sum, message) => {
    const detail = detailDisplay(message);
    return sum + Math.max(detail.files?.length || 0, message.type === 'file_change' ? 1 : 0);
  }, 0), fileChangeCount);
  const latestTitle = detailDisplay(messages.at(-1) || {}).title;
  const commandTitle = running ? `正在运行 ${commandCount} 条命令` : `已运行 ${commandCount} 条命令`;
  const fileTitle = `${fileCount} 个文件已更改`;
  const title = item.groupKind === 'mixed'
    ? `${commandTitle} · ${fileTitle}`
    : item.groupKind === 'file_change'
      ? fileTitle
      : commandTitle;
  return (
    <article className={`detail-group ${expanded ? 'expanded' : ''}`}>
      <DetailHeader
        title={title}
        subtitle={latestTitle}
        expanded={expanded}
        onToggle={() => setExpanded((value) => !value)}
      />
      {expanded && (
        <div className="detail-group-body">
          {messages.map((message, index) => (
            <DetailMessageCard key={stableMessageKey(message, index)} message={message} nested />
          ))}
        </div>
      )}
    </article>
  );
}

function DetailHeader({ title, subtitle, expanded, onToggle, stats }) {
  return (
    <button className="detail-header" type="button" onClick={onToggle}>
      <span className="detail-icon">▣</span>
      <span className="detail-title">{title}</span>
      {subtitle && <span className="detail-subtitle">{subtitle}</span>}
      {stats && <ChangeStats additions={stats.additions} deletions={stats.deletions} />}
      <span className="detail-chevron">{expanded ? '⌃' : '⌄'}</span>
    </button>
  );
}

function DetailMessageCard({ message, nested = false }) {
  const [expanded, setExpanded] = useState(false);
  const [textExpanded, setTextExpanded] = useState(false);
  const detail = useMemo(() => detailDisplay(message), [message]);
  const body = useMemo(() => (
    message.type === 'file_change' ? cleanFileChangeBody(detail.body) : detail.body
  ) || '...', [detail.body, message.type]);
  const isLong = body.length > longDetailTextLimit;
  const visibleBody = isLong && !textExpanded ? body.slice(0, longDetailTextLimit) : body;

  if (message.type === 'file_change') {
    return (
      <FileChangeCard
        detail={detail}
        expanded={expanded}
        nested={nested}
        onToggle={() => setExpanded((value) => !value)}
      />
    );
  }

  return (
    <article className={`detail-card ${nested ? 'nested' : ''}`}>
      <DetailHeader
        title={detail.title}
        subtitle={detail.subtitle}
        expanded={expanded}
        onToggle={() => setExpanded((value) => !value)}
      />
      {expanded && (
        <div className="detail-card-body">
          <pre>{visibleBody}</pre>
          {isLong && (
            <button className="detail-more" type="button" onClick={() => setTextExpanded((value) => !value)}>
              {textExpanded ? '收起' : '展开更多'}
            </button>
          )}
        </div>
      )}
    </article>
  );
}

function FileChangeCard({ detail, expanded, nested, onToggle }) {
  const entries = detail.fileEntries?.length
    ? detail.fileEntries
    : (detail.files || []).map((path) => ({ path, additions: 0, deletions: 0 }));
  return (
    <article className={`detail-card file-detail ${nested ? 'nested' : ''}`}>
      <DetailHeader
        title={`${Math.max(detail.files?.length || 0, 1)} 个文件已更改`}
        subtitle=""
        expanded={expanded}
        onToggle={onToggle}
        stats={{ additions: detail.additions || 0, deletions: detail.deletions || 0 }}
      />
      {expanded && (
        <div className="detail-card-body file-list">
          {entries.slice(0, 4).map((entry, index) => (
            <div className="file-row" key={`${entry.path}_${index}`}>
              <span>{entry.path}</span>
              <ChangeStats additions={entry.additions || 0} deletions={entry.deletions || 0} />
            </div>
          ))}
          {entries.length > 4 && <div className="file-row more-files">另有 {entries.length - 4} 个文件</div>}
        </div>
      )}
    </article>
  );
}

function ChangeStats({ additions = 0, deletions = 0 }) {
  if (additions <= 0 && deletions <= 0) return null;
  return (
    <span className="change-stats">
      <span className="additions">+{additions}</span>
      <span className="deletions">-{deletions}</span>
    </span>
  );
}

function ContextPane({ agent, thread, workbench, state, api, updateText, connectedClients, onRefreshGit, onApprove }) {
  const requests = workbench.pendingRequestsByAgent[agent?.id] || [];
  const cwd = agent?.cwd || '';
  const gitContext = cwd ? workbench.gitContextByCwd[cwd] : null;
  const gitLoading = cwd ? workbench.loadingGitCwds[cwd] : false;
  const summary = diffSummary(gitContext?.diff || '');
  const files = Array.from(new Set([...summary.files, ...gitStatusFiles(gitContext?.status)]));
  return (
    <aside className="context-pane">
      <section className="context-block">
        <h2>任务信息</h2>
        <dl>
          {(agent ? [
            ['状态', statusLabel(agent.status)],
            ['模型', agent.model || '-'],
            ['项目', agent.cwd || '-'],
            ['Thread', agent.codexThreadId || agent.threadId || '-'],
            ['运行状态', agent.activityLabel || agent.activity || '-'],
          ] : thread ? [
            ['状态', statusLabel(thread.status)],
            ['项目', thread.cwd || thread.projectRoot || '-'],
            ['Thread', thread.id],
            ['最近更新', thread.updatedAt ? new Date(thread.updatedAt).toLocaleString() : '-'],
            ['队列', String(thread.queuedFollowUpCount || 0)],
          ] : [
            ['状态', workbench.relaySocketState === 'online' ? '已连接' : '未连接'],
            ['提示', '从左侧选择任务'],
          ]).map(([key, value]) => (
            <div key={key}>
              <dt>{key}</dt>
              <dd>{value}</dd>
            </div>
          ))}
        </dl>
      </section>

      <section className="context-block">
        <div className="context-heading">
          <h2>审批</h2>
          <span>{requests.length ? '等待确认' : '无请求'}</span>
        </div>
        {requests.length === 0
          ? <p>当前没有等待审批的操作。</p>
          : requests.slice(0, 1).map((request) => (
            <div className="approval-card" key={request.requestId}>
              <pre>{request.text || request.params?.command || request.method || 'Codex 请求批准操作'}</pre>
              <div className="button-row">
                <button className="primary" type="button" onClick={() => onApprove(request, true)}>批准</button>
                <button className="secondary danger" type="button" onClick={() => onApprove(request, false)}>拒绝</button>
              </div>
            </div>
          ))}
      </section>

      <section className="context-block">
        <div className="context-heading">
          <h2>文件变更</h2>
          <button className="text-button" type="button" disabled={!agent} onClick={onRefreshGit}>刷新</button>
        </div>
        {!agent ? <p>选择运行中的任务后显示 Git 状态。</p> : gitLoading && !gitContext ? <p>正在读取 Git 状态...</p> : gitContext?.error ? <p>Git 状态不可用：{gitContext.error}</p> : (
          <>
            <p>{files.length ? `${gitContext?.status?.branch || 'unknown'} / ${files.length} 个文件 / +${summary.additions} -${summary.deletions}` : `${gitContext?.status?.branch || 'unknown'} / 工作区干净`}</p>
            <div className="changed-files">
              {files.length ? files.slice(0, 18).map((file) => <span className="file-chip" key={file}>{file}</span>) : <span className="muted-chip">没有文件变更</span>}
            </div>
            {gitContext?.diff && <pre className="diff-preview">{gitContext.diff.slice(0, 5000)}</pre>}
          </>
        )}
      </section>

      <section className="context-block">
        <h2>连接</h2>
        <p>{workbench.relaySocketText}</p>
        <p>手机连接：{connectedClients}</p>
        <p>Relay：{state?.relayUrl || '-'}</p>
        <div className="button-row stack">
          <button className="secondary full" type="button" disabled={!state} onClick={() => api.copyText(connectionText(state))}>复制连接信息</button>
          <button className="secondary full" type="button" disabled={!state?.apiKey} onClick={() => api.copyText(state.apiKey)}>复制 API Key</button>
        </div>
      </section>

      <section className="context-block">
        <h2>版本与更新</h2>
        <p>{updateText}</p>
      </section>
    </aside>
  );
}

render(<App />, document.getElementById('app'));
