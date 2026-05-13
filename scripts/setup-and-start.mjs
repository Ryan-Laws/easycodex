#!/usr/bin/env node

import crypto from 'node:crypto';
import { spawn } from 'node:child_process';
import { existsSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import process from 'node:process';
import { createInterface } from 'node:readline/promises';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');
const relayDir = path.join(rootDir, 'agent-relay');
const mobileDir = path.join(rootDir, 'mobile');

const npmCmd = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const defaultPort = 3001;

function firstLanAddress() {
  for (const addresses of Object.values(os.networkInterfaces())) {
    const match = addresses?.find((address) => address.family === 'IPv4' && !address.internal);
    if (match) return match.address;
  }
  return '127.0.0.1';
}

function parsePort(input, fallback = defaultPort) {
  const value = Number(input || fallback);
  if (!Number.isInteger(value) || value < 1 || value > 65535) {
    throw new Error('Port must be an integer between 1 and 65535.');
  }
  return value;
}

function shouldInstall(input) {
  const normalized = input.trim().toLowerCase();
  if (!normalized || normalized === 'y' || normalized === 'yes') return true;
  if (normalized === 'n' || normalized === 'no') return false;
  throw new Error('Install choice must be Y or N.');
}

function wait(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function runCommand(command, args, options = {}) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, args, { stdio: 'inherit', ...options });
    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0) return resolve();
      reject(new Error(`${command} ${args.join(' ')} exited with code ${code ?? 'unknown'}`));
    });
  });
}

async function askSetupQuestions() {
  const rl = createInterface({ input: process.stdin, output: process.stdout });
  try {
    console.log('\nEasyCodex terminal setup\n');
    const portInput = await rl.question(`Relay port [${defaultPort}]: `);
    const apiKeyInput = await rl.question('Relay API key [auto-generate]: ');
    const installInput = await rl.question('Install relay npm dependencies first? [Y/n]: ');

    return {
      port: parsePort(portInput.trim()),
      apiKey: apiKeyInput.trim() || crypto.randomBytes(24).toString('hex'),
      installDeps: shouldInstall(installInput),
    };
  } finally {
    rl.close();
  }
}

function assertRepoLayout() {
  if (existsSync(relayDir) && existsSync(mobileDir)) return;
  throw new Error(`Expected directories not found. Run this from the EasyCodex repo: ${rootDir}`);
}

function printAndroidNextSteps(relayUrl, apiKey) {
  console.log('\nNative Android app setup:');
  console.log(`1. Open ${mobileDir} in Android Studio.`);
  console.log('2. Run the app on an Android device or emulator.');
  console.log('3. Scan the relay QR code with your phone camera to import:');
  console.log(`   Relay URL: ${relayUrl}`);
  console.log(`   API key:    ${apiKey}`);
  console.log('\nLeave this terminal open while using the app. Press Ctrl+C to stop the relay.\n');
}

function startRelay(port, apiKey) {
  return spawn(npmCmd, ['run', 'dev'], {
    cwd: relayDir,
    stdio: 'inherit',
    env: {
      ...process.env,
      PORT: String(port),
      API_KEY: apiKey,
    },
  });
}

async function main() {
  assertRepoLayout();

  const { port, apiKey, installDeps } = await askSetupQuestions();
  const relayUrl = `ws://${firstLanAddress()}:${port}`;

  console.log(`\nRelay URL: ${relayUrl}`);
  console.log(`Relay API key: ${apiKey}\n`);

  if (installDeps) {
    console.log('Installing relay dependencies...\n');
    await runCommand(npmCmd, ['install'], { cwd: relayDir });
  }

  console.log('\nStarting agent relay...\n');
  const relayProcess = startRelay(port, apiKey);

  relayProcess.on('error', (error) => {
    console.error(`Relay failed to start: ${error.message}`);
    process.exit(1);
  });

  await wait(1200);

  printAndroidNextSteps(relayUrl, apiKey);
}

main().catch((error) => {
  console.error(`\nSetup failed: ${error.message}`);
  process.exit(1);
});
