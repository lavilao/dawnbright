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
  <!-- Embedded and CDN fallback for MediaSource Extensions (MSE) H.264 Player on plain HTTP -->
  <script src="/js/jmuxer.min.js"></script>
  <script>
    if (typeof JMuxer === 'undefined') {
      const s = document.createElement('script');
      s.src = 'https://cdn.jsdelivr.net/npm/jmuxer@2.0.5/dist/jmuxer.min.js';
      document.head.appendChild(s);
    }
  </script>
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
      transition: transform 0.25s ease, opacity 0.25s ease;
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

    /* INSECURE HTTP TIP BANNER */
    #insecure-http-banner {
      display: none;
      background: rgba(0, 229, 255, 0.12);
      border-bottom: 1px solid var(--primary);
      padding: 6px 16px;
      font-size: 0.78rem;
      color: var(--text);
      justify-content: space-between;
      align-items: center;
      z-index: 99;
    }

    /* IMMERSIVE CLEAN MODE (Hide all UI clutter while streaming games) */
    body.clean-mode header {
      display: none !important;
    }

    body.clean-mode .stream-hud {
      display: none !important;
    }

    body.clean-mode .quick-dock {
      display: none !important;
    }

    body.clean-mode #insecure-http-banner {
      display: none !important;
    }

    #clean-mode-floating-btn {
      position: absolute;
      top: 12px;
      right: 16px;
      background: rgba(18, 24, 38, 0.75);
      backdrop-filter: blur(10px);
      border: 1px solid var(--surface-border);
      color: var(--text-muted);
      border-radius: 20px;
      padding: 6px 14px;
      font-size: 0.78rem;
      cursor: pointer;
      display: none;
      z-index: 1001;
      transition: all 0.2s;
      box-shadow: 0 4px 16px rgba(0,0,0,0.5);
    }

    #clean-mode-floating-btn:hover {
      background: var(--surface);
      color: var(--primary);
      border-color: var(--primary);
    }

    body.clean-mode #clean-mode-floating-btn {
      display: block;
      opacity: 0.35;
    }

    body.clean-mode #clean-mode-floating-btn:hover {
      opacity: 1;
    }

    /* MAIN CONTAINER */
    main {
      flex: 1;
      position: relative;
      display: flex;
      overflow: hidden;
      background: #000;
    }

    /* STREAM CANVAS/VIDEO CONTAINER - OPTIMIZED SCALING FOR LAPTOP REAL ESTATE */
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
      cursor: crosshair;
    }

    #stream-canvas,
    #stream-video {
      display: block;
      max-width: 100%;
      max-height: 100%;
      object-fit: contain;
      image-rendering: high-quality;
      box-shadow: 0 0 20px rgba(0,0,0,0.8);
      transition: width 0.15s, height 0.15s;
    }

    /* Scaling styles */
    #stream-container.scale-fill #stream-canvas,
    #stream-container.scale-fill #stream-video {
      width: 100% !important;
      height: 100% !important;
      max-width: none;
      max-height: none;
      object-fit: fill !important;
    }

    #stream-container.scale-fit #stream-canvas,
    #stream-container.scale-fit #stream-video {
      max-width: 100%;
      max-height: 100%;
      object-fit: contain !important;
    }

    #stream-container.scale-16-10 #stream-canvas,
    #stream-container.scale-16-10 #stream-video {
      aspect-ratio: 16 / 10;
      width: 100%;
      height: auto;
      max-height: 100%;
      object-fit: fill;
    }

    #stream-container.scale-16-9 #stream-canvas,
    #stream-container.scale-16-9 #stream-video {
      aspect-ratio: 16 / 9;
      width: 100%;
      height: auto;
      max-height: 100%;
      object-fit: fill;
    }

    #stream-container.scale-3-2 #stream-canvas,
    #stream-container.scale-3-2 #stream-video {
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
      z-index: 10;
    }

    /* PERFORMANCE HUD */
    .stream-hud {
      position: absolute;
      top: 14px;
      left: 14px;
      background: rgba(9, 13, 22, 0.85);
      backdrop-filter: blur(8px);
      border: 1px solid var(--surface-border);
      border-radius: 8px;
      padding: 6px 12px;
      font-size: 0.78rem;
      font-family: monospace;
      color: var(--text-muted);
      display: flex;
      gap: 12px;
      z-index: 20;
      pointer-events: none;
    }

    .stream-hud strong {
      color: var(--primary);
    }

    /* QUICK DOCK */
    .quick-dock {
      position: absolute;
      bottom: 20px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(18, 24, 38, 0.9);
      backdrop-filter: blur(12px);
      border: 1px solid var(--surface-border);
      padding: 6px 12px;
      border-radius: 30px;
      display: flex;
      gap: 8px;
      z-index: 30;
      box-shadow: 0 8px 32px rgba(0,0,0,0.5);
    }

    /* GAME LAUNCHER OVERLAY (Sunshine Style) */
    #launcher-panel {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(9, 13, 22, 0.95);
      backdrop-filter: blur(16px);
      z-index: 50;
      display: flex;
      flex-direction: column;
      padding: 28px 36px;
      overflow-y: auto;
      transition: opacity 0.25s, visibility 0.25s;
    }

    #launcher-panel.hidden {
      opacity: 0;
      visibility: hidden;
      pointer-events: none;
    }

    .launcher-title-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 24px;
    }

    .games-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
      gap: 20px;
      margin-top: 10px;
    }

    .game-card {
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 12px;
      padding: 16px;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      cursor: pointer;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
    }

    .game-card:hover {
      transform: translateY(-4px);
      border-color: var(--primary);
      box-shadow: 0 10px 25px rgba(0, 229, 255, 0.2);
    }

    .game-icon-wrapper {
      width: 72px;
      height: 72px;
      border-radius: 16px;
      overflow: hidden;
      margin-bottom: 12px;
      background: rgba(255,255,255,0.05);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 2.2rem;
    }

    .game-icon-wrapper img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .game-card-title {
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--text);
      margin-bottom: 4px;
      line-height: 1.2;
    }

    .game-card-tag {
      font-size: 0.72rem;
      color: var(--text-muted);
      background: rgba(255,255,255,0.05);
      padding: 2px 8px;
      border-radius: 10px;
    }

    /* CONTROLS GUIDE MODAL */
    #controls-modal {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background: var(--surface);
      border: 1px solid var(--surface-border);
      border-radius: 16px;
      padding: 24px;
      width: 90%;
      max-width: 540px;
      z-index: 80;
      display: none;
      box-shadow: 0 16px 48px rgba(0,0,0,0.8);
    }

    #controls-modal.active {
      display: block;
    }

    .modal-backdrop {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.6);
      z-index: 70;
      display: none;
    }

    .modal-backdrop.active {
      display: block;
    }

    .key-table {
      width: 100%;
      margin: 16px 0;
      border-collapse: collapse;
      font-size: 0.85rem;
    }

    .key-table td {
      padding: 8px;
      border-bottom: 1px solid rgba(255,255,255,0.06);
    }

    .key-badge {
      display: inline-block;
      background: rgba(255, 255, 255, 0.1);
      border: 1px solid rgba(255, 255, 255, 0.2);
      border-radius: 4px;
      padding: 2px 6px;
      font-family: monospace;
      font-size: 0.8rem;
    }
  </style>
</head>
<body>
  <!-- HEADER -->
  <header>
    <div class="brand">
      <span>☀️ Sunshine Cast</span>
      <span class="brand-badge">Remote Play</span>
    </div>

    <div class="header-center">
      <div class="stat-chip">
        <span class="status-dot" id="status-indicator"></span>
        <span id="connection-status">Connecting...</span>
      </div>
      <div class="stat-chip">
        <span>Active:</span>
        <strong id="active-app-label" style="color: var(--primary);">Desktop Mode</strong>
      </div>
    </div>

    <div class="header-actions">
      <!-- Clean Mode Toggle Button -->
      <button id="btn-toggle-clean" class="btn-icon" title="Hide All UI Menus & Clutter for Clean Gaming (Hotkeys: ` or F10)">
        <span>👁️</span> Hide UI
      </button>

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

      <button id="btn-launcher" class="btn-icon" title="Open Game Launcher (Hotkey: F2)">
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

  <!-- INSECURE HTTP TIP BANNER -->
  <div id="insecure-http-banner">
    <span>
      ⚡ <strong>MSE Hardware Player Active:</strong> Playing over plain HTTP. For lowest input lag WebCodecs (&lt;5ms), enable flag in Chrome: <code style="background: rgba(0,0,0,0.4); padding: 2px 6px; border-radius: 4px; user-select: all;">chrome://flags/#unsafely-treat-insecure-origin-as-secure</code> and add your phone IP.
    </span>
    <button onclick="document.getElementById('insecure-http-banner').style.display='none'" style="background: none; border: none; color: var(--text-muted); cursor: pointer; font-size: 1rem; padding: 0 4px;">✕</button>
  </div>

  <!-- MAIN STREAM AREA -->
  <main>
    <div id="stream-container" class="scale-fit">
      <!-- WebCodecs Canvas -->
      <canvas id="stream-canvas" style="display:none;"></canvas>
      <!-- MediaSource MSE Video element (works everywhere on plain HTTP on LAN) -->
      <video id="stream-video" autoplay playsinline muted style="display:none;"></video>
      <div id="touch-cursor"></div>

      <!-- Live Performance HUD -->
      <div class="stream-hud">
        <span id="hud-engine">Engine...</span>
        <span id="hud-fps">0 FPS</span>
        <span id="hud-bitrate">0.0 Mbps</span>
        <span id="hud-latency">0 ms RTT</span>
      </div>

      <!-- Quick Floating Control Dock -->
      <div class="quick-dock" id="quick-dock">
        <button id="dock-btn-clean" class="btn-icon">👁️ Clean Screen</button>
        <button id="dock-btn-launcher" class="btn-icon">🎮 Switch Game</button>
        <button id="dock-btn-audio" class="btn-icon">🔊 Audio On</button>
        <button id="dock-btn-lock" class="btn-icon">🖱️ Lock Mouse</button>
      </div>

      <!-- Floating Button in Clean Mode to Restore UI -->
      <button id="clean-mode-floating-btn" title="Show UI Controls (Hotkey: ` or F10)">
        👁️ Show Controls (<span class="key-badge" style="font-size:0.7rem;">`</span>)
      </button>
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
        <!-- Loaded dynamically from phone via /api/apps -->
      </div>
    </div>
  </main>

  <!-- CONTROLS GUIDE MODAL -->
  <div class="modal-backdrop" id="modal-backdrop"></div>
  <div id="controls-modal">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px;">
      <h3>🎮 Laptop Keyboard & Mouse Controls</h3>
      <button id="btn-close-modal" style="background:none; border:none; color:var(--text-muted); font-size: 1.2rem; cursor:pointer;">✕</button>
    </div>
    <p style="font-size: 0.85rem; color: var(--text-muted); margin-bottom: 12px;">
      Full desktop typing, mouse gestures, and controller bindings are sent in real time:
    </p>
    <table class="key-table">
      <tr>
        <td><span class="key-badge">` (Backtick)</span> or <span class="key-badge">F10</span></td>
        <td><strong>Toggle Clean Screen:</strong> Hides all menus and options for distraction-free gaming</td>
      </tr>
      <tr>
        <td><span class="key-badge">Left Mouse Click & Drag</span></td>
        <td>Direct On-Screen Touch / Tap & Drag anywhere on phone display</td>
      </tr>
      <tr>
        <td><span class="key-badge">Entire Keyboard (A-Z, 0-9, etc.)</span></td>
        <td>Types text into active fields / KDE Connect style remote keyboard</td>
      </tr>
      <tr>
        <td><span class="key-badge">W / A / S / D</span> or <span class="key-badge">Arrows</span></td>
        <td>D-Pad / Analog Direction Movement</td>
      </tr>
      <tr>
        <td><span class="key-badge">J / K / U / I</span></td>
        <td>Action Buttons (Cross, Circle, Square, Triangle / A, B, X, Y)</td>
      </tr>
      <tr>
        <td><span class="key-badge">Q / E</span></td>
        <td>Shoulder Buttons (L1 / R1)</td>
      </tr>
      <tr>
        <td><span class="key-badge">Enter / Space</span></td>
        <td>Start / Select / Jump</td>
      </tr>
      <tr>
        <td><span class="key-badge">Escape</span></td>
        <td>Android Back Button / In-Game Pause Menu</td>
      </tr>
      <tr>
        <td><span class="key-badge">F2</span></td>
        <td>Open / Close Game Launcher Library</td>
      </tr>
    </table>
    <div style="background: rgba(0, 229, 255, 0.08); border: 1px solid rgba(0, 229, 255, 0.2); border-radius: 8px; padding: 10px; font-size: 0.8rem; margin-bottom: 16px;">
      💡 <strong>Pro-Tip:</strong> Press <span class="key-badge">`</span> (Backtick) at any time to hide all buttons, headers, and HUDs while gaming!
    </div>
    <div style="text-align: right;">
      <button id="btn-modal-ok" class="btn-icon" style="background: var(--primary); color: #000; font-weight: 700;">Got It!</button>
    </div>
  </div>

  <script>
    // --- STATE & CLIENT CONFIGURATION ---
    let ws = null;
    let videoDecoder = null;
    let jmuxer = null;
    let useWebCodecs = ('VideoDecoder' in window);
    let audioContext = null;
    let isConnected = false;
    let canvas = document.getElementById('stream-canvas');
    let ctx = canvas.getContext('2d');
    let videoElement = document.getElementById('stream-video');
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

    // --- IMMERSIVE CLEAN MODE (Hide all clutter) ---
    const btnToggleClean = document.getElementById('btn-toggle-clean');
    const dockBtnClean = document.getElementById('dock-btn-clean');
    const cleanModeBtn = document.getElementById('clean-mode-floating-btn');

    function toggleCleanMode(forceState) {
      const isClean = typeof forceState === 'boolean'
        ? forceState
        : !document.body.classList.contains('clean-mode');
      if (isClean) {
        document.body.classList.add('clean-mode');
      } else {
        document.body.classList.remove('clean-mode');
      }
    }

    if (btnToggleClean) btnToggleClean.addEventListener('click', () => toggleCleanMode(true));
    if (dockBtnClean) dockBtnClean.addEventListener('click', () => toggleCleanMode(true));
    if (cleanModeBtn) cleanModeBtn.addEventListener('click', () => toggleCleanMode(false));

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

    // --- DUAL-ENGINE VIDEO DECODER PIPELINE ---
    let hasReceivedKeyframe = false;
    let lastKeyframeRequestTime = 0;
    let currentCodecStr = 'avc1.420029';

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

    function initDisplayEngine(codecStr) {
      if ('VideoDecoder' in window) {
        useWebCodecs = true;
        canvas.style.display = 'block';
        videoElement.style.display = 'none';
        document.getElementById('hud-engine').innerText = 'WebCodecs (<5ms)';
        document.getElementById('hud-engine').style.color = 'var(--primary)';
        document.getElementById('insecure-http-banner').style.display = 'none';
        videoDecoder = initVideoDecoder(codecStr);
      } else {
        useWebCodecs = false;
        canvas.style.display = 'none';
        videoElement.style.display = 'block';
        document.getElementById('hud-engine').innerText = 'MSE Player (Hardware fMP4)';
        document.getElementById('hud-engine').style.color = 'var(--success)';
        document.getElementById('insecure-http-banner').style.display = 'flex';
        initJMuxer();
      }
    }

    function initVideoDecoder(codecStr) {
      if (!('VideoDecoder' in window)) {
        return null;
      }

      if (videoDecoder && videoDecoder.state !== 'closed') {
        try { videoDecoder.close(); } catch (e) {}
      }

      const targetCodec = codecStr || currentCodecStr || 'avc1.420029';
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
        console.log('WebCodecs VideoDecoder configured successfully with codec:', targetCodec);
        return decoder;
      } catch (e) {
        console.error('Failed to configure VideoDecoder with ' + targetCodec + ':', e);
        useWebCodecs = false;
        canvas.style.display = 'none';
        videoElement.style.display = 'block';
        initJMuxer();
        return null;
      }
    }

    function initJMuxer() {
      if (typeof JMuxer === 'undefined') {
        setTimeout(initJMuxer, 150);
        return;
      }

      if (jmuxer) {
        try { jmuxer.destroy(); } catch (e) {}
      }

      try {
        jmuxer = new JMuxer({
          node: 'stream-video',
          mode: 'video',
          flushingTime: 0,
          fps: 60,
          clearBuffer: true,
          onError: function(err) {
            console.warn('JMuxer player message:', err);
          }
        });
        videoElement.muted = true;
        videoElement.play().catch(() => {});
        console.log('JMuxer MSE player successfully started for plain HTTP stream');
      } catch (err) {
        console.error('Failed to init JMuxer:', err);
      }
    }

    function requestKeyframe() {
      if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'control', action: 'request_keyframe' }));
      }
    }

    // --- REAL-TIME LOW-LATENCY AUDIO PIPELINE (Web Audio API) ---
    let nextAudioTime = 0;

    function initAudio() {
      if (!audioContext) {
        audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 48000, latencyHint: 'interactive' });
      }
      if (audioContext.state === 'suspended') {
        audioContext.resume();
      }
      nextAudioTime = 0;
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

        const curTime = audioContext.currentTime;
        // Keep audio tightly synchronized to real-time (<40ms). If drift occurs, snap immediately to live edge.
        if (nextAudioTime < curTime || nextAudioTime > curTime + 0.06) {
          nextAudioTime = curTime + 0.01;
        }

        source.start(nextAudioTime);
        nextAudioTime += buffer.duration;
      } catch (e) {
        // audio buffer handling
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
        initDisplayEngine();
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

        if (useWebCodecs) {
          // --- ENGINE A: WebCodecs Pipeline ---
          if (isKey) {
            const detectedCodec = extractSpsInfo(naluData);
            if (detectedCodec && (!videoDecoder || videoDecoder.state !== 'configured' || currentCodecStr !== detectedCodec)) {
              console.log('Stream SPS detected, reconfiguring decoder with ' + detectedCodec);
              videoDecoder = initVideoDecoder(detectedCodec);
            }
            hasReceivedKeyframe = true;
          }

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
        } else {
          // --- ENGINE B: MediaSource MSE / JMuxer Pipeline ---
          if (!jmuxer && typeof JMuxer !== 'undefined') {
            initJMuxer();
          }
          if (jmuxer) {
            try {
              jmuxer.feed({
                video: naluData
              });
              frameCount++;

              if (videoElement.paused) {
                videoElement.play().catch(() => {});
              }
              // Prevent buffer accumulation to preserve ultra-low latency
              if (videoElement.buffered && videoElement.buffered.length > 0) {
                const liveEdge = videoElement.buffered.end(videoElement.buffered.length - 1);
                if (liveEdge - videoElement.currentTime > 0.3) {
                  videoElement.currentTime = liveEdge - 0.02;
                }
              }
            } catch (err) {
              console.warn('JMuxer feed error:', err);
            }
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

    function getActiveDisplay() {
      return (useWebCodecs && canvas.style.display !== 'none') ? canvas : videoElement;
    }

    // Calculate normalized coordinates taking into account scaling mode
    function getNormalizedCoords(e) {
      const el = getActiveDisplay();
      const rect = el.getBoundingClientRect();
      const x = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
      const y = Math.max(0, Math.min(1, (e.clientY - rect.top) / rect.height));
      return { x, y };
    }

    // Mouse Input
    let isMouseDown = false;
    streamContainer.addEventListener('mousedown', (e) => {
      if (e.target.closest('#quick-dock') || e.target.closest('.stream-hud') || e.target.id === 'clean-mode-floating-btn') return;
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

    streamContainer.addEventListener('mousemove', (e) => {
      if (e.target.closest('#quick-dock') || e.target.closest('.stream-hud') || e.target.id === 'clean-mode-floating-btn') return;
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
      const el = getActiveDisplay();
      el.requestPointerLock = el.requestPointerLock || el.mozRequestPointerLock;
      el.requestPointerLock();
    });

    // Laptop Keyboard Capture (Full Keyboard KDE Connect style + Game Controls)
    window.addEventListener('keydown', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;

      // Toggle Clean Mode with Backquote (`) or F10
      if (e.code === 'Backquote' || e.code === 'F10') {
        e.preventDefault();
        toggleCleanMode();
        return;
      }

      // F2 toggles Launcher
      if (e.code === 'F2') {
        e.preventDefault();
        toggleLauncher(launcherPanel.classList.contains('hidden'));
        return;
      }

      // If controls modal is active, Escape closes modal
      if (e.code === 'Escape' && controlsModal.classList.contains('active')) {
        controlsModal.classList.remove('active');
        modalBackdrop.classList.remove('active');
        return;
      }

      // If game launcher modal is active, Escape closes launcher
      if (e.code === 'Escape' && !launcherPanel.classList.contains('hidden')) {
        toggleLauncher(false);
        return;
      }

      // Send the keystroke to the phone (processed by KDE Connect Keyboard IME and Accessibility)
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
