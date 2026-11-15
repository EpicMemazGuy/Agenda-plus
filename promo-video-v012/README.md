# Agenda+ v0.1.2-pre-alpha • 140 BPM Motion Graphics Promo 🚀✨

Een pure, zelfstandige **HTML5 / CSS3 / JavaScript** motion-graphics promoclip die de release van **Agenda+ `v0.1.2-pre-alpha`** op GitHub Releases (`github.com/EpicMemazGuy`) en `agendaplus.nl` viert.

- ⏱️ **Exact 140 BPM Beat-Sync Engine**: Elke slam, pill-morph, card-flip en particle burst valt exact op de kwart- en achtste-noten (`428.57ms` per beat).
- 🎨 **Material Design 3 (M3) Expressive Design**: Tonal dark surfaces, pastel accents (Electric Periwinkle, Mint, Warm Coral, Lavender, Gold), en elastische spring physics.
- 📱 **Dual Aspect Ratio**: Standaard **9:16 Verticaal** (1080×1920 voor TikTok, YouTube Shorts en Instagram Reels), wisselbaar naar **16:9 Horizontaal** (1920×1080).
- 🔇 **Web Audio 140 BPM Synthesizer**: Optionele sub-kick & hi-hat puls (druk op `M` of klik op de knop) om beat-synchronisatie te verifiëren vóór het opnemen.
- 🎬 **Clean Recording Mode**: Druk op `H` of open met `?clean=1` om alle besturingselementen te verbergen voor een 100% schone schermopname.

---

## ⚡ Direct Lokaal Bekijken

Open het bestand `index.html` in elke moderne browser (Chrome, Edge, Safari of Firefox):

### Optie 1: Direct in de browser
Dubbelklik op `index.html` in de map `promo-video-v012/`.

### Optie 2: Lokale HTTP Server (Aanbevolen voor OBS/Browser Source)
Voer in je terminal uit vanuit de projectmap:
```bash
cd promo-video-v012
python3 -m http.server 8000
```
Open vervolgens in je browser:
- Met HUD bediening: [http://localhost:8000](http://localhost:8000)
- In Clean Recording Mode (zonder knoppen): [http://localhost:8000/?clean=1](http://localhost:8000/?clean=1)

---

## ⌨️ Sneltoetsen (Hotkeys)

| Toets | Functie |
| :--- | :--- |
| <kbd>Spatie</kbd> | Afspelen / Pauzeren |
| <kbd>R</kbd> | Opnieuw starten vanaf Bar 01 : Beat 1 |
| <kbd>H</kbd> of <kbd>Esc</kbd> | Clean Recording Mode aan/uit (verbergt/toont HUD) |
| <kbd>M</kbd> | 140 BPM Audio Klik / Sub-Kick dempen of aanzetten |
| <kbd>A</kbd> | Schakelen tussen **9:16 Verticaal** en **16:9 Horizontaal** |
| <kbd>F</kbd> | Volledig scherm (Fullscreen) |

---

## 🎥 Tips voor Schermopname op 60fps (OBS Studio / TikTok)

1. **Resolutie**: Stel in je opnamesoftware (zoals OBS Studio of Mac QuickTime/Screen Recording) de canvasresolutie in op `1080x1920` (voor 9:16) bij `60 FPS`.
2. **Clean Mode**: Druk op <kbd>H</kbd> of gebruik de URL `http://localhost:8000/?clean=1`.
3. **Muziek toevoegen**: Gebruik een energieke 140 BPM phonk, trap, dance of electronic beat onder de video. Omdat alle animaties hard-locked zijn op 140 BPM (`428.57ms`), sluit elke 140 BPM audio-track direct naadloos aan op elke visuele drop en typography slam!
4. **Duur**: De clip duurt precies **16 bars (64 beats = ~27.43 seconden)** en loopt daarna naadloos in een loop door.

---

## 📁 Bestanden in `promo-video-v012/`
- `index.html`: Canvas stage, dynamische M3 layouts en toggleable HUD.
- `style.css`: Material Design 3 tokens, spring bezier curves, morphing shapes en responsive scaling.
- `script.js`: 140 BPM beat scheduler, requestAnimationFrame timeline controller, canvas particle engine en Web Audio synthesizer.
- `README.md`: Deze handleiding.
