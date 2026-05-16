import assert from 'node:assert/strict';
import test from 'node:test';
import { inferCodexSessionRuntimeState } from '../src/session-orchestrator';

function jsonl(records: Record<string, unknown>[]): string[] {
  return records.map((record) => JSON.stringify(record));
}

function event(timestamp: string, type: string): Record<string, unknown> {
  return {
    timestamp,
    type: 'event_msg',
    payload: { type },
  };
}

function response(timestamp: string, type: string): Record<string, unknown> {
  return {
    timestamp,
    type: 'response_item',
    payload: { type },
  };
}

test('terminal task_complete after active events is not running', () => {
  const state = inferCodexSessionRuntimeState(jsonl([
    event('2026-05-16T06:00:00.000Z', 'task_started'),
    response('2026-05-16T06:00:01.000Z', 'reasoning'),
    event('2026-05-16T06:00:02.000Z', 'task_complete'),
  ]));

  assert.equal(state?.running, false);
  assert.equal(state?.activityLabel, null);
});

test('active response after prior task_complete starts a new running turn', () => {
  const state = inferCodexSessionRuntimeState(jsonl([
    event('2026-05-16T06:00:00.000Z', 'task_started'),
    event('2026-05-16T06:00:02.000Z', 'task_complete'),
    response('2026-05-16T06:01:00.000Z', 'reasoning'),
    response('2026-05-16T06:01:01.000Z', 'function_call'),
  ]));

  assert.equal(state?.running, true);
  assert.equal(state?.activityLabel, '正在运行命令，等待执行结果');
});

test('terminal event after reopened active turn wins again', () => {
  const state = inferCodexSessionRuntimeState(jsonl([
    event('2026-05-16T06:00:00.000Z', 'task_complete'),
    response('2026-05-16T06:01:00.000Z', 'message'),
    event('2026-05-16T06:01:10.000Z', 'task_complete'),
  ]));

  assert.equal(state?.running, false);
  assert.equal(state?.activityLabel, null);
});

test('active events without lifecycle still use file mtime grace', () => {
  const lines = jsonl([
    response('2026-05-16T06:00:00.000Z', 'reasoning'),
  ]);

  const recent = inferCodexSessionRuntimeState(lines, {
    now: 10_000,
    mtimeMs: 9_000,
    activeGraceMs: 2_000,
  });
  const stale = inferCodexSessionRuntimeState(lines, {
    now: 10_000,
    mtimeMs: 7_000,
    activeGraceMs: 2_000,
  });

  assert.equal(recent?.running, true);
  assert.equal(recent?.activityLabel, '正在思考中，推理内容持续返回');
  assert.equal(stale?.running, false);
  assert.equal(stale?.activityLabel, null);
});
