const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('agentApp', {
  load(configPath) {
    return ipcRenderer.invoke('agent:load', configPath);
  },
  saveConfig(config) {
    return ipcRenderer.invoke('agent:save-config', config);
  },
  saveRunPreferences(config) {
    return ipcRenderer.invoke('agent:save-config', config);
  },
  run(options) {
    return ipcRenderer.invoke('agent:run', options);
  },
  openOutput(configPath) {
    return ipcRenderer.invoke('agent:open-output', configPath);
  },
  openConfigDir(configPath) {
    return ipcRenderer.invoke('agent:open-config-dir', configPath);
  },
  runtimeInfo() {
    return ipcRenderer.invoke('agent:runtime-info');
  },
  previewDevice() {
    return ipcRenderer.invoke('agent:preview-device');
  },
  onRunLog(handler) {
    const listener = (_event, payload) => handler(payload);
    ipcRenderer.on('agent-run-log', listener);
    return () => ipcRenderer.removeListener('agent-run-log', listener);
  },
  onRunState(handler) {
    const listener = (_event, payload) => handler(payload);
    ipcRenderer.on('agent-run-state', listener);
    return () => ipcRenderer.removeListener('agent-run-state', listener);
  }
});
