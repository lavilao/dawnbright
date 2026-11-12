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
      --bg: #0b0c0f;
      --surface: #15171c;
      --surface-2: #1d2026;
      --border: #2a2e36;
      --accent: #ffb454;
      --accent-dim: rgba(255, 180, 84, 0.14);
      --success: #3fb950;
      --warning: #d29922;
      --danger: #f85149;
      --text: #f4f5f7;
      --text-muted: #a0a5ad;
      --text-faint: #6e7480;
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
      height: 46px;
      background: rgba(11, 12, 15, 0.92);
      backdrop-filter: blur(12px);
      border-bottom: 1px solid var(--border);
      display: flex;
      align-items: center;
      justify-content: space-between;
      padding: 0 14px;
      z-index: 100;
      flex-shrink: 0;
      transition: transform 0.25s ease, opacity 0.25s ease;
    }

    .brand {
      display: flex;
      align-items: center;
      gap: 9px;
      font-weight: 600;
      font-size: 0.9rem;
      color: var(--text);
    }

    .brand-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--accent);
    }

    .header-center {
      display: flex;
      align-items: center;
      gap: 14px;
      font-size: 0.8rem;
      color: var(--text-muted);
    }

    #active-app-label {
      color: var(--text-faint);
    }

    .status-dot {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: var(--text-faint);
    }

    .header-actions {
      display: flex;
      align-items: center;
      gap: 6px;
    }

    button.btn-icon {
      background: transparent;
      border: 1px solid transparent;
      color: var(--text-muted);
      height: 30px;
      padding: 0 10px;
      border-radius: 8px;
      cursor: pointer;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 0.78rem;
      font-weight: 500;
      font-family: inherit;
      transition: color 0.15s, background 0.15s, border-color 0.15s;
      white-space: nowrap;
    }

    button.btn-icon svg {
      width: 14px;
      height: 14px;
      flex-shrink: 0;
    }

    button.btn-icon:hover {
      background: rgba(255, 255, 255, 0.06);
      border-color: var(--border);
      color: var(--text);
    }

    button.btn-icon.active {
      color: var(--accent);
    }

    select.select-scale {
      background: transparent;
      border: 1px solid var(--border);
      color: var(--text-muted);
      height: 30px;
      padding: 0 8px;
      border-radius: 8px;
      font-size: 0.78rem;
      font-family: inherit;
      cursor: pointer;
      outline: none;
    }

    select.select-scale:hover {
      color: var(--text);
    }

    select.select-scale:focus {
      border-color: #3a3f49;
      color: var(--text);
    }

    /* INFO / WARNING BANNERS */
    #insecure-http-banner {
      display: none;
      background: var(--surface);
      border-bottom: 1px solid var(--border);
      padding: 6px 14px;
      font-size: 0.75rem;
      color: var(--text-muted);
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      z-index: 99;
    }

    #insecure-http-banner code {
      background: rgba(255, 255, 255, 0.07);
      padding: 1px 6px;
      border-radius: 4px;
      user-select: all;
      color: var(--text);
    }

    /* INPUT UNAVAILABLE WARNING BANNER */
    #input-warn-banner {
      display: none;
      background: rgba(248, 81, 73, 0.08);
      border-bottom: 1px solid rgba(248, 81, 73, 0.35);
      color: var(--text-muted);
      padding: 6px 14px;
      font-size: 0.78rem;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      z-index: 98;
    }

    #input-warn-banner.active {
      display: flex;
    }

    #input-warn-banner button,
    #insecure-http-banner button {
      background: none;
      border: none;
      color: var(--text-faint);
      cursor: pointer;
      font-size: 1rem;
      padding: 0 4px;
      flex-shrink: 0;
    }

    #input-warn-banner button:hover,
    #insecure-http-banner button:hover {
      color: var(--text);
    }

    /* KEYMAP CALIBRATION PANEL */
    #keymap-panel {
      position: absolute;
      top: 0;
      right: 0;
      width: 300px;
      max-width: 85%;
      height: 100%;
      background: rgba(11, 12, 15, 0.97);
      border-left: 1px solid var(--border);
      z-index: 60;
      padding: 20px;
      display: none;
      flex-direction: column;
      overflow-y: auto;
    }

    #keymap-panel.active {
      display: flex;
    }

    .keymap-head {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 6px;
    }

    .keymap-sub {
      font-size: 0.72rem;
      color: var(--text-muted);
      margin-bottom: 14px;
    }

    .keymap-row {
      display: flex;
      align-items: center;
      gap: 8px;
      padding: 7px 0;
      border-bottom: 1px solid rgba(255, 255, 255, 0.06);
    }

    .keymap-label {
      flex: 1;
      font-size: 0.8rem;
    }

    .keymap-pos {
      font-size: 0.68rem;
      color: var(--text-muted);
      font-family: monospace;
    }

    #keymap-reset {
      margin-top: 14px;
    }

    #calibration-hint {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      z-index: 65;
      display: none;
      text-align: center;
      background: rgba(11, 12, 15, 0.92);
      border: 1px solid var(--border);
      padding: 16px 24px;
      border-radius: 12px;
      font-size: 0.85rem;
      color: var(--text-muted);
      pointer-events: none;
    }

    #calibration-hint strong {
      color: var(--text);
      font-size: 0.95rem;
    }

    #calibration-hint.active {
      display: block;
    }

    #map-dots {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      z-index: 15;
      pointer-events: none;
      display: none;
    }

    .map-dot {
      position: absolute;
      transform: translate(-50%, -50%);
      background: var(--accent-dim);
      border: 1px solid var(--accent);
      color: var(--accent);
      font-size: 0.62rem;
      padding: 2px 6px;
      border-radius: 10px;
      white-space: nowrap;
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
      background: rgba(21, 23, 28, 0.75);
      backdrop-filter: blur(10px);
      border: 1px solid var(--border);
      color: var(--text-muted);
      border-radius: 999px;
      padding: 6px 14px;
      font-size: 0.75rem;
      font-family: inherit;
      cursor: pointer;
      display: none;
      z-index: 1001;
      transition: all 0.2s;
    }

    #clean-mode-floating-btn:hover {
      background: var(--surface);
      color: var(--text);
      border-color: #3a3f49;
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
      touch-action: none;
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
      width: 22px;
      height: 22px;
      border: 2px solid var(--accent);
      border-radius: 50%;
      background: var(--accent-dim);
      pointer-events: none;
      transform: translate(-50%, -50%);
      display: none;
      z-index: 10;
    }

    /* PERFORMANCE HUD */
    .stream-hud {
      position: absolute;
      top: 10px;
      left: 10px;
      background: rgba(11, 12, 15, 0.72);
      backdrop-filter: blur(8px);
      border: 1px solid var(--border);
      border-radius: 8px;
      padding: 4px 10px;
      font-size: 0.7rem;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      color: var(--text-faint);
      display: flex;
      gap: 12px;
      z-index: 20;
      pointer-events: none;
    }

    .stream-hud strong {
      color: var(--text-muted);
      font-weight: 600;
    }

    /* QUICK DOCK */
    .quick-dock {
      position: absolute;
      bottom: 14px;
      left: 50%;
      transform: translateX(-50%);
      background: rgba(21, 23, 28, 0.88);
      backdrop-filter: blur(12px);
      border: 1px solid var(--border);
      padding: 4px;
      border-radius: 999px;
      display: flex;
      gap: 2px;
      z-index: 30;
    }

    .quick-dock .btn-icon {
      height: 28px;
      padding: 0 12px;
      border-radius: 999px;
    }

    /* GAME LAUNCHER OVERLAY */
    #launcher-panel {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(11, 12, 15, 0.96);
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
      margin-bottom: 20px;
    }

    .launcher-title-row h2 {
      font-size: 1.05rem;
      font-weight: 600;
    }

    .games-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
      gap: 14px;
      margin-top: 10px;
    }

    .game-card {
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 12px;
      padding: 14px;
      display: flex;
      flex-direction: column;
      align-items: center;
      text-align: center;
      cursor: pointer;
      transition: border-color 0.15s, transform 0.15s;
    }

    .game-card:hover {
      transform: translateY(-2px);
      border-color: #3a3f49;
    }

    .game-icon-wrapper {
      width: 64px;
      height: 64px;
      border-radius: 14px;
      overflow: hidden;
      margin-bottom: 10px;
      background: var(--surface-2);
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 1.6rem;
    }

    .game-icon-wrapper img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }

    .game-card-title {
      font-weight: 500;
      font-size: 0.85rem;
      color: var(--text);
      line-height: 1.25;
    }

    /* CONTROLS GUIDE MODAL */
    #controls-modal {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      background: var(--surface);
      border: 1px solid var(--border);
      border-radius: 14px;
      padding: 22px;
      width: 90%;
      max-width: 500px;
      z-index: 80;
      display: none;
      box-shadow: 0 16px 48px rgba(0,0,0,0.5);
    }

    #controls-modal.active {
      display: block;
    }

    #controls-modal h3 {
      font-size: 1rem;
      font-weight: 600;
    }

    .modal-backdrop {
      position: absolute;
      top: 0;
      left: 0;
      width: 100%;
      height: 100%;
      background: rgba(0,0,0,0.55);
      z-index: 70;
      display: none;
    }

    .modal-backdrop.active {
      display: block;
    }

    .key-table {
      width: 100%;
      margin: 14px 0 6px;
      border-collapse: collapse;
      font-size: 0.82rem;
    }

    .key-table td {
      padding: 7px 6px;
      border-bottom: 1px solid rgba(255,255,255,0.05);
      color: var(--text-muted);
    }

    .key-table td:last-child {
      text-align: right;
    }

    .key-table td strong {
      color: var(--text);
      font-weight: 500;
    }

    .key-badge {
      display: inline-block;
      background: var(--surface-2);
      border: 1px solid var(--border);
      border-radius: 5px;
      padding: 2px 7px;
      font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
      font-size: 0.75rem;
      color: var(--text);
      white-space: nowrap;
    }

    .modal-footer {
      display: flex;
      justify-content: flex-end;
      margin-top: 12px;
    }

    .btn-solid {
      background: var(--accent);
      color: #231503;
      border: none;
      border-radius: 8px;
      height: 32px;
      padding: 0 18px;
      font-size: 0.82rem;
      font-weight: 600;
      font-family: inherit;
      cursor: pointer;
    }

    .btn-solid:hover {
      background: #ffc270;
    }
  </style>
</head>
<body>
  <!-- HEADER -->
  <header>
    <div class="brand">
      <span class="brand-dot"></span>
      <span>Sunshine Cast</span>
    </div>

    <div class="header-center">
      <span class="status-dot" id="status-indicator"></span>
      <span id="connection-status">Connecting</span>
      <span id="active-app-label"></span>
    </div>

    <div class="header-actions">
      <button id="btn-toggle-clean" class="btn-icon" title="Hide all UI (` or F10)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
        <span>Hide UI</span>
      </button>

      <select id="scaling-select" class="select-scale" title="Screen scaling">
        <option value="scale-fill" title="Stretch to fill the window">Fill</option>
        <option value="scale-fit" selected title="Preserve aspect ratio">Fit</option>
        <option value="scale-16-10" title="Lock to 16:10">16:10</option>
        <option value="scale-16-9" title="Lock to 16:9">16:9</option>
        <option value="scale-3-2" title="Lock to 3:2">3:2</option>
      </select>

      <button id="btn-detect-res" class="btn-icon" title="Match stream resolution to this display">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="3" width="20" height="14" rx="2"/><path d="M8 21h8M12 17v4"/></svg>
        <span id="laptop-res-label">…</span>
      </button>

      <button id="btn-launcher" class="btn-icon" title="Game library (F2)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 12h4M8 10v4M15 11h.01M18 13h.01"/><path d="M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 12 2 12s0 2.4.686 3.258c.007.05.011.1.017.152A4 4 0 0 0 6.68 19h10.64a4 4 0 0 0 3.978-3.59c.006-.052.01-.101.017-.152C22 14.4 22 12 22 12s0-2.4-.686-3.258c-.007-.05-.011-.1-.017-.152A4 4 0 0 0 17.32 5z"/></svg>
        <span>Games</span>
      </button>

      <button id="btn-controls" class="btn-icon" title="Controls guide">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="2" y="6" width="20" height="12" rx="2"/><path d="M6 10h.01M10 10h.01M14 10h.01M18 10h.01M6 14h.01M18 14h.01M9 14h6"/></svg>
      </button>

      <button id="btn-fullscreen" class="btn-icon" title="Fullscreen (F11)">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3"/></svg>
      </button>
    </div>
  </header>

  <!-- MSE FALLBACK TIP BANNER -->
  <div id="insecure-http-banner">
    <span>
      Using the MSE decoder. For the lower-latency WebCodecs path, allow this origin at
      <code>chrome://flags/#unsafely-treat-insecure-origin-as-secure</code> and reload.
    </span>
    <button onclick="document.getElementById('insecure-http-banner').style.display='none'">×</button>
  </div>

  <!-- INPUT UNAVAILABLE WARNING BANNER -->
  <div id="input-warn-banner">
    <span>
      <strong>Remote input is off.</strong>
      Enable Sunshine Cast Controller in the phone's Accessibility settings - if it is already on, toggle it off and on once.
    </span>
    <button onclick="document.getElementById('input-warn-banner').classList.remove('active')">×</button>
  </div>

  <!-- MAIN STREAM AREA -->
  <main>
    <div id="stream-container" class="scale-fit">
      <!-- WebCodecs Canvas -->
      <canvas id="stream-canvas" style="display:none;"></canvas>
      <!-- MediaSource MSE Video element (works everywhere on plain HTTP on LAN) -->
      <video id="stream-video" autoplay playsinline muted style="display:none;"></video>
      <div id="touch-cursor"></div>
      <div id="map-dots"></div>

      <!-- Live Performance HUD -->
      <div class="stream-hud">
        <span id="hud-engine">…</span>
        <span id="hud-fps">0 fps</span>
        <span id="hud-bitrate">0.0 Mbps</span>
        <span id="hud-latency">0 ms</span>
      </div>

      <!-- Quick Floating Control Dock -->
      <div class="quick-dock" id="quick-dock">
        <button id="dock-btn-clean" class="btn-icon" title="Hide all UI (` or F10)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94"/><path d="M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19"/><path d="M14.12 14.12a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
          <span>Hide UI</span>
        </button>
        <button id="dock-btn-map" class="btn-icon" title="Map keys to on-screen buttons (F3)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="8"/><path d="M12 2v4M12 18v4M2 12h4M18 12h4"/></svg>
          <span>Map keys</span>
        </button>
        <button id="dock-btn-launcher" class="btn-icon" title="Game library (F2)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M6 12h4M8 10v4M15 11h.01M18 13h.01"/><path d="M17.32 5H6.68a4 4 0 0 0-3.978 3.59c-.006.052-.01.101-.017.152C2.604 9.416 2 12 2 12s0 2.4.686 3.258c.007.05.011.1.017.152A4 4 0 0 0 6.68 19h10.64a4 4 0 0 0 3.978-3.59c.006-.052.01-.101.017-.152C22 14.4 22 12 22 12s0-2.4-.686-3.258c-.007-.05-.011-.1-.017-.152A4 4 0 0 0 17.32 5z"/></svg>
          <span>Games</span>
        </button>
        <button id="dock-btn-audio" class="btn-icon active" title="Toggle audio">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M11 5L6 9H2v6h4l5 4V5z"/><path d="M15.5 8.5a5 5 0 0 1 0 7"/></svg>
          <span id="dock-audio-label">Audio</span>
        </button>
        <button id="dock-btn-lock" class="btn-icon" title="Lock mouse pointer">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><rect x="6" y="3" width="12" height="18" rx="6"/><path d="M12 7v5"/></svg>
          <span>Lock pointer</span>
        </button>
      </div>

      <!-- Floating Button in Clean Mode to Restore UI -->
      <button id="clean-mode-floating-btn" title="Show controls (` or F10)">
        Show UI · `
      </button>
    </div>

    <!-- GAME LAUNCHER OVERLAY -->
    <div id="launcher-panel">
      <div class="launcher-title-row">
        <h2>Games</h2>
        <button id="btn-close-launcher" class="btn-icon">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
          <span>Close</span>
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
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;">
      <h3>Controls</h3>
      <button id="btn-close-modal" style="background:none; border:none; color:var(--text-faint); font-size: 1.1rem; cursor:pointer; padding: 2px 4px;">×</button>
    </div>
    <table class="key-table">
      <tr><td><strong>Touch / drag</strong></td><td><span class="key-badge">Click / drag</span></td></tr>
      <tr><td><strong>Scroll</strong></td><td><span class="key-badge">Wheel</span></td></tr>
      <tr><td><strong>Typing</strong></td><td><span class="key-badge">Any key</span></td></tr>
      <tr><td><strong>D-pad</strong></td><td><span class="key-badge">WASD / Arrows</span></td></tr>
      <tr><td><strong>A · B · X · Y</strong></td><td><span class="key-badge">J · K · U · I</span></td></tr>
      <tr><td><strong>L1 / R1</strong></td><td><span class="key-badge">Q / E</span></td></tr>
      <tr><td><strong>Start / Select</strong></td><td><span class="key-badge">Enter / Space</span></td></tr>
      <tr><td><strong>Android back</strong></td><td><span class="key-badge">Esc</span></td></tr>
      <tr><td><strong>Game library</strong></td><td><span class="key-badge">F2</span></td></tr>
      <tr><td><strong>Map game buttons</strong></td><td><span class="key-badge">F3</span></td></tr>
      <tr><td><strong>Hide UI</strong></td><td><span class="key-badge">` / F10</span></td></tr>
      <tr><td><strong>Fullscreen</strong></td><td><span class="key-badge">F11</span></td></tr>
    </table>
    <div class="modal-footer">
      <button id="btn-modal-ok" class="btn-solid">Close</button>
    </div>
  </div>

  <!-- KEYMAP CALIBRATION PANEL -->
  <div id="keymap-panel">
    <div class="keymap-head">
      <h3>Map keys</h3>
      <button id="keymap-close" class="btn-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"><path d="M18 6L6 18M6 6l12 12"/></svg>
      </button>
    </div>
    <p class="keymap-sub">
      Click <strong>Set</strong>, then click that button on the stream. Keys press-and-hold at that spot. Esc cancels.
    </p>
    <div id="keymap-list"></div>
    <button id="keymap-reset" class="btn-icon">Reset</button>
  </div>

  <!-- CALIBRATION HINT -->
  <div id="calibration-hint">
    <strong id="calibration-label">Button</strong><br/>
    Click the button position on the stream
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
    let ctx = canvas.getContext('2d', { desynchronized: true, alpha: false });
    let videoElement = document.getElementById('stream-video');
    let streamContainer = document.getElementById('stream-container');
    let touchCursor = document.getElementById('touch-cursor');
    let audioEnabled = true;

    // Clean up any PIN stored by older versions (auth was removed).
    try { localStorage.removeItem('sc_pin'); } catch (e) {}

    let pingTimerStarted = false;

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
      refreshMapDots();
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

    function gameIconContent(app) {
      if (app.iconBase64) {
        return '<img src="data:image/png;base64,' + app.iconBase64 + '" alt="' + app.appName + '" />';
      }
      const initial = (app.appName || '?').trim().charAt(0).toUpperCase();
      return '<span style="font-size: 1.5rem; font-weight: 600; color: var(--text-muted);">' + initial + '</span>';
    }

    async function loadGames() {
      const grid = document.getElementById('games-grid');
      try {
        const res = await fetch('/api/apps');
        const apps = await res.json();
        grid.innerHTML = '';

        if (!apps || apps.length === 0) {
          grid.innerHTML = '<div style="grid-column: 1/-1; text-align: center; color: var(--text-faint); padding: 40px;">No apps found</div>';
          return;
        }

        apps.forEach(app => {
          const card = document.createElement('div');
          card.className = 'game-card';
          card.innerHTML =
            '<div class="game-icon-wrapper">' + gameIconContent(app) + '</div>' +
            '<div class="game-card-title">' + app.appName + '</div>';
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
        { name: 'PPSSPP', pkg: 'org.ppsspp.ppsspp' },
        { name: 'PPSSPP Gold', pkg: 'org.ppsspp.ppssppgold' },
        { name: 'RetroArch', pkg: 'com.retroarch' },
        { name: 'AetherSX2', pkg: 'xyz.aethersx2.android' },
        { name: 'Dolphin Emulator', pkg: 'org.dolphinemu.dolphinemu' },
        { name: 'DuckStation', pkg: 'com.github.stenzek.duckstation' },
        { name: 'Minecraft', pkg: 'com.mojang.minecraftpe' },
        { name: 'Home Screen', pkg: 'launcher' }
      ];

      grid.innerHTML = '';
      commonGames.forEach(app => {
        const card = document.createElement('div');
        card.className = 'game-card';
        card.innerHTML =
          '<div class="game-icon-wrapper">' + gameIconContent(app) + '</div>' +
          '<div class="game-card-title">' + app.name + '</div>';
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
        document.getElementById('hud-engine').innerText = 'WebCodecs';
        document.getElementById('insecure-http-banner').style.display = 'none';
        videoDecoder = initVideoDecoder(codecStr);
      } else {
        useWebCodecs = false;
        canvas.style.display = 'none';
        videoElement.style.display = 'block';
        document.getElementById('hud-engine').innerText = 'MSE';
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

    // --- MSE LIVE-EDGE CHASING (latency killer for the jmuxer path) ---
    // MSE happily buffers seconds of video; without chasing, "latency" is just
    // buffered time. Small drift -> play slightly faster than 1x; big drift
    // (network hiccup) -> snap to the live edge.
    function chaseLiveEdge() {
      if (useWebCodecs) return;
      try {
        if (videoElement.seeking || videoElement.readyState < 2) return;
        const b = videoElement.buffered;
        if (!b || b.length === 0) return;
        const liveEdge = b.end(b.length - 1);
        const behind = liveEdge - videoElement.currentTime;
        if (behind > 0.35) {
          if (videoElement.playbackRate !== 1) videoElement.playbackRate = 1;
          videoElement.currentTime = liveEdge - 0.05;
        } else if (behind > 0.06) {
          const targetRate = Math.min(1.12, 1 + behind * 0.5);
          if (Math.abs(videoElement.playbackRate - targetRate) > 0.005) {
            videoElement.playbackRate = targetRate;
          }
        } else if (videoElement.playbackRate !== 1) {
          videoElement.playbackRate = 1;
        }
      } catch (e) {}
    }
    setInterval(chaseLiveEdge, 250);

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
      const btn = document.getElementById('dock-btn-audio');
      btn.classList.toggle('active', audioEnabled);
      document.getElementById('dock-audio-label').innerText = audioEnabled ? 'Audio' : 'Muted';
    });

    // --- REMOTE INPUT: KEYMAP CALIBRATION, TOUCH, WHEEL, GAMEPAD ---

    function afterAuth() {
      document.getElementById('connection-status').innerText = 'Streaming';
      document.getElementById('status-indicator').style.background = 'var(--success)';
      initAudio();
      initDisplayEngine();
      requestKeyframe();
      startPingTimer();
      setTimeout(refreshMapDots, 250); // video is visible now; position the dots
    }

    function updateInputStatus(msg) {
      const banner = document.getElementById('input-warn-banner');
      if (!banner) return;
      if (msg && msg.accessibility) {
        banner.classList.remove('active');
      } else {
        banner.classList.add('active');
      }
    }

    // --- KEYMAP CALIBRATION (teach keys where on-screen buttons are) ---
    const KEY_CONTROLS = [
      { control: 'up', label: 'D-Pad Up', codes: ['KeyW', 'ArrowUp'] },
      { control: 'down', label: 'D-Pad Down', codes: ['KeyS', 'ArrowDown'] },
      { control: 'left', label: 'D-Pad Left', codes: ['KeyA', 'ArrowLeft'] },
      { control: 'right', label: 'D-Pad Right', codes: ['KeyD', 'ArrowRight'] },
      { control: 'a', label: 'Cross / A', codes: ['KeyJ'] },
      { control: 'b', label: 'Circle / B', codes: ['KeyK'] },
      { control: 'x', label: 'Square / X', codes: ['KeyU'] },
      { control: 'y', label: 'Triangle / Y', codes: ['KeyI'] },
      { control: 'l1', label: 'L Shoulder', codes: ['KeyQ'] },
      { control: 'r1', label: 'R Shoulder', codes: ['KeyE'] },
      { control: 'start', label: 'Start', codes: ['Enter'] },
      { control: 'select', label: 'Select', codes: ['Space'] }
    ];
    const CONTROL_TO_CODES = {};
    for (const kc of KEY_CONTROLS) {
      for (const c of kc.codes) CONTROL_TO_CODES[c] = kc.control;
    }

    let keymap = {};
    try { keymap = JSON.parse(localStorage.getItem('sc_keymap') || '{}'); } catch (e) { keymap = {}; }

    const keymapPanel = document.getElementById('keymap-panel');
    const calibHint = document.getElementById('calibration-hint');
    let calibratingControl = null;

    function getKeyTarget(code) {
      const control = CONTROL_TO_CODES[code];
      if (!control) return null;
      const m = keymap[control];
      if (m && typeof m.x === 'number' && typeof m.y === 'number') return m;
      return null;
    }

    function saveKeymap() {
      localStorage.setItem('sc_keymap', JSON.stringify(keymap));
      refreshMapDots();
    }

    function sendKeyboard(code, key, isDown) {
      if (isDown) heldKeys[code] = true; else delete heldKeys[code];
      const msg = { type: 'keyboard', code: code, key: key, isDown: isDown };
      const t = getKeyTarget(code);
      if (t) {
        msg.x = t.x;
        msg.y = t.y;
      }
      sendInput(msg);
    }

    const heldKeys = {};

    // Release everything if the window loses focus mid-press
    window.addEventListener('blur', () => {
      for (const code in heldKeys) {
        sendInput({ type: 'keyboard', code: code, key: '', isDown: false });
      }
      for (const k of Object.keys(heldKeys)) delete heldKeys[k];
      if (isMouseDown) {
        isMouseDown = false;
        sendInput({ type: 'mouse', action: 'up', x: 0, y: 0, button: 0 });
      }
    });

    function toggleKeymapEditor(show) {
      const want = typeof show === 'boolean' ? show : !keymapPanel.classList.contains('active');
      if (want) {
        buildKeymapList();
        keymapPanel.classList.add('active');
      } else {
        keymapPanel.classList.remove('active');
        calibratingControl = null;
        calibHint.classList.remove('active');
      }
    }

    function buildKeymapList() {
      const list = document.getElementById('keymap-list');
      list.innerHTML = '';
      for (const kc of KEY_CONTROLS) {
        const row = document.createElement('div');
        row.className = 'keymap-row';
        const mapped = keymap[kc.control];
        const posLabel = mapped
          ? ('(' + Math.round(mapped.x * 100) + '%, ' + Math.round(mapped.y * 100) + '%)')
          : 'default';
        const label = document.createElement('span');
        label.className = 'keymap-label';
        label.innerText = kc.label;
        const pos = document.createElement('span');
        pos.className = 'keymap-pos';
        pos.innerText = posLabel;
        const btn = document.createElement('button');
        btn.className = 'btn-icon';
        btn.innerText = 'Set';
        btn.addEventListener('click', (ev) => {
          ev.stopPropagation();
          calibratingControl = kc.control;
          document.getElementById('calibration-label').innerText = kc.label;
          calibHint.classList.add('active');
        });
        row.appendChild(label);
        row.appendChild(pos);
        row.appendChild(btn);
        list.appendChild(row);
      }
    }

    function captureCalibration(xNorm, yNorm) {
      if (!calibratingControl) return;
      keymap[calibratingControl] = { x: xNorm, y: yNorm };
      saveKeymap();
      calibratingControl = null;
      calibHint.classList.remove('active');
      buildKeymapList();
    }

    function resetKeymap() {
      keymap = {};
      saveKeymap();
      buildKeymapList();
    }

    document.getElementById('keymap-close').addEventListener('click', () => toggleKeymapEditor(false));
    document.getElementById('keymap-reset').addEventListener('click', resetKeymap);
    document.getElementById('dock-btn-map').addEventListener('click', () => toggleKeymapEditor());

    function refreshMapDots() {
      const layer = document.getElementById('map-dots');
      if (!layer) return;
      layer.innerHTML = '';
      const el = getActiveDisplay();
      if (!el) { layer.style.display = 'none'; return; }
      const cRect = streamContainer.getBoundingClientRect();
      const vRect = displayedContentRect();
      if (vRect.width <= 0 || vRect.height <= 0) {
        // Display not visible yet (e.g. before the stream starts)
        layer.style.display = 'none';
        return;
      }
      let count = 0;
      for (const kc of KEY_CONTROLS) {
        const m = keymap[kc.control];
        if (!m) continue;
        const dot = document.createElement('div');
        dot.className = 'map-dot';
        dot.style.left = (vRect.left - cRect.left + m.x * vRect.width) + 'px';
        dot.style.top = (vRect.top - cRect.top + m.y * vRect.height) + 'px';
        dot.innerText = kc.label;
        layer.appendChild(dot);
        count++;
      }
      layer.style.display = count > 0 ? 'block' : 'none';
    }

    // --- MULTI-TOUCH BRIDGING (touchscreen laptops & tablets) ---
    function touchNormalizedCoords(t) {
      return normIntoContent(t.clientX, t.clientY);
    }

    streamContainer.addEventListener('touchstart', (e) => {
      if (e.target.closest('#quick-dock') || e.target.closest('.stream-hud')) return;
      e.preventDefault();
      initAudio();
      for (let i = 0; i < e.changedTouches.length; i++) {
        const t = e.changedTouches.item(i);
        const c = touchNormalizedCoords(t);
        sendInput({ type: 'touch', action: 'down', x: c.x, y: c.y, pointerId: t.identifier });
        showTouchVisualizer(t.clientX, t.clientY);
      }
    }, { passive: false });

    streamContainer.addEventListener('touchmove', (e) => {
      e.preventDefault();
      for (let i = 0; i < e.changedTouches.length; i++) {
        const t = e.changedTouches.item(i);
        const c = touchNormalizedCoords(t);
        sendInput({ type: 'touch', action: 'move', x: c.x, y: c.y, pointerId: t.identifier });
        showTouchVisualizer(t.clientX, t.clientY);
      }
    }, { passive: false });

    streamContainer.addEventListener('touchend', (e) => {
      e.preventDefault();
      for (let i = 0; i < e.changedTouches.length; i++) {
        const t = e.changedTouches.item(i);
        sendInput({ type: 'touch', action: 'up', x: 0, y: 0, pointerId: t.identifier });
      }
      if (e.touches.length === 0) hideTouchVisualizer();
    }, { passive: false });

    streamContainer.addEventListener('touchcancel', (e) => {
      for (let i = 0; i < e.changedTouches.length; i++) {
        const t = e.changedTouches.item(i);
        sendInput({ type: 'touch', action: 'up', x: 0, y: 0, pointerId: t.identifier });
      }
    });

    // --- MOUSE WHEEL / TRACKPAD SCROLL ---
    let wheelAccum = 0;
    let lastWheelSent = 0;
    streamContainer.addEventListener('wheel', (e) => {
      e.preventDefault();
      wheelAccum += e.deltaY;
      const now = performance.now();
      if (now - lastWheelSent > 40) {
        const c = getNormalizedCoords(e);
        sendInput({ type: 'mouse', action: 'wheel', x: c.x, y: c.y, deltaY: wheelAccum });
        wheelAccum = 0;
        lastWheelSent = now;
      }
    }, { passive: false });

    // --- GAMEPAD BRIDGING (physical controllers connected to the laptop) ---
    const GAMEPAD_BUTTONS = {
      0: 'KeyJ', // A / Cross
      1: 'KeyK', // B / Circle
      2: 'KeyU', // X / Square
      3: 'KeyI', // Y / Triangle
      4: 'KeyQ', // L1
      5: 'KeyE', // R1
      8: 'Space', // Select
      9: 'Enter', // Start
      12: 'ArrowUp',
      13: 'ArrowDown',
      14: 'ArrowLeft',
      15: 'ArrowRight'
    };
    const gamepadActive = {};
    const gamepadAxes = {};

    window.addEventListener('gamepadconnected', (e) => {
      console.log('Gamepad connected:', e.gamepad.id);
    });

    function stickAxis(code, active) {
      const was = gamepadAxes[code];
      if (active && !was) sendKeyboard(code, '', true);
      if (!active && was) sendKeyboard(code, '', false);
      gamepadAxes[code] = active;
    }

    function pollGamepads() {
      if (navigator.getGamepads) {
        const pads = navigator.getGamepads();
        for (let i = 0; i < pads.length; i++) {
          const pad = pads[i];
          if (!pad) continue;
          for (const idx in GAMEPAD_BUTTONS) {
            const code = GAMEPAD_BUTTONS[idx];
            const pressed = !!(pad.buttons[idx] && pad.buttons[idx].pressed);
            const was = gamepadActive[code];
            if (pressed && !was) sendKeyboard(code, '', true);
            if (!pressed && was) sendKeyboard(code, '', false);
            gamepadActive[code] = pressed;
          }
          if (pad.axes && pad.axes.length >= 2) {
            const ax = pad.axes[0];
            const ay = pad.axes[1];
            stickAxis('ArrowLeft', ax < -0.5);
            stickAxis('ArrowRight', ax > 0.5);
            stickAxis('ArrowUp', ay < -0.5);
            stickAxis('ArrowDown', ay > 0.5);
          }
        }
      }
      requestAnimationFrame(pollGamepads);
    }
    requestAnimationFrame(pollGamepads);

    // Initial calibration overlay render (declarations above are all resolved here)
    refreshMapDots();

    // --- WEBSOCKET CONNECTION & PACKET DISPATCH ---
    function connectWebSocket() {
      const loc = window.location;
      const wsUrl = (loc.protocol === 'https:' ? 'wss://' : 'ws://') + loc.host + '/stream';
      console.log('Connecting to Sunshine Cast stream:', wsUrl);

      ws = new WebSocket(wsUrl);
      ws.binaryType = 'arraybuffer';

      ws.onopen = () => {
        isConnected = true;
        afterAuth();
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
          document.getElementById('hud-latency').innerText = currentRtt + ' ms';
        } else if (msg.type === 'input_status') {
          updateInputStatus(msg);
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
              // Live-edge chasing runs on its own interval (and on the feed
              // below) - see chaseLiveEdge().
              chaseLiveEdge();
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
      if (pingTimerStarted) return;
      pingTimerStarted = true;
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
      document.getElementById('hud-fps').innerText = currentFps + ' fps';

      const elapsedBitrate = (now - lastBitrateTime) / 1000;
      currentBitrateMbps = ((bytesReceived * 8) / (elapsedBitrate * 1_000_000)).toFixed(1);
      bytesReceived = 0;
      lastBitrateTime = now;
      document.getElementById('hud-bitrate').innerText = currentBitrateMbps + ' Mbps';
    }, 1000);

    // --- INPUT CONTROLS: KEYBOARD, MOUSE, TOUCH & POINTER LOCK ---

    function getActiveDisplay() {
      return (useWebCodecs && canvas.style.display !== 'none') ? canvas : videoElement;
    }

    // Pointer-lock-aware coordinates: while locked, clientX/Y freeze, so we
    // track a virtual position via movementX/Y instead.
    let virtualMouseX = -1;
    let virtualMouseY = -1;

    // The displayed frame may be letterboxed inside the element (object-fit)
    // AND letterboxed by the phone (frame aspect vs screen aspect). Input must
    // be normalized against the *displayed frame*, not the element rect, or
    // touches drift toward the center and skew around the edges (analog
    // sticks read the wrong direction).
    function displayedContentRect() {
      const el = getActiveDisplay();
      const elRect = el.getBoundingClientRect();
      let vw = 0, vh = 0;
      if (el.tagName === 'VIDEO') {
        vw = el.videoWidth || 0; vh = el.videoHeight || 0;
      } else {
        vw = el.width || 0; vh = el.height || 0;
      }
      if (!vw || !vh || !elRect.width || !elRect.height) {
        return { left: elRect.left, top: elRect.top, width: elRect.width, height: elRect.height };
      }
      let fit = 'contain';
      try { fit = getComputedStyle(el).objectFit || 'contain'; } catch (e) {}
      if (fit === 'fill') {
        return { left: elRect.left, top: elRect.top, width: elRect.width, height: elRect.height };
      }
      const scale = (fit === 'cover')
        ? Math.max(elRect.width / vw, elRect.height / vh)
        : Math.min(elRect.width / vw, elRect.height / vh);
      const cw = vw * scale, ch = vh * scale;
      return {
        left: elRect.left + (elRect.width - cw) / 2,
        top: elRect.top + (elRect.height - ch) / 2,
        width: cw,
        height: ch
      };
    }

    function normIntoContent(clientX, clientY) {
      const r = displayedContentRect();
      const x = Math.max(0, Math.min(1, (clientX - r.left) / r.width));
      const y = Math.max(0, Math.min(1, (clientY - r.top) / r.height));
      return { x, y };
    }

    function effectiveCoords(e) {
      if (document.pointerLockElement) {
        const r = displayedContentRect();
        if (virtualMouseX < 0) {
          virtualMouseX = (e.clientX - r.left) / r.width;
          virtualMouseY = (e.clientY - r.top) / r.height;
        }
        virtualMouseX = Math.max(0, Math.min(1, virtualMouseX + (e.movementX || 0) / r.width));
        virtualMouseY = Math.max(0, Math.min(1, virtualMouseY + (e.movementY || 0) / r.height));
        return { x: virtualMouseX, y: virtualMouseY };
      }
      virtualMouseX = -1;
      return getNormalizedCoords(e);
    }
    function getNormalizedCoords(e) {
      return normIntoContent(e.clientX, e.clientY);
    }

    // Mouse Input
    let isMouseDown = false;
    streamContainer.addEventListener('mousedown', (e) => {
      if (e.target.closest('#quick-dock') || e.target.closest('.stream-hud') || e.target.id === 'clean-mode-floating-btn') return;
      if (calibratingControl) {
        // Key-map calibration: this click teaches where the button is
        e.preventDefault();
        const cal = getNormalizedCoords(e);
        captureCalibration(cal.x, cal.y);
        return;
      }
      initAudio();
      isMouseDown = true;
      const { x, y } = effectiveCoords(e);
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
        const { x, y } = effectiveCoords(e);
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
      const { x, y } = effectiveCoords(e);
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

      // Ignore browser auto-repeat: the phone HOLDS the key until keyup
      if (e.repeat) return;

      // Ignore pure modifier keys (they are already reflected in e.key)
      if (e.code.startsWith('Shift') || e.code.startsWith('Control') ||
          e.code.startsWith('Alt') || e.code.startsWith('Meta') ||
          e.code === 'CapsLock' || e.code === 'FnLock') return;

      // F3 opens / closes the key-map calibration editor
      if (e.code === 'F3') {
        e.preventDefault();
        toggleKeymapEditor();
        return;
      }

      // Escape cancels a pending calibration instead of reaching the phone
      if (e.code === 'Escape' && calibratingControl) {
        calibratingControl = null;
        calibHint.classList.remove('active');
        return;
      }

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

      // Send the keystroke to the phone: IME typing / accessibility game
      // mapping, with calibrated on-screen coordinates when the key-map has them
      sendKeyboard(e.code, e.key, true);

      // Prevent scrolling on space / arrow keys during gameplay
      if (['Space', 'ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight'].includes(e.code)) {
        e.preventDefault();
      }
    });

    window.addEventListener('keyup', (e) => {
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'SELECT') return;

      sendKeyboard(e.code, e.key, false);
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
