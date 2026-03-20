const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const projectRoot = path.resolve(__dirname, '..');
const buildDir = path.join(projectRoot, 'build');
const buildConfigPath = path.join(buildDir, 'agent-build-config.json');
const distDir = path.join(projectRoot, 'dist');
const releaseDir = path.join(projectRoot, 'release');
const FIXED_BASE_URL = 'http://192.168.254.20:8080';

function normalizeBaseUrl(value) {
  if (!value) {
    return FIXED_BASE_URL;
  }
  const text = String(value).trim();
  if (!text) {
    return FIXED_BASE_URL;
  }
  if (text.replace(/\/$/, '') !== FIXED_BASE_URL) {
    throw new Error(`This agent is currently pinned to ${FIXED_BASE_URL}. Do not override the base URL in this build.`);
  }
  return FIXED_BASE_URL;
}

function writeBuildConfig(baseUrl) {
  fs.mkdirSync(buildDir, { recursive: true });
  const payload = {
    app_name: '24onlineMDMAgent',
    generated_at: new Date().toISOString(),
    base_url: baseUrl,
    lock_base_url: true
  };
  fs.writeFileSync(buildConfigPath, `${JSON.stringify(payload, null, 2)}\n`, 'utf8');
}

function runNodeScript(relativeScriptPath, args = []) {
  const result = spawnSync(process.execPath, [path.join(projectRoot, relativeScriptPath), ...args], {
    cwd: projectRoot,
    stdio: 'inherit',
    shell: false
  });
  if ((result.status ?? 1) !== 0) {
    process.exit(result.status ?? 1);
  }
}

function cleanDirectory(directory) {
  if (fs.existsSync(directory)) {
    fs.rmSync(directory, { recursive: true, force: true });
  }
  fs.mkdirSync(directory, { recursive: true });
}

function stageReleaseArtifacts() {
  fs.mkdirSync(releaseDir, { recursive: true });
  for (const entry of fs.readdirSync(distDir, { withFileTypes: true })) {
    if (!entry.isFile()) {
      continue;
    }
    fs.copyFileSync(path.join(distDir, entry.name), path.join(releaseDir, entry.name));
  }
}

function main() {
  const forwardedArgs = [];
  let baseUrl = FIXED_BASE_URL;

  for (const arg of process.argv.slice(2)) {
    if (arg.startsWith('--base-url=')) {
      baseUrl = normalizeBaseUrl(arg.slice('--base-url='.length));
      continue;
    }
    if (arg === '--base-url') {
      throw new Error(`Use --base-url=${FIXED_BASE_URL} if you need to pass the fixed endpoint explicitly.`);
    }
    forwardedArgs.push(arg);
  }

  writeBuildConfig(baseUrl);
  runNodeScript(path.join('scripts', 'generate-assets.cjs'));
  cleanDirectory(distDir);
  cleanDirectory(releaseDir);
  forwardedArgs.push(`--config.directories.output=${path.relative(projectRoot, distDir)}`);

  runNodeScript(path.join('scripts', 'run-builder.cjs'), forwardedArgs);
  stageReleaseArtifacts();
}

try {
  main();
} catch (error) {
  console.error(error.message || error);
  process.exit(1);
}
