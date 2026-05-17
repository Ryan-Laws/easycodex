import assert from 'node:assert/strict';
import test from 'node:test';
import { codexThreadResumeCall, codexThreadStartCall, codexTurnStartCall } from '../src/codex-rpc';
import { permissionModeFromRuntime, permissionRuntimeConfig } from '../src/permission-modes';

function paramsOf(payload: string): Record<string, unknown> {
  return JSON.parse(payload).params;
}

test('maps EasyCodex permission modes to Codex runtime fields', () => {
  assert.deepEqual(permissionRuntimeConfig('default-review'), {
    permissionMode: 'default-review',
    approvalPolicy: 'on-request',
    sandboxMode: 'workspace-write',
    approvalsReviewer: 'user',
  });
  assert.deepEqual(permissionRuntimeConfig('auto-review'), {
    permissionMode: 'auto-review',
    approvalPolicy: 'on-request',
    sandboxMode: 'workspace-write',
    approvalsReviewer: 'auto_review',
  });
  assert.deepEqual(permissionRuntimeConfig('full-access'), {
    permissionMode: 'full-access',
    approvalPolicy: 'never',
    sandboxMode: 'danger-full-access',
  });
});

test('infers permission mode from runtime fields when no explicit mode exists', () => {
  assert.equal(permissionModeFromRuntime({}), 'default-review');
  assert.equal(permissionModeFromRuntime({ approvalPolicy: 'never' }), 'full-access');
  assert.equal(permissionModeFromRuntime({ approvalsReviewer: 'auto_review' }), 'auto-review');
});

test('thread and turn rpc payloads carry permission fields', () => {
  assert.deepEqual(
    paramsOf(codexThreadStartCall('gpt-5.5', 'C:/repo', 'on-request', 'workspace-write', 'auto_review')),
    {
      model: 'gpt-5.5',
      approvalPolicy: 'on-request',
      sandbox: 'workspace-write',
      approvalsReviewer: 'auto_review',
      cwd: 'C:/repo',
    },
  );

  assert.deepEqual(
    paramsOf(codexThreadResumeCall('thread_1', {
      approvalPolicy: 'never',
      sandbox: 'danger-full-access',
    })),
    {
      threadId: 'thread_1',
      approvalPolicy: 'never',
      sandbox: 'danger-full-access',
    },
  );

  assert.deepEqual(
    paramsOf(codexTurnStartCall('thread_1', 'hi', {
      approvalPolicy: 'on-request',
      approvalsReviewer: 'user',
      sandboxPolicy: { type: 'workspaceWrite', writableRoots: ['C:/repo'], networkAccess: false },
    })),
    {
      threadId: 'thread_1',
      input: [{ type: 'text', text: 'hi' }],
      approvalPolicy: 'on-request',
      approvalsReviewer: 'user',
      sandboxPolicy: { type: 'workspaceWrite', writableRoots: ['C:/repo'], networkAccess: false },
    },
  );
});
