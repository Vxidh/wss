const { app, Tray, Menu, nativeImage } = require('electron');
const WebSocket = require('ws');
const screenshot = require('screenshot-desktop');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

// --- CONFIG ---
const NODE_ID_FILE = path.join(app.getPath('userData'), 'agent-nodeid.txt');

function getOrCreateNodeId() {
  if (fs.existsSync(NODE_ID_FILE)) {
    return fs.readFileSync(NODE_ID_FILE, 'utf8').trim();
  } else {
    const uuid = 'node-' + crypto.randomUUID();
    fs.writeFileSync(NODE_ID_FILE, uuid, { encoding: 'utf8', mode: 0o600 });
    return uuid;
  }
}

const NODE_ID = getOrCreateNodeId();

let tray = null;
let ws = null;
let connected = false;

function updateTrayMenu() {
  const statusLabel = connected ? 'Status: Connected' : 'Status: Disconnected';
  const contextMenu = Menu.buildFromTemplate([
    { label: statusLabel, enabled: false },
    { type: 'separator' },
    { label: 'Quit', click: () => app.quit() }
  ]);
  tray.setContextMenu(contextMenu);
}

function createTray() {
  tray = new Tray(nativeImage.createEmpty());
  tray.setToolTip('System Agent');
  updateTrayMenu();
}

function getServerUrl() {
  // No authToken/secret
  return `ws://localhost:8080/ws?nodeId=${encodeURIComponent(NODE_ID)}&role=agent`;
}

function connectWebSocket() {
  const SERVER_URL = getServerUrl();
  ws = new WebSocket(SERVER_URL);

  ws.on('open', () => {
    connected = true;
    updateTrayMenu();
    console.log('Connected to backend');
  });

  ws.on('close', () => {
    connected = false;
    updateTrayMenu();
    setTimeout(connectWebSocket, 5000); // Reconnect after delay
  });

  ws.on('error', (err) => {
    connected = false;
    updateTrayMenu();
    console.error('WebSocket error:', err);
  });

  ws.on('message', async (data) => {
    try {
      const msg = JSON.parse(data);
      if (msg.type === 'screenshot') {
        const img = await screenshot({ format: 'png' });
        ws.send(JSON.stringify({
          type: 'screenshot_result',
          requestId: msg.requestId,
          image: img.toString('base64')
        }));
      }
      // ...other handlers...
    } catch (e) {
      console.error('Error handling message:', e);
    }
  });
}

app.whenReady().then(() => {
  console.log(`Agent nodeId: ${NODE_ID}`);
  createTray();
  connectWebSocket();
  // No window shown, no dock/taskbar icon
  if (app.dock) app.dock.hide(); // macOS: hide dock icon
});

app.on('window-all-closed', (e) => {
  e.preventDefault(); // Prevent app from quitting
});

// No changes needed for packaging
