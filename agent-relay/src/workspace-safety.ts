import fs from 'fs';
import os from 'os';
import path from 'path';

export function normalizePathKey(targetPath: string): string {
  const resolved = path.resolve(targetPath);
  return process.platform === 'win32' ? resolved.toLowerCase() : resolved;
}

export function isWithinBase(base: string, targetPath: string): boolean {
  const resolvedBase = normalizePathKey(base);
  const resolved = normalizePathKey(targetPath);
  return resolved === resolvedBase || resolved.startsWith(`${resolvedBase}${path.sep}`);
}

export function uniqueResolvedPaths(paths: string[]): string[] {
  const seen = new Set<string>();
  const result: string[] = [];
  for (const entry of paths) {
    const resolved = path.resolve(entry);
    const key = normalizePathKey(resolved);
    if (seen.has(key)) continue;
    seen.add(key);
    result.push(resolved);
  }
  return result;
}

export function isDisallowedWorkspaceRoot(
  targetPath: string,
  env: NodeJS.ProcessEnv = process.env,
  homeDir = os.homedir(),
): boolean {
  const resolved = path.resolve(targetPath);
  const parsed = path.parse(resolved);
  if (normalizePathKey(resolved) === normalizePathKey(parsed.root)) return true;

  const home = path.resolve(homeDir);
  if (normalizePathKey(resolved) === normalizePathKey(home)) return true;

  const homeBoundaries = ['Desktop', 'Documents', 'Downloads'].map((name) => path.join(home, name));
  if (homeBoundaries.some((root) => normalizePathKey(resolved) === normalizePathKey(root))) return true;

  const disallowed = [
    env.SystemRoot,
    env.ProgramFiles,
    env['ProgramFiles(x86)'],
    env.APPDATA,
    env.LOCALAPPDATA,
  ].filter((entry): entry is string => typeof entry === 'string' && entry.trim() !== '');
  return disallowed.some((root) => isWithinBase(root, resolved));
}

export function assertWorkspaceRootAllowed(targetPath: string): void {
  const resolved = path.resolve(targetPath);
  const realResolved = fs.realpathSync(resolved);
  if (isDisallowedWorkspaceRoot(resolved) || isDisallowedWorkspaceRoot(realResolved)) {
    throw new Error('Refusing to use a system, profile, or application data directory as a project workspace.');
  }
}

export function isWorkspaceRootAllowed(targetPath: string): boolean {
  try {
    assertWorkspaceRootAllowed(targetPath);
    return true;
  } catch {
    return false;
  }
}
