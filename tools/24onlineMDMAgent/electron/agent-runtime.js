const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFile } = require('child_process');
const { promisify } = require('util');

const execFileAsync = promisify(execFile);
const packageJson = require('../package.json');

const DEFAULT_CONFIG = {
  base_url: 'http://192.168.254.20:8080',
  tenant_id: '',
  tenant_key: '',
  setup_key: '',
  launch_at_startup: false,
  desktop_shortcut: false,
  state_path: 'agent-state.json',
  output_dir: 'out'
};

const DEFAULT_TIMEOUT_MS = 60000;
const MAX_INSTALLED_APPS = 2000;

function normalizeText(value, maxLength = 0) {
  if (value === null || value === undefined) {
    return null;
  }
  const text = String(value).trim();
  if (!text) {
    return null;
  }
  if (maxLength > 0 && text.length > maxLength) {
    return text.slice(0, maxLength);
  }
  return text;
}

function normalizeBoolean(value) {
  if (typeof value === 'boolean') {
    return value;
  }
  if (typeof value === 'string') {
    return ['1', 'true', 'yes', 'on'].includes(value.trim().toLowerCase());
  }
  if (typeof value === 'number') {
    return value !== 0;
  }
  return false;
}

function ensureDir(dirPath) {
  fs.mkdirSync(dirPath, { recursive: true });
}

function readJson(filePath, fallback = null) {
  if (!fs.existsSync(filePath)) {
    return fallback;
  }
  const raw = fs.readFileSync(filePath, 'utf8').trim();
  if (!raw) {
    return fallback;
  }
  return JSON.parse(raw);
}

function writeJson(filePath, value) {
  ensureDir(path.dirname(filePath));
  fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, 'utf8');
}

function sha256Hex(value) {
  return crypto.createHash('sha256').update(value, 'utf8').digest('hex');
}

function utcIso(date = new Date()) {
  return new Date(date).toISOString();
}

function joinUrl(baseUrl, resourcePath) {
  if (/^https?:\/\//i.test(resourcePath)) {
    return resourcePath;
  }
  const normalizedBase = String(baseUrl || '').replace(/\/+$/, '');
  if (String(resourcePath).startsWith('/')) {
    return `${normalizedBase}${resourcePath}`;
  }
  return `${normalizedBase}/${resourcePath}`;
}

function buildHeaders(config) {
  const tenantId = normalizeText(config.tenant_id, 64);
  const tenantKey = normalizeText(config.tenant_key, 1024);
  if (!tenantId) {
    throw new Error('Tenant ID is required.');
  }
  if (!tenantKey) {
    throw new Error('Tenant key is required.');
  }
  return {
    'Content-Type': 'application/json',
    'X-Tenant-Id': tenantId.toLowerCase(),
    'X-Tenant-Key': tenantKey
  };
}

async function invokeJsonApi(method, url, { headers = {}, body = null, timeoutMs = DEFAULT_TIMEOUT_MS } = {}) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const response = await fetch(url, {
      method,
      headers,
      body: body === null ? undefined : JSON.stringify(body),
      signal: controller.signal
    });
    const raw = await response.text();
    const trimmed = raw.trim();
    let parsed = null;
    if (trimmed) {
      try {
        parsed = JSON.parse(trimmed);
      } catch {
        parsed = trimmed;
      }
    }
    if (!response.ok) {
      throw new Error(`HTTP ${method} ${url} failed with ${response.status}: ${typeof parsed === 'string' ? parsed : JSON.stringify(parsed)}`);
    }
    return parsed;
  } finally {
    clearTimeout(timeoutId);
  }
}

async function runCommand(command, args = [], { timeoutMs = DEFAULT_TIMEOUT_MS, allowFailure = false, cwd = undefined } = {}) {
  try {
    const result = await execFileAsync(command, args, {
      timeout: timeoutMs,
      maxBuffer: 20 * 1024 * 1024,
      windowsHide: true,
      cwd
    });
    return {
      stdout: (result.stdout || '').trim(),
      stderr: (result.stderr || '').trim()
    };
  } catch (error) {
    if (allowFailure) {
      return null;
    }
    const stderr = error.stderr ? String(error.stderr).trim() : '';
    const suffix = stderr ? `: ${stderr}` : '';
    throw new Error(`Command failed: ${command} ${args.join(' ')}${suffix}`);
  }
}

function readTextIfExists(filePath) {
  try {
    if (fs.existsSync(filePath)) {
      return fs.readFileSync(filePath, 'utf8').trim() || null;
    }
  } catch {
  }
  return null;
}

function parseOsRelease() {
  const raw = readTextIfExists('/etc/os-release');
  if (!raw) {
    return {};
  }
  const values = {};
  for (const line of raw.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) {
      continue;
    }
    const separatorIndex = trimmed.indexOf('=');
    if (separatorIndex <= 0) {
      continue;
    }
    const key = trimmed.slice(0, separatorIndex);
    let value = trimmed.slice(separatorIndex + 1).trim();
    value = value.replace(/^"/, '').replace(/"$/, '');
    values[key] = value;
  }
  return values;
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function defaultAgentId() {
  return `desktop-agent-${process.platform}`;
}

function defaultAgentVersion() {
  return `${packageJson.version}-${process.platform}`;
}

function normalizeConfig(configPath, input, options = {}) {
  const source = input || {};
  const lockedBaseUrl = normalizeText(options.lockedBaseUrl, 1024);
  const defaultBaseUrl = normalizeText(options.defaultBaseUrl, 1024) || DEFAULT_CONFIG.base_url;
  const effectiveBaseUrl = lockedBaseUrl || normalizeText(source.base_url, 1024) || defaultBaseUrl;
  return {
    base_url: effectiveBaseUrl,
    tenant_id: normalizeText(source.tenant_id, 64) || '',
    tenant_key: normalizeText(source.tenant_key, 1024) || '',
    setup_key: normalizeText(source.setup_key, 512) || '',
    launch_at_startup: normalizeBoolean(source.launch_at_startup),
    desktop_shortcut: normalizeBoolean(source.desktop_shortcut),
    state_path: normalizeText(source.state_path, 512) || DEFAULT_CONFIG.state_path,
    output_dir: normalizeText(source.output_dir, 512) || DEFAULT_CONFIG.output_dir,
    __lockedBaseUrl: Boolean(lockedBaseUrl),
    __configPath: configPath
  };
}

function sanitizeConfigForDisk(config, options = {}) {
  const normalized = normalizeConfig(config.__configPath || '.', config, options);
  return {
    base_url: normalized.base_url,
    tenant_id: normalized.tenant_id,
    launch_at_startup: normalized.launch_at_startup,
    desktop_shortcut: normalized.desktop_shortcut
  };
}

function ensureConfigExists(configPath, templatePath, options = {}) {
  if (fs.existsSync(configPath)) {
    return;
  }
  ensureDir(path.dirname(configPath));
  const template = readJson(templatePath, DEFAULT_CONFIG);
  const templateConfig = normalizeConfig(configPath, template, options);
  writeJson(configPath, sanitizeConfigForDisk(templateConfig, options));
}

function resolveManagedPaths(configPath, config) {
  const normalizedConfigPath = path.resolve(configPath);
  const configDir = path.dirname(normalizedConfigPath);
  const statePath = path.resolve(configDir, config.state_path || DEFAULT_CONFIG.state_path);
  const outputDir = path.resolve(configDir, config.output_dir || DEFAULT_CONFIG.output_dir);
  return {
    configPath: normalizedConfigPath,
    configDir,
    statePath,
    outputDir
  };
}

function mergeState(existingState, patch) {
  return {
    ...(existingState || {}),
    ...(patch || {})
  };
}

function artifactPath(outputDir, fileName) {
  ensureDir(outputDir);
  return path.join(outputDir, fileName);
}

function writeArtifact(outputDir, fileName, value) {
  writeJson(artifactPath(outputDir, fileName), value);
}

function logInfo(log, message) {
  if (typeof log === 'function') {
    log(message);
  }
}

async function collectWindowsPosture(maxInstalledApps, log) {
  logInfo(log, 'Collecting Windows posture');
  const script = `
$ErrorActionPreference = 'SilentlyContinue'
$maxApps = ${Math.max(1, Math.min(MAX_INSTALLED_APPS, maxInstalledApps))}
$os = Get-CimInstance Win32_OperatingSystem
$cs = Get-CimInstance Win32_ComputerSystem
$product = Get-CimInstance Win32_ComputerSystemProduct
$bios = Get-CimInstance Win32_BIOS
$tz = Get-TimeZone
$currentVersion = Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion'
$paths = @(
  'HKLM:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',
  'HKLM:\\Software\\WOW6432Node\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*',
  'HKCU:\\Software\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\*'
)
$apps = foreach ($item in (Get-ItemProperty -Path $paths)) {
  if ([string]::IsNullOrWhiteSpace($item.DisplayName)) { continue }
  [pscustomobject]@{
    app_name = $item.DisplayName
    publisher = $item.Publisher
    package_id = if ([string]::IsNullOrWhiteSpace($item.PSChildName)) { $item.DisplayName } else { $item.PSChildName }
    app_os_type = 'WINDOWS'
    app_version = $item.DisplayVersion
    latest_available_version = $null
    is_system_app = ($item.SystemComponent -eq 1)
    install_source = 'WINDOWS_REGISTRY'
    status = 'ACTIVE'
  }
}
$deduped = $apps |
  Group-Object { ('{0}|{1}' -f $_.package_id, $_.app_name).ToLowerInvariant() } |
  ForEach-Object { $_.Group[0] } |
  Sort-Object package_id, app_name |
  Select-Object -First $maxApps
$manufacturer = [string]$cs.Manufacturer
$model = [string]$cs.Model
$combined = ('{0} {1}' -f $manufacturer, $model).ToLowerInvariant()
$virtual = $combined.Contains('vmware') -or $combined.Contains('virtualbox') -or $combined.Contains('virtual machine') -or $combined.Contains('hyper-v') -or $combined.Contains('kvm') -or $combined.Contains('qemu')
$displayVersion = [string]$currentVersion.DisplayVersion
$releaseId = [string]$currentVersion.ReleaseId
$currentBuild = [string]$currentVersion.CurrentBuild
$ubr = $currentVersion.UBR
$buildNumber = if ($null -ne $ubr -and -not [string]::IsNullOrWhiteSpace($currentBuild)) { '{0}.{1}' -f $currentBuild, $ubr } else { $currentBuild }
[pscustomobject]@{
  deviceType = switch ([int]$cs.PCSystemType) { 2 {'LAPTOP'} 4 {'SERVER'} 5 {'SERVER'} 7 {'SERVER'} default {'DESKTOP'} }
  osName = [string]$os.Caption
  osVersion = [string]$os.Version
  osCycle = if ([string]::IsNullOrWhiteSpace($displayVersion)) { $releaseId } else { $displayVersion }
  manufacturer = $manufacturer
  timeZone = [string]$tz.Id
  osBuildNumber = $buildNumber
  kernelVersion = [string]$os.Version
  runningOnEmulator = $virtual
  machineId = (Get-ItemProperty 'HKLM:\\SOFTWARE\\Microsoft\\Cryptography').MachineGuid
  serialNumber = [string]$bios.SerialNumber
  platformUuid = [string]$product.UUID
  hostName = [string]$env:COMPUTERNAME
  model = $model
  installedApps = @($deduped)
} | ConvertTo-Json -Depth 8 -Compress
`;
  const result = await runCommand('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script]);
  return JSON.parse(result.stdout || '{}');
}

async function collectMacosPosture(maxInstalledApps, log) {
  logInfo(log, 'Collecting macOS posture');
  const [productName, productVersion, buildVersion] = await Promise.all([
    runCommand('sw_vers', ['-productName']),
    runCommand('sw_vers', ['-productVersion']),
    runCommand('sw_vers', ['-buildVersion'])
  ]);

  const hardware = await runCommand('system_profiler', ['SPHardwareDataType', '-json'], {
    timeoutMs: 120000,
    allowFailure: true
  });
  const appReport = await runCommand('system_profiler', ['SPApplicationsDataType', '-json'], {
    timeoutMs: 180000,
    allowFailure: true
  });

  const hardwareJson = hardware?.stdout ? JSON.parse(hardware.stdout) : {};
  const hardwareItems = Array.isArray(hardwareJson.SPHardwareDataType) ? hardwareJson.SPHardwareDataType : [];
  const hw = hardwareItems[0] || {};
  const model = normalizeText(hw.machine_model, 255) || normalizeText(hw.machine_name, 255);
  const installedApps = [];
  const applicationEntries = appReport?.stdout ? JSON.parse(appReport.stdout).SPApplicationsDataType || [] : [];
  for (const item of applicationEntries.slice(0, Math.max(1, Math.min(MAX_INSTALLED_APPS, maxInstalledApps)))) {
    const appName = normalizeText(item._name, 255);
    if (!appName) {
      continue;
    }
    installedApps.push({
      app_name: appName,
      publisher: 'Apple/macOS',
      package_id: normalizeText(item.path, 255) || appName,
      app_os_type: 'MACOS',
      app_version: normalizeText(item.version, 128),
      latest_available_version: null,
      is_system_app: String(item.path || '').startsWith('/System/'),
      install_source: 'APPLICATIONS_FOLDER',
      status: 'ACTIVE'
    });
  }

  const modelText = `${model || ''}`.toLowerCase();
  const manufacturer = 'Apple';
  return {
    deviceType: modelText.includes('book') ? 'LAPTOP' : 'DESKTOP',
    osName: normalizeText(productName.stdout, 255),
    osVersion: normalizeText(productVersion.stdout, 64),
    osCycle: normalizeText(productVersion.stdout, 64),
    manufacturer,
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
    osBuildNumber: normalizeText(buildVersion.stdout, 64),
    kernelVersion: os.release(),
    runningOnEmulator: /vmware|virtual|parallels|qemu/i.test(`${hw.machine_name || ''} ${hw.machine_model || ''}`),
    machineId: normalizeText(hw.platform_UUID || hw.platform_uuid || '', 255),
    serialNumber: normalizeText(hw.serial_number || '', 255),
    platformUuid: normalizeText(hw.platform_UUID || '', 255),
    hostName: os.hostname(),
    model,
    installedApps
  };
}

function linuxChassisToDeviceType(chassisType, productName) {
  const numeric = Number(chassisType || 0);
  if ([8, 9, 10, 14].includes(numeric)) {
    return 'LAPTOP';
  }
  const lowered = String(productName || '').toLowerCase();
  if (/(book|laptop|notebook)/.test(lowered)) {
    return 'LAPTOP';
  }
  if (/server/.test(lowered)) {
    return 'SERVER';
  }
  return 'DESKTOP';
}

async function collectLinuxPackages(maxInstalledApps, log) {
  logInfo(log, 'Collecting Linux installed packages');
  const maxItems = Math.max(1, Math.min(MAX_INSTALLED_APPS, maxInstalledApps));
  const dpkg = await runCommand('dpkg-query', ['-W', '-f=${Package}\t${Version}\t${Maintainer}\n'], {
    timeoutMs: 120000,
    allowFailure: true
  });
  if (dpkg?.stdout) {
    return dpkg.stdout
      .split(/\r?\n/)
      .filter(Boolean)
      .slice(0, maxItems)
      .map(line => {
        const [pkg, version, maintainer] = line.split('\t');
        return {
          app_name: normalizeText(pkg, 255),
          publisher: normalizeText(maintainer, 255),
          package_id: normalizeText(pkg, 255),
          app_os_type: 'LINUX',
          app_version: normalizeText(version, 128),
          latest_available_version: null,
          is_system_app: true,
          install_source: 'DPKG',
          status: 'ACTIVE'
        };
      })
      .filter(item => item.app_name);
  }

  const rpm = await runCommand('rpm', ['-qa', '--queryformat', '%{NAME}\t%{VERSION}-%{RELEASE}\t%{VENDOR}\n'], {
    timeoutMs: 120000,
    allowFailure: true
  });
  if (rpm?.stdout) {
    return rpm.stdout
      .split(/\r?\n/)
      .filter(Boolean)
      .slice(0, maxItems)
      .map(line => {
        const [pkg, version, vendor] = line.split('\t');
        return {
          app_name: normalizeText(pkg, 255),
          publisher: normalizeText(vendor, 255),
          package_id: normalizeText(pkg, 255),
          app_os_type: 'LINUX',
          app_version: normalizeText(version, 128),
          latest_available_version: null,
          is_system_app: true,
          install_source: 'RPM',
          status: 'ACTIVE'
        };
      })
      .filter(item => item.app_name);
  }

  logInfo(log, 'No supported Linux package manager detected for installed-app collection');
  return [];
}

async function collectLinuxPosture(maxInstalledApps, log) {
  logInfo(log, 'Collecting Linux posture');
  const osRelease = parseOsRelease();
  const productName = readTextIfExists('/sys/devices/virtual/dmi/id/product_name');
  const sysVendor = readTextIfExists('/sys/devices/virtual/dmi/id/sys_vendor');
  const chassisType = readTextIfExists('/sys/devices/virtual/dmi/id/chassis_type');
  const machineId = readTextIfExists('/etc/machine-id') || readTextIfExists('/var/lib/dbus/machine-id');
  const productVersion = readTextIfExists('/sys/devices/virtual/dmi/id/product_version');
  const virtualizationHint = `${sysVendor || ''} ${productName || ''} ${productVersion || ''}`.toLowerCase();
  return {
    deviceType: linuxChassisToDeviceType(chassisType, productName),
    osName: normalizeText(osRelease.PRETTY_NAME || osRelease.NAME || os.type(), 255),
    osVersion: normalizeText(osRelease.VERSION_ID || os.release(), 64),
    osCycle: normalizeText(osRelease.VERSION_ID || os.release(), 64),
    manufacturer: normalizeText(sysVendor || 'Linux', 255),
    timeZone: Intl.DateTimeFormat().resolvedOptions().timeZone || null,
    osBuildNumber: normalizeText(os.release(), 64),
    kernelVersion: os.release(),
    runningOnEmulator: /vmware|virtualbox|kvm|qemu|microsoft corporation virtual|hyper-v/.test(virtualizationHint),
    machineId: normalizeText(machineId, 255),
    serialNumber: normalizeText(readTextIfExists('/sys/devices/virtual/dmi/id/product_serial'), 255),
    platformUuid: normalizeText(readTextIfExists('/sys/devices/virtual/dmi/id/product_uuid'), 255),
    hostName: os.hostname(),
    model: normalizeText(productName, 255),
    installedApps: await collectLinuxPackages(maxInstalledApps, log)
  };
}

async function collectPlatformPosture(maxInstalledApps, log) {
  switch (process.platform) {
    case 'win32':
      return collectWindowsPosture(maxInstalledApps, log);
    case 'darwin':
      return collectMacosPosture(maxInstalledApps, log);
    case 'linux':
      return collectLinuxPosture(maxInstalledApps, log);
    default:
      throw new Error(`Unsupported platform for desktop agent: ${process.platform}`);
  }
}

function osTypeForPlatform() {
  switch (process.platform) {
    case 'win32':
      return 'WINDOWS';
    case 'darwin':
      return 'MACOS';
    case 'linux':
      return 'LINUX';
    default:
      return os.type().toUpperCase();
  }
}

function computeFingerprint(source) {
  return sha256Hex(
    [
      normalizeText(source.hostName, 255),
      normalizeText(source.machineId, 255),
      normalizeText(source.serialNumber, 255),
      normalizeText(source.platformUuid, 255)
    ]
      .filter(Boolean)
      .join('|') || os.hostname()
  );
}

function scoreBandFor(score) {
  if (!Number.isFinite(score)) {
    return 'UNKNOWN';
  }
  if (score < 25) {
    return 'CRITICAL';
  }
  if (score < 50) {
    return 'HIGH_RISK';
  }
  if (score < 70) {
    return 'MEDIUM_RISK';
  }
  if (score < 90) {
    return 'LOW_RISK';
  }
  return 'TRUSTED';
}

function rootDetectedForCurrentProcess() {
  return process.platform === 'win32'
    ? false
    : (typeof process.getuid === 'function' && process.getuid() === 0);
}

function buildDeviceProfile(posture, {
  deviceExternalId = null,
  enrollmentNo = null,
  fingerprint = null
} = {}) {
  const installedApps = Array.isArray(posture?.installedApps) ? posture.installedApps : [];
  return {
    captured_at: utcIso(),
    host_name: normalizeText(posture?.hostName, 255) || os.hostname(),
    platform: process.platform,
    os_type: osTypeForPlatform(),
    device_type: normalizeText(posture?.deviceType, 32) || 'DESKTOP',
    os_name: normalizeText(posture?.osName, 255),
    os_version: normalizeText(posture?.osVersion, 64),
    os_cycle: normalizeText(posture?.osCycle, 64),
    os_build_number: normalizeText(posture?.osBuildNumber, 64),
    kernel_version: normalizeText(posture?.kernelVersion, 128),
    manufacturer: normalizeText(posture?.manufacturer, 255),
    model: normalizeText(posture?.model, 255),
    time_zone: normalizeText(posture?.timeZone, 128),
    running_on_emulator: Boolean(posture?.runningOnEmulator),
    root_detected: rootDetectedForCurrentProcess(),
    installed_app_count: installedApps.length,
    installed_apps: installedApps.map(app => ({
      app_name: normalizeText(app?.app_name, 255),
      publisher: normalizeText(app?.publisher, 255),
      package_id: normalizeText(app?.package_id, 255),
      app_version: normalizeText(app?.app_version, 128),
      install_source: normalizeText(app?.install_source, 64),
      status: normalizeText(app?.status, 64),
      is_system_app: Boolean(app?.is_system_app)
    })),
    sample_apps: installedApps.slice(0, 8).map(app => ({
      app_name: normalizeText(app?.app_name, 255),
      publisher: normalizeText(app?.publisher, 255),
      app_version: normalizeText(app?.app_version, 128),
      install_source: normalizeText(app?.install_source, 64)
    })),
    device_fingerprint: normalizeText(fingerprint, 255) || computeFingerprint(posture || {}),
    device_external_id: normalizeText(deviceExternalId, 255),
    enrollment_no: normalizeText(enrollmentNo, 255),
    agent_id: defaultAgentId(),
    agent_version: defaultAgentVersion()
  };
}

function readArtifactJson(outputDir, fileName) {
  const filePath = artifactPath(outputDir, fileName);
  return {
    filePath,
    value: readJson(filePath, null)
  };
}

function summarizePayloadArtifact(payload) {
  if (!payload || typeof payload !== 'object') {
    return null;
  }
  return {
    device_external_id: payload.device_external_id || null,
    payload_version: payload.payload_version || null,
    capture_time: payload.capture_time || payload.payload_json?.capture_time || null,
    payload_hash: payload.payload_hash || null,
    agent_id: payload.agent_id || null,
    agent_version: payload.agent_version || null,
    app_count: Array.isArray(payload.payload_json?.installed_apps) ? payload.payload_json.installed_apps.length : 0
  };
}

function summarizeClaimArtifact(claim) {
  if (!claim || typeof claim !== 'object') {
    return null;
  }
  return {
    enrollment_no: claim.enrollment_no || null,
    device_token_expires_at: claim.device_token_expires_at || null,
    claimed_at: claim.claimed_at || null
  };
}

function summarizeResultArtifact(result) {
  if (!result || typeof result !== 'object') {
    return null;
  }
  return {
    payload_id: result.payload_id ?? null,
    status: result.status || null,
    evaluation_run_id: result.evaluation_run_id ?? null,
    decision_response_id: result.decision_response_id ?? null,
    decision_action: result.decision_action || null,
    trust_score: result.trust_score ?? null,
    decision_reason: result.decision_reason || null,
    remediation_required: Boolean(result.remediation_required),
    remediation: Array.isArray(result.remediation)
      ? result.remediation.slice(0, 6).map(item => ({
        title: item.title || item.remediation_code || 'Remediation',
        remediation_type: item.remediation_type || null,
        enforce_mode: item.enforce_mode || null,
        remediation_status: item.remediation_status || null,
        description: item.description || null
      }))
      : [],
    schema_compatibility_status: result.schema_compatibility_status || null,
    validation_warnings: Array.isArray(result.validation_warnings) ? result.validation_warnings : []
  };
}

function summarizeDecisionArtifact(decision) {
  if (!decision || typeof decision !== 'object') {
    return null;
  }
  return {
    id: decision.id ?? null,
    decision_action: decision.decision_action || null,
    trust_score: decision.trust_score ?? null,
    delivery_status: decision.delivery_status || null,
    created_at: decision.created_at || null,
    acknowledged_at: decision.acknowledged_at || null
  };
}

function summarizeAckArtifact(ack) {
  if (!ack || typeof ack !== 'object') {
    return null;
  }
  return {
    delivery_status: ack.delivery_status || null,
    acknowledged_at: ack.acknowledged_at || null,
    response_id: ack.id ?? null
  };
}

function loadArtifactSummaries(outputDir) {
  const claim = readArtifactJson(outputDir, 'last-claim.json');
  const payload = readArtifactJson(outputDir, 'last-payload.json');
  const ingest = readArtifactJson(outputDir, 'last-ingest-response.json');
  const result = readArtifactJson(outputDir, 'last-result.json');
  const latestDecision = readArtifactJson(outputDir, 'last-latest-decision.json');
  const ack = readArtifactJson(outputDir, 'last-ack.json');

  return {
    files: {
      claim: claim.filePath,
      payload: payload.filePath,
      ingest: ingest.filePath,
      result: result.filePath,
      latestDecision: latestDecision.filePath,
      ack: ack.filePath
    },
    last_claim: summarizeClaimArtifact(claim.value),
    last_payload: summarizePayloadArtifact(payload.value),
    last_ingest: summarizeResultArtifact(ingest.value),
    last_result: summarizeResultArtifact(result.value),
    last_latest_decision: summarizeDecisionArtifact(latestDecision.value),
    last_ack: summarizeAckArtifact(ack.value)
  };
}

function buildComplianceSummary(config, state, artifacts) {
  const latestResult = artifacts.last_result || artifacts.last_ingest || null;
  const latestDecision = artifacts.last_latest_decision || null;
  const decisionAction = normalizeText(
    state.last_decision_action || latestResult?.decision_action || latestDecision?.decision_action,
    32
  );
  const trustScore = Number.isFinite(state.last_trust_score)
    ? state.last_trust_score
    : (Number.isFinite(latestResult?.trust_score) ? latestResult.trust_score : latestDecision?.trust_score ?? null);
  const syncStatus = normalizeText(state.last_result_status || state.last_ingest_status, 32) || 'NOT_STARTED';
  const remediation = Array.isArray(latestResult?.remediation) ? latestResult.remediation : [];
  const warnings = Array.isArray(latestResult?.validation_warnings) ? latestResult.validation_warnings : [];
  const schemaCompatibility = normalizeText(
    state.last_schema_compatibility_status || latestResult?.schema_compatibility_status,
    64
  );

  let attentionState = 'inactive';
  let headline = 'Ready for provisioning';
  let detail = 'Complete organization setup and activate device protection.';

  if (normalizeText(state.device_external_id, 255) || normalizeText(state.enrollment_no, 255)) {
    attentionState = 'monitoring';
    headline = 'Provisioned and awaiting sync';
    detail = 'Run a compliance sync to establish the first managed posture baseline.';
  }

  if (syncStatus === 'FAILED') {
    attentionState = 'critical';
    headline = 'Sync failed';
    detail = 'Review diagnostics and retry the compliance sync.';
  } else if (decisionAction === 'BLOCK') {
    attentionState = 'critical';
    headline = 'Access blocked';
    detail = 'Immediate remediation is required before normal access can be restored.';
  } else if (decisionAction === 'QUARANTINE') {
    attentionState = 'warning';
    headline = 'Device quarantined';
    detail = 'The device remains managed but is currently under restricted policy.';
  } else if (decisionAction === 'NOTIFY') {
    attentionState = 'warning';
    headline = 'Action recommended';
    detail = 'The device is active, but compliance findings still require attention.';
  } else if (decisionAction === 'ALLOW' && syncStatus === 'EVALUATED') {
    attentionState = 'healthy';
    headline = 'Protected and compliant';
    detail = 'The latest posture sync completed successfully with no blocking action.';
  } else if (syncStatus === 'EVALUATED') {
    attentionState = 'monitoring';
    headline = 'Protected and monitored';
    detail = 'The latest posture sync completed. Review the latest policy outcome below.';
  }

  return {
    connection_ready: Boolean(
      normalizeText(config.base_url, 1024)
      && normalizeText(config.tenant_id, 64)
      && normalizeText(config.tenant_key, 1024)
      && (normalizeText(config.setup_key, 512) || normalizeText(state.device_external_id, 255) || normalizeText(state.enrollment_no, 255))
    ),
    sync_status: syncStatus,
    attention_state: attentionState,
    headline,
    detail,
    decision_action: decisionAction,
    trust_score: Number.isFinite(trustScore) ? trustScore : null,
    trust_band: scoreBandFor(Number(trustScore)),
    remediation_required: remediation.length > 0 || Boolean(latestResult?.remediation_required),
    remediation_count: remediation.length,
    remediation,
    last_payload_id: state.last_payload_id ?? latestResult?.payload_id ?? null,
    last_decision_response_id: state.last_decision_response_id ?? latestResult?.decision_response_id ?? latestDecision?.id ?? null,
    last_sync_at: state.last_completed_at || null,
    schema_compatibility_status: schemaCompatibility,
    validation_warnings: warnings,
    decision_reason: latestResult?.decision_reason || null
  };
}

function buildActivityTimeline(state, compliance) {
  const events = [];
  if (state.claimed_at) {
    events.push({
      id: 'claimed',
      title: 'Device provisioned',
      detail: normalizeText(state.device_external_id, 255) || normalizeText(state.enrollment_no, 255) || 'Enrollment established',
      at: state.claimed_at,
      tone: 'healthy'
    });
  }
  if (state.last_run_started_at) {
    events.push({
      id: 'sync-started',
      title: 'Compliance sync started',
      detail: `Payload ${state.last_payload_id ?? '-'}`,
      at: state.last_run_started_at,
      tone: 'monitoring'
    });
  }
  if (state.last_completed_at) {
    events.push({
      id: 'sync-finished',
      title: 'Compliance sync completed',
      detail: compliance.sync_status || 'Completed',
      at: state.last_completed_at,
      tone: compliance.attention_state || 'monitoring'
    });
  }
  if (state.last_acknowledged_at) {
    events.push({
      id: 'decision-ack',
      title: 'Decision acknowledged',
      detail: `Response ${state.last_acknowledged_response_id ?? '-'}`,
      at: state.last_acknowledged_at,
      tone: 'healthy'
    });
  }

  return events
    .filter(event => event.at)
    .sort((left, right) => String(right.at).localeCompare(String(left.at)));
}

function buildSummary(config, state, { runtimeInfo = null, devicePreview = null } = {}) {
  const paths = resolveManagedPaths(config.__configPath, config);
  const artifacts = loadArtifactSummaries(paths.outputDir);
  const provisionedId = normalizeText(state.device_external_id, 255) || normalizeText(state.enrollment_no, 255);
  const device = {
    ...(state.device_profile || {}),
    ...(devicePreview || {})
  };
  const normalizedDevice = {
    ...device,
    device_external_id: normalizeText(device.device_external_id, 255) || provisionedId,
    enrollment_no: normalizeText(device.enrollment_no, 255) || normalizeText(state.enrollment_no, 255),
    device_fingerprint: normalizeText(device.device_fingerprint, 255) || normalizeText(state.device_fingerprint, 255),
    host_name: normalizeText(device.host_name, 255) || normalizeText(state.host_name, 255) || os.hostname(),
    agent_id: normalizeText(device.agent_id, 255) || defaultAgentId(),
    agent_version: normalizeText(device.agent_version, 255) || defaultAgentVersion(),
    token_expires_at: state.device_token_expires_at || null,
    claimed_at: state.claimed_at || null
  };
  const compliance = buildComplianceSummary(config, state, artifacts);

  return {
    config,
    state,
    paths,
    runtime: runtimeInfo,
    organization: {
      base_url: config.base_url,
      tenant_id: config.tenant_id || null,
      configured: Boolean(normalizeText(config.base_url, 1024) && normalizeText(config.tenant_id, 64) && normalizeText(config.tenant_key, 1024)),
      base_url_locked: Boolean(config.__lockedBaseUrl)
    },
    device: normalizedDevice,
    compliance,
    activity: buildActivityTimeline(state, compliance),
    support: {
      artifacts,
      state_path: paths.statePath,
      config_path: paths.configPath,
      output_dir: paths.outputDir
    }
  };
}

async function previewDevice(maxInstalledApps = 200, log = () => {}) {
  const posture = await collectPlatformPosture(maxInstalledApps, log);
  return buildDeviceProfile(posture);
}

function buildPosturePayload(config, state, posture) {
  const captureTime = utcIso();
  const agentId = defaultAgentId();
  const agentVersion = defaultAgentVersion();
  const deviceExternalId = normalizeText(state.device_external_id, 255) || normalizeText(state.enrollment_no, 255);
  if (!deviceExternalId) {
    throw new Error('No enrolled device_external_id found. Claim the setup key first.');
  }

  const payloadJson = {
    capture_time: captureTime,
    device_type: normalizeText(posture.deviceType, 32) || 'DESKTOP',
    os_type: osTypeForPlatform(),
    os_name: normalizeText(posture.osName, 255),
    os_version: normalizeText(posture.osVersion, 64),
    os_cycle: normalizeText(posture.osCycle, 64),
    manufacturer: normalizeText(posture.manufacturer, 255),
    time_zone: normalizeText(posture.timeZone, 128),
    api_level: null,
    os_build_number: normalizeText(posture.osBuildNumber, 64),
    kernel_version: normalizeText(posture.kernelVersion, 128) || os.release(),
    root_detected: rootDetectedForCurrentProcess(),
    running_on_emulator: Boolean(posture.runningOnEmulator),
    usb_debugging_status: false,
    installed_apps: Array.isArray(posture.installedApps) ? posture.installedApps.slice(0, MAX_INSTALLED_APPS) : []
  };

  const payloadHash = `sha256:${sha256Hex(JSON.stringify(payloadJson))}`;
  return {
    device_external_id: deviceExternalId,
    agent_id: agentId,
    payload_version: 'v1',
    capture_time: captureTime,
    agent_version: agentVersion,
    agent_capabilities: {
      cross_platform_desktop: true,
      setup_key_claim: true,
      posture_collection: true,
      decision_ack: true,
      tenant_key_full_workflow: true,
      platform: process.platform
    },
    payload_hash: payloadHash,
    payload_json: payloadJson
  };
}

async function claimEnrollment(config, state, outputDir, log) {
  const setupKey = normalizeText(config.setup_key, 512);
  if (!setupKey) {
    throw new Error('Setup key is required for first-time enrollment.');
  }

  logInfo(log, 'Claiming enrollment with setup key');
  const posture = await collectPlatformPosture(MAX_INSTALLED_APPS, log);
  const fingerprint = computeFingerprint(posture);
  const body = {
    setup_key: setupKey,
    agent_id: defaultAgentId(),
    device_fingerprint: fingerprint,
    device_label: os.hostname(),
    tenant_id: normalizeText(config.tenant_id, 64)?.toLowerCase() || ''
  };
  const response = await invokeJsonApi('POST', joinUrl(config.base_url, '/v1/agent/enrollment/claim/setup-key'), {
    headers: {
      'Content-Type': 'application/json'
    },
    body
  });
  writeArtifact(outputDir, 'last-claim.json', response);
  const deviceProfile = buildDeviceProfile(posture, {
    deviceExternalId: response.enrollment_no,
    enrollmentNo: response.enrollment_no,
    fingerprint
  });
  return mergeState(state, {
    enrollment_no: response.enrollment_no,
    device_external_id: response.enrollment_no,
    device_token: response.device_token || '',
    device_token_expires_at: response.device_token_expires_at || null,
    claimed_at: utcIso(),
    device_fingerprint: fingerprint,
    device_label: os.hostname(),
    host_name: os.hostname(),
    os_type: osTypeForPlatform(),
    device_profile: deviceProfile
  });
}

async function pollResult(config, resultStatusUrl, log) {
  const headers = buildHeaders(config);
  const start = Date.now();
  const intervalMs = 3000;
  const timeoutMs = 90000;
  let current = await invokeJsonApi('GET', joinUrl(config.base_url, resultStatusUrl), { headers });
  while (current && ['RECEIVED', 'QUEUED', 'VALIDATED'].includes(current.status || '')) {
    if (Date.now() - start >= timeoutMs) {
      return current;
    }
    logInfo(log, `Polling queued result for payload ${current.payload_id ?? ''}`);
    await sleep(intervalMs);
    current = await invokeJsonApi('GET', joinUrl(config.base_url, resultStatusUrl), { headers });
  }
  return current;
}

async function getLatestDecision(config, deviceExternalId) {
  const headers = buildHeaders(config);
  const encodedDeviceId = encodeURIComponent(deviceExternalId);
  return invokeJsonApi('GET', joinUrl(config.base_url, `/v1/agent/devices/${encodedDeviceId}/decision/latest`), { headers });
}

async function acknowledgeDecision(config, decisionResponseId) {
  const headers = buildHeaders(config);
  const body = {
    delivery_status: 'ACKNOWLEDGED',
    acknowledged_at: utcIso()
  };
  return invokeJsonApi('POST', joinUrl(config.base_url, `/v1/agent/decision-responses/${decisionResponseId}/ack`), {
    headers,
    body
  });
}

async function runAgent(config, { mode = 'live', skipAck = false, noPoll = false, log = () => {} } = {}) {
  const paths = resolveManagedPaths(config.__configPath, config);
  ensureDir(paths.outputDir);
  let state = readJson(paths.statePath, {});
  const hasEnrollment = normalizeText(state.device_external_id, 255) || normalizeText(state.enrollment_no, 255);

  if (!hasEnrollment) {
    state = await claimEnrollment(config, state, paths.outputDir, log);
    writeJson(paths.statePath, state);
    logInfo(log, `Enrollment claimed: ${state.device_external_id}`);
  }

  if (mode === 'claim-only') {
    writeJson(paths.statePath, state);
    return {
      state,
      artifacts: {
        outputDir: paths.outputDir
      }
    };
  }

  const posture = await collectPlatformPosture(MAX_INSTALLED_APPS, log);
  const payload = buildPosturePayload(config, state, posture);
  writeArtifact(paths.outputDir, 'last-payload.json', payload);
  state = mergeState(state, {
    device_profile: buildDeviceProfile(posture, {
      deviceExternalId: normalizeText(state.device_external_id, 255) || normalizeText(state.enrollment_no, 255),
      enrollmentNo: normalizeText(state.enrollment_no, 255),
      fingerprint: computeFingerprint(posture)
    }),
    last_posture_collected_at: utcIso()
  });

  if (mode === 'dry-run') {
    state = mergeState(state, {
      last_completed_at: utcIso(),
      last_result_status: 'DRY_RUN',
      last_decision_action: null,
      last_trust_score: null
    });
    writeJson(paths.statePath, state);
    return {
      state,
      artifacts: {
        outputDir: paths.outputDir
      }
    };
  }

  logInfo(log, `Submitting posture payload for ${payload.device_external_id}`);
  const headers = buildHeaders(config);
  const ingestResponse = await invokeJsonApi('POST', joinUrl(config.base_url, '/v1/agent/posture-payloads'), {
    headers,
    body: payload
  });
  writeArtifact(paths.outputDir, 'last-ingest-response.json', ingestResponse);
  state = mergeState(state, {
    last_payload_id: ingestResponse.payload_id ?? null,
    last_ingest_status: ingestResponse.status ?? null,
    last_result_status_url: ingestResponse.result_status_url ?? null,
    last_run_started_at: utcIso(),
    last_decision_response_id: ingestResponse.decision_response_id ?? null
  });
  writeJson(paths.statePath, state);

  let result = ingestResponse;
  if (!noPoll && ingestResponse.result_status_url && ['RECEIVED', 'QUEUED', 'VALIDATED'].includes(ingestResponse.status || '')) {
    result = await pollResult(config, ingestResponse.result_status_url, log);
    writeArtifact(paths.outputDir, 'last-result.json', result);
  }

  let decisionResponseId = result.decision_response_id ?? null;
  if (!decisionResponseId) {
    try {
      const latestDecision = await getLatestDecision(config, payload.device_external_id);
      writeArtifact(paths.outputDir, 'last-latest-decision.json', latestDecision);
      decisionResponseId = latestDecision.id ?? null;
    } catch (error) {
      logInfo(log, `Latest decision lookup skipped: ${error.message}`);
    }
  }

  if (!skipAck && decisionResponseId) {
    logInfo(log, `Acknowledging decision response ${decisionResponseId}`);
    const ack = await acknowledgeDecision(config, decisionResponseId);
    writeArtifact(paths.outputDir, 'last-ack.json', ack);
  state = mergeState(state, {
      last_acknowledged_response_id: decisionResponseId,
      last_acknowledged_at: ack.acknowledged_at || utcIso()
    });
  }

  state = mergeState(state, {
    last_result_status: result.status || null,
    last_decision_action: result.decision_action || null,
    last_trust_score: result.trust_score ?? null,
    last_decision_response_id: decisionResponseId,
    last_schema_compatibility_status: result.schema_compatibility_status || null,
    last_validation_warnings: Array.isArray(result.validation_warnings) ? result.validation_warnings : [],
    last_remediation_count: Array.isArray(result.remediation) ? result.remediation.length : 0,
    last_completed_at: utcIso()
  });
  writeJson(paths.statePath, state);
  return {
    state,
    result,
    artifacts: {
      outputDir: paths.outputDir
    }
  };
}

function runtimeInfo({ appPath, userData, isPackaged, assetRoot, buildProfile = null, security = null, preferences = null }) {
  return {
    appPath,
    userData,
    isPackaged,
    assetRoot,
    arch: process.arch,
    platform: process.platform,
    buildProfile,
    security,
    preferences
  };
}

module.exports = {
  DEFAULT_CONFIG,
  buildSummary,
  defaultAgentId,
  defaultAgentVersion,
  ensureConfigExists,
  normalizeConfig,
  sanitizeConfigForDisk,
  resolveManagedPaths,
  readJson,
  writeJson,
  mergeState,
  previewDevice,
  runAgent,
  serializeSummary(config, state, runtimeInfo = null) {
    return buildSummary(config, state, { runtimeInfo });
  },
  runtimeInfo
};
