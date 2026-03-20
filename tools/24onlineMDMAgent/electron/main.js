const fs = require('fs');
const path = require('path');
const { pathToFileURL } = require('url');
const { execFileSync } = require('child_process');
const { app, BrowserWindow, ipcMain, Menu, Tray, nativeImage, safeStorage, session, shell } = require('electron');
const runtime = require('./agent-runtime');

const APP_ID = 'com.e24online.mdmagent';
const APP_NAME = '24onlineMDMAgent';
const TEMPLATE_FILE = 'agent-config.template.json';
const BUILD_PROFILE_FILE = path.join('build', 'agent-build-config.json');
const SECRET_FILE = 'agent-secrets.json';
const RENDERER_ENTRY = path.join(__dirname, 'renderer', 'index.html');
const RENDERER_URL = pathToFileURL(RENDERER_ENTRY).toString();

let mainWindow = null;
let tray = null;
let currentRunPromise = null;
let isQuitRequested = false;

app.setName(APP_NAME);
app.setAppUserModelId(APP_ID);

function parseLauncherOptions(args) {
  const options = {
    smokeTest: false,
    configPath: '',
    startHidden: false
  };

  for (let index = 0; index < args.length; index += 1) {
    const arg = args[index];
    if (!arg) {
      continue;
    }
    if (arg === '--smoke-test') {
      options.smokeTest = true;
      continue;
    }
    if (arg === '--startup' || arg === '--background' || arg === '--hidden') {
      options.startHidden = true;
      continue;
    }
    if (arg === '--config' && args[index + 1]) {
      options.configPath = args[index + 1];
      index += 1;
      continue;
    }
    if (arg.startsWith('--config=')) {
      options.configPath = arg.slice('--config='.length);
    }
  }

  return options;
}

const launcherOptions = parseLauncherOptions(process.argv.slice(1));

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

function assetRoot() {
  return app.isPackaged ? app.getAppPath() : path.join(__dirname, '..');
}

function templatePath() {
  return path.join(assetRoot(), TEMPLATE_FILE);
}

function buildProfilePath() {
  return path.join(assetRoot(), BUILD_PROFILE_FILE);
}

function loadBuildProfile() {
  const raw = runtime.readJson(buildProfilePath(), {}) || {};
  const baseUrl = normalizeText(raw.base_url, 1024) || runtime.DEFAULT_CONFIG.base_url;
  return {
    appName: normalizeText(raw.app_name, 128) || APP_NAME,
    baseUrl,
    lockBaseUrl: raw.lock_base_url !== false,
    generatedAt: raw.generated_at || null
  };
}

function runtimeConfigOptions() {
  const buildProfile = loadBuildProfile();
  return {
    defaultBaseUrl: buildProfile.baseUrl,
    lockedBaseUrl: buildProfile.baseUrl
  };
}

function shouldStartHidden() {
  if (launcherOptions.startHidden) {
    return true;
  }
  try {
    const loginState = app.getLoginItemSettings();
    return Boolean(loginState.wasOpenedAtLogin || loginState.wasOpenedAsHidden);
  } catch {
    return false;
  }
}

function desktopPath() {
  return app.getPath('desktop');
}

function appExecutablePath() {
  return app.getPath('exe');
}

function macosAppBundlePath() {
  const executable = appExecutablePath();
  const marker = '.app';
  const markerIndex = executable.indexOf(marker);
  if (markerIndex < 0) {
    return executable;
  }
  return executable.slice(0, markerIndex + marker.length);
}

function desktopShortcutPath() {
  switch (process.platform) {
    case 'win32':
      return path.join(desktopPath(), `${APP_NAME}.lnk`);
    case 'linux':
      return path.join(desktopPath(), `${APP_NAME}.desktop`);
    case 'darwin':
      return path.join(desktopPath(), `${APP_NAME}.app`);
    default:
      return path.join(desktopPath(), APP_NAME);
  }
}

function linuxAutostartPath() {
  return path.join(app.getPath('home'), '.config', 'autostart', `${APP_NAME}.desktop`);
}

function shellQuote(value) {
  return `"${String(value || '').replace(/(["\\$`])/g, '\\$1')}"`;
}

function desktopEntryContent({ startup = false, desktopShortcut = false } = {}) {
  const executable = appExecutablePath();
  const iconFile = iconPath();
  const args = startup ? ' --startup' : '';
  const iconLine = iconFile ? `Icon=${iconFile}\n` : '';
  const startupClass = desktopShortcut ? `StartupWMClass=${APP_NAME}\n` : '';
  return [
    '[Desktop Entry]',
    'Type=Application',
    `Name=${APP_NAME}`,
    `Exec=${shellQuote(executable)}${args}`,
    iconLine.trimEnd(),
    'Terminal=false',
    'Categories=Security;',
    'X-GNOME-Autostart-enabled=true',
    startupClass.trimEnd()
  ]
    .filter(Boolean)
    .join('\n')
    .concat('\n');
}

function ensureParentDir(filePath) {
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
}

function removeFileIfExists(filePath) {
  if (fs.existsSync(filePath)) {
    fs.rmSync(filePath, { force: true });
  }
}

function readLaunchAtStartupEnabled() {
  try {
    if (process.platform === 'linux') {
      return fs.existsSync(linuxAutostartPath());
    }
    const loginItem = app.getLoginItemSettings();
    return Boolean(loginItem.openAtLogin);
  } catch {
    return false;
  }
}

function readDesktopShortcutEnabled() {
  try {
    return fs.existsSync(desktopShortcutPath());
  } catch {
    return false;
  }
}

function applyLaunchAtStartup(enabled) {
  if (process.platform === 'linux') {
    const targetPath = linuxAutostartPath();
    if (!enabled) {
      removeFileIfExists(targetPath);
      return;
    }
    ensureParentDir(targetPath);
    fs.writeFileSync(targetPath, desktopEntryContent({ startup: true }), 'utf8');
    fs.chmodSync(targetPath, 0o755);
    return;
  }

  const settings = {
    openAtLogin: enabled,
    openAsHidden: enabled,
    args: ['--startup']
  };
  if (process.platform === 'win32') {
    settings.path = appExecutablePath();
  }
  app.setLoginItemSettings(settings);
}

function applyWindowsDesktopShortcut(enabled) {
  const targetPath = desktopShortcutPath();
  if (!enabled) {
    removeFileIfExists(targetPath);
    return;
  }
  const executable = appExecutablePath().replace(/'/g, "''");
  const shortcut = targetPath.replace(/'/g, "''");
  const iconFile = (iconPath() || executable).replace(/'/g, "''");
  const script = `
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut('${shortcut}')
$shortcut.TargetPath = '${executable}'
$shortcut.WorkingDirectory = '${path.dirname(appExecutablePath()).replace(/'/g, "''")}'
$shortcut.IconLocation = '${iconFile}'
$shortcut.Save()
`;
  execFileSync('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', script], {
    windowsHide: true
  });
}

function applyLinuxDesktopShortcut(enabled) {
  const targetPath = desktopShortcutPath();
  if (!enabled) {
    removeFileIfExists(targetPath);
    return;
  }
  ensureParentDir(targetPath);
  fs.writeFileSync(targetPath, desktopEntryContent({ desktopShortcut: true }), 'utf8');
  fs.chmodSync(targetPath, 0o755);
}

function applyMacosDesktopShortcut(enabled) {
  const targetPath = desktopShortcutPath();
  if (!enabled) {
    removeFileIfExists(targetPath);
    return;
  }
  removeFileIfExists(targetPath);
  fs.symlinkSync(macosAppBundlePath(), targetPath, 'dir');
}

function applyDesktopShortcut(enabled) {
  switch (process.platform) {
    case 'win32':
      applyWindowsDesktopShortcut(enabled);
      return;
    case 'linux':
      applyLinuxDesktopShortcut(enabled);
      return;
    case 'darwin':
      applyMacosDesktopShortcut(enabled);
      return;
    default:
      return;
  }
}

function applyRunPreferences(config) {
  applyLaunchAtStartup(Boolean(config.launch_at_startup));
  applyDesktopShortcut(Boolean(config.desktop_shortcut));
}

function withRuntimePreferenceState(config) {
  return {
    ...config,
    launch_at_startup: readLaunchAtStartupEnabled(),
    desktop_shortcut: readDesktopShortcutEnabled()
  };
}

function userDataConfigPath() {
  return path.join(app.getPath('userData'), 'agent-config.json');
}

function resolveConfigPath(explicitPath) {
  return explicitPath && explicitPath.trim()
    ? path.resolve(explicitPath.trim())
    : userDataConfigPath();
}

function secretsPath(configPath) {
  return path.join(path.dirname(path.resolve(configPath)), SECRET_FILE);
}

function secretStorageStatus() {
  const available = safeStorage.isEncryptionAvailable();
  const backend = typeof safeStorage.getSelectedStorageBackend === 'function'
    ? safeStorage.getSelectedStorageBackend()
    : null;
  return {
    available,
    backend,
    persistent: available && !(process.platform === 'linux' && backend === 'basic_text')
  };
}

function readEncryptedSecrets(configPath) {
  const filePath = secretsPath(configPath);
  const raw = runtime.readJson(filePath, {}) || {};
  const status = secretStorageStatus();
  const secrets = {
    tenant_key: '',
    setup_key: ''
  };

  if (!raw.tenant_key && !raw.setup_key) {
    return secrets;
  }

  if (!status.available) {
    throw new Error('Secure secret storage is unavailable. Device credentials cannot be decrypted on this host.');
  }

  if (raw.tenant_key) {
    secrets.tenant_key = safeStorage.decryptString(Buffer.from(String(raw.tenant_key), 'base64'));
  }
  if (raw.setup_key) {
    secrets.setup_key = safeStorage.decryptString(Buffer.from(String(raw.setup_key), 'base64'));
  }
  return secrets;
}

function writeEncryptedSecrets(configPath, { tenant_key, setup_key }) {
  const filePath = secretsPath(configPath);
  const nextTenantKey = normalizeText(tenant_key, 1024) || '';
  const nextSetupKey = normalizeText(setup_key, 512) || '';

  if (!nextTenantKey && !nextSetupKey) {
    if (fs.existsSync(filePath)) {
      fs.rmSync(filePath, { force: true });
    }
    return;
  }

  const status = secretStorageStatus();
  if (!status.available) {
    throw new Error('Secure secret storage is unavailable. Cannot save tenant credentials on this device.');
  }
  if (!status.persistent) {
    throw new Error('Secure secret persistence requires a protected OS key store. Configure libsecret or KWallet before saving credentials.');
  }

  runtime.writeJson(filePath, {
    tenant_key: nextTenantKey ? safeStorage.encryptString(nextTenantKey).toString('base64') : '',
    setup_key: nextSetupKey ? safeStorage.encryptString(nextSetupKey).toString('base64') : '',
    stored_at: new Date().toISOString()
  });
}

function migrateLegacySecrets(configPath, rawConfig) {
  const legacyTenantKey = normalizeText(rawConfig?.tenant_key, 1024) || '';
  const legacySetupKey = normalizeText(rawConfig?.setup_key, 512) || '';
  if (!legacyTenantKey && !legacySetupKey) {
    return rawConfig;
  }

  const sanitized = {
    ...(rawConfig || {})
  };
  delete sanitized.tenant_key;
  delete sanitized.setup_key;

  try {
    writeEncryptedSecrets(configPath, {
      tenant_key: legacyTenantKey,
      setup_key: legacySetupKey
    });
    runtime.writeJson(
      configPath,
      runtime.sanitizeConfigForDisk(
        runtime.normalizeConfig(configPath, sanitized, runtimeConfigOptions()),
        runtimeConfigOptions()
      )
    );
  } catch {
    return rawConfig;
  }

  return sanitized;
}

function loadConfig(configPathArg) {
  const configPath = resolveConfigPath(configPathArg || launcherOptions.configPath);
  const options = runtimeConfigOptions();
  runtime.ensureConfigExists(configPath, templatePath(), options);
  let raw = runtime.readJson(configPath, runtime.DEFAULT_CONFIG) || runtime.DEFAULT_CONFIG;
  raw = migrateLegacySecrets(configPath, raw);

  let secrets = {
    tenant_key: '',
    setup_key: ''
  };
  try {
    secrets = readEncryptedSecrets(configPath);
  } catch {
    secrets = {
      tenant_key: normalizeText(raw.tenant_key, 1024) || '',
      setup_key: normalizeText(raw.setup_key, 512) || ''
    };
  }

  return runtime.normalizeConfig(configPath, {
    ...raw,
    ...secrets
  }, options);
}

function buildRuntimeSummary() {
  const buildProfile = loadBuildProfile();
  return runtime.runtimeInfo({
    appPath: app.getAppPath(),
    userData: app.getPath('userData'),
    isPackaged: app.isPackaged,
    assetRoot: assetRoot(),
    buildProfile: {
      appName: buildProfile.appName,
      baseUrlLocked: true,
      baseUrl: buildProfile.baseUrl,
      generatedAt: buildProfile.generatedAt
    },
    security: secretStorageStatus(),
    preferences: {
      trayResident: true,
      startupHidden: shouldStartHidden(),
      launchAtStartupEnabled: readLaunchAtStartupEnabled(),
      desktopShortcutEnabled: readDesktopShortcutEnabled()
    }
  });
}

function summaryFor(config) {
  return runtime.buildSummary(withRuntimePreferenceState(config), loadState(config), {
    runtimeInfo: buildRuntimeSummary()
  });
}

function loadState(config) {
  const paths = runtime.resolveManagedPaths(config.__configPath, config);
  return runtime.readJson(paths.statePath, {});
}

function sendToRenderer(channel, payload) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    mainWindow.webContents.send(channel, payload);
  }
}

function iconPath() {
  for (const fileName of ['icon-256.png', 'icon-128.png', 'icon.ico']) {
    const candidate = path.join(assetRoot(), 'build', 'icons', fileName);
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return undefined;
}

function createTrayImage() {
  const iconFile = iconPath();
  if (!iconFile) {
    return undefined;
  }
  let image = nativeImage.createFromPath(iconFile);
  if (image.isEmpty()) {
    return undefined;
  }
  if (process.platform === 'darwin') {
    image = image.resize({ width: 18, height: 18 });
    if (typeof image.setTemplateImage === 'function') {
      image.setTemplateImage(true);
    }
  }
  return image;
}

function showMainWindow() {
  if (!mainWindow || mainWindow.isDestroyed()) {
    createWindow(true);
    return;
  }
  if (process.platform === 'darwin' && app.dock) {
    app.dock.show();
  }
  mainWindow.show();
  if (mainWindow.isMinimized()) {
    mainWindow.restore();
  }
  mainWindow.focus();
  refreshTrayMenu();
}

function hideMainWindowToTray() {
  if (!mainWindow || mainWindow.isDestroyed()) {
    return;
  }
  mainWindow.hide();
  if (process.platform === 'darwin' && app.dock) {
    app.dock.hide();
  }
  refreshTrayMenu();
}

function quitApplication() {
  isQuitRequested = true;
  app.quit();
}

function refreshTrayMenu() {
  if (!tray) {
    return;
  }
  const windowVisible = Boolean(mainWindow && !mainWindow.isDestroyed() && mainWindow.isVisible());
  const template = [
    {
      label: windowVisible ? 'Hide Window' : 'View Window',
      click: () => {
        if (windowVisible) {
          hideMainWindowToTray();
          return;
        }
        showMainWindow();
      }
    },
    {
      label: 'Quit',
      click: () => {
        quitApplication();
      }
    }
  ];
  tray.setContextMenu(Menu.buildFromTemplate(template));
  tray.setToolTip(windowVisible ? `${APP_NAME} is running` : `${APP_NAME} is running in the tray`);
}

function createTray() {
  if (tray) {
    return;
  }
  const image = createTrayImage();
  tray = image ? new Tray(image) : new Tray(iconPath());
  tray.on('right-click', () => {
    refreshTrayMenu();
    tray.popUpContextMenu();
  });
  refreshTrayMenu();
}

function createWindow(showWindow = true) {
  if (mainWindow && !mainWindow.isDestroyed()) {
    if (showWindow) {
      showMainWindow();
    }
    return mainWindow;
  }

  mainWindow = new BrowserWindow({
    width: 1380,
    height: 940,
    minWidth: 1120,
    minHeight: 760,
    show: showWindow,
    backgroundColor: '#e8eef7',
    autoHideMenuBar: true,
    title: APP_NAME,
    icon: iconPath(),
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      webSecurity: true,
      spellcheck: false,
      devTools: !app.isPackaged
    }
  });

  mainWindow.removeMenu();
  mainWindow.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  mainWindow.webContents.on('will-navigate', event => {
    event.preventDefault();
  });
  mainWindow.on('close', event => {
    if (isQuitRequested || launcherOptions.smokeTest) {
      return;
    }
    event.preventDefault();
    hideMainWindowToTray();
  });
  mainWindow.on('show', refreshTrayMenu);
  mainWindow.on('hide', refreshTrayMenu);
  mainWindow.on('closed', () => {
    mainWindow = null;
    refreshTrayMenu();
  });
  mainWindow.loadFile(RENDERER_ENTRY);

  if (!showWindow && process.platform === 'darwin' && app.dock) {
    app.dock.hide();
  }

  refreshTrayMenu();
  return mainWindow;
}

function assertTrustedSender(event) {
  if (event?.senderFrame?.url !== RENDERER_URL) {
    throw new Error('Blocked renderer request from an unexpected origin.');
  }
}

function registerHandler(channel, handler) {
  ipcMain.handle(channel, async (event, ...args) => {
    assertTrustedSender(event);
    return handler(...args);
  });
}

async function startRun(payload) {
  if (currentRunPromise) {
    throw new Error('An agent run is already in progress.');
  }

  const config = loadConfig(payload?.configPath);
  const mode = payload?.mode || 'live';
  sendToRenderer('agent-run-state', {
    phase: 'started',
    mode
  });

  currentRunPromise = runtime.runAgent(config, {
    mode,
    skipAck: Boolean(payload?.skipAck),
    noPoll: Boolean(payload?.noPoll),
    log(message) {
      sendToRenderer('agent-run-log', {
        stream: 'stdout',
        chunk: `[agent] ${message}\n`
      });
    }
  });

  try {
    await currentRunPromise;
    sendToRenderer('agent-run-state', {
      phase: 'finished',
      mode,
      exitCode: 0,
      summary: summaryFor(loadConfig(config.__configPath))
    });
  } catch (error) {
    sendToRenderer('agent-run-log', {
      stream: 'stderr',
      chunk: `${error.stack || error.message}\n`
    });
    sendToRenderer('agent-run-state', {
      phase: 'finished',
      mode,
      exitCode: 1,
      summary: summaryFor(loadConfig(config.__configPath))
    });
  } finally {
    currentRunPromise = null;
  }
}

function saveConfig(payload) {
  const configPath = resolveConfigPath(payload?.__configPath || launcherOptions.configPath);
  const options = runtimeConfigOptions();
  const nextConfig = runtime.normalizeConfig(configPath, payload || runtime.DEFAULT_CONFIG, options);
  applyRunPreferences(nextConfig);
  const persistedConfig = withRuntimePreferenceState(nextConfig);
  runtime.writeJson(configPath, runtime.sanitizeConfigForDisk(persistedConfig, options));
  writeEncryptedSecrets(configPath, {
    tenant_key: persistedConfig.tenant_key,
    setup_key: persistedConfig.setup_key
  });
  return persistedConfig;
}

registerHandler('agent:load', async configPathArg => summaryFor(loadConfig(configPathArg)));
registerHandler('agent:save-config', async payload => summaryFor(saveConfig(payload)));
registerHandler('agent:open-output', async configPathArg => {
  const config = loadConfig(configPathArg);
  const paths = runtime.resolveManagedPaths(config.__configPath, config);
  return shell.openPath(paths.outputDir);
});
registerHandler('agent:open-config-dir', async configPathArg => {
  const config = loadConfig(configPathArg);
  const paths = runtime.resolveManagedPaths(config.__configPath, config);
  return shell.openPath(paths.configDir);
});
registerHandler('agent:runtime-info', async () => buildRuntimeSummary());
registerHandler('agent:preview-device', async () => runtime.previewDevice());
registerHandler('agent:run', async payload => {
  startRun(payload).catch(error => {
    sendToRenderer('agent-run-state', {
      phase: 'failed',
      mode: payload?.mode || 'live',
      message: error.message
    });
  });
  return { ok: true };
});

const singleInstance = app.requestSingleInstanceLock();
if (!singleInstance) {
  app.quit();
} else {
  app.on('second-instance', () => {
    showMainWindow();
  });

  app.whenReady().then(() => {
    session.defaultSession.setPermissionRequestHandler((_webContents, _permission, callback) => {
      callback(false);
    });

    session.defaultSession.setPermissionCheckHandler(() => false);

    if (launcherOptions.smokeTest) {
      createWindow(false);
      mainWindow.webContents.once('did-finish-load', () => {
        process.stdout.write('electron-smoke-ok\n');
        app.quit();
      });
      return;
    }

    const startHidden = shouldStartHidden();
    createTray();
    createWindow(!startHidden);
    if (startHidden) {
      hideMainWindowToTray();
    }
  });
}

app.on('before-quit', () => {
  isQuitRequested = true;
});

app.on('activate', () => {
  showMainWindow();
});

app.on('window-all-closed', () => {
});
