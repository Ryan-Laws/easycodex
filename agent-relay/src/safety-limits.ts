export const MAX_ATTACHMENT_BYTES = 12 * 1024 * 1024;
export const MAX_ATTACHMENT_BATCH_BYTES = 48 * 1024 * 1024;
export const MAX_MEDIA_IMAGE_BYTES = 16 * 1024 * 1024;
export const MAX_MEDIA_FILE_BYTES = 16 * 1024 * 1024;
export const MAX_READ_FILE_BYTES = 1024 * 1024;
export const MAX_GIT_DIFF_CHARS = 200_000;

export function assertAttachmentBatchWithinLimit(files: Array<{ name?: unknown; size: number }>): void {
  const totalBytes = files.reduce((sum, file) => sum + file.size, 0);
  if (totalBytes > MAX_ATTACHMENT_BATCH_BYTES) {
    throw new Error('Attachment batch exceeds the 48 MB total limit.');
  }
}

export function assertReadableFileWithinLimit(size: number): void {
  if (size > MAX_READ_FILE_BYTES) {
    throw new Error('File is too large to read through EasyCodex mobile.');
  }
}

export function assertMediaImageWithinLimit(size: number): void {
  if (size > MAX_MEDIA_IMAGE_BYTES) {
    throw new Error('Image is too large to preview through EasyCodex mobile.');
  }
}

export function assertMediaFileWithinLimit(size: number): void {
  if (size > MAX_MEDIA_FILE_BYTES) {
    throw new Error('File is too large to open through EasyCodex mobile.');
  }
}

export function capGitDiff(diff: string): { diff: string; truncated: boolean; limit: number } {
  if (diff.length <= MAX_GIT_DIFF_CHARS) {
    return { diff, truncated: false, limit: MAX_GIT_DIFF_CHARS };
  }
  return {
    diff: `${diff.slice(0, MAX_GIT_DIFF_CHARS).trimEnd()}\n\n[EasyCodex truncated this git diff. Use the desktop repository for the full diff.]`,
    truncated: true,
    limit: MAX_GIT_DIFF_CHARS,
  };
}
