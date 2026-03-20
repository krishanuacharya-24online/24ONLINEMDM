const { spawn } = require('child_process');

delete process.env.ELECTRON_RUN_AS_NODE;

const electronBinary = require('electron');
const args = process.argv.slice(2);

const child = spawn(electronBinary, args, {
  stdio: 'inherit',
  shell: false,
  env: process.env
});

child.on('exit', code => {
  process.exit(code ?? 0);
});

child.on('error', error => {
  console.error(error);
  process.exit(1);
});
