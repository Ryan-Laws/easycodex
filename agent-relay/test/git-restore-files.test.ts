import test from 'node:test';
import assert from 'node:assert/strict';
import path from 'node:path';
import { mobileGitStatusPayload, normalizeGitCommitFiles, normalizeGitRenamedFiles, normalizeGitRestoreFiles } from '../src/git-paths';

const cwd = path.resolve('C:/repo/project');

test('git restore accepts explicit tracked files', () => {
  assert.deepEqual(
    normalizeGitRestoreFiles(cwd, ['src/app.ts', 'README.md', 'src/app.ts'], new Set(['src/app.ts', 'README.md'])),
    ['src/app.ts', 'README.md'],
  );
});

test('git restore refuses repo root and git options', () => {
  assert.throws(() => normalizeGitRestoreFiles(cwd, ['.'], new Set(['src/app.ts'])), /explicit file paths/);
  assert.throws(() => normalizeGitRestoreFiles(cwd, ['--source=HEAD'], new Set(['--source=HEAD'])), /git options/);
});

test('git restore refuses path escapes, globs, and untracked files', () => {
  assert.throws(() => normalizeGitRestoreFiles(cwd, ['../outside.ts'], new Set(['../outside.ts'])), /escapes cwd/);
  assert.throws(() => normalizeGitRestoreFiles(cwd, ['src/*.ts'], new Set(['src/*.ts'])), /not globs/);
  assert.throws(() => normalizeGitRestoreFiles(cwd, ['notes.txt'], new Set(['src/app.ts'])), /untracked or unknown/);
});

test('git commit refuses pathspec magic and globs', () => {
  assert.throws(() => normalizeGitCommitFiles(cwd, ['src/*.ts']), /not globs/);
  assert.throws(() => normalizeGitCommitFiles(cwd, [':(glob)src/**']), /not globs/);
  assert.throws(() => normalizeGitCommitFiles(cwd, ['.']), /explicit file paths/);
});

test('git status rename entries normalize to target paths for mobile', () => {
  assert.deepEqual(
    normalizeGitRenamedFiles([
      { from: 'src/old.ts', to: 'src/new.ts' },
      { path: 'docs/new.md' },
      'README.md',
      { from: 'missing-target.ts' },
    ]),
    ['src/new.ts', 'docs/new.md', 'README.md'],
  );
});

test('git status payload separates visible changes from safe restore targets', () => {
  const payload = mobileGitStatusPayload(
    {
      current: 'feature/mobile',
      ahead: 1,
      behind: 2,
      modified: ['src/app.ts'],
      created: ['src/new.ts'],
      deleted: ['src/deleted.ts'],
      renamed: [{ from: 'src/old.ts', to: 'src/renamed.ts' }],
      not_added: ['scratch.txt'],
      conflicted: ['src/conflict.ts'],
      isClean: () => false,
    },
    new Set(['src/app.ts', 'src/deleted.ts', 'src/renamed.ts', 'src/conflict.ts']),
  );

  assert.deepEqual(payload.renamed, ['src/renamed.ts']);
  assert.deepEqual(payload.notAdded, ['scratch.txt']);
  assert.deepEqual(payload.restorableFiles, ['src/app.ts', 'src/deleted.ts', 'src/renamed.ts', 'src/conflict.ts']);
  assert.equal(payload.branch, 'feature/mobile');
  assert.equal(payload.isClean, false);
});
