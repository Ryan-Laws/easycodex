import assert from 'node:assert/strict';
import path from 'node:path';
import test from 'node:test';
import { buildCliArgs, replayStreamHistory, type StreamHistoryEntry } from '../src/cli-helpers';

test('builds exec args with safe advanced options', () => {
  const workspace = path.resolve('.');
  const result = buildCliArgs({
    windowId: 'cli_1',
    cwd: workspace,
    prompt: 'summarize',
    model: 'gpt-5.5',
    reasoningEffort: 'high',
    sandboxMode: 'workspace-write',
    skipGitRepoCheck: true,
    profile: 'work',
    images: ['shot.png'],
    addDirs: [workspace],
    jsonOutput: true,
    ephemeral: true,
    ignoreRules: true,
  }, workspace);

  assert.equal(result.mode, 'exec');
  assert.deepEqual(result.args.slice(0, 6), ['exec', '--skip-git-repo-check', '--ephemeral', '--ignore-rules', '--json', '--model']);
  assert.ok(result.args.includes('--profile'));
  assert.ok(result.args.includes('--image'));
  assert.ok(result.args.includes('--add-dir'));
  assert.equal(result.args[result.args.length - 1], 'summarize');
});

test('builds review base args', () => {
  const workspace = path.resolve('.');
  const result = buildCliArgs({
    windowId: 'cli_1',
    cwd: workspace,
    prompt: 'focus on regressions',
    mode: 'review',
    reviewTarget: 'base:main',
  }, workspace);

  assert.equal(result.mode, 'review');
  assert.deepEqual(result.args, ['exec', 'review', '--skip-git-repo-check', '--base', 'main', 'focus on regressions']);
});

test('builds resume last args without requiring prompt', () => {
  const workspace = path.resolve('.');
  const result = buildCliArgs({
    windowId: 'cli_1',
    cwd: workspace,
    prompt: '',
    mode: 'resume',
    sessionId: 'last',
  }, workspace);

  assert.equal(result.mode, 'resume');
  assert.deepEqual(result.args, ['exec', 'resume', '--skip-git-repo-check', '--last']);
});

test('replay stream history filters same-session events and reports truncation', () => {
  const history: StreamHistoryEntry[] = Array.from({ length: 5 }, (_, index) => ({
    type: 'stream',
    sessionId: 'session',
    seq: index + 1,
    timestamp: index,
    agentId: 'cli',
    event: 'cli/output',
    data: { windowId: 'cli_1', chunk: String(index + 1) },
  }));

  const result = replayStreamHistory(history, 'session', { sessionId: 'session', afterSeq: 1, limit: 2 }, 5000);

  assert.equal(result.latestSeq, 5);
  assert.equal(result.truncated, true);
  assert.deepEqual(result.events.map((entry) => entry.seq), [4, 5]);
});
