package com.example.server

object WebClientHtml {

    fun getHtml(): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
  <title>Sunshine Cast - Remote Play</title>
  <style>
    :root {
      --bg: #090d16;
      --surface: #121826;
      --surface-border: #1f2a40;
      --primary: #00e5ff;
      --primary-glow: rgba(0, 229, 255, 0.35);
      --accent: #7c4dff;
      --success: #00e676;
      --warning: #ffab00;
      --text: #f0f4f8;
      --text-muted: #8a99ad;
      --danger: #ff5252;
    }

    * {
      box-sizing: border-box;
      margin: 0;
      padding: 0;
      user-select: none;
      -webkit-user-select: none;
    }

    body {
      background-color: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", sans-serif;
      overflow: hidden;
      width: 100vw;
      height: 100vh;
      display: flex;
      flex-direction: column;
    }

    /* TOP HEADER */
    header {
      height: 52px;
      background: rgba(18, 24, 38, 0.95);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--surface-border);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 16px;
      z-index: 100;
      flex-shrink: 0;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 10px;
      font-weight: 700;
      font-size: 1.05rem;
      letter-spacing: 0.5px;
      color: var(--text);
    }

    .brand-badge {
      background: linear-gradient(135deg, var(--primary), var(--accent));
      color: #000;
      padding: 2px 8px;
      border-radius: 4px;
      font-size: 0.72rem;
      font-weight: 800;
      text-transform: uppercase;
    }

    .header-center {
      display: flex;
      align-items: center;
      gap: 16px;
      font-size: 0.82rem;
      color: var(--text-muted);
    }

    .stat-chip {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      background: rgba(255, 255, 255, 0.05);
      padding: 4px 10px;
      border-radius: 20px;
      border: 1px solid rgba(255, 255, 255, 0.08);
    }

    .status-dot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--success);
      box-shadow: 0 0 8px var(--success);
      animation: pulse 2s infinite;
    }

    @keyframes pulse {
      0% { opacity: 0.6; }
      50% { opacity: 1; }
      100% { opacity: 0.6; }
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    button.btn-icon {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      color: var(--text);
      padding: 6px 12px;
      border-radius: 6px;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 0.82rem;
      font-weight: 500;
      transition: all 0.2s;
    }

    button.btn-icon:hover {
      background: rgba(0, 229, 255, 0.1);
      border-color: var(--primary);
      color: var(--primary);
    }

    select.select-scale {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      color: var(--text);
      padding: 6px 10px;
      border-radius: 6px;
      font-size: 0.82rem;
      cursor: pointer;
      outline: none;
    }

    select.select-scale:focus {
      border-color: var(--primary);
    }

    /* MAIN CONTAINER */
    main {
      flex: 1;
      position: relative;
      display: flex;
      overflow: hidden;
      background: #000;
    }

    /* STREAM CANVAS CONTAINER - OPTIMIZED SCALING FOR LAPTOP REAL ESTATE */
    #stream-container {
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      width: 100%;
      height: 100%;
      background: #000;
      overflow: hidden;
    }

    #stream-canvas {
      display: block;
      /* Default mode: Fill max screen with aspect preservation */
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
      cursor: crosshair;
      image-rendering: high-quality;
      box-shadow: 0 0 20px rgba(0,0,0,0.8);
      transition: width 0.15s, height 0.15s;
    }

    /* Scaling styles */
    #stream-container.scale-fill #stream-canvas {
      width: 100% !important;
      height: 100% !important;
      max-width: none;
      max-height: none;
      object-fit: fill !important;
    }

    #stream-container.scale-fit #stream-canvas {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain !important;
    }

    #stream-container.scale-16-10 #stream-canvas {
      aspect-ratio: 16 / 10;
      width: 100%;
      height: auto;
      max-height: 100%;
      object-fit: fill;
    }

    #stream-container.scale-16-9 #stream-canvas {
      aspect-ratio: 16 / 9;
      width: 100%;
      height: auto;
      max-height: 100%;
      object-fit: fill;
    }

    #stream-container.scale-3-2 #stream-canvas {
      aspect-ratio: 3 / 2;
      width: 100%;
      height: auto;
      max-height: 100%;
      object-fit: fill;
    }

    /* VIRTUAL POINTER & INPUT OVERLAY */
    #touch-cursor {
      position: absolute;
      width: 24px;
      height: 24px;
      border: 2px solid var(--primary);
      border-radius: 50%;
      background: rgba(0, 229, 255, 0.25);
      pointer-events: none;
      transform: translate(-50%, -50%);
      display: none;
      box-shadow: 0 0 10px var(--primary);
      z-index: 50;
      transition: opacity 0.2s;
    }

    /* FLOATING STATS HUD */
    .stream-hud {
      position: absolute;
      top: 14px;
      left: 14px;
      background: rgba(10, 14, 23, 0.85);
      border: 1px solid rgba(255, 255, 255, 0.1);
      backdrop-filter: blur(8px);
      padding: 6px 12px;
      border-radius: 8px;
      font-size: 0.75rem;
      font-family: "SF Mono", Consolas, monospace;
      color: var(--primary);
      display: flex;
      gap: 12px;
      pointer-events: none;
      z-index: 60;
    }

    /* GAME LAUNCHER OVERLAY */
    #launcher-panel {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(9, 13, 22, 0.94);
      backdrop-filter: blur(16px);
      z-index: 80;
      display: flex;
      flex-direction: column;
      padding: 30px 40px;
      overflow-y: auto;
      transition: opacity 0.25s, transform 0.25s;
    }

    #launcher-panel.hidden {
      opacity: 0;
      pointer-events: none;
      transform: scale(0.97);
    }

    .launcher-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 24px;
    }

    .launcher-title-row h2 {
      font-size: 1.6rem;
      font-weight: 700;
      background: linear-gradient(90deg, #fff, var(--primary));
      -webkit-background-clip: text;
      -webkit-text-fill-color: transparent;
    }

    .games-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 20px;
      margin-bottom: 30px;
    }

    .game-card {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      padding: 16px;
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 12px;
      cursor: pointer;
      transition: all 0.22s ease-out;
      text-align: center;
      position: relative;
      overflow: hidden;
    }

    .game-card:hover {
      transform: translateY(-4px);
      border-color: var(--primary);
      box-shadow: 0 8px 24px var(--primary-glow);
    }

    .game-icon-wrapper {
      width: 72px;
      height: 72px;
      border-radius: 16px;
      background: rgba(255, 255, 255, 0.04);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2.2rem;
      border: 1px solid rgba(255, 255, 255, 0.08);
      overflow: hidden;
    }

    .game-icon-wrapper img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .game-card-title {
      font-size: 0.95rem;
      font-weight: 600;
      color: var(--text);
      white-space: nowrap;
      overflow: hidden;
      text-overflow: ellipsis;
      max-width: 100%;
    }

    .game-card-tag {
      font-size: 0.7rem;
      padding: 2px 8px;
      border-radius: 10px;
      background: rgba(0, 229, 255, 0.12);
      color: var(--primary);
      font-weight: 600;
    }

    /* KEYBOARD CONTROLS HELPER MODAL */
    #controls-modal {
      position: fixed;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      width: 90%;
      max-width: 620px;
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 16px;
      padding: 24px;
      z-index: 120;
      box-shadow: 0 16px 40px rgba(0, 0, 0, 0.8);
      display: none;
    }

    #controls-modal.active {
      display: block;
    }

    .modal-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 18px;
    }

    .modal-header h3 {
      font-size: 1.2rem;
      color: var(--primary);
    }

    .key-grid {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 12px;
      margin-bottom: 20px;
    }

    .key-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 8px 12px;
      background: rgba(255, 255, 255, 0.03);
      border-radius: 8px;
      border: 1px solid rgba(255, 255, 255, 0.05);
    }

    .key-badge {
      background: #1f2a40;
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 4px;
      padding: 2px 8px;
      font-family: monospace;
      font-weight: 700;
      font-size: 0.85rem;
      color: var(--primary);
      box-shadow: 0 2px 0 rgba(0,0,0,0.5);
    }

    /* MODAL BACKDROP */
    #modal-backdrop {
      position: fixed;
      inset: 0;
      background: rgba(0, 0, 0, 0.7);
      backdrop-filter: blur(4px);
      z-index: 110;
      display: none;
    }

    #modal-backdrop.active {
      display: block;
    }

    /* FLOATING QUICK BAR */
    .quick-dock {
      position: absolute;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(18, 24, 38, 0.88);
      backdrop-filter: blur(12px);
      border: 1px solid var(--surface-border);
      border-radius: 30px;
      padding: 6px 14px;
      display: flex;
      align-items: center;
      gap: 12px;
      z-index: 70;
      box-shadow: 0 8px 24px rgba(0,0,0,0.6);
      transition: opacity 0.3s;
    }

    .quick-dock:hover {
      opacity: 1;
    }
  </style>
</head>
<body>

  <!-- HEADER -->
  <header>
    <div class="brand">
      <span style="font-size: 1.4rem;">☀️</span>
      <span>Sunshine Cast</span>
      <span class="brand-badge">Remote Play</span>
    </div>

    <div class="header-center">
      <div class="stat-chip">
        <span class="status-dot" id="status-indicator"></span>
        <span id="connection-status">Connected</span>
      </div>
      <div class="stat-chip">
        <span>Active:</span>
        <strong id="active-app-label" style="color: var(--primary);">Desktop Mode</strong>
      </div>
    </div>

    <div class="header-actions">
      <!-- Scaling Mode Dropdown for Maximum Laptop Screen Real Estate -->
      <select id="scaling-select" class="select-scale" title="Change Screen Scaling">
        <option value="scale-fill">Fill Screen (Max Real Estate)</option>
        <option value="scale-fit" selected>Fit Screen (Preserve Ratio)</option>
        <option value="scale-16-10">16:10 Laptop Match</option>
        <option value="scale-16-9">16:9 Wide</option>
        <option value="scale-3-2">3:2 Productivity</option>
      </select>

      <button id="btn-detect-res" class="btn-icon" title="Match Stream to Laptop Display Resolution">
        <span>🖥️</span> Auto-Match (<span id="laptop-res-label">...</span>)
      </button>

      <button id="btn-launcher" class="btn-icon" title="Open Game Launcher">
        <span>🎮</span> Games
      </button>

      <button id="btn-controls" class="btn-icon" title="Keyboard & Mouse Controls Guide">
        <span>⌨️</span> Keys
      </button>

      <button id="btn-fullscreen" class="btn-icon" title="Full Screen (F11)">
        <span>⛶</span> Fullscreen
      </button>
    </div>
  </header>

  <!-- MAIN STREAM AREA -->
  <main>
    <div id="stream-container" class="scale-fit">
      <canvas id="stream-canvas"></canvas>
      <div id="touch-cursor"></div>

      <!-- Live Performance HUD -->
      <div class="stream-hud">
        <span id="hud-fps">0 FPS</span>
        <span id="hud-bitrate">0.0 Mbps</span>
        <span id="hud-latency">0 ms RTT</span>
      </div>

      <!-- Quick Floating Control Dock -->
      <div class="quick-dock" id="quick-dock">
        <button id="dock-btn-launcher" class="btn-icon">🎮 Switch Game</button>
        <button id="dock-btn-audio" class="btn-icon">🔊 Audio On</button>
        <button id="dock-btn-lock" class="btn-icon">🖱️ Lock Mouse</button>
      </div>
    </div>

    <!-- GAME LAUNCHER OVERLAY (Sunshine Style) -->
    <div id="launcher-panel">
      <div class="launcher-title-row">
        <div>
          <h2>Game & Emulator Library</h2>
          <p style="color: var(--text-muted); font-size: 0.9rem; margin-top: 4px;">
            Select a game or emulator to launch remotely on your phone and stream immediately to Chrome.
          </p>
        </div>
        <button id="btn-close-launcher" class="btn-icon" style="padding: 8px 16px;">
          ✕ Close to Stream
        </button>
      </div>

      <div class="games-grid" id="games-grid">
        <!-- Dynamically populated from /api/apps -->
      </div>
    </div>
  </main>

  <!-- KEYBOARD CONTROLS MODAL -->
  <div id="modal-backdrop"></div>
  <div id="controls-modal">
    <div class="modal-header">
      <h3>🎮 Laptop Keyboard & Mouse Control Mapping</h3>
      <button id="btn-close-modal" class="btn-icon">✕</button>
    </div>
    <p style="color: var(--text-muted); font-size: 0.85rem; margin-bottom: 16px;">
      Your laptop keyboard and mouse are directly bridged to your phone with ultra-low latency. Standard game & PSP emulator controls:
    </p>
    <div class="key-grid">
      <div class="key-row">
        <span>D-Pad / Movement</span>
        <span class="key-badge">W / A / S / D or Arrows</span>
      </div>
      <div class="key-row">
        <span>Cross (A) / Accept</span>
        <span class="key-badge">K or Space</span>
      </div>
      <div class="key-row">
        <span>Circle (B) / Back</span>
        <span class="key-badge">L or Esc</span>
      </div>
      <div class="key-row">
        <span>Square (X) / Attack</span>
        <span class="key-badge">J</span>
      </div>
      <div class="key-row">
        <span>Triangle (Y) / Special</span>
        <span class="key-badge">I</span>
      </div>
      <div class="key-row">
        <span>L Bumper (Trigger)</span>
        <span class="key-badge">Q</span>
      </div>
      <div class="key-row">
        <span>R Bumper (Trigger)</span>
        <span class="key-badge">E</span>
      </div>
      <div class="key-row">
        <span>Start / Pause</span>
        <span class="key-badge">Enter</span>
      </div>
      <div class="key-row">
        <span>Select / Menu</span>
        <span class="key-badge">Shift</span>
      </div>
      <div class="key-row">
        <span>Mouse Click & Drag</span>
        <span class="key-badge">Touch Tap / Swipe</span>
      </div>
    </div>
    <div style="text-align: right;">
      <button id="btn-modal-ok" class="btn-icon" style="background: var(--primary); color: #000; font-weight: 700;">Got It!</button>
    </div>
  </div>

  <script>
    // --- STATE & CLIENT CONFIGURATION ---
    let ws = null;
    let videoDecoder = null;
    let audioContext = null;
    let isConnected = false;
    let canvas = document.getElementById('stream-canvas');
    let ctx = canvas.getContext('2d');
    let streamContainer = document.getElementById('stream-container');
    let touchCursor = document.getElementById('touch-cursor');
    let audioEnabled = true;

    // Performance telemetry
    let frameCount = 0;
    let lastFpsTime = performance.now();
    let currentFps = 0;
    let bytesReceived = 0;
    let lastBitrateTime = performance.now();
    let currentBitrateMbps = 0;
    let pingStartTime = 0;
    let currentRtt = 0;

    // --- SCALING MODE HANDLING (Maximize Laptop Screen Real Estate) ---
    const scalingSelect = document.getElementById('scaling-select');
    function applyScaling(mode) {
      streamContainer.className = '';
      streamContainer.classList.add(mode);
      localStorage.setItem('sunshine_scaling_mode', mode);
      console.log('Applied scaling mode:', mode);
    }

    scalingSelect.addEventListener('change', (e) => {
      applyScaling(e.target.value);
    });

    // Load saved scaling mode
    const savedScale = localStorage.getItem('sunshine_scaling_mode') || 'scale-fit';
    scalingSelect.value = savedScale;
    applyScaling(savedScale);

    const laptopResLabel = document.getElementById('laptop-res-label');
    if (laptopResLabel) {
      laptopResLabel.innerText = window.screen.width + '×' + window.screen.height;
    }
    const btnDetectRes = document.getElementById('btn-detect-res');
    if (btnDetectRes) {
      btnDetectRes.addEventListener('click', () => {
        sendInput({
          type: 'control',
          action: 'set_resolution',
          value: window.screen.width + 'x' + window.screen.height
        });
        btnDetectRes.style.borderColor = 'var(--success)';
        btnDetectRes.style.color = 'var(--success)';
        setTimeout(() => {
          btnDetectRes.style.borderColor = '';
          btnDetectRes.style.color = '';
        }, 1500);
      });
    }

    // Fullscreen toggle
    document.getElementById('btn-fullscreen').addEventListener('click', () => {
      if (!document.fullscreenElement) {
        document.documentElement.requestFullscreen().catch(err => console.log(err));
      } else {
        document.exitFullscreen();
      }
    });

    // --- GAME LAUNCHER OVERLAY ---
    const launcherPanel = document.getElementById('launcher-panel');
    const btnLauncher = document.getElementById('btn-launcher');
    const btnCloseLauncher = document.getElementById('btn-close-launcher');
    const dockBtnLauncher = document.getElementById('dock-btn-launcher');

    function toggleLauncher(show) {
      if (show) {
        launcherPanel.classList.remove('hidden');
        loadGames();
      } else {
        launcherPanel.classList.add('hidden');
      }
    }

    btnLauncher.addEventListener('click', () => toggleLauncher(true));
    dockBtnLauncher.addEventListener('click', () => toggleLauncher(true));
    btnCloseLauncher.addEventListener('click', () => toggleLauncher(false));

    async function loadGames() {
      const grid = document.getElementById('games-grid');
      try {
        const res = await fetch('/api/apps');
        const apps = await res.json();
        grid.innerHTML = '';

        if (!apps || apps.length === 0) {
          grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-muted); padding: 40px;">No installed games detected yet. Phone home screen is streaming live!</div>';
          return;
        }

        apps.forEach(app => {
          const card = document.createElement('div');
          card.className = 'game-card';
          const iconContent = app.iconBase64
            ? '<img src="data:image/png;base64,' + app.iconBase64 + '" alt="' + app.appName + '" />'
            : '🎮';
          const tagLabel = app.isEmulator ? 'Emulator' : 'Game';
          card.innerHTML =
            '<div class="game-icon-wrapper">' + iconContent + '</div>' +
            '<div class="game-card-title">' + app.appName + '</div>' +
            '<div class="game-card-tag">' + tagLabel + '</div>';
          card.addEventListener('click', () => launchGame(app.packageName, app.appName));
          grid.appendChild(card);
        });
      } catch (e) {
        console.warn('Could not fetch /api/apps, loading fallback emulator shortcuts', e);
        loadFallbackGames();
      }
    }

    function loadFallbackGames() {
      const grid = document.getElementById('games-grid');
      const commonGames = [
        { name: 'PPSSPP (PSP Emulator)', pkg: 'org.ppsspp.ppsspp', icon: '🎮', tag: 'PSP Emulator' },
        { name: 'PPSSPP Gold', pkg: 'org.ppsspp.ppssppgold', icon: '🏆', tag: 'PSP Emulator' },
        { name: 'RetroArch', pkg: 'com.retroarch', icon: '🕹️', tag: 'Multi-Emulator' },
        { name: 'AetherSX2 / NetherSX2', pkg: 'xyz.aethersx2.android', icon: '⚡', tag: 'PS2 Emulator' },
        { name: 'Dolphin Emulator', pkg: 'org.dolphinemu.dolphinemu', icon: '🐬', tag: 'GameCube / Wii' },
        { name: 'DuckStation', pkg: 'com.github.stenzek.duckstation', icon: '🦆', tag: 'PS1 Emulator' },
        { name: 'Minecraft', pkg: 'com.mojang.minecraftpe', icon: '⛏️', tag: 'Action / Sandbox' },
        { name: 'Home Screen / Any App', pkg: 'launcher', icon: '📱', tag: 'Desktop' }
      ];

      grid.innerHTML = '';
      commonGames.forEach(app => {
        const card = document.createElement('div');
        card.className = 'game-card';
        card.innerHTML =
          '<div class="game-icon-wrapper">' + app.icon + '</div>' +
          '<div class="game-card-title">' + app.name + '</div>' +
          '<div class="game-card-tag">' + app.tag + '</div>';
        card.addEventListener('click', () => launchGame(app.pkg, app.name));
        grid.appendChild(card);
      });
    }

    async function launchGame(pkg, name) {
      console.log('Launching remote game:', pkg, name);
      document.getElementById('active-app-label').innerText = name;
      toggleLauncher(false);

      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
          type: 'launch',
          packageName: pkg
        }));
      } else {
        try {
          await fetch('/api/launch', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ packageName: pkg })
          });
        } catch (e) {
          console.error('Launch request failed:', e);
        }
      }
    }

    // --- CONTROLS MODAL ---
    const controlsModal = document.getElementById('controls-modal');
    const modalBackdrop = document.getElementById('modal-backdrop');
    document.getElementById('btn-controls').addEventListener('click', () => {
      controlsModal.classList.add('active');
      modalBackdrop.classList.add('active');
    });
    document.getElementById('btn-close-modal').addEventListener('click', () => {
      controlsModal.classList.remove('active');
      modalBackdrop.classList.remove('active');
    });
    document.getElementById('btn-modal-ok').addEventListener('click', () => {
      controlsModal.classList.remove('active');
      modalBackdrop.classList.remove('active');
    });
    modalBackdrop.addEventListener('click', () => {
      controlsModal.classList.remove('active');
      modalBackdrop.classList.remove('active');
    });

    // --- WEBCODECS VIDEO DECODER PIPELINE ---
    let hasReceivedKeyframe = false;
    let lastKeyframeRequestTime = 0;
    let currentCodecStr = 'avc1.42E01F';

    function extractSpsInfo(data) {
      for (let i = 0; i < data.length - 4; i++) {
        let nalOffset = -1;
        if (data[i] === 0 && data[i + 1] === 0 && data[i + 2] === 1) {
          nalOffset = i + 3;
        } else if (data[i] === 0 && data[i + 1] === 0 && data[i + 2] === 0 && data[i + 3] === 1) {
          nalOffset = i + 4;
        }
        if (nalOffset !== -1 && nalOffset < data.length) {
          const nalType = data[nalOffset] & 0x1F;
          if (nalType === 7 && nalOffset + 3 < data.length) {
            const profileIdc = data[nalOffset + 1];
            const profileIop = data[nalOffset + 2];
            const levelIdc = data[nalOffset + 3];
            const pIdcHex = profileIdc.toString(16).padStart(2, '0');
            const pIopHex = profileIop.toString(16).padStart(2, '0');
            const lvlHex = levelIdc.toString(16).padStart(2, '0');
            return 'avc1.' + pIdcHex + pIopHex + lvlHex;
          }
        }
      }
      return null;
    }

    function initVideoDecoder(codecStr) {
      if (!('VideoDecoder' in window)) {
        console.warn('WebCodecs VideoDecoder API not supported in this browser');
        return null;
      }

      if (videoDecoder && videoDecoder.state !== 'closed') {
        try { videoDecoder.close(); } catch (e) {}
      }

      const targetCodec = codecStr || currentCodecStr || 'avc1.42E01F';
      try {
        const decoder = new VideoDecoder({
          output: (frame) => {
            if (canvas.width !== frame.displayWidth || canvas.height !== frame.displayHeight) {
              canvas.width = frame.displayWidth;
              canvas.height = frame.displayHeight;
            }
            ctx.drawImage(frame, 0, 0, canvas.width, canvas.height);
            frame.close();
            frameCount++;
          },
          error: (e) => {
            console.warn('VideoDecoder error encountered:', e);
            hasReceivedKeyframe = false;
            setTimeout(() => {
              videoDecoder = initVideoDecoder('avc1.640028');
              requestKeyframe();
            }, 100);
          }
        });

        decoder.configure({
          codec: targetCodec,
          optimizeForLatency: true,
          hardwareAcceleration: 'prefer-hardware'
        });

        currentCodecStr = targetCodec;
        console.log('VideoDecoder configured successfully with codec:', targetCodec);
        return decoder;
      } catch (e) {
        console.error('Failed to configure VideoDecoder with ' + targetCodec + ':', e);
        return null;
      }
    }

    function requestKeyframe() {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'control', action: 'request_keyframe' }));
      }
    }

    // --- AUDIO PIPELINE (Web Audio API) ---
    function initAudio() {
      if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 48000 });
      }
      if (audioContext.state === 'suspended') {
        audioContext.resume();
      }
    }

    function playAudioChunk(pcmData) {
      if (!audioEnabled || !audioContext) return;
      try {
        const int16Array = new Int16Array(pcmData.buffer, pcmData.byteOffset, pcmData.byteLength / 2);
        const floatArray = new Float32Array(int16Array.length / 2); // Left channel or interleaved
        for (let i = 0; i < floatArray.length; i++) {
          floatArray[i] = int16Array[i * 2] / 32768.0;
        }

        const buffer = audioContext.createBuffer(1, floatArray.length, 48000);
        buffer.copyToChannel(floatArray, 0);

        const source = audioContext.createBufferSource();
        source.buffer = buffer;
        source.connect(audioContext.destination);
        source.start();
      } catch (e) {
        // audio jitter buffer handling
      }
    }

    document.getElementById('dock-btn-audio').addEventListener('click', () => {
      initAudio();
      audioEnabled = !audioEnabled;
      document.getElementById('dock-btn-audio').innerText = audioEnabled ? '🔊 Audio On' : '🔇 Audio Off';
    });

    // --- WEBSOCKET CONNECTION & PACKET DISPATCH ---
    function connectWebSocket() {
      const loc = window.location;
      const wsUrl = (loc.protocol === 'https:' ? 'wss://' : 'ws://') + loc.host + '/stream';
      console.log('Connecting to Sunshine Cast stream:', wsUrl);

      ws = new WebSocket(wsUrl);
      ws.binaryType = 'arraybuffer';

      ws.onopen = () => {
        isConnected = true;
        document.getElementById('connection-status').innerText = 'Streaming';
        document.getElementById('status-indicator').style.background = 'var(--success)';
        initAudio();
        videoDecoder = initVideoDecoder();
        requestKeyframe();
        startPingTimer();
      };

      ws.onclose = () => {
        isConnected = false;
        document.getElementById('connection-status').innerText = 'Disconnected';
        document.getElementById('status-indicator').style.background = 'var(--danger)';
        setTimeout(connectWebSocket, 1500); // Auto reconnect
      };

      ws.onerror = (e) => {
        console.warn('WebSocket error:', e);
      };

      ws.onmessage = (event) => {
        if (typeof event.data === 'string') {
          handleTextMessage(event.data);
        } else {
          handleBinaryPacket(event.data);
        }
      };
    }

    function handleTextMessage(text) {
      try {
        const msg = JSON.parse(text);
        if (msg.type === 'pong') {
          currentRtt = Math.round(performance.now() - pingStartTime);
          document.getElementById('hud-latency').innerText = currentRtt + ' ms RTT';
        } else if (msg.type === 'status') {
          if (msg.activeApp) {
            document.getElementById('active-app-label').innerText = msg.activeApp;
          }
        }
      } catch (e) {
        console.error('Failed to parse text message:', e);
      }
    }

    function handleBinaryPacket(buffer) {
      bytesReceived += buffer.byteLength;
      const view = new DataView(buffer);
      // Packet Header: byte 0 = Packet Type (0x01 = Video H.264, 0x02 = Audio PCM)
      const packetType = view.getUint8(0);

      if (packetType === 0x01) {
        // Video Frame
        const isKey = view.getUint8(1) === 1;
        const ptsUs = Number(view.getBigInt64(2, false));
        const naluData = new Uint8Array(buffer, 10);

        if (isKey) {
          const detectedCodec = extractSpsInfo(naluData);
          if (detectedCodec && (!videoDecoder || videoDecoder.state !== 'configured' || currentCodecStr !== detectedCodec)) {
            console.log('Stream SPS detected, reconfiguring decoder with ' + detectedCodec);
            videoDecoder = initVideoDecoder(detectedCodec);
          }
          hasReceivedKeyframe = true;
        }

        // Drop delta frames until the first keyframe has been received and decoder is primed
        if (!hasReceivedKeyframe) {
          const now = performance.now();
          if (now - lastKeyframeRequestTime > 800) {
            requestKeyframe();
            lastKeyframeRequestTime = now;
          }
          return;
        }

        if (videoDecoder && videoDecoder.state === 'configured') {
          try {
            videoDecoder.decode(new EncodedVideoChunk({
              type: isKey ? 'key' : 'delta',
              timestamp: ptsUs,
              data: naluData
            }));
          } catch (err) {
            console.warn('Decoder chunk decode failed:', err);
            hasReceivedKeyframe = false;
            requestKeyframe();
          }
        }
      } else if (packetType === 0x02) {
        // Audio Frame
        const audioData = new Uint8Array(buffer, 10);
        playAudioChunk(audioData);
      }
    }

    function startPingTimer() {
      setInterval(() => {
        if (ws && ws.readyState === WebSocket.OPEN) {
          pingStartTime = performance.now();
          ws.send(JSON.stringify({ type: 'ping' }));
        }
      }, 2000);
    }

    // Telemetry updates
    setInterval(() => {
      const now = performance.now();
      const elapsedFps = (now - lastFpsTime) / 1000;
      currentFps = Math.round(frameCount / elapsedFps);
      frameCount = 0;
      lastFpsTime = now;
      document.getElementById('hud-fps').innerText = currentFps + ' FPS';

      const elapsedBitrate = (now - lastBitrateTime) / 1000;
      currentBitrateMbps = ((bytesReceived * 8) / (elapsedBitrate * 1_000_000)).toFixed(1);
      bytesReceived = 0;
      lastBitrateTime = now;
      document.getElementById('hud-bitrate').innerText = currentBitrateMbps + ' Mbps';
    }, 1000);

    // --- INPUT CONTROLS: LAPTOP KEYBOARD, MOUSE & TOUCH BRIDGING ---

    // Calculate normalized coordinates taking into account scaling mode
    function getNormalizedCoords(e) {
      const rect = canvas.getBoundingClientRect();
      const x = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      const y = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));
      return { x, y };
    }

    // Mouse Input
    let isMouseDown = false;
    canvas.addEventListener('mousedown', (e) => {
      initAudio();
      isMouseDown = true;
      const { x, y } = getNormalizedCoords(e);
      sendInput({
        type: 'mouse',
        action: 'down',
        button: e.button,
        x: x,
        y: y
      });
      showTouchVisualizer(e.clientX, e.clientY);
    });

    window.addEventListener('mouseup', (e) => {
      if (isMouseDown) {
        isMouseDown = false;
        const { x, y } = getNormalizedCoords(e);
        sendInput({
          type: 'mouse',
          action: 'up',
          button: e.button,
          x: x,
          y: y
        });
        hideTouchVisualizer();
      }
    });

    canvas.addEventListener('mousemove', (e) => {
      const { x, y } = getNormalizedCoords(e);
      if (isMouseDown) {
        sendInput({
          type: 'mouse',
          action: 'move',
          button: e.button,
          x: x,
          y: y,
          movementX: e.movementX,
          movementY: e.movementY
        });
        showTouchVisualizer(e.clientX, e.clientY);
      }
    });

    function showTouchVisualizer(clientX, clientY) {
      touchCursor.style.left = clientX + 'px';
      touchCursor.style.top = clientY + 'px';
      touchCursor.style.display = 'block';
    }

    function hideTouchVisualizer() {
      touchCursor.style.display = 'none';
    }

    // Pointer Lock for FPS / Game controls
    const dockBtnLock = document.getElementById('dock-btn-lock');
    dockBtnLock.addEventListener('click', () => {
      canvas.requestPointerLock = canvas.requestPointerLock || canvas.mozRequestPointerLock;
      canvas.requestPointerLock();
    });

    // Laptop Keyboard Capture (preventing default browser shortcuts like F11 or Esc if desired)
    window.addEventListener('keydown', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;

      // Handle Escape to toggle Launcher
      if (e.code === 'Escape' && !controlsModal.classList.contains('active')) {
        toggleLauncher(launcherPanel.classList.contains('hidden'));
        return;
      }

      sendInput({
        type: 'keyboard',
        code: e.code,
        key: e.key,
        isDown: true
      });

      // Prevent scrolling on space / arrow keys during gameplay
      if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code)) {
        e.preventDefault();
      }
    });

    window.addEventListener('keyup', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;

      sendInput({
        type: 'keyboard',
        code: e.code,
        key: e.key,
        isDown: false
      });
    });

    function sendInput(data) {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(data));
      }
    }

    // Initialize stream connection on start
    window.addEventListener('DOMContentLoaded', () => {
      connectWebSocket();
      loadGames();
    });
  </script>
</body>
</html>
        """.trimIndent()
    }
}
