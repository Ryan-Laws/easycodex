const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('easyCodexRelay', {
  getState: () => ipcRenderer.invoke('get-state'),
  installAndBuild: () => ipcRenderer.invoke('install-build'),
  startRelay: (input) => ipcRenderer.invoke('start-relay', input),
  stopRelay: () => ipcRenderer.invoke('stop-relay'),
  refreshApiKey: () => ipcRenderer.invoke('refresh-api-key'),
  saveConfig: (input) => ipcRenderer.invoke('save-config', input),
  previewPort: (input) => ipcRenderer.invoke('preview-port', input),
  browseWorkspace: () => ipcRenderer.invoke('browse-workspace'),
  browseCodex: () => ipcRenderer.invoke('browse-codex'),
  copyText: (text) => ipcRenderer.invoke('copy-text', text),
  openExternal: (url) => ipcRenderer.invoke('open-external', url),
  minimizeWindow: () => ipcRenderer.invoke('window-minimize'),
  hideWindow: () => ipcRenderer.invoke('window-hide'),
  closeWindow: () => ipcRenderer.invoke('window-close'),
  onState: (callback) => ipcRenderer.on('state', (_event, state) => callback(state)),
  onHealth: (callback) => ipcRenderer.on('health', (_event, health) => callback(health)),
  onLog: (callback) => ipcRenderer.on('log', (_event, line) => callback(line)),
});
