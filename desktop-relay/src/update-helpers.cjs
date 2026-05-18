const crypto = require('node:crypto');
const fs = require('node:fs');

function selectReleaseForChannel(payload, channel) {
  if (!Array.isArray(payload)) return payload;
  if (channel === 'beta') return payload.find((release) => release?.prerelease === true) || null;
  return payload.find((release) => release?.prerelease !== true) || null;
}

function assetScore(asset, platform = process.platform, arch = process.arch) {
  const name = String(asset?.name || '').toLowerCase();
  if (platform === 'win32') {
    if (name.includes('relay.setup') && name.endsWith('.exe')) return 100;
    if (name.includes('relay.portable') && name.endsWith('.exe')) return 80;
  }
  if (platform === 'darwin') {
    const macArch = arch === 'arm64' ? 'arm64' : 'x64';
    if (name.includes(`mac-${macArch}`) && name.endsWith('.dmg')) return 100;
    if (name.includes(`mac-${macArch}`) && name.endsWith('.zip')) return 80;
  }
  if (platform === 'linux') {
    if (name.endsWith('.appimage')) return 100;
    if (name.endsWith('.deb')) return 80;
  }
  return 0;
}

function selectUpdateAsset(info, platform = process.platform, arch = process.arch) {
  const assets = Array.isArray(info?.assets) ? info.assets : [];
  return assets
    .map((asset) => ({ asset, score: assetScore(asset, platform, arch) }))
    .filter((entry) => entry.score > 0)
    .sort((a, b) => b.score - a.score)[0]?.asset || null;
}

async function sha256File(targetPath) {
  return new Promise((resolve, reject) => {
    const hash = crypto.createHash('sha256');
    const stream = fs.createReadStream(targetPath);
    stream.on('data', (chunk) => hash.update(chunk));
    stream.on('error', reject);
    stream.on('end', () => resolve(hash.digest('hex')));
  });
}

async function verifyDownloadedAsset(asset, targetPath, options = {}) {
  const expectedDigest = String(asset?.digest || '').trim();
  const match = expectedDigest.match(/^sha256:([a-f0-9]{64})$/i);
  if (!match) {
    if (options.allowUnsigned === true) {
      if (typeof options.log === 'function') options.log('Warning: update asset has no SHA-256 digest; continuing because unsigned updates are explicitly allowed.');
      return;
    }
    throw new Error('Update asset does not include a SHA-256 digest. Refusing to run an unsigned installer.');
  }
  const actual = await sha256File(targetPath);
  if (actual.toLowerCase() !== match[1].toLowerCase()) {
    throw new Error('Downloaded update failed SHA-256 verification.');
  }
}

module.exports = {
  assetScore,
  selectReleaseForChannel,
  selectUpdateAsset,
  verifyDownloadedAsset,
};
