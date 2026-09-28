# Drawing Review — flow animation

An animated, self-playing prototype of the Drawing Review workflow, rebuilt in HTML/CSS/JS from the
Figma file (`Untitled`, node `114:388`). A scripted cursor walks through the whole flow; the controls
bar lets you pause, change speed, restart, or jump to any chapter.

Open `index.html` through any static server (e.g. `python3 -m http.server`) and visit it in a browser.
Add `?chapter=7` to start at a specific chapter. Keyboard: `Space` play/pause, `←` / `→` previous/next chapter.

## Chapters

1. **Launch workflow** — open Drawing Review from Workflows, attach the drawing, type and send the prompt.
2. **Clarifying questions** — reasoning panel with questions, answer options and a custom response.
3. **Plan & tasks** — the agent's task list completes step by step.
4. **Drawing Review ready** — streamed answer with the Drawing Review artifact card.
5. **Issues panel** — the header button on the right opens the issues panel on the left.
6. **Issue actions** — action menu (Autodesk / Fieldwire / Bentley ProjectWise), accept, ignore, reopen.
7. **Drawing with callouts** — the artifact opens; callouts follow the selected issue.
8. **Filter issues** — discipline filter; the list, chips and drawing markers update together.
9. **Download & history** — download as Excel/JSON, browse version history.
10. **Issues ↔ Files** — switch artifact source, switch the panel between issues and files in context.
11. **Split view** — two drawings side by side.
12. **Layout combinations** — issues + artifact + chat, issues + artifact, artifact + chat, artifact only, issues + chat.

## Notes

- The stage is a fixed 1920×1080 mock scaled to fit the window, matching the Figma frames.
- Fonts: the design uses Switzer / Geist Mono; the page uses Switzer if installed, else Inter, plus Geist Mono (Google Fonts).
- `assets/drawing.png` is the drawing sheet exported from the Figma file. Icons are inline SVG approximations.
- Issue content beyond the four issues in the design (egress corridor, door label, submittal set) is illustrative.
