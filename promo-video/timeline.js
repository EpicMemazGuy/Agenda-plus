/**
 * AGENDA+ MOTION GRAPHICS PROMO VIDEO (9:16 VERTICAL 1080x1920)
 * Deterministic 115 BPM Master Beat Timeline Engine
 * Dual Theme (Nothing OS vs Material You) + MediaRecorder MP4 Export
 */

(function () {
  'use strict';

  // --- Constants ---
  const BPM = 115;
  const BEAT_MS = 60000 / BPM; // 521.73913ms
  const BAR_MS = BEAT_MS * 4;  // 2086.95652ms
  const TOTAL_BARS = 16;
  const TOTAL_BEATS = TOTAL_BARS * 4; // 64 beats
  const TOTAL_DURATION_MS = TOTAL_BEATS * BEAT_MS; // 33391.3ms (~33.39s)

  // --- DOM Elements ---
  const stage = document.getElementById('stage');
  const videoContainer = document.getElementById('video-container');
  const beatFlash = document.getElementById('beat-flash');

  // Scenes
  const sceneHook = document.getElementById('scene-hook');
  const sceneDrop = document.getElementById('scene-drop');
  const sceneHero = document.getElementById('scene-hero');
  const sceneFeatures = document.getElementById('scene-features');
  const sceneKinetic = document.getElementById('scene-kinetic');
  const sceneOutro = document.getElementById('scene-outro');

  // Scene 1 Elements
  const memeTitle = document.getElementById('meme-title');
  const legacyWindow = document.getElementById('legacy-window');
  const handDrawnSvg = document.getElementById('hand-drawn-svg');
  const capTraag = document.getElementById('caption-traag');
  const capWidgets = document.getElementById('caption-widgets');
  const capDarkmode = document.getElementById('caption-darkmode');

  // Scene 2 Elements
  const shatterSvg = document.getElementById('shatter-svg');
  const dropRing = document.getElementById('drop-ring');
  const dropText = document.getElementById('drop-text');
  const dropSub = document.getElementById('drop-sub');

  // Scene 3 Elements
  const heroPhoneWrapper = document.getElementById('hero-phone-wrapper');
  const brandThemeBadge = document.getElementById('brand-theme-badge');
  const pillNothing = document.getElementById('pill-nothing');
  const pill1Text = document.getElementById('pill-1-text');
  const pillOvapi = document.getElementById('pill-ovapi');
  const pillOffline = document.getElementById('pill-offline');
  const pillKwt = document.getElementById('pill-kwt');

  // Scene 4 Elements
  const showcaseStepLabel = document.getElementById('showcase-step-label');
  const showcaseStepTitle = document.getElementById('showcase-step-title');
  const slideCijfers = document.getElementById('slide-cijfers');
  const slideMeer = document.getElementById('slide-meer');
  const slideWidgets = document.getElementById('slide-widgets');
  const gradeProgress = document.getElementById('grade-progress');
  const gradeVal = document.getElementById('grade-val');
  const whatifFill = document.getElementById('whatif-fill');
  const whatifThumb = document.getElementById('whatif-thumb');
  const whatifVal = document.getElementById('whatif-val');

  // Scene 5 Elements
  const kinLine1 = document.getElementById('kin-line-1');
  const kinLine2 = document.getElementById('kin-line-2');
  const kinLine3 = document.getElementById('kin-line-3');
  const kinLine4 = document.getElementById('kin-line-4');
  const kinLine5 = document.getElementById('kin-line-5');
  const kinLine6 = document.getElementById('kin-line-6');

  // HUD Controls
  const timelineHud = document.getElementById('timeline-hud');
  const hudScrubber = document.getElementById('hud-scrubber');
  const hudFill = document.getElementById('hud-fill');
  const hudThumb = document.getElementById('hud-thumb');
  const btnPlayPause = document.getElementById('btn-play-pause');
  const labelPlayPause = document.getElementById('label-play-pause');
  const btnRestart = document.getElementById('btn-restart');
  const btnThemeToggle = document.getElementById('btn-theme-toggle');
  const labelTheme = document.getElementById('label-theme');
  const btnAudioSynth = document.getElementById('btn-audio-synth');
  const labelAudio = document.getElementById('label-audio');
  const btnDownloadVideo = document.getElementById('btn-download-video');
  const labelDownload = document.getElementById('label-download');
  const btnCleanMode = document.getElementById('btn-clean-mode');
  const btnFullscreen = document.getElementById('btn-fullscreen');
  const hudClockText = document.getElementById('hud-clock-text');
  const beatLed = document.getElementById('beat-led');

  // --- Engine State ---
  let isPlaying = true;
  let currentTimeMs = 0;
  let lastTimestamp = 0;
  let lastProcessedBeatIndex = -1;
  let audioSynthEnabled = true;
  let cleanRecordingMode = false;
  let isDraggingScrubber = false;
  let idleTimer = null;
  let isMaterialYouTheme = false;

  // --- Web Audio API Synth Engine ---
  let audioCtx = null;

  function initAudioContext() {
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

  function playBeatSound(beatIndex) {
    if (!audioSynthEnabled || !audioCtx) return;
    try {
      const now = audioCtx.currentTime;
      const beatInBar = (beatIndex % 4);

      if (beatIndex === 12) {
        playSubDrop(now);
      } else if (beatInBar === 0) {
        playKick(now, 1.0);
      } else if (beatInBar === 2) {
        playKick(now, 0.75);
      } else {
        playHiHat(now);
      }
    } catch (_) {}
  }

  function playKick(time, volume) {
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(140, time);
    osc.frequency.exponentialRampToValueAtTime(38, time + 0.12);

    gain.gain.setValueAtTime(0.7 * volume, time);
    gain.gain.exponentialRampToValueAtTime(0.001, time + 0.22);

    osc.connect(gain);
    gain.connect(audioCtx.destination);

    osc.start(time);
    osc.stop(time + 0.25);
  }

  function playHiHat(time) {
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'triangle';
    osc.frequency.setValueAtTime(950, time);
    osc.frequency.exponentialRampToValueAtTime(250, time + 0.04);

    gain.gain.setValueAtTime(0.25, time);
    gain.gain.exponentialRampToValueAtTime(0.001, time + 0.06);

    osc.connect(gain);
    gain.connect(audioCtx.destination);

    osc.start(time);
    osc.stop(time + 0.07);
  }

  function playSubDrop(time) {
    const osc = audioCtx.createOscillator();
    const gain = audioCtx.createGain();

    osc.type = 'sine';
    osc.frequency.setValueAtTime(180, time);
    osc.frequency.exponentialRampToValueAtTime(28, time + 0.65);

    gain.gain.setValueAtTime(1.0, time);
    gain.gain.exponentialRampToValueAtTime(0.001, time + 0.9);

    osc.connect(gain);
    gain.connect(audioCtx.destination);

    osc.start(time);
    osc.stop(time + 0.95);
  }

  // --- Stage Responsive Scaling ---
  function updateStageScale() {
    const windowW = window.innerWidth;
    const windowH = window.innerHeight;
    const scale = Math.min(windowW / 1080, windowH / 1920);
    stage.style.transform = `scale(${scale})`;
  }

  window.addEventListener('resize', updateStageScale);
  updateStageScale();

  // --- Auto-hiding HUD on mouse idle ---
  function resetIdleTimer() {
    timelineHud.classList.remove('idle-hidden');
    clearTimeout(idleTimer);
    if (isPlaying && !cleanRecordingMode) {
      idleTimer = setTimeout(() => {
        timelineHud.classList.add('idle-hidden');
      }, 2500);
    }
  }

  window.addEventListener('mousemove', resetIdleTimer);
  window.addEventListener('mousedown', resetIdleTimer);
  window.addEventListener('touchstart', resetIdleTimer);

  // --- Frame-Accurate Deterministic Scene Evaluator ---
  function evaluateTimeline(timeMs) {
    const beatFloat = timeMs / BEAT_MS;
    const currentBeat = Math.floor(beatFloat);
    const barFloat = beatFloat / 4;
    const currentBar = Math.floor(barFloat) + 1;
    const beatInBar = (currentBeat % 4) + 1;

    if (currentBeat !== lastProcessedBeatIndex) {
      if (currentBeat >= 0 && currentBeat < TOTAL_BEATS) {
        playBeatSound(currentBeat);
        pulseBeatFlash();
      }
      lastProcessedBeatIndex = currentBeat;
    }

    const progressRatio = Math.min(Math.max(timeMs / TOTAL_DURATION_MS, 0), 1);
    hudFill.style.width = `${progressRatio * 100}%`;
    hudThumb.style.left = `${progressRatio * 100}%`;
    hudClockText.textContent = `BAR ${String(currentBar).padStart(2, '0')} : BEAT ${beatInBar} (115 BPM)`;

    [sceneHook, sceneDrop, sceneHero, sceneFeatures, sceneKinetic, sceneOutro].forEach(s => s.classList.remove('active'));

    // ========================================================================
    // BARS 1–3 (Beats 0–12 / 0.0s – 6.26s) — THE MEME HOOK
    // ========================================================================
    if (beatFloat < 12) {
      sceneHook.classList.add('active');
      memeTitle.classList.toggle('visible', beatFloat >= 0.2);
      legacyWindow.classList.toggle('visible', beatFloat >= 0.5);
      capTraag.classList.toggle('visible', beatFloat >= 4);
      capWidgets.classList.toggle('visible', beatFloat >= 7);
      capDarkmode.classList.toggle('visible', beatFloat >= 10);
      handDrawnSvg.classList.toggle('visible', beatFloat >= 3);
    }
    // ========================================================================
    // BAR 4 (Beats 12–16 / 6.26s – 8.35s) — BEAT DROP TRANSITION
    // ========================================================================
    else if (beatFloat < 16) {
      sceneDrop.classList.add('active');
      shatterSvg.classList.toggle('active', beatFloat >= 12 && beatFloat < 14);
      dropRing.classList.toggle('pulse', beatFloat >= 12.5);
      dropText.classList.toggle('visible', beatFloat >= 13);
      dropSub.classList.toggle('visible', beatFloat >= 14);
    }
    // ========================================================================
    // BARS 5–8 (Beats 16–32 / 8.35s – 16.70s) — AGENDA+ HERO REVEAL
    // ========================================================================
    else if (beatFloat < 32) {
      sceneHero.classList.add('active');
      heroPhoneWrapper.classList.toggle('straight', beatFloat >= 17);
      pillNothing.classList.toggle('visible', beatFloat >= 18);
      pillOvapi.classList.toggle('visible', beatFloat >= 22);
      pillOffline.classList.toggle('visible', beatFloat >= 26);
      pillKwt.classList.toggle('visible', beatFloat >= 30);
    }
    // ========================================================================
    // BARS 9–11 (Beats 32–44 / 16.70s – 22.96s) — FAST FEATURE SHOWCASE
    // ========================================================================
    else if (beatFloat < 44) {
      sceneFeatures.classList.add('active');
      [slideCijfers, slideMeer, slideWidgets].forEach(sl => sl.classList.remove('active'));

      if (beatFloat < 36) {
        slideCijfers.classList.add('active');
        showcaseStepLabel.textContent = 'FEATURE 01 / 03';
        showcaseStepTitle.textContent = 'Cijfers & What-If';
        const targetOffset = 880 - (7.8 / 10.0 * 880);
        gradeProgress.style.strokeDashoffset = targetOffset;
        const whatifRatio = Math.min((beatFloat - 32) / 3.5, 1);
        whatifFill.style.width = `${78 - whatifRatio * 6}%`;
        whatifThumb.style.left = `${78 - whatifRatio * 6}%`;
        whatifVal.textContent = whatifRatio > 0.4 ? '7,8 → 7,6' : '7,8';
      } else if (beatFloat < 40) {
        slideMeer.classList.add('active');
        showcaseStepLabel.textContent = 'FEATURE 02 / 03';
        showcaseStepTitle.textContent = 'Berichten & KWT';
      } else {
        slideWidgets.classList.add('active');
        showcaseStepLabel.textContent = 'FEATURE 03 / 03';
        showcaseStepTitle.textContent = 'Nothing OS & Material You';
      }
    }
    // ========================================================================
    // BARS 12–14 (Beats 44–56 / 22.96s – 29.22s) — KINETIC TYPOGRAPHY FLEX
    // ========================================================================
    else if (beatFloat < 56) {
      sceneKinetic.classList.add('active');
      kinLine1.classList.toggle('visible', beatFloat >= 44);
      kinLine2.classList.toggle('visible', beatFloat >= 46);
      kinLine3.classList.toggle('visible', beatFloat >= 48);
      kinLine4.classList.toggle('visible', beatFloat >= 51);
      kinLine5.classList.toggle('visible', beatFloat >= 52);
      kinLine6.classList.toggle('visible', beatFloat >= 54);
    }
    // ========================================================================
    // BARS 15–16 (Beats 56–64 / 29.22s – 33.39s) — OUTRO & CTA
    // ========================================================================
    else {
      sceneOutro.classList.add('active');
    }
  }

  function pulseBeatFlash() {
    beatFlash.classList.add('pulse');
    beatLed.classList.add('flash');
    setTimeout(() => {
      beatFlash.classList.remove('pulse');
      beatLed.classList.remove('flash');
    }, 70);
  }

  // --- Main Animation Loop ---
  function animationTick(timestamp) {
    if (!lastTimestamp) lastTimestamp = timestamp;
    const delta = timestamp - lastTimestamp;
    lastTimestamp = timestamp;

    if (isPlaying && !isDraggingScrubber) {
      currentTimeMs += delta;
      if (currentTimeMs >= TOTAL_DURATION_MS) {
        currentTimeMs = 0;
        lastProcessedBeatIndex = -1;
      }
    }

    evaluateTimeline(currentTimeMs);
    requestAnimationFrame(animationTick);
  }

  requestAnimationFrame(animationTick);

  // --- Scrubber Interaction ---
  function seekToClientX(e) {
    const rect = hudScrubber.getBoundingClientRect();
    const clientX = e.clientX || (e.touches && e.touches[0].clientX);
    const ratio = Math.min(Math.max((clientX - rect.left) / rect.width, 0), 1);
    currentTimeMs = ratio * TOTAL_DURATION_MS;
    lastProcessedBeatIndex = Math.floor(currentTimeMs / BEAT_MS);
    evaluateTimeline(currentTimeMs);
  }

  hudScrubber.addEventListener('mousedown', (e) => {
    isDraggingScrubber = true;
    initAudioContext();
    seekToClientX(e);
  });

  window.addEventListener('mousemove', (e) => {
    if (isDraggingScrubber) seekToClientX(e);
  });

  window.addEventListener('mouseup', () => {
    isDraggingScrubber = false;
  });

  hudScrubber.addEventListener('touchstart', (e) => {
    isDraggingScrubber = true;
    initAudioContext();
    seekToClientX(e);
  }, { passive: true });

  window.addEventListener('touchmove', (e) => {
    if (isDraggingScrubber) seekToClientX(e);
  }, { passive: true });

  window.addEventListener('touchend', () => {
    isDraggingScrubber = false;
  });

  // --- Button Handlers ---
  function togglePlayPause() {
    initAudioContext();
    isPlaying = !isPlaying;
    labelPlayPause.textContent = isPlaying ? 'Pauzeer' : 'Afspelen';
    btnPlayPause.classList.toggle('active', !isPlaying);
    resetIdleTimer();
  }

  btnPlayPause.addEventListener('click', togglePlayPause);

  btnRestart.addEventListener('click', () => {
    initAudioContext();
    currentTimeMs = 0;
    lastProcessedBeatIndex = -1;
    isPlaying = true;
    labelPlayPause.textContent = 'Pauzeer';
    btnPlayPause.classList.remove('active');
    resetIdleTimer();
  });

  // --- Theme Toggle Handler ---
  btnThemeToggle.addEventListener('click', () => {
    isMaterialYouTheme = !isMaterialYouTheme;
    document.body.classList.toggle('material-you', isMaterialYouTheme);
    btnThemeToggle.classList.toggle('active', !isMaterialYouTheme);
    labelTheme.textContent = isMaterialYouTheme ? 'Thema: Material You' : 'Thema: Nothing OS';
    if (brandThemeBadge) {
      brandThemeBadge.textContent = isMaterialYouTheme ? 'Material You • 100% Offline' : 'Nothing OS • 100% Offline';
    }
    if (pill1Text) {
      pill1Text.textContent = isMaterialYouTheme ? 'Material You Thema' : 'Nothing OS Thema';
    }
    resetIdleTimer();
  });

  btnAudioSynth.addEventListener('click', () => {
    initAudioContext();
    audioSynthEnabled = !audioSynthEnabled;
    btnAudioSynth.classList.toggle('active', audioSynthEnabled);
    labelAudio.textContent = audioSynthEnabled ? '115 BPM Audio Beat' : 'Geluid Uit';
    resetIdleTimer();
  });

  // --- Download MP4 via MediaRecorder (or WebM fallback) ---
  let mediaRecorder = null;
  let recordedChunks = [];

  btnDownloadVideo.addEventListener('click', async () => {
    if (!window.MediaRecorder) {
      alert('MediaRecorder is niet ondersteund in deze browser. Gebruik Chrome of Edge.');
      return;
    }

    try {
      initAudioContext();
      // Temporarily switch to clean mode to hide HUD during export
      const wasClean = cleanRecordingMode;
      if (!cleanRecordingMode) toggleCleanMode();

      // Restart video from 0
      currentTimeMs = 0;
      lastProcessedBeatIndex = -1;
      isPlaying = true;

      const stream = stage.captureStream(60); // 60 FPS capture
      const options = { mimeType: 'video/webm;codecs=vp9' };
      mediaRecorder = new MediaRecorder(stream, options.mimeType ? options : {});

      recordedChunks = [];
      mediaRecorder.ondataavailable = (e) => {
        if (e.data && e.data.size > 0) {
          recordedChunks.push(e.data);
        }
      };

      mediaRecorder.onstop = () => {
        const blob = new Blob(recordedChunks, { type: 'video/webm' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.style.display = 'none';
        a.href = url;
        a.download = 'agenda-plus-promo-115bpm.webm';
        document.body.appendChild(a);
        a.click();
        setTimeout(() => {
          document.body.removeChild(a);
          URL.revokeObjectURL(url);
        }, 100);

        labelDownload.textContent = 'Download MP4';
        btnDownloadVideo.classList.remove('active');
        if (!wasClean) toggleCleanMode();
      };

      mediaRecorder.start();
      labelDownload.textContent = 'Bezig met opnemen...';
      btnDownloadVideo.classList.add('active');

      // Stop automatically after TOTAL_DURATION_MS
      setTimeout(() => {
        if (mediaRecorder && mediaRecorder.state === 'recording') {
          mediaRecorder.stop();
          isPlaying = false;
          labelPlayPause.textContent = 'Afspelen';
          btnPlayPause.classList.add('active');
        }
      }, TOTAL_DURATION_MS + 400);

    } catch (err) {
      alert('Kon opname niet starten: ' + err.message);
      labelDownload.textContent = 'Download MP4';
      btnDownloadVideo.classList.remove('active');
    }
  });

  function toggleCleanMode() {
    cleanRecordingMode = !cleanRecordingMode;
    document.body.classList.toggle('clean-recording-mode', cleanRecordingMode);
    btnCleanMode.classList.toggle('active', cleanRecordingMode);
    if (!cleanRecordingMode) resetIdleTimer();
  }

  btnCleanMode.addEventListener('click', toggleCleanMode);

  btnFullscreen.addEventListener('click', () => {
    if (!document.fullscreenElement) {
      document.documentElement.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  });

  // --- Keyboard Shortcuts ---
  window.addEventListener('keydown', (e) => {
    if (e.code === 'Space') {
      e.preventDefault();
      togglePlayPause();
    } else if (e.code === 'KeyR') {
      currentTimeMs = 0;
      lastProcessedBeatIndex = -1;
      isPlaying = true;
      labelPlayPause.textContent = 'Pauzeer';
      btnPlayPause.classList.remove('active');
      resetIdleTimer();
    } else if (e.code === 'KeyC') {
      toggleCleanMode();
    } else if (e.code === 'KeyM') {
      btnAudioSynth.click();
    } else if (e.code === 'KeyT') {
      btnThemeToggle.click();
    }
  });

  window.addEventListener('click', () => {
    initAudioContext();
  }, { once: true });

})();
