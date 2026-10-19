// No app server or database: run the Agent and structured scene mock checks.
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
const root = fileURLToPath(new URL('../', import.meta.url));
const cli = fileURLToPath(new URL('../node_modules/@playwright/test/cli.js', import.meta.url));
const result = spawnSync(process.execPath, [cli, 'test',
  'tests/platform/agent-confirmation.test.ts', 'tests/platform/scene-action-executor.test.ts',
  'tests/platform/home-scene-controls.test.ts', 'e2e/platform/assistant-dialogue.spec.ts',
  'tests/platform/legacy-assistant-removal.test.ts',
  'e2e/platform/agent-session.spec.ts', 'e2e/platform/model-probe.spec.ts'], { cwd: root, stdio: 'inherit' });
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
