import assert from 'node:assert/strict';
import test from 'node:test';
import {
  MAX_ATTACHMENT_BATCH_BYTES,
  MAX_GIT_DIFF_CHARS,
  MAX_MEDIA_FILE_BYTES,
  MAX_MEDIA_IMAGE_BYTES,
  MAX_READ_FILE_BYTES,
  assertAttachmentBatchWithinLimit,
  assertMediaFileWithinLimit,
  assertMediaImageWithinLimit,
  assertReadableFileWithinLimit,
  capGitDiff,
} from '../src/safety-limits';

test('rejects attachment batches over the relay total limit', () => {
  assert.throws(
    () => assertAttachmentBatchWithinLimit([
      { name: 'a.bin', size: MAX_ATTACHMENT_BATCH_BYTES },
      { name: 'b.bin', size: 1 },
    ]),
    /48 MB total limit/,
  );
});

test('rejects mobile file reads over the relay read limit', () => {
  assert.throws(() => assertReadableFileWithinLimit(MAX_READ_FILE_BYTES + 1), /too large/);
});

test('rejects mobile image previews over the relay media limit', () => {
  assert.throws(() => assertMediaImageWithinLimit(MAX_MEDIA_IMAGE_BYTES + 1), /too large/);
});

test('rejects mobile artifact file opens over the relay media limit', () => {
  assert.throws(() => assertMediaFileWithinLimit(MAX_MEDIA_FILE_BYTES + 1), /too large/);
});

test('caps large git diffs with an explicit truncation marker', () => {
  const result = capGitDiff('x'.repeat(MAX_GIT_DIFF_CHARS + 10));

  assert.equal(result.truncated, true);
  assert.match(result.diff, /EasyCodex truncated this git diff/);
  assert.ok(result.diff.length < MAX_GIT_DIFF_CHARS + 200);
});
