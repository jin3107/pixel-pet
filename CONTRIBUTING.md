# Contributing to Pixel Pet

Thanks for wanting to help. Issues and pull requests are welcome: new tricks,
better animations, Linux or macOS support, bug fixes.

## Requirements

- Windows
- [Node.js](https://nodejs.org/) 18 or newer (20/22 LTS recommended, see the
  Node 24 note below)

## Run from source

```bash
git clone https://github.com/jin3107/pixel-pet.git
cd pixel-pet
npm install
npm start
```

A tray icon appears. Right-click it for settings, to switch pet, to toggle the
optional features, or to **Exit**.

### Known issue: Node 24 + Electron install

On Node 24, `npm install`'s Electron postinstall step can silently fail to
extract `electron.exe` (you will see only a `locales` folder under
`node_modules/electron/dist`, and `npm start` fails with "Electron failed to
install correctly"). This is an `extract-zip` incompatibility, not a network
issue: the zip is already downloaded correctly into
`%LOCALAPPDATA%\electron\Cache`.

Workarounds, in order of preference:
1. Use Node 20 or 22 LTS instead (easiest fix).
2. Or finish the install by hand after `npm install`:
   ```powershell
   npm approve-scripts electron
   Expand-Archive "$env:LOCALAPPDATA\electron\Cache\<hash>\electron-v45.3.1-win32-x64.zip" -DestinationPath "node_modules\electron\dist"
   "electron.exe" | Out-File -NoNewline "node_modules\electron\path.txt"
   ```

## Building the installer

```bash
npm run dist
```

Produces `dist/pixel-pet.exe` with `electron-builder` (NSIS, see the `build`
section of `package.json`) and copies it next to the project folder. An
`afterPack` hook (`scripts/afterPack.js`) embeds `build/icon.ico` into the app
executable. Regenerate that file if you change `renderer/app-icon.png`.

## Project structure

```
main.js                Electron main process: windows, the 30 ms state-machine
                        loop (tick), drag/pat IPC, care-message scheduling,
                        tray menu, settings and picker windows
brain.js               Mini brain: OpenAI-compatible request, whitelist,
                        cooldown/backoff, connection test
config.js              pet-config.json load/save, API-key encryption, one-time
                        migration from the old app-data folder
awareness/             Optional foreground-app observer (observer, in-memory
                        memory, the gate that decides when to speak)
preload.js             contextBridge for the pet renderer (petAPI)
bubblePreload.js       ... for the speech-bubble window
settingsPreload.js     ... for the settings window
modelPickerPreload.js  ... for the picker window
renderer/
  index.html           One <svg id="creature"> mount plus one
                        <script type="text/svg-template" id="tpl-*"> per pet
                        (clawd / cat / sheep); pet.js swaps the right one in
  pet.js               Applies state classes, wires up drag/hover; also the
                        ?preview=<model> mode used by the picker
  model-picker.html/js Pet picker with live preview
  settings.html/js     Settings window (General and Mini brain tabs)
  bubble.html/js       The speech-bubble overlay
  css/
    ui.css             Shared look of the picker and settings windows
    base.css           Layout, cursors, transform origins, hidden groups
    states.css         One body.<state> block per state or trick
    keyframes.css      All @keyframes
build/icon.ico         Multi-size app icon
scripts/               afterPack.js (embed icon), postbuild.js (copy installer)
docs/images/           Screenshots used in this README
```

The pet's behavior is a state machine driven by `main.js`'s `tick()` loop (every
30 ms). It broadcasts `{ state, facing, careMsg }` to the renderer, which only
toggles CSS classes, so all animation lives in `renderer/css/`. The speech bubble
is a second transparent `BrowserWindow` that `main.js` positions above the pet's
head.

## Guidelines

A few things that help:

- **Keep the sprites pixel-accurate.** Plain axis-aligned `<rect>` elements, no
  rounded corners, no smooth easing on movement (use `steps(1)`, not
  `ease-in-out`). The three pets share one grid: columns at `x = 10 + 7.5k`,
  rows at `y = 2.5 + 7.5j`, feet on `y = 55`, facing right. Keep dark colours
  mid-tone so they stay visible on a dark taskbar.
- **New tricks** go in the `tricks` array in `nextAction()` (`main.js`). Gate
  them by `petConfig.petModel` if the trick needs props a pet does not have, add a
  matching `body.<state>` block in `renderer/css/states.css`, and add the state to
  that pet's list in `brain.js` so Mini brain can choose it.
- **Test by running it** (`npm start`) and watching the pet on your desktop
  before opening a PR. This is a visual project, so screenshots or GIFs in the PR
  description help a lot.
