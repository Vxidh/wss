const { app, Tray, Menu, nativeImage } = require('electron');
const WebSocket = require('ws');
const screenshot = require('screenshot-desktop');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const axios = require('axios'); // npm install axios
const winston = require('winston');


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

const NODE_ID = getOrCreateNodeId(); 
const SECRET_FILE = path.join(app.getPath('userData'), 'agent-secret.txt');
const TRAY_ICON = path.join(__dirname, 'icon.png'); 

let tray = null;
let ws = null;
let agentSecret = null;

function getOrCreateSecret() {
  if (fs.existsSync(SECRET_FILE)) {
    agentSecret = fs.readFileSync(SECRET_FILE, 'utf8').trim();
    try {
      fs.chmodSync(SECRET_FILE, 0o600);
    } catch (e) {
      logger.warn('Could not set secret file permissions:', e);
    }
  } else {
    agentSecret = crypto.randomBytes(32).toString('hex');
    fs.writeFileSync(SECRET_FILE, agentSecret, { encoding: 'utf8', mode: 0o600 });
  }
}

function rotateSecret() {
  const oldSecret = agentSecret;
  agentSecret = crypto.randomBytes(32).toString('hex');
  fs.writeFileSync(SECRET_FILE, agentSecret, { encoding: 'utf8', mode: 0o600 });
  logger.info('Agent secret rotated.');
  if (ws) {
    ws.close();
  }
}

function getServerUrl() {
  return `ws://localhost:4567/?nodeId=${encodeURIComponent(NODE_ID)}&authToken=${encodeURIComponent(agentSecret)}&role=agent`;
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
    logger.info('Connected to backend');
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
      logger.error('Error handling message:', e);
    }
  });

  ws.on('close', () => {
    setTimeout(connectWebSocket, 5000); // Reconnect after delay
  });

  ws.on('error', (err) => {
    logger.error('WebSocket error:', err);
  });
}

async function registerAgent() {
  try {
    const resp = await axios.post('http://localhost:8080/api/register-agent', {
      nodeId: NODE_ID,
      secret: agentSecret,
      registrationToken: 'super-secret-token-123'
    });
    if (resp.data.status === 'registered') {
      logger.info('Agent registered successfully.');
      return true;
    } else {
      logger.error('Agent registration failed: ' + JSON.stringify(resp.data));
      return false;
    }
  } catch (e) {
    logger.error('Agent registration error: ' + e.message);
    return false;
  }
}

// 2. Configure winston logger
const logger = winston.createLogger({
  level: 'info',
  format: winston.format.combine(
    winston.format.timestamp({ format: 'YYYY-MM-DD HH:mm:ss' }),
    winston.format.printf(({ timestamp, level, message }) => `${timestamp} [${level.toUpperCase()}] ${message}`)
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({ filename: 'agent.log', maxsize: 1048576, maxFiles: 3 })
  ]
});

app.whenReady().then(async () => {
  getOrCreateSecret();
  const registrationResult = await registerAgent();
  // Print nodeId and secret for backend registration
  logger.info(`Agent nodeId: ${NODE_ID}`);
  logger.info(`Agent secret: ${agentSecret}`);
  createTray();
  if (registrationResult === true) {
    connectWebSocket();
  } else {
    logger.error('Agent registration failed, not connecting WebSocket.');
  }
  // No window shown
});

app.on('window-all-closed', (e) => {
  e.preventDefault(); // Prevent app from quitting
});
