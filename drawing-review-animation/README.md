# Drawing Review — flow animation

An animated, self-playing prototype of the Drawing Review workflow, rebuilt in HTML/CSS/JS from the
Figma file (`Untitled`, node `114:388`). It plays as one continuous video (about 3 minutes, looping):
a scripted cursor walks through the whole flow while a virtual camera zooms and pans to each moment.
The player bar has play/pause, restart, a seekable timeline and playback speed.

Open `index.html` through any static server (e.g. `python3 -m http.server`) and visit it in a browser.
Keyboard: `Space` play/pause; with the timeline focused, `←` / `→` jump back or forward.
URL options: `?scene=7` starts at the 7th scene, `?speed=2` plays faster.

## Flow

The video runs through these scenes without cuts:

- Launch Drawing Review from Workflows, attach the drawing, type and send the prompt.
- The agent reasons and asks clarifying questions (answer options and a custom response).
- The agent's plan and task list complete step by step.
- The streamed answer with the Drawing Review artifact card.
- The header button on the right opens the issues panel on the left.
- Issue actions (Autodesk / Fieldwire / Bentley ProjectWise), accept, ignore, reopen.
- The artifact opens; drawing callouts follow the selected issue.
- Discipline filters update the list, chips and drawing markers together.
- Download as Excel/JSON and browse version history.
- Switch artifact source, and switch the panel between issues and files in context.
- Split view with two drawings side by side.
- Layout combinations: issues + artifact + chat, issues + artifact, artifact + chat, artifact only, issues + chat.

## Motion

Directed with the [LottieFiles motion-design skill](https://github.com/LottieFiles/motion-design-skill):

- **Camera.** The 1920×1080 screen sits in a fixed frame; a virtual camera zooms and pans to the
  element each beat is about (menus, composer, question panel, task list, callouts), then pulls back
  for layout changes. Long pans between two close-ups pull out through a mid keyframe (1/3 rule).
- **One personality.** Calm product-demo motion: a single signature curve `cubic-bezier(.4,0,.2,1)`
  for camera and on-screen moves, emphasized decelerate `(.05,.7,.1,1)` for entrances, a light
  overshoot only on small confirmations (badges, markers, checkmarks).
- **Three layers.** Primary (camera, cursor, UI action); secondary (issue list cascade 45ms apart,
  menu items 22ms apart, drawing lift-in after the column opens, layout "kick"); ambient (slow drift
  while zoomed, edge vignette, background gradient).
- **Cursor.** macOS system cursors from the Figma "Cursors (Community)" file (`assets/cursors/`), with macOS
  rules: arrow by default and inside menus, pointing hand over clickable content, I-beam over editable
  fields and selectable text, open/closed hand over the draggable drawing, hidden while typing until the
  next mouse move.
- **Arcs.** The cursor travels on curved paths. Scenes hand off directly; only a start or seek dissolves in.
- **Controls.** Pause freezes every running animation. `prefers-reduced-motion` turns off camera
  moves and ambient drift.

## Notes

- The stage is a fixed 1920×1080 mock scaled to fit the window, matching the Figma frames.
- Fonts: the design uses Switzer / Geist Mono; the page uses Switzer if installed, else Inter, plus Geist Mono (Google Fonts).
- `assets/drawing.png` is the drawing sheet exported from the Figma file. Icons are inline SVG approximations.
- Issue content beyond the four issues in the design (egress corridor, door label, submittal set) is illustrative.

## Exporting the video

`tools/capture.js` renders the film deterministically, frame by frame, at 3840×2160 (1920×1080 at 2× pixel ratio).
A virtual clock drives the script's waits and timers, every CSS / Web Animation is paused and stepped by one
frame, and `Math.random` is seeded, so parallel workers produce identical timelines.

```sh
python3 -m http.server 8765 &                       # serve this folder
node tools/capture.js frames 0 1800 &               # workers render frame ranges in parallel
node tools/capture.js frames 1800 3600 &
node tools/capture.js frames 3600 end
ffmpeg -framerate 30 -i frames/%06d.jpg -c:v libx264 -preset slow -crf 16 -pix_fmt yuv420p -movflags +faststart drawing-review.mp4
```

Needs Playwright (with Chromium) and an ffmpeg build with libx264.
