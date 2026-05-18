import assert from 'node:assert/strict';
import test from 'node:test';
import { checkForUpdates } from '../src/updater';

const originalFetch = globalThis.fetch;
const originalChannel = process.env.EASYCODEX_UPDATE_CHANNEL;

function mockFetch(payload: unknown, status = 200) {
  globalThis.fetch = (async () => new Response(JSON.stringify(payload), { status })) as typeof fetch;
}

test.afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalChannel == null) delete process.env.EASYCODEX_UPDATE_CHANNEL;
  else process.env.EASYCODEX_UPDATE_CHANNEL = originalChannel;
});

test('stable update checks serialize latest non-prerelease release assets', async () => {
  delete process.env.EASYCODEX_UPDATE_CHANNEL;
  mockFetch({
    tag_name: 'v99.0.0',
    prerelease: false,
    html_url: 'https://github.com/Ryan-Laws/easycodex/releases/tag/v99.0.0',
    name: 'EasyCodex 99',
    published_at: '2026-01-01T00:00:00Z',
    assets: [
      {
        name: 'EasyCodex.Mobile.99.0.0.apk',
        browser_download_url: 'https://github.com/Ryan-Laws/easycodex/releases/download/v99.0.0/EasyCodex.Mobile.99.0.0.apk',
        size: 123,
      },
      { name: 42, browser_download_url: 'ignored' },
    ],
  });

  const info = await checkForUpdates();

  assert.equal(info.channel, 'stable');
  assert.equal(info.latestVersion, '99.0.0');
  assert.equal(info.updateAvailable, true);
  assert.deepEqual(info.assets, [
    {
      name: 'EasyCodex.Mobile.99.0.0.apk',
      url: 'https://github.com/Ryan-Laws/easycodex/releases/download/v99.0.0/EasyCodex.Mobile.99.0.0.apk',
      size: 123,
    },
  ]);
});

test('beta update checks pick the first prerelease from release lists', async () => {
  process.env.EASYCODEX_UPDATE_CHANNEL = 'beta';
  mockFetch([
    { tag_name: 'v99.0.0', prerelease: false, assets: [] },
    { tag_name: 'v99.0.1-beta.1', prerelease: true, assets: [] },
  ]);

  const info = await checkForUpdates();

  assert.equal(info.channel, 'beta');
  assert.equal(info.latestVersion, '99.0.1-beta.1');
  assert.equal(info.updateAvailable, true);
});

test('update checks return structured errors for failed release requests', async () => {
  mockFetch({ message: 'rate limited' }, 403);

  const info = await checkForUpdates();

  assert.equal(info.updateAvailable, false);
  assert.equal(info.latestVersion, null);
  assert.match(info.error || '', /HTTP 403/);
  assert.deepEqual(info.assets, []);
});
