import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { extractPendingUserInputRequestsFromSession } from '../src/session-orchestrator';

function writeJsonl(records: Record<string, unknown>[]): string {
  const file = path.join(os.tmpdir(), `easycodex-user-input-${Date.now()}-${Math.random().toString(16).slice(2)}.jsonl`);
  fs.writeFileSync(file, records.map((record) => JSON.stringify(record)).join('\n'), 'utf8');
  return file;
}

test('extracts unanswered Codex desktop request_user_input calls', () => {
  const file = writeJsonl([
    {
      timestamp: '2026-05-16T19:32:29.907Z',
      type: 'response_item',
      payload: {
        type: 'function_call',
        name: 'request_user_input',
        call_id: 'call_question',
        arguments: JSON.stringify({
          questions: [{
            id: 'external_reputation',
            header: '外部信誉',
            question: '首版全局风控是否接入外部 IP/邮箱信誉服务？',
            options: [{ label: '先不接 (Recommended)', description: '成本最低。' }],
          }],
        }),
      },
    },
  ]);

  const pending = extractPendingUserInputRequestsFromSession(file);

  assert.equal(pending.length, 1);
  assert.equal(pending[0].requestId, 'codex_user_input_call_question');
  assert.equal(pending[0].method, 'item/tool/requestUserInput');
  assert.match(pending[0].text, /首版全局风控/);
  assert.equal((pending[0].params.questions as unknown[]).length, 1);
});

test('drops request_user_input calls that already have an output', () => {
  const file = writeJsonl([
    {
      timestamp: '2026-05-16T19:32:29.907Z',
      type: 'response_item',
      payload: {
        type: 'function_call',
        name: 'request_user_input',
        call_id: 'call_answered',
        arguments: JSON.stringify({ message: '请选择。' }),
      },
    },
    {
      timestamp: '2026-05-16T19:33:00.000Z',
      type: 'response_item',
      payload: {
        type: 'function_call_output',
        call_id: 'call_answered',
        output: JSON.stringify({ answers: { answer: { answers: ['继续'] } } }),
      },
    },
  ]);

  assert.deepEqual(extractPendingUserInputRequestsFromSession(file), []);
});
