import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import {
  assertWorkspaceRootAllowed,
  isDisallowedWorkspaceRoot,
  isWithinBase,
  isWorkspaceRootAllowed,
} from '../src/workspace-safety';

test('workspace safety rejects profile and system roots using lexical paths', () => {
  const home = path.resolve('/home/example');
  const env = {
    SystemRoot: path.resolve('/system'),
    ProgramFiles: path.resolve('/program-files'),
    APPDATA: path.resolve('/home/example/.config'),
  };

  assert.equal(isDisallowedWorkspaceRoot(home, env, home), true);
  assert.equal(isDisallowedWorkspaceRoot(path.join(home, 'Desktop'), env, home), true);
  assert.equal(isDisallowedWorkspaceRoot(path.join(home, 'Projects', 'repo'), env, home), false);
  assert.equal(isDisallowedWorkspaceRoot(path.join(env.APPDATA, 'EasyCodex'), env, home), true);
  assert.equal(isDisallowedWorkspaceRoot(path.join(env.SystemRoot, 'System32'), env, home), true);
});

test('workspace safety does not confuse sibling path prefixes', () => {
  assert.equal(isWithinBase('C:\\repo', 'C:\\repo\\child'), true);
  assert.equal(isWithinBase('C:\\repo', 'C:\\repo2\\child'), false);
});

test('workspace safety rejects roots whose realpath points at the user profile', (t) => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'easycodex-workspace-safety-'));
  const link = path.join(temp, 'home-link');
  try {
    try {
      fs.symlinkSync(os.homedir(), link, process.platform === 'win32' ? 'junction' : 'dir');
    } catch (error) {
      t.skip(`Cannot create directory symlink for realpath safety test: ${error instanceof Error ? error.message : String(error)}`);
      return;
    }

    assert.throws(() => assertWorkspaceRootAllowed(link), /Refusing to use/);
    assert.equal(isWorkspaceRootAllowed(link), false);
  } finally {
    try {
      fs.rmSync(link, { force: true });
    } catch {}
    try {
      fs.rmdirSync(temp);
    } catch {}
  }
});
