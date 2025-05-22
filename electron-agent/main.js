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
    const uuid = crypto.randomUUID();
    fs.writeFileSync(NODE_ID_FILE, uuid, { encoding: 'utf8', mode: 0o600 });
    return uuid;
  }
}

const NODE_ID = getOrCreateNodeId(); // Optionally make this unique per machine
const SECRET_FILE = path.join(app.getPath('userData'), 'agent-secret.txt');
const TRAY_ICON = path.join(__dirname, 'icon.png'); // Use a small, neutral icon

let tray = null;
let ws = null;
let agentSecret = null;

function getOrCreateSecret() {
  if (fs.existsSync(SECRET_FILE)) {
    agentSecret = fs.readFileSync(SECRET_FILE, 'utf8').trim();
    // Ensure file permissions are strict (owner read/write only)
    try {
      fs.chmodSync(SECRET_FILE, 0o600);
    } catch (e) {
      console.warn('Could not set secret file permissions:', e);
    }
  } else {
    agentSecret = crypto.randomBytes(32).toString('hex');
    fs.writeFileSync(SECRET_FILE, agentSecret, { encoding: 'utf8', mode: 0o600 });
  }
}

// Rotate the agent secret securely
function rotateSecret() {
  const oldSecret = agentSecret;
  agentSecret = crypto.randomBytes(32).toString('hex');
  fs.writeFileSync(SECRET_FILE, agentSecret, { encoding: 'utf8', mode: 0o600 });
  console.log('Agent secret rotated.');
  // Reconnect with new secret
  if (ws) {
    ws.close();
  }
}

function getServerUrl() {
  return `ws://localhost:8080/?nodeId=${encodeURIComponent(NODE_ID)}&authToken=${encodeURIComponent(agentSecret)}&role=agent`;
}

function createTray() {
  const icon = nativeImage.createFromPath(TRAY_ICON);
  tray = new Tray(icon);
  const contextMenu = Menu.buildFromTemplate([
    { label: 'Quit', click: () => app.quit() }
  ]);
  tray.setToolTip('System Agent');
  tray.setContextMenu(contextMenu);
}

function connectWebSocket() {
  const SERVER_URL = getServerUrl();
  ws = new WebSocket(SERVER_URL);

  ws.on('open', () => {
    console.log('Connected to backend');
    // Optionally send a hello/ping
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
      // Secret rotation command from backend
      else if (msg.type === 'rotate_secret') {
        rotateSecret();
      }
      // Add more command handlers here (mouse, etc)
    } catch (e) {
      console.error('Error handling message:', e);
    }
  });

  ws.on('close', () => {
    setTimeout(connectWebSocket, 5000); // Reconnect after delay
  });

  ws.on('error', (err) => {
    console.error('WebSocket error:', err);
  });
}

app.whenReady().then(() => {
  getOrCreateSecret();
  // Print nodeId and secret for backend registration
  console.log(`Agent nodeId: ${NODE_ID}`);
  console.log(`Agent secret: ${agentSecret}`);
  createTray();
  connectWebSocket();
  // No window shown
});

app.on('window-all-closed', (e) => {
  e.preventDefault(); // Prevent app from quitting
});
