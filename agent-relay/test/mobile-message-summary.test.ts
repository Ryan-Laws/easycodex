import assert from 'node:assert/strict';
import test from 'node:test';
import { summarizeMessageForMobile } from '../src/session-orchestrator';

test('summarizes PowerShell wrapper commands for mobile', () => {
  const message = summarizeMessageForMobile({
    role: 'agent',
    type: 'command',
    text: '"C:\\Program Files\\PowerShell\\7\\pwsh.exe" -Command "Get-Content -Path apps\\dashboard\\server\\api\\v1\\ips\\quote.get.ts"',
  }) as any;

  assert.equal(message.text, '运行命令\n读取文件 apps/dashboard/server/api/v1/ips/quote.get.ts');
  assert.match(message.detailText, /pwsh\.exe/);
});

test('summarizes command output metadata and strips ansi', () => {
  const message = summarizeMessageForMobile({
    role: 'agent',
    type: 'command_output',
    text: '\u001b[31mERROR\u001b[0m\nexit: 1\nduration: 1008ms',
  }) as any;

  assert.equal(message.text, 'exit 1 · 1.0s\nERROR');
  assert.equal(message.detailText, 'ERROR\nexit: 1\nduration: 1008ms');
});

test('summarizes subagent without leaking internal parameter errors', () => {
  const message = summarizeMessageForMobile({
    role: 'agent',
    type: 'sub_agent',
    text: 'error: Full-history forked agents inherit the parent agent type, model, and reasoning effort; omit agent_type, model, and reasoning_effort.',
    subAgentStatus: 'failed',
    subAgentNickname: 'explorer',
    subAgentThreadId: 'thread_123',
  }) as any;

  assert.equal(message.text, '子代理失败 · explorer');
  assert.equal(message.detailText, '子代理启动参数与 full-history fork 不兼容。');
});

test('summarizes inline user images without leaking base64 data', () => {
  const message = summarizeMessageForMobile({
    role: 'user',
    type: 'user',
    text: '为什么会报错\n<image>\n![image](data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAAB)',
  }) as any;

  assert.equal(message.text, '为什么会报错\n已附加 1 张图片');
  assert.doesNotMatch(message.text, /base64/);
});
