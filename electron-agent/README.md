# Electron Agent

Electron agent for remote screenshot and status reporting.

## Prerequisites

- [Node.js](https://nodejs.org/) (v18 or newer recommended)
- [npm](https://www.npmjs.com/) (comes with Node.js)
- For building executables:
  - Windows: No extra requirements
  - macOS: Xcode Command Line Tools
  - Linux: `libgtk-3-dev`, `libnss3`, `libxss1`, `libasound2`, `libatk-bridge2.0-0`, `libdrm2`, `libgbm1`, `libxshmfence1`, `libxrandr2`, `libxdamage1`, `libxcomposite1`, `libxcursor1`, `libxinerama1`, `libxkbcommon0`, `libxrender1`, `libxext6`, `libxfixes3`, `libxi6`, `libxtst6`, `libglib2.0-0`, `libpango-1.0-0`, `libpangocairo-1.0-0`, `libatk1.0-0`, `libcairo2`, `libcups2`, `libdbus-1-3`, `libgdk-pixbuf2.0-0`, `libgtk-3-0`

## Install dependencies

```sh
npm install
```

## Run in development mode

```sh
npm start
```

## Build executables

- **Windows:**
  ```sh
  npm run build:win
  ```
  Output: `dist/` folder with `.exe` installer or portable `.exe`

- **macOS:**
  ```sh
  npm run build:mac
  ```
  Output: `dist/` folder with `.dmg` or `.app`

- **Linux:**
  ```sh
  npm run build:linux
  ```
  Output: `dist/` folder with `.AppImage`, `.deb`, etc.

## Notes

- No custom icon is used; the default Electron tray icon will be shown.
- The agent runs in the background and does not display a window.
- To quit the agent, use the tray menu and select "Quit".
