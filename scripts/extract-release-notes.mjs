#!/usr/bin/env node

import fs from 'node:fs';
import path from 'node:path';

const version = String(process.argv[2] || '').trim().replace(/^v/i, '');
if (!version) {
  console.error('Usage: node scripts/extract-release-notes.mjs <version-or-tag>');
  process.exit(1);
}

const changelogPath = path.resolve('CHANGELOG.md');
const changelog = fs.readFileSync(changelogPath, 'utf8');
const headingPattern = new RegExp(`^##\\s+(?:\\[)?v?${version.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?:\\])?(?:\\s+-\\s+.*)?\\s*$`, 'im');
const match = headingPattern.exec(changelog);

if (!match) {
  console.error(`CHANGELOG.md does not contain a section for ${version}. Add "## ${version} - YYYY-MM-DD" before releasing.`);
  process.exit(1);
}

const start = match.index + match[0].length;
const rest = changelog.slice(start);
const nextHeading = /^##\s+/m.exec(rest);
const notes = rest.slice(0, nextHeading ? nextHeading.index : undefined).trim();

if (!notes) {
  console.error(`CHANGELOG.md section for ${version} is empty.`);
  process.exit(1);
}

process.stdout.write(`${notes}\n`);
