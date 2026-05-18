const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const {
  selectReleaseForChannel,
  selectUpdateAsset,
  verifyDownloadedAsset,
} = require('../src/update-helpers.cjs');

test('selectReleaseForChannel separates stable and beta releases', () => {
  const releases = [
    { tag_name: 'v1.0.0-beta.1', prerelease: true },
    { tag_name: 'v0.9.0', prerelease: false },
  ];

  assert.equal(selectReleaseForChannel(releases, 'beta')?.tag_name, 'v1.0.0-beta.1');
  assert.equal(selectReleaseForChannel(releases, 'stable')?.tag_name, 'v0.9.0');
});

test('selectUpdateAsset chooses platform installers and ignores unrelated assets', () => {
  const info = {
    assets: [
      { name: 'EasyCodex.Mobile.1.0.0.apk' },
      { name: 'EasyCodex.Relay.Portable.1.0.0-x64.exe' },
      { name: 'EasyCodex.Relay.Setup.1.0.0-x64.exe' },
      { name: 'EasyCodex.Relay.1.0.0.mac-arm64.zip' },
      { name: 'EasyCodex.Relay.1.0.0.mac-arm64.dmg' },
      { name: 'EasyCodex.Relay.1.0.0.linux-x64.deb' },
      { name: 'EasyCodex.Relay.1.0.0.linux-x64.AppImage' },
    ],
  };

  assert.equal(selectUpdateAsset(info, 'win32', 'x64')?.name, 'EasyCodex.Relay.Setup.1.0.0-x64.exe');
  assert.equal(selectUpdateAsset(info, 'darwin', 'arm64')?.name, 'EasyCodex.Relay.1.0.0.mac-arm64.dmg');
  assert.equal(selectUpdateAsset(info, 'linux', 'x64')?.name, 'EasyCodex.Relay.1.0.0.linux-x64.AppImage');
});

test('verifyDownloadedAsset accepts matching sha256 and rejects mismatches', async () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'easycodex-update-'));
  const file = path.join(temp, 'asset.bin');
  try {
    fs.writeFileSync(file, 'payload');
    const digest = crypto.createHash('sha256').update('payload').digest('hex');

    await verifyDownloadedAsset({ digest: `sha256:${digest}` }, file);
    await assert.rejects(
      () => verifyDownloadedAsset({ digest: `sha256:${'0'.repeat(64)}` }, file),
      /SHA-256 verification/,
    );
    await assert.rejects(
      () => verifyDownloadedAsset({}, file),
      /does not include/,
    );
    await verifyDownloadedAsset({}, file, { allowUnsigned: true });
  } finally {
    try { fs.unlinkSync(file); } catch {}
    try { fs.rmdirSync(temp); } catch {}
  }
});
