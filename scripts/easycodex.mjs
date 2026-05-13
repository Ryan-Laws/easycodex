#!/usr/bin/env node

import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { existsSync, readdirSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { createInterface } from 'node:readline/promises';

const DEFAULT_REPO_URL = process.env.EASY_CODEX_REPO_URL || 'https://github.com/Ryan-Laws/easycodex.git';
const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';

function printHelp() {
  console.log(`
EasyCodex CLI

Usage:
  easycodex init [directory] [--repo <url>] [--no-start]
  easycodex setup [directory]
  easycodex --help

Examples:
  easycodex init
  easycodex init my-easycodex
  easycodex init my-easycodex --repo https://github.com/Ryan-Laws/easycodex.git
  easycodex setup
`);
}

function runCommand(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', ...options });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) {
        resolve();
        return;
      }
      reject(new Error(`${command} ${args.join(' ')} exited with code ${code ?? 'unknown'}`));
    });
  });
}

async function installMobileDependencies() {
  console.log('Mobile is now a native Android project; install/build it from Android Studio or Gradle.');
}

function isEasyCodexRepo(dir) {
  return (
    existsSync(path.join(dir, 'agent-relay')) &&
    existsSync(path.join(dir, 'mobile'))
  );
}

function findEasyCodexRoot(baseDir) {
  const resolved = path.resolve(baseDir);
  if (isEasyCodexRepo(resolved)) return resolved;

  let children = [];
  try {
    children = readdirSync(resolved, { withFileTypes: true });
  } catch {
    return null;
  }

  for (const child of children) {
    if (!child.isDirectory()) continue;
    const candidate = path.join(resolved, child.name);
    if (isEasyCodexRepo(candidate)) return candidate;
  }

  return null;
}

function parseInitArgs(argv) {
  let directory = 'easycodex-mobile';
  let repo = DEFAULT_REPO_URL;
  let noStart = false;
  const positional = [];

  for (let i = 0; i < argv.length; i += 1) {
    const arg = argv[i];
    if (arg === '--repo') {
      const next = argv[i + 1];
      if (!next) throw new Error('--repo requires a value');
      repo = next;
      i += 1;
      continue;
    }
    if (arg === '--no-start') {
      noStart = true;
      continue;
    }
    if (arg.startsWith('-')) {
      throw new Error(`Unknown option: ${arg}`);
    }
    positional.push(arg);
  }

  if (positional[0]) directory = positional[0];
  return { directory, repo, noStart };
}

function resolveEasyCodexRoot(baseDir) {
  const rootDir = findEasyCodexRoot(baseDir);
  if (!rootDir) {
    const attempted = path.resolve(baseDir);
    throw new Error(`Could not find EasyCodex repo at ${attempted}. Expected agent-relay and mobile at root or one level below.`);
  }
  return rootDir;
}

function getLocalIPv4() {
  const interfaces = os.networkInterfaces();
  for (const name of Object.keys(interfaces)) {
    for (const iface of interfaces[name] || []) {
      if (iface.family === 'IPv4' && !iface.internal) return iface.address;
    }
  }
  return '127.0.0.1';
}

function parsePort(input) {
  const value = Number(input);
  if (!Number.isInteger(value) || value < 1 || value > 65535) {
    throw new Error('Port must be an integer between 1 and 65535.');
  }
  return value;
}

function parseInstallChoice(input) {
  const normalized = input.trim().toLowerCase();
  if (!normalized || normalized === 'y' || normalized === 'yes') return true;
  if (normalized === 'n' || normalized === 'no') return false;
  throw new Error('Install choice must be Y or N.');
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function readSetupConfig() {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  try {
    console.log('\nEasyCodex terminal setup\n');
    const portInput = await rl.question('Relay port [3001]: ');
    const apiKeyInput = await rl.question('Relay API key [auto-generate]: ');
    const installInput = await rl.question('Install relay npm dependencies first? [Y/n]: ');
    const openAiApiKey = (await rl.question('OPENAI_API_KEY (optional, Enter to use current shell/Codex login): ')).trim();
    const workspaceInput = (await rl.question('Agent workspace path [repo root]: ')).trim();

    return {
      port: parsePort((portInput || '3001').trim()),
      apiKey: apiKeyInput.trim() || crypto.randomBytes(24).toString('hex'),
      openAiApiKey,
      workspaceInput,
      installDeps: parseInstallChoice(installInput),
    };
  } finally {
    rl.close();
  }
}

async function runInteractiveSetup(rootDir) {
  const relayDir = path.join(rootDir, 'agent-relay');
  const mobileDir = path.join(rootDir, 'mobile');
  if (!existsSync(relayDir) || !existsSync(mobileDir)) {
    throw new Error(`Invalid EasyCodex repository at ${rootDir}`);
  }

  const {
    port,
    apiKey,
    openAiApiKey,
    workspaceInput,
    installDeps,
  } = await readSetupConfig();
  const relayUrl = `ws://${getLocalIPv4()}:${port}`;
  const workspacePath = workspaceInput ? path.resolve(workspaceInput) : rootDir;

  if (!existsSync(workspacePath)) {
    throw new Error(`Workspace path does not exist: ${workspacePath}`);
  }

  console.log(`\nRelay URL: ${relayUrl}`);
  console.log(`Relay API key: ${apiKey}`);
  console.log(`Agent workspace: ${workspacePath}\n`);

  if (installDeps) {
    console.log('Installing relay dependencies...\n');
    await runCommand(npmCmd, ['install'], { cwd: relayDir });
    // Keep older clones working where this dependency might be missing.
    await runCommand(npmCmd, ['install', 'qrcode-terminal@^0.12.0'], { cwd: relayDir });
    console.log('\nChecking mobile project...\n');
    await installMobileDependencies(mobileDir);
  }

  console.log('\nStarting agent relay...\n');
  const relayProcess = spawn(npmCmd, ['run', 'dev'], {
    cwd: relayDir,
    stdio: 'inherit',
    env: {
      ...process.env,
      PORT: String(port),
      API_KEY: apiKey,
      CODEX_CWD: workspacePath,
      ...(openAiApiKey ? { OPENAI_API_KEY: openAiApiKey } : {}),
    },
  });

  relayProcess.on('error', (error) => {
    console.error(`Relay failed to start: ${error.message}`);
    process.exit(1);
  });

  await wait(1200);

  console.log('\nNative Android app setup:');
  console.log(`1. Open ${mobileDir} in Android Studio.`);
  console.log('2. Run the app on an Android device or emulator.');
  console.log('3. Scan the relay QR code with your phone camera to import:');
  console.log(`   Relay URL: ${relayUrl}`);
  console.log(`   API key:    ${apiKey}`);
  console.log('\nLeave this terminal open while using the app. Press Ctrl+C to stop the relay.\n');
}

async function handleInit(argv) {
  const { directory, repo, noStart } = parseInitArgs(argv);
  const targetDir = path.resolve(process.cwd(), directory);

  if (existsSync(targetDir)) {
    throw new Error(`Target directory already exists: ${targetDir}`);
  }

  console.log(`\nCloning EasyCodex into ${targetDir}`);
  await runCommand('git', ['clone', repo, targetDir]);

  if (noStart) {
    console.log('\nRepository installed.');
    console.log(`Next: easycodex setup ${directory}`);
    return;
  }

  const rootDir = resolveEasyCodexRoot(targetDir);
  console.log('\nStarting interactive setup...\n');
  await runInteractiveSetup(rootDir);
}

async function handleSetup(argv) {
  const baseDir = argv[0] ? path.resolve(process.cwd(), argv[0]) : process.cwd();
  const rootDir = resolveEasyCodexRoot(baseDir);
  await runInteractiveSetup(rootDir);
}

async function main() {
  const argv = process.argv.slice(2);
  const command = argv[0];

  if (!command || command === 'init') {
    await handleInit(command === 'init' ? argv.slice(1) : argv);
    return;
  }

  if (command === 'setup') {
    await handleSetup(argv.slice(1));
    return;
  }

  if (command === '--help' || command === '-h' || command === 'help') {
    printHelp();
    return;
  }

  throw new Error(`Unknown command: ${command}`);
}

main().catch((error) => {
  console.error(`\nEasyCodex CLI error: ${error.message}`);
  process.exit(1);
});
