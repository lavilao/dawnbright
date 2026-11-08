# Sunshine Android: Remote Play Host for Chrome

A high-performance remote gaming host for Android—inspired by Sunshine and Moonlight—enabling real-time game streaming and remote control from any Chrome browser on a laptop or desktop with ultra-low latency, hardware-accelerated encoding, and integrated app launching.

## User Review & Critical Decisions

> [!IMPORTANT]
> Based on your Phase 1 answers, here are the core technical decisions guiding the implementation:
> - **Streaming Protocol**: WebRTC with hardware-accelerated H.264 encoding via Android `MediaCodec` and low-latency DataChannels.
> - **Client Inputs**: Mouse, keyboard, and touch controls supported in the browser interface, with customizable keyboard mapping (e.g. mapping laptop keys to game controller and touch inputs).
> - **Input Injection & Play Policy**: For cross-app keyboard and touch injection, Android requires either an Accessibility Service gesture bridge or an overlay/helper bridge. To ensure full compatibility with the **Google Play Creator Program** (which restricts `BIND_ACCESSIBILITY_SERVICE`), we provide an integrated floating touch/keyboard overlay bridge alongside a direct accessibility service toggle for users who want seamless OS-level background input injection.

- **Confirmed Decision 1**: Ultra-low latency WebRTC pipeline streaming directly to Chrome with hardware H.264 (`MediaCodec`) and system audio capture (`AudioPlaybackCapture`).
- **Confirmed Decision 2**: Chrome web client with full laptop keyboard, mouse click/drag, and touch support.
- **Confirmed Decision 3**: Sunshine-style Game/App launcher grid in both the Android app and the browser web UI, allowing one-click remote app launching (e.g., launching PPSSPP or any installed game directly from Chrome).

---

## 1. Overview & Core Concept

- **What It Does**: Sunshine Android turns your Android phone or tablet into a high-performance game streaming server. You connect from Chrome on your laptop, browse your installed games library (emulators like PPSSPP, RetroArch, Android games), click to launch a game, and play immediately with your laptop's keyboard and mouse with sub-second response times and synchronized audio.
- **Target Audience**: Mobile and retro gamers who want to enjoy their Android games and emulators on a larger laptop/PC screen without cumbersome cables, proprietary software, or high-latency screen mirroring tools.
- **Key Value**: Low-latency hardware encoding, integrated game library launcher, synchronized audio streaming, and laptop keyboard/mouse control mapping directly in the browser without installing any client software.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Dashboard & Server Setup**:
   - The user opens Sunshine Android. The app displays the server status (IP address, port, WebRTC signaling state, active connections).
   - An intuitive "Start Server" toggle requests standard Android screen casting permissions (`MediaProjection`) and foreground service initialization.
2. **Game & App Library Management**:
   - The user views scanned games and installed emulators (PPSSPP, RetroArch, etc.) in a sleek grid.
   - Users can pin favorite games, configure stream quality profiles (720p 60fps, 1080p 60fps, custom bitrates from 5 to 30 Mbps), and customize keyboard key-mappings.
3. **Browser Remote Play Experience**:
   - The user opens the local URL (e.g., `http://192.168.1.50:8080`) on their laptop Chrome browser.
   - **Browser Game Launcher Grid**: Displays cards for all configured games with high-res icons.
   - Clicking a game sends a launch signal to the phone, which starts the app in the foreground.
   - **Full-Screen Low-Latency Player**: Switches to the live stream canvas with zero letterbox distortion, displaying live telemetry (FPS, bitrate, RTT latency).
   - **Controls**: Laptop keyboard presses (WASD, Enter, Space, custom PSP keybindings) and mouse clicks/drags are transmitted over WebRTC DataChannel in real time.

### Visual Identity & Theme (Android & Web UI)

- **Aesthetic Direction**: High-tech Cyberpunk & Gaming Dark theme, inspired by Sunshine/Moonlight with neon cyan (`#00E5FF`) and electric purple (`#7C4DFF`) accents on deep slate surfaces (`#0D1117`, `#161B22`).
- **Color Palette**:
  - Background: `#0B0F19` (Deep Obsidian)
  - Surface: `#161F30` (Navy Slate)
  - Primary Accent: `#00E5FF` (Neon Cyan)
  - Secondary Accent: `#7C4DFF` (Electric Purple)
  - Success/Connected: `#00E676` (Vibrant Emerald)
  - Warning/Dropped: `#FFAB00` (Amber)
- **Typography & Hierarchy**:
  - Display Font: Clean modern sans-serif with bold tech styling for stats, FPS, and titles.
  - Body Font: Highly legible system font with monospaced accents for IP addresses, latency gauges, and port settings.
- **Component Styling & Layout**:
  - Floating glassmorphism cards with subtle neon borders.
  - Real-time connection indicators (Pulsing green dot for active WebRTC stream).
  - Responsive Grid adapting from compact phone screens to wide tablets and the Chrome web client.

---

## 3. Key Product Decisions & Trade-Offs

### Decision 1: Video Encoding Architecture (`MediaCodec` Surface vs Bitmap Loop)
- **Chosen Approach**: Direct `VirtualDisplay` projection onto a hardware `MediaCodec` input surface configured for low-latency H.264 baseline/main profile with real-time CBR rate control and zero B-frames.
- **Why**: Capturing raw frames via CPU or Bitmap copies incurs severe frame drops and 100ms+ latency. Hardware `MediaCodec` input surface encodes directly in GPU/VPU memory with sub-15ms encoding latency.
- **Alternatives Considered**: Software VP8/VP9 encoding (too CPU intensive, causes thermal throttling).

### Decision 2: Streaming Protocol (WebRTC vs WebSocket H.264 vs HTTP Live)
- **Chosen Approach**: WebRTC PeerConnection with WebSockets for initial SDP/ICE signaling and an embedded HTTP server serving the single-page Chrome client.
- **Why**: WebRTC is natively supported by Chrome with hardware decode, jitter buffers, and sub-frame latency. DataChannels provide reliable, ultra-fast input transmission without TCP head-of-line blocking.
- **Fallback**: Browser WebCodecs / low-latency WebSocket binary stream fallback for network environments where direct WebRTC ICE negotiation encounters restrictive NATs.

### Decision 3: Audio Streaming Pipeline
- **Chosen Approach**: Android 10+ `AudioPlaybackCaptureConfiguration` combined with `AudioRecord` capturing clean internal app/game audio without room echo or microphone interference, encoded into Opus/AAC.
- **Why**: Essential for immersive gaming where audio synchronization matches the on-screen action.

### Decision 4: Keyboard & Mouse Input Injection
- **Chosen Approach**: Dual-mode input bridge:
  1. *In-App & Floating Overlay Controller*: Zero special permissions required, injects mapped controls and provides on-screen helper.
  2. *Accessibility Service Gesture & Key Dispatcher*: For unrooted phones requiring cross-app touch and key injection into third-party apps like PPSSPP.
- **Why**: Gives the user immediate zero-setup functionality while offering the full automated background injection when the user enables the accessibility service.

---

## 4. Technical Architecture & Data Strategy

### System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                   Laptop / PC Chrome Browser                │
│  ┌───────────────────────┐  ┌────────────────────────────┐  │
│  │   App Launcher Grid   │  │   Low-Latency WebRTC       │  │
│  │  (PPSSPP, Games, etc.)│  │   Video / Audio Canvas     │  │
│  └───────────┬───────────┘  └─────────────▲──────────────┘  │
│              │ App Launch Cmd             │ H.264 Video     │
│              │                            │ Opus Audio      │
│  ┌───────────▼───────────┐                │                 │
│  │ Keyboard / Mouse Data ├────────────────┼──────────────┐  │
│  │ Channel Input Events  │                │              │  │
│  └───────────────────────┘                │              │  │
└──────────────────▲────────────────────────┼──────────────┼──┘
                   │ HTTP / WS Signaling    │ WebRTC Peer  │ DataChannel
                   │                        │ Connection   │
┌──────────────────▼────────────────────────┴──────────────▼──┐
│                   Sunshine Android Host App                 │
│                                                             │
│  ┌───────────────────────────────────────────────────────┐  │
│  │            Embedded HTTP & WebSocket Server           │  │
│  │  - Serves Chrome Web App Bundle (HTML5, JS, CSS)      │  │
│  │  - REST API: /api/apps (Game list & icons)            │  │
│  │  - WebSocket: SDP / ICE Candidate Signaling           │  │
│  └───────────────────────────┬───────────────────────────┘  │
│                              │                              │
│  ┌───────────────────────────▼───────────────────────────┐  │
│  │         ScreenCastForegroundService (MediaProjection)  │  │
│  │  ┌──────────────────────┐  ┌────────────────────────┐ │  │
│  │  │  MediaCodec (H.264)  │  │ AudioPlaybackCapture   │ │  │
│  │  │  Low-latency Surface │  │ (System & Game Audio)  │ │  │
│  │  └──────────┬───────────┘  └───────────┬────────────┘ │  │
│  └─────────────┼──────────────────────────┼──────────────┘  │
│                │                          │                 │
│  ┌─────────────▼──────────────────────────▼──────────────┐  │
│  │            Remote Input Dispatch & Injection          │  │
│  │  - Keyboard to KeyCode / KeyCombination Mapper        │  │
│  │  - Mouse to Touch Coordinate Transformer              │  │
│  │  - Package Launcher (Intent.CATEGORY_LAUNCHER)        │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Data Model & App State

- **`GameApp`**:
  - `packageName`: String (unique identifier, e.g., `org.ppsspp.ppsspp`)
  - `appName`: String (display title)
  - `iconUri`: String / Base64 (cached drawable icon served to web client)
  - `isPinned`: Boolean (favorite status)
  - `customKeymap`: Map<String, Int> (laptop key to Android keycode or touch point)
- **`StreamConfig`**:
  - `resolution`: Pair<Int, Int> (e.g. 1920x1080, 1280x720)
  - `frameRate`: Int (30 or 60 FPS)
  - `bitrateMbps`: Int (5 to 30 Mbps)
  - `audioEnabled`: Boolean
  - `port`: Int (default 8080)
- **`ServerState`**:
  - `isServerRunning`: Boolean
  - `isStreaming`: Boolean
  - `connectedClients`: Int
  - `currentBitrate`: Float
  - `currentFps`: Int
  - `activePackage`: String?

### Key Components

1. **`ScreenCastForegroundService`**: Manages the lifecycle of screen capture (`MediaProjection`), virtual display, and foreground notification.
2. **`HardwareVideoEncoder`**: Configures `MediaCodec` for `video/avc` (H.264) at high profile with low latency, outputting Annex-B NAL units.
3. **`AudioStreamRecorder`**: Captures playback audio using `AudioPlaybackCaptureConfiguration`.
4. **`EmbeddedHttpServer`**: Embedded lightweight multi-threaded HTTP server providing the web UI assets and REST endpoints (`/api/apps`, `/api/launch`, `/api/status`).
5. **`WebRtcSignalingServer`**: Handles WebSocket connections for SDP offer/answer exchanges and DataChannel input delivery.
6. **`InputBridgeService`**: Decodes incoming keyboard and mouse events and dispatches them via Accessibility / Overlay injection.
7. **`MainDashboard`**: Jetpack Compose UI showing server control, IP connection info, stream stats, app selection grid, and settings.
