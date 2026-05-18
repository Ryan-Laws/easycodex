import path from 'path';

function isWithinBase(base: string, targetPath: string): boolean {
  const resolvedBaseRaw = path.resolve(base);
  const resolvedRaw = path.resolve(targetPath);
  const resolvedBase = process.platform === 'win32' ? resolvedBaseRaw.toLowerCase() : resolvedBaseRaw;
  const resolved = process.platform === 'win32' ? resolvedRaw.toLowerCase() : resolvedRaw;
  return resolved === resolvedBase || resolved.startsWith(`${resolvedBase}${path.sep}`);
}

function resolveWithinCwd(cwd: string, relativePath?: string): string {
  const safeCwd = path.resolve(cwd || process.cwd());
  const resolved = path.resolve(safeCwd, relativePath || '.');
  if (isWithinBase(safeCwd, resolved)) {
    return resolved;
  }
  throw new Error('Path escapes cwd');
}

function assertNoGitGlobOrPathspec(value: string): void {
  if (/[*?\[\]{}]/.test(value) || value.includes(':(glob)') || value.includes(':(icase)')) {
    throw new Error('files must be explicit tracked file paths, not globs or pathspec expressions.');
  }
}

export function normalizeGitCommitFiles(cwd: string, files: unknown): string[] {
  if (!Array.isArray(files) || files.length === 0) {
    throw new Error('files is required; choose the exact files to commit.');
  }
  const normalized = files.map((file) => {
    if (typeof file !== 'string' || !file.trim()) {
      throw new Error('files must contain non-empty path strings.');
    }
    const target = resolveWithinCwd(cwd, file.trim());
    const relative = path.relative(cwd, target).split(path.sep).join('/');
    if (!relative || relative === '.' || relative.startsWith('-')) {
      throw new Error('files must contain explicit file paths, not git options or repository roots.');
    }
    assertNoGitGlobOrPathspec(relative);
    return relative;
  });
  return Array.from(new Set(normalized));
}

export function normalizeGitRestoreFiles(cwd: string, files: unknown, trackedFiles: Set<string>): string[] {
  const normalized = normalizeGitCommitFiles(cwd, files);
  if (normalized.length === 0) {
    throw new Error('files is required; choose the exact files to restore.');
  }
  for (const file of normalized) {
    if (!trackedFiles.has(file)) {
      throw new Error(`Refusing to restore untracked or unknown file: ${file}`);
    }
  }
  return normalized;
}

export function normalizeGitRenamedFiles(renamed: unknown): string[] {
  if (!Array.isArray(renamed)) return [];
  return renamed.flatMap((entry) => {
    if (typeof entry === 'string') return entry.trim() ? [entry.trim()] : [];
    if (!entry || typeof entry !== 'object') return [];
    const record = entry as Record<string, unknown>;
    const target = record.to ?? record.path ?? record.file;
    return typeof target === 'string' && target.trim() ? [target.trim()] : [];
  });
}

export interface MobileGitStatusInput {
  current?: string | null;
  ahead?: number;
  behind?: number;
  modified?: string[];
  created?: string[];
  deleted?: string[];
  renamed?: unknown;
  not_added?: string[];
  conflicted?: string[];
  isClean?: () => boolean;
}

export function mobileGitStatusPayload(status: MobileGitStatusInput, trackedFiles: Set<string>) {
  const renamed = normalizeGitRenamedFiles(status.renamed);
  const modified = status.modified ?? [];
  const created = status.created ?? [];
  const deleted = status.deleted ?? [];
  const notAdded = status.not_added ?? [];
  const conflicted = status.conflicted ?? [];
  const restorableFiles = Array.from(
    new Set(
      [
        ...modified,
        ...created,
        ...deleted,
        ...renamed,
        ...conflicted,
      ].filter((file) => trackedFiles.has(file)),
    ),
  );
  const changedFiles = [...modified, ...created, ...deleted, ...renamed, ...notAdded, ...conflicted];
  return {
    branch: status.current ?? '',
    isClean: status.isClean?.() ?? changedFiles.length === 0,
    ahead: status.ahead ?? 0,
    behind: status.behind ?? 0,
    modified,
    created,
    deleted,
    renamed,
    notAdded,
    conflicted,
    restorableFiles,
  };
}
