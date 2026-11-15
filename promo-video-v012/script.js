/**
 * AGENDA+ v0.1.2-PRE-ALPHA MOTION GRAPHICS PROMO ENGINE
 * Hard-locked to 140 BPM (428.5714ms per beat / 1714.2857ms per bar)
 * Pure HTML5 Canvas, Web Audio API & CSS Keyframe Orchestration
 */

(function () {
  'use strict';

  // =========================================================================
  // 1. BEAT ENGINE CONFIGURATION & CONSTANTS
  // =========================================================================
  const BPM = 140;
  const BEAT_MS = 60000 / BPM;              // 428.5714 ms
  const BAR_MS = BEAT_MS * 4;               // 1714.2857 ms
  const HALF_BEAT_MS = BEAT_MS / 2;         // 214.2857 ms
  const TOTAL_BARS = 16;
  const TOTAL_BEATS = TOTAL_BARS * 4;       // 64 beats
  const TOTAL_DURATION_MS = TOTAL_BARS * BAR_MS; // 27428.5714 ms (~27.43s)

  // App State
  let isPlaying = true;
  let isAudioEnabled = false;
  let isCleanMode = false;
  let isLandscape = false; // default 9:16
  let currentTimeMs = 0;
  let lastTimestamp = 0;
  let lastTriggeredBeat = -1;
  let lastTriggeredHalfBeat = -1;

  // DOM Elements
  const stage = document.getElementById('stage');
  const particleCanvas = document.getElementById('particle-canvas');
  const ctx = particleCanvas.getContext('2d');

  // HUD Elements
  const hudPanel = document.getElementById('hud-panel');
  const btnUnhideHud = document.getElementById('btn-unhide-hud');
  const timelineFill = document.getElementById('timeline-fill');
  const timelineScrubber = document.getElementById('timeline-scrubber');
  const timelineBar = document.getElementById('timeline-bar');
  const btnPlayPause = document.getElementById('btn-play-pause');
  const iconPlay = btnPlayPause.querySelector('.icon-play');
  const iconPause = btnPlayPause.querySelector('.icon-pause');
  const playBtnText = document.getElementById('play-btn-text');
  const btnRestart = document.getElementById('btn-restart');
  const btnAudio = document.getElementById('btn-audio');
  const iconAudioOff = btnAudio.querySelector('.icon-audio-off');
  const iconAudioOn = btnAudio.querySelector('.icon-audio-on');
  const audioBtnText = document.getElementById('audio-btn-text');
  const btnAspect = document.getElementById('btn-aspect');
  const aspectBtnText = document.getElementById('aspect-btn-text');
  const btnClean = document.getElementById('btn-clean');
  const beatLed = document.getElementById('beat-led');
  const beatReadout = document.getElementById('beat-readout');

  // Scene Elements
  const scenes = {
    hook: document.getElementById('scene-hook'),
    drop: document.getElementById('scene-drop'),
    celebrate: document.getElementById('scene-celebrate'),
    montage: document.getElementById('scene-montage'),
    outro: document.getElementById('scene-outro'),
  };

  // Scene 1 Elements
  const hookReleasePill = document.getElementById('hook-release-pill');
  const wordIts = document.getElementById('word-its');
  const wordFinally = document.getElementById('word-finally');
  const wordHere = document.getElementById('word-here');
  const hookSubChip = document.getElementById('hook-sub-chip');
  const rippleRings = document.querySelectorAll('.m3-ripple-ring');

  // Scene 2 Elements
  const burstBadge = document.getElementById('burst-badge');
  const versionCard = document.getElementById('version-card');

  // Scene 3 Elements
  const deckCards = [
    document.getElementById('deck-card-1'),
    document.getElementById('deck-card-2'),
    document.getElementById('deck-card-3'),
    document.getElementById('deck-card-4'),
  ];

  // Scene 4 Elements
  const montageCuts = [
    document.getElementById('montage-1'),
    document.getElementById('montage-2'),
    document.getElementById('montage-3'),
    document.getElementById('montage-4'),
    document.getElementById('montage-5'),
  ];

  // Scene 5 Elements
  const outroCard = document.getElementById('outro-card');
  const outroCtaBtn = document.getElementById('outro-cta-btn');

  // Check URL parameter for clean mode
  const urlParams = new URLSearchParams(window.location.search);
  if (urlParams.get('clean') === '1' || urlParams.get('clean') === 'true') {
    setCleanMode(true);
  }

  // =========================================================================
  // 2. WEB AUDIO SYNTHESIZER (140 BPM CLICK & SUB-KICK)
  // =========================================================================
  let audioCtx = null;

  function initAudio() {
    if (!audioCtx) {
      const AudioContextClass = window.AudioContext || window.webkitAudioContext;
      if (AudioContextClass) {
        audioCtx = new AudioContextClass();
      }
    }
    if (audioCtx && audioCtx.state === 'suspended') {
      audioCtx.resume();
    }
  }

  function playBeatSound(isDownbeat) {
    if (!isAudioEnabled || !audioCtx) return;

    try {
      const now = audioCtx.currentTime;

      // 1. Sub-kick / Punchy Sine
      const osc = audioCtx.createOscillator();
      const gain = audioCtx.createGain();

      osc.type = 'sine';
      if (isDownbeat) {
        // Downbeat (Beat 1 of Bar): Big accented kick (140Hz -> 45Hz)
        osc.frequency.setValueAtTime(140, now);
        osc.frequency.exponentialRampToValueAtTime(45, now + 0.12);
        gain.gain.setValueAtTime(0.7, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.18);
      } else {
        // Regular Beats 2, 3, 4: Snappy 110Hz -> 55Hz kick
        osc.frequency.setValueAtTime(110, now);
        osc.frequency.exponentialRampToValueAtTime(55, now + 0.08);
        gain.gain.setValueAtTime(0.45, now);
        gain.gain.exponentialRampToValueAtTime(0.001, now + 0.12);
      }

      // 2. High Transient Click (Woodblock / Rim tick)
      const clickOsc = audioCtx.createOscillator();
      const clickGain = audioCtx.createGain();
      clickOsc.type = 'triangle';
      clickOsc.frequency.setValueAtTime(isDownbeat ? 1400 : 900, now);
      clickGain.gain.setValueAtTime(0.25, now);
      clickGain.gain.exponentialRampToValueAtTime(0.001, now + 0.025);

      osc.connect(gain);
      gain.connect(audioCtx.destination);
      clickOsc.connect(clickGain);
      clickGain.connect(audioCtx.destination);

      osc.start(now);
      clickOsc.start(now);
      osc.stop(now + 0.2);
      clickOsc.stop(now + 0.04);
    } catch (e) {
      console.warn('Audio playback error:', e);
    }
  }

  // =========================================================================
  // 3. CELEBRATORY M3 GEOMETRIC PARTICLES ENGINE
  // =========================================================================
  let particles = [];
  const PARTICLE_COLORS = ['#A8C7FA', '#6DD58C', '#FFB4AB', '#D0BCFF', '#FEE57E', '#FFFFFF'];

  function resizeCanvas() {
    const rect = stage.getBoundingClientRect();
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    
    // Internal coordinate system matches stage dimensions
    const w = isLandscape ? 1920 : 1080;
    const h = isLandscape ? 1080 : 1920;
    
    particleCanvas.width = w;
    particleCanvas.height = h;
  }

  class M3Particle {
    constructor(x, y, options = {}) {
      this.x = x;
      this.y = y;
      const angle = options.angle ?? Math.random() * Math.PI * 2;
      const speed = options.speed ?? (8 + Math.random() * 22);
      this.vx = Math.cos(angle) * speed;
      this.vy = Math.sin(angle) * speed - (options.lift ?? 4);
      this.gravity = options.gravity ?? 0.35;
      this.friction = 0.985;
      
      this.size = options.size ?? (14 + Math.random() * 20);
      this.color = options.color ?? PARTICLE_COLORS[Math.floor(Math.random() * PARTICLE_COLORS.length)];
      this.type = options.type ?? ['pill', 'circle', 'star'][Math.floor(Math.random() * 3)];
      
      this.rotation = Math.random() * Math.PI * 2;
      this.vRotation = (Math.random() - 0.5) * 0.2;
      this.opacity = 1;
      this.fadeRate = options.fadeRate ?? (0.012 + Math.random() * 0.015);
      this.dead = false;
    }

    update() {
      this.vx *= this.friction;
      this.vy = this.vy * this.friction + this.gravity;
      this.x += this.vx;
      this.y += this.vy;
      this.rotation += this.vRotation;
      this.opacity -= this.fadeRate;

      if (this.opacity <= 0) {
        this.dead = true;
      }
    }

    draw(c) {
      if (this.dead || this.opacity <= 0) return;
      c.save();
      c.translate(this.x, this.y);
      c.rotate(this.rotation);
      c.globalAlpha = Math.max(0, this.opacity);
      c.fillStyle = this.color;

      if (this.type === 'pill') {
        // M3 Rounded Capsule
        const w = this.size * 1.8;
        const h = this.size * 0.65;
        c.beginPath();
        c.roundRect(-w / 2, -h / 2, w, h, h / 2);
        c.fill();
      } else if (this.type === 'circle') {
        // M3 Circle Dot
        c.beginPath();
        c.arc(0, 0, this.size * 0.5, 0, Math.PI * 2);
        c.fill();
      } else if (this.type === 'star') {
        // M3 4-point Diamond Star
        const r = this.size * 0.8;
        c.beginPath();
        c.moveTo(0, -r);
        c.quadraticCurveTo(0, 0, r, 0);
        c.quadraticCurveTo(0, 0, 0, r);
        c.quadraticCurveTo(0, 0, -r, 0);
        c.quadraticCurveTo(0, 0, 0, -r);
        c.fill();
      }

      c.restore();
    }
  }

  function spawnConfettiBurst(count = 50, originX = null, originY = null) {
    const cx = originX !== null ? originX : (isLandscape ? 1920 / 2 : 1080 / 2);
    const cy = originY !== null ? originY : (isLandscape ? 1080 / 2 : 1920 / 2);

    for (let i = 0; i < count; i++) {
      particles.push(new M3Particle(cx, cy, {
        angle: Math.random() * Math.PI * 2,
        speed: 10 + Math.random() * 26,
        size: 14 + Math.random() * 22,
        lift: 6,
      }));
    }
  }

  function updateAndDrawParticles() {
    ctx.clearRect(0, 0, particleCanvas.width, particleCanvas.height);
    for (let i = particles.length - 1; i >= 0; i--) {
      const p = particles[i];
      p.update();
      p.draw(ctx);
      if (p.dead) {
        particles.splice(i, 1);
      }
    }
  }

  // =========================================================================
  // 4. STAGE AUTO-RESIZE & SCALE CONTROLLER
  // =========================================================================
  function adjustStageScale() {
    const stageWidth = isLandscape ? 1920 : 1080;
    const stageHeight = isLandscape ? 1080 : 1920;

    const winW = window.innerWidth;
    const winH = window.innerHeight;

    // Leave a safe margin for the floating HUD
    const paddingX = 40;
    const paddingY = isCleanMode ? 40 : 140;

    const availableW = Math.max(200, winW - paddingX);
    const availableH = Math.max(200, winH - paddingY);

    const scale = Math.min(availableW / stageWidth, availableH / stageHeight);

    stage.style.transform = `scale(${scale})`;
    resizeCanvas();
  }

  window.addEventListener('resize', adjustStageScale);

  // =========================================================================
  // 5. DETERMINISTIC 140 BPM SCENE STORYBOARD
  // =========================================================================
  function renderSceneAtBeat(beatFloat) {
    // Current Bar & Beat numbers
    const currentBar = Math.floor(beatFloat / 4) + 1;
    const currentBeatInBar = Math.floor(beatFloat % 4) + 1;

    // Update HUD Beat Text
    beatReadout.textContent = `MAAT ${String(currentBar).padStart(2, '0')} : TEL ${currentBeatInBar}`;

    // Timeline Scrubber Fill
    const progress = Math.min(1, Math.max(0, beatFloat / TOTAL_BEATS));
    timelineFill.style.width = `${progress * 100}%`;
    timelineScrubber.style.left = `${progress * 100}%`;

    // -----------------------------------------------------------------------
    // SCENE ROUTING (16 Bars = 64 Beats total)
    // -----------------------------------------------------------------------
    // Scene 1: Bars 1–2 (Beats 0 to 8)
    // Scene 2: Bars 3–4 (Beats 8 to 16)
    // Scene 3: Bars 5–8 (Beats 16 to 32)
    // Scene 4: Bars 9–12 (Beats 32 to 48)
    // Scene 5: Bars 13–16 (Beats 48 to 64)

    const isScene1 = beatFloat >= 0 && beatFloat < 8;
    const isScene2 = beatFloat >= 8 && beatFloat < 16;
    const isScene3 = beatFloat >= 16 && beatFloat < 32;
    const isScene4 = beatFloat >= 32 && beatFloat < 48;
    const isScene5 = beatFloat >= 48;

    scenes.hook.classList.toggle('active', isScene1);
    scenes.drop.classList.toggle('active', isScene2);
    scenes.celebrate.classList.toggle('active', isScene3);
    scenes.montage.classList.toggle('active', isScene4);
    scenes.outro.classList.toggle('active', isScene5);

    // -----------------------------------------------------------------------
    // SCENE 1 CHOREOGRAPHY (Beats 0–8)
    // -----------------------------------------------------------------------
    if (isScene1) {
      // Beat 0: Pill pops in
      hookReleasePill.classList.toggle('show', beatFloat >= 0.5);

      // Half-beat / 2-beat word slams:
      // Beat 1.0: "IT'S"
      wordIts.classList.toggle('slam', beatFloat >= 1.0);
      
      // Beat 2.5: "FINALLY"
      wordFinally.classList.toggle('slam', beatFloat >= 2.5);
      
      // Beat 4.0: "HERE."
      wordHere.classList.toggle('slam', beatFloat >= 4.0);

      // Beat 5.5: Sub-chip
      hookSubChip.classList.toggle('show', beatFloat >= 5.5);
    } else {
      // Reset Scene 1 states
      hookReleasePill.classList.remove('show');
      wordIts.classList.remove('slam');
      wordFinally.classList.remove('slam');
      wordHere.classList.remove('slam');
      hookSubChip.classList.remove('show');
    }

    // -----------------------------------------------------------------------
    // SCENE 2 CHOREOGRAPHY (Beats 8–16) — VERSION DROP
    // -----------------------------------------------------------------------
    if (isScene2) {
      const dropProgress = beatFloat - 8;
      
      // Beat 8: Spin burst
      burstBadge.classList.toggle('active-spin', dropProgress >= 0.2);

      // Beat 8.5: Hero Card drop slam
      versionCard.classList.toggle('drop-slam', dropProgress >= 0.5);
    } else {
      burstBadge.classList.remove('active-spin');
      versionCard.classList.remove('drop-slam');
    }

    // -----------------------------------------------------------------------
    // SCENE 3 CHOREOGRAPHY (Beats 16–32) — PURE CELEBRATION
    // -----------------------------------------------------------------------
    if (isScene3) {
      const celProgress = beatFloat - 16;
      // 4 cards, 4 beats each (1 card per bar):
      // Card 0: Beats 16–20 (celProgress 0–4)
      // Card 1: Beats 20–24 (celProgress 4–8)
      // Card 2: Beats 24–28 (celProgress 8–12)
      // Card 3: Beats 28–32 (celProgress 12–16)
      const activeCardIdx = Math.min(3, Math.floor(celProgress / 4));

      deckCards.forEach((card, idx) => {
        if (idx === activeCardIdx) {
          card.classList.add('active-card');
          card.classList.remove('prev-card');
        } else if (idx < activeCardIdx) {
          card.classList.remove('active-card');
          card.classList.add('prev-card');
        } else {
          card.classList.remove('active-card');
          card.classList.remove('prev-card');
        }
      });
    } else {
      deckCards.forEach((card) => {
        card.classList.remove('active-card', 'prev-card');
      });
    }

    // -----------------------------------------------------------------------
    // SCENE 4 CHOREOGRAPHY (Beats 32–48) — RAPID ENERGY MONTAGE
    // -----------------------------------------------------------------------
    if (isScene4) {
      const montProgress = beatFloat - 32; // 16 beats total
      // 5 rapid cuts:
      // Cut 1: Beats 32–35 (0–3) -> NO ADS
      // Cut 2: Beats 35–38 (3–6) -> NO BLOAT
      // Cut 3: Beats 38–41 (6–9) -> NO PAYWALLS
      // Cut 4: Beats 41–44 (9–12) -> JUST SPEED
      // Cut 5: Beats 44–48 (12–16) -> APK READY NOW

      let activeCutIdx = 0;
      if (montProgress >= 12) activeCutIdx = 4;
      else if (montProgress >= 9) activeCutIdx = 3;
      else if (montProgress >= 6) activeCutIdx = 2;
      else if (montProgress >= 3) activeCutIdx = 1;
      else activeCutIdx = 0;

      montageCuts.forEach((cut, idx) => {
        cut.classList.toggle('cut-active', idx === activeCutIdx);
      });
    } else {
      montageCuts.forEach((cut) => cut.classList.remove('cut-active'));
    }

    // -----------------------------------------------------------------------
    // SCENE 5 CHOREOGRAPHY (Beats 48–64) — OUTRO & CTA
    // -----------------------------------------------------------------------
    if (isScene5) {
      const outroProgress = beatFloat - 48;
      
      outroCard.classList.toggle('show', outroProgress >= 0.5);

      // Button tap ripple trigger on Beat 52
      outroCtaBtn.classList.toggle('tap-pulse', outroProgress >= 3.5 && outroProgress < 5.5);
    } else {
      outroCard.classList.remove('show');
      outroCtaBtn.classList.remove('tap-pulse');
    }
  }

  // =========================================================================
  // 6. BEAT TICK EVENT DISPATCHER (140 BPM SYNCHRONIZER)
  // =========================================================================
  function onBeatHit(beatIndex) {
    const isDownbeat = (beatIndex % 4) === 0;

    // Visual LED Pulse
    beatLed.classList.add('flash');
    setTimeout(() => {
      beatLed.classList.remove('flash');
    }, 110);

    // Audio Click / Sub-Kick Pulse
    playBeatSound(isDownbeat);

    // Ripple Pulse on Hook Scene
    if (beatIndex === 4) {
      rippleRings.forEach(r => {
        r.classList.remove('pulse');
        void r.offsetWidth;
        r.classList.add('pulse');
      });
    }

    // Key Beat Particle Explosions
    if (beatIndex === 0) {
      spawnConfettiBurst(25);
    } else if (beatIndex === 8) {
      // Big Version Drop Confetti!
      spawnConfettiBurst(75);
    } else if (beatIndex === 16 || beatIndex === 20 || beatIndex === 24 || beatIndex === 28) {
      // Celebratory Card flips
      spawnConfettiBurst(35);
    } else if (beatIndex === 32 || beatIndex === 44) {
      // Rapid Montage kickoff & APK drop
      spawnConfettiBurst(40);
    } else if (beatIndex === 48) {
      // Outro Reveal Celebration!
      spawnConfettiBurst(80);
    }
  }

  // =========================================================================
  // 7. HIGH-PRECISION MAIN ANIMATION LOOP
  // =========================================================================
  function tick(timestamp) {
    if (!lastTimestamp) lastTimestamp = timestamp;
    const delta = timestamp - lastTimestamp;
    lastTimestamp = timestamp;

    if (isPlaying) {
      currentTimeMs += delta;

      // Seamless Loop when reaching end of 16 bars
      if (currentTimeMs >= TOTAL_DURATION_MS) {
        currentTimeMs = 0;
        lastTriggeredBeat = -1;
        lastTriggeredHalfBeat = -1;
      }
    }

    const currentBeatFloat = currentTimeMs / BEAT_MS;
    const currentBeatInt = Math.floor(currentBeatFloat);
    const currentHalfBeatInt = Math.floor(currentTimeMs / HALF_BEAT_MS);

    // Trigger Beat Events
    if (currentBeatInt !== lastTriggeredBeat && currentBeatInt >= 0) {
      lastTriggeredBeat = currentBeatInt;
      onBeatHit(currentBeatInt);
    }

    lastTriggeredHalfBeat = currentHalfBeatInt;

    // Render Scene Keyframes
    renderSceneAtBeat(currentBeatFloat);

    // Update Particle System
    updateAndDrawParticles();

    requestAnimationFrame(tick);
  }

  // =========================================================================
  // 8. INTERACTION & HUD EVENT LISTENERS
  // =========================================================================
  function togglePlayPause() {
    isPlaying = !isPlaying;
    iconPlay.style.display = isPlaying ? 'none' : 'block';
    iconPause.style.display = isPlaying ? 'block' : 'none';
    playBtnText.textContent = isPlaying ? 'Pauzeer' : 'Afspelen';
  }

  function restart() {
    currentTimeMs = 0;
    lastTriggeredBeat = -1;
    lastTriggeredHalfBeat = -1;
    particles = [];
    if (!isPlaying) {
      togglePlayPause();
    }
  }

  function toggleAudio() {
    initAudio();
    isAudioEnabled = !isAudioEnabled;
    iconAudioOff.style.display = isAudioEnabled ? 'none' : 'block';
    iconAudioOn.style.display = isAudioEnabled ? 'block' : 'none';
    audioBtnText.textContent = isAudioEnabled ? '140 BPM Klik (Actief)' : '140 BPM Klik (Gedempt)';
  }

  function toggleAspectRatio() {
    isLandscape = !isLandscape;
    if (isLandscape) {
      document.body.classList.remove('mode-9-16');
      document.body.classList.add('mode-16-9');
      stage.classList.remove('stage-9-16');
      stage.classList.add('stage-16-9');
      aspectBtnText.textContent = '16:9 Horizontaal';
    } else {
      document.body.classList.remove('mode-16-9');
      document.body.classList.add('mode-9-16');
      stage.classList.remove('stage-16-9');
      stage.classList.add('stage-9-16');
      aspectBtnText.textContent = '9:16 Verticaal';
    }
    adjustStageScale();
  }

  function setCleanMode(active) {
    isCleanMode = active;
    hudPanel.classList.toggle('hidden', isCleanMode);
    btnUnhideHud.classList.toggle('show', isCleanMode);
    adjustStageScale();
  }

  // Timeline Click / Scrub
  timelineBar.addEventListener('click', (e) => {
    const rect = timelineBar.getBoundingClientRect();
    const ratio = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
    currentTimeMs = ratio * TOTAL_DURATION_MS;
    lastTriggeredBeat = Math.floor(currentTimeMs / BEAT_MS);
    renderSceneAtBeat(currentTimeMs / BEAT_MS);
  });

  // Button Listeners
  btnPlayPause.addEventListener('click', togglePlayPause);
  btnRestart.addEventListener('click', restart);
  btnAudio.addEventListener('click', toggleAudio);
  btnAspect.addEventListener('click', toggleAspectRatio);
  btnClean.addEventListener('click', () => setCleanMode(true));
  btnUnhideHud.addEventListener('click', () => setCleanMode(false));

  // Global Keyboard Shortcuts
  window.addEventListener('keydown', (e) => {
    // Avoid interfering if focus is in an input
    if (['INPUT', 'TEXTAREA'].includes(e.target.tagName)) return;

    switch (e.code) {
      case 'Space':
        e.preventDefault();
        togglePlayPause();
        break;
      case 'KeyR':
        e.preventDefault();
        restart();
        break;
      case 'KeyH':
      case 'Escape':
        e.preventDefault();
        setCleanMode(!isCleanMode);
        break;
      case 'KeyM':
        e.preventDefault();
        toggleAudio();
        break;
      case 'KeyA':
        e.preventDefault();
        toggleAspectRatio();
        break;
      case 'KeyF':
        e.preventDefault();
        if (!document.fullscreenElement) {
          document.documentElement.requestFullscreen().catch(() => {});
        } else {
          document.exitFullscreen().catch(() => {});
        }
        break;
    }
  });

  // Enable audio context on any user click
  window.addEventListener('click', () => {
    if (!audioCtx) initAudio();
  }, { once: true });

  // =========================================================================
  // 9. INITIALIZE
  // =========================================================================
  adjustStageScale();
  requestAnimationFrame(tick);

})();
