import assert from 'node:assert/strict';
import test from 'node:test';
import { stopAgentLocallyForArchive } from '../src/session-orchestrator';

function fakeAgent(events: string[]) {
  return {
    id: 'agent_1',
    status: 'working',
    process: {
      kill: () => {
        events.push('kill');
        return true;
      },
    },
  };
}

test('local archive cleanup stops and removes a running agent before archive request', async () => {
  const events: string[] = [];
  const agent = fakeAgent(events);
  const agents = new Map([[agent.id, agent]]);
  const archiveRequest = async () => {
    events.push('archive_request');
    return { result: { ok: true } };
  };

  stopAgentLocallyForArchive(
    agent,
    agents,
    () => events.push('mark_stopped'),
    () => events.push('persist'),
  );
  await archiveRequest();

  assert.deepEqual(events, ['mark_stopped', 'kill', 'persist', 'archive_request']);
  assert.equal(agents.has(agent.id), false);
  assert.equal(agent.status, 'stopped');
});

test('local archive cleanup remains applied when archive request fails later', async () => {
  const events: string[] = [];
  const agent = fakeAgent(events);
  const agents = new Map([[agent.id, agent]]);

  stopAgentLocallyForArchive(
    agent,
    agents,
    () => events.push('mark_stopped'),
    () => events.push('persist'),
  );

  assert.deepEqual(events, ['mark_stopped', 'kill', 'persist']);
  assert.equal(agents.has(agent.id), false);
  assert.equal(agent.status, 'stopped');
});
