export type PermissionMode = 'default-review' | 'auto-review' | 'full-access';
export type SandboxMode = 'workspace-write' | 'danger-full-access';
export type ApprovalsReviewer = 'user' | 'auto_review';

export interface PermissionRuntimeConfig {
  permissionMode: PermissionMode;
  approvalPolicy: 'on-request' | 'never';
  sandboxMode: SandboxMode;
  approvalsReviewer?: ApprovalsReviewer;
}

export function normalizePermissionMode(value: unknown): PermissionMode {
  const clean = typeof value === 'string' ? value.trim().toLowerCase() : '';
  if (clean === 'auto-review') return 'auto-review';
  if (clean === 'full-access' || clean === 'full_control' || clean === 'full-control') return 'full-access';
  return 'default-review';
}

export function permissionRuntimeConfig(value: unknown): PermissionRuntimeConfig {
  const permissionMode = normalizePermissionMode(value);
  if (permissionMode === 'full-access') {
    return {
      permissionMode,
      approvalPolicy: 'never',
      sandboxMode: 'danger-full-access',
    };
  }
  if (permissionMode === 'auto-review') {
    return {
      permissionMode,
      approvalPolicy: 'on-request',
      sandboxMode: 'workspace-write',
      approvalsReviewer: 'auto_review',
    };
  }
  return {
    permissionMode: 'default-review',
    approvalPolicy: 'on-request',
    sandboxMode: 'workspace-write',
    approvalsReviewer: 'user',
  };
}

export function permissionModeFromRuntime(options: {
  permissionMode?: unknown;
  approvalPolicy?: unknown;
  sandboxMode?: unknown;
  approvalsReviewer?: unknown;
}): PermissionMode {
  const explicit = typeof options.permissionMode === 'string' ? options.permissionMode.trim() : '';
  if (explicit) return normalizePermissionMode(explicit);
  if (options.approvalPolicy === 'never' || options.sandboxMode === 'danger-full-access') return 'full-access';
  if (options.approvalsReviewer === 'auto_review') return 'auto-review';
  return 'default-review';
}

export function permissionModeFromCreateAgentParams(options: {
  permissionMode?: unknown;
  approvalPolicy?: unknown;
  sandboxMode?: unknown;
  approvalsReviewer?: unknown;
}): PermissionMode {
  const explicit = typeof options.permissionMode === 'string' ? options.permissionMode.trim() : '';
  if (explicit) return normalizePermissionMode(explicit);
  return permissionModeFromRuntime({
    approvalPolicy: options.approvalPolicy,
    sandboxMode: options.sandboxMode,
    approvalsReviewer: options.approvalsReviewer,
  });
}

export function sandboxPolicyForMode(sandboxMode: SandboxMode, cwd: string): Record<string, unknown> {
  if (sandboxMode === 'danger-full-access') return { type: 'dangerFullAccess' };
  return {
    type: 'workspaceWrite',
    writableRoots: cwd ? [cwd] : [],
    networkAccess: false,
    excludeTmpdirEnvVar: false,
    excludeSlashTmp: false,
  };
}
