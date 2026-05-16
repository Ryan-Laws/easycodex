export interface StreamHistoryEntry {
  type: 'stream';
  sessionId: string;
  seq: number;
  timestamp: number;
  agentId: string;
  event: string;
  data: unknown;
}

export interface CliRunOptions {
  windowId: string;
  cwd: string;
  prompt: string;
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
}

export type CliMode = 'exec' | 'resume' | 'review';

export function cleanCliSandboxMode(value: unknown): string {
  const clean = typeof value === 'string' ? value.trim() : '';
  return ['read-only', 'workspace-write', 'danger-full-access'].includes(clean) ? clean : 'workspace-write';
}

export function cleanCliMode(value: unknown): CliMode {
  const clean = typeof value === 'string' ? value.trim().toLowerCase() : '';
  return clean === 'resume' || clean === 'review' ? clean : 'exec';
}

export function cleanStringList(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value
    .map((entry) => (typeof entry === 'string' ? entry.trim() : ''))
    .filter(Boolean)
    .slice(0, 12);
}

export function appendCliReviewTarget(args: string[], reviewTarget?: string) {
  const clean = typeof reviewTarget === 'string' ? reviewTarget.trim() : '';
  if (!clean || clean === 'uncommitted') {
    args.push('--uncommitted');
    return;
  }
  if (clean.startsWith('base:')) {
    const base = clean.slice('base:'.length).trim();
    if (base) args.push('--base', base);
    return;
  }
  if (clean.startsWith('commit:')) {
    const commit = clean.slice('commit:'.length).trim();
    if (commit) args.push('--commit', commit);
  }
}

export function appendCliCommonArgs(
  args: string[],
  options: CliRunOptions,
  safeCwd: string,
  mode: CliMode,
  resolveImage: (image: string) => string = (image) => image,
  resolveAddDir: (dir: string) => string = (dir) => dir,
) {
  const cleanModel = typeof options.model === 'string' ? options.model.trim() : '';
  const cleanReasoning = typeof options.reasoningEffort === 'string' ? options.reasoningEffort.trim() : '';
  if (options.skipGitRepoCheck !== false) args.push('--skip-git-repo-check');
  if (options.ephemeral === true) args.push('--ephemeral');
  if (options.ignoreRules === true) args.push('--ignore-rules');
  if (options.jsonOutput === true) args.push('--json');
  if (cleanModel) args.push('--model', cleanModel);
  if (cleanReasoning) args.push('-c', `model_reasoning_effort=${JSON.stringify(cleanReasoning)}`);

  if (mode === 'exec') {
    args.push('--cd', safeCwd, '--sandbox', cleanCliSandboxMode(options.sandboxMode), '--color', 'never');
    const cleanProfile = typeof options.profile === 'string' ? options.profile.trim() : '';
    if (cleanProfile) args.push('--profile', cleanProfile);
    for (const image of cleanStringList(options.images)) args.push('--image', resolveImage(image));
    for (const dir of cleanStringList(options.addDirs)) args.push('--add-dir', resolveAddDir(dir));
  } else if (mode === 'resume') {
    for (const image of cleanStringList(options.images)) args.push('--image', resolveImage(image));
  }
}

export function buildCliArgs(
  options: CliRunOptions,
  safeCwd: string,
  resolveImage: (image: string) => string = (image) => image,
  resolveAddDir: (dir: string) => string = (dir) => dir,
): { args: string[]; mode: CliMode } {
  const mode = cleanCliMode(options.mode);
  const prompt = options.prompt.trim();
  if (mode === 'exec' && !prompt) throw new Error('prompt is required');
  const args = ['exec'];

  if (mode === 'resume') {
    args.push('resume');
    appendCliCommonArgs(args, options, safeCwd, mode, resolveImage, resolveAddDir);
    const cleanSessionId = typeof options.sessionId === 'string' ? options.sessionId.trim() : '';
    args.push(!cleanSessionId || cleanSessionId === 'last' ? '--last' : cleanSessionId);
    if (prompt) args.push(prompt);
    return { args, mode };
  }

  if (mode === 'review') {
    args.push('review');
    appendCliCommonArgs(args, options, safeCwd, mode, resolveImage, resolveAddDir);
    appendCliReviewTarget(args, options.reviewTarget);
    if (prompt) args.push(prompt);
    return { args, mode };
  }

  appendCliCommonArgs(args, options, safeCwd, mode, resolveImage, resolveAddDir);
  args.push(prompt);
  return { args, mode };
}

export function replayStreamHistory(
  history: StreamHistoryEntry[],
  currentSessionId: string,
  params: { afterSeq?: number; limit?: number; sessionId?: string },
  maxLimit: number,
): { sessionId: string; events: StreamHistoryEntry[]; latestSeq: number; truncated: boolean } {
  const safeAfterSeq = typeof params.afterSeq === 'number' && Number.isFinite(params.afterSeq) ? params.afterSeq : 0;
  const safeLimit = Math.min(Math.max(typeof params.limit === 'number' ? params.limit : 1000, 1), maxLimit);
  const sameSession = params.sessionId === currentSessionId;
  const events = history
    .filter((entry) => !sameSession || entry.seq > safeAfterSeq)
    .slice(-safeLimit);
  return {
    sessionId: currentSessionId,
    events,
    latestSeq: history[history.length - 1]?.seq || safeAfterSeq,
    truncated: sameSession && history.length > events.length && events[0]?.seq > safeAfterSeq + 1,
  };
}
