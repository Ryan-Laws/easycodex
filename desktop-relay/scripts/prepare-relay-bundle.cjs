const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const projectDir = path.resolve(__dirname, '..');
const repoRoot = path.resolve(projectDir, '..');
const relayDir = path.join(repoRoot, 'agent-relay');
const bundleDir = path.join(projectDir, '.relay-bundle');

function pathKey(targetPath) {
  const resolved = path.resolve(targetPath);
  return process.platform === 'win32' ? resolved.toLowerCase() : resolved;
}

function assertInside(base, targetPath) {
  const resolvedBase = path.resolve(base);
  const resolvedTarget = path.resolve(targetPath);
  const baseKey = pathKey(resolvedBase);
  const targetKey = pathKey(resolvedTarget);
  if (targetKey !== baseKey && !targetKey.startsWith(`${baseKey}${path.sep}`)) {
    throw new Error(`Refusing to clean path outside project directory: ${resolvedTarget}`);
  }
}

function resetBundleDir() {
  assertInside(projectDir, bundleDir);
  if (path.basename(bundleDir) !== '.relay-bundle') {
    throw new Error(`Refusing to clean unexpected bundle directory: ${bundleDir}`);
  }
  fs.rmSync(bundleDir, { recursive: true, force: true });
  fs.mkdirSync(bundleDir, { recursive: true });
}

function npmInvocation(args) {
  if (process.env.npm_execpath && fs.existsSync(process.env.npm_execpath)) {
    return {
      command: process.execPath,
      args: [process.env.npm_execpath, ...args],
    };
  }
  if (process.platform === 'win32') {
    return {
      command: process.env.ComSpec || 'cmd.exe',
      args: ['/d', '/s', '/c', ['call', 'npm', ...args].join(' ')],
    };
  }
  return { command: 'npm', args };
}

function runNpm(args, cwd) {
  const invocation = npmInvocation(args);
  const result = spawnSync(invocation.command, invocation.args, {
    cwd,
    stdio: 'inherit',
    shell: false,
    windowsVerbatimArguments: process.platform === 'win32' && path.basename(invocation.command).toLowerCase() === 'cmd.exe',
    env: process.env,
  });
  if (result.error) throw result.error;
  if (result.status !== 0) {
    throw new Error(`${invocation.command} ${invocation.args.join(' ')} exited with code ${result.status}`);
  }
}

function copyFile(name) {
  fs.copyFileSync(path.join(relayDir, name), path.join(bundleDir, name));
}

function copyDir(name) {
  fs.cpSync(path.join(relayDir, name), path.join(bundleDir, name), {
    recursive: true,
    force: true,
  });
}

function removeIfExists(filePath) {
  if (fs.existsSync(filePath) && fs.statSync(filePath).isFile()) {
    fs.unlinkSync(filePath);
  }
}

function pruneRelayBundle() {
  removeIfExists(path.join(bundleDir, 'package-lock.json'));
  removeIfExists(path.join(bundleDir, 'tsconfig.json'));

  const distDir = path.join(bundleDir, 'dist');
  if (fs.existsSync(distDir)) {
    for (const entry of fs.readdirSync(distDir)) {
      if (entry.endsWith('.d.ts') || entry.endsWith('.map')) {
        removeIfExists(path.join(distDir, entry));
      }
    }
  }
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

resetBundleDir();

console.log('Preparing agent-relay build dependencies...');
runNpm(['ci'], relayDir);
runNpm(['run', 'build'], relayDir);

console.log(`Staging relay bundle in ${bundleDir}`);
copyFile('package.json');
copyFile('package-lock.json');
copyDir('dist');

const relayPackage = readJson(path.join(relayDir, 'package.json'));
const stagedPackage = readJson(path.join(bundleDir, 'package.json'));
if (stagedPackage.version !== relayPackage.version) {
  throw new Error(`Relay bundle version mismatch: expected ${relayPackage.version}, got ${stagedPackage.version}`);
}

console.log('Installing relay production dependencies into staged bundle...');
runNpm(['ci', '--omit=dev'], bundleDir);

const serverPath = path.join(bundleDir, 'dist', 'server.js');
const expressPath = path.join(bundleDir, 'node_modules', 'express');
const wsPath = path.join(bundleDir, 'node_modules', 'ws');
if (!fs.existsSync(serverPath) || !fs.existsSync(expressPath) || !fs.existsSync(wsPath)) {
  throw new Error('Relay bundle is incomplete after staging.');
}

pruneRelayBundle();
console.log('Relay bundle is ready.');
