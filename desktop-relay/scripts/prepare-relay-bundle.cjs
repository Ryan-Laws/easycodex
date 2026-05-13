const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const projectDir = path.resolve(__dirname, '..');
const repoRoot = path.resolve(projectDir, '..');
const relayDir = path.join(repoRoot, 'agent-relay');
const bundleDir = path.join(projectDir, '.relay-bundle');

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

fs.mkdirSync(bundleDir, { recursive: true });

console.log('Preparing agent-relay build dependencies...');
runNpm(['ci'], relayDir);
runNpm(['run', 'build'], relayDir);

console.log(`Staging relay bundle in ${bundleDir}`);
copyFile('package.json');
copyFile('package-lock.json');
copyFile('tsconfig.json');
copyDir('dist');
copyDir('src');

console.log('Installing relay production dependencies into staged bundle...');
runNpm(['ci', '--omit=dev'], bundleDir);

const serverPath = path.join(bundleDir, 'dist', 'server.js');
const expressPath = path.join(bundleDir, 'node_modules', 'express');
const wsPath = path.join(bundleDir, 'node_modules', 'ws');
if (!fs.existsSync(serverPath) || !fs.existsSync(expressPath) || !fs.existsSync(wsPath)) {
  throw new Error('Relay bundle is incomplete after staging.');
}

console.log('Relay bundle is ready.');
