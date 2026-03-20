const { spawn } = require('child_process');
const path = require('path');

delete process.env.ELECTRON_RUN_AS_NODE;

const builderCli = require.resolve('electron-builder/out/cli/cli.js');
const args = [builderCli, ...process.argv.slice(2)];

const child = spawn(process.execPath, args, {
  stdio: 'inherit',
  shell: false,
  env: process.env,
  cwd: path.resolve(__dirname, '..')
});

child.on('exit', code => {
  process.exit(code ?? 0);
});

child.on('error', error => {
  console.error(error);
  process.exit(1);
});
