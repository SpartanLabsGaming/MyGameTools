# GameTools Reference Folder

A print-formatted, two-layer reference for the whole library: every chapter opens with a
**Quick Reference** — tables, cards and diagrams laid out to be scanned — and continues into
**In Detail**, written as textbook prose.

| File | What it is |
|---|---|
| `gametools-reference.html` | The source. Self-contained: one file, embedded CSS, inline SVG figures, no external assets. |
| `GameTools-Reference-Folder.pdf` | The built output — 97 pages, US Letter, ready to print. |
| `build-reference-pdf.mjs` | The build script. |
| `dist/reference-body.html` | A body-only copy of the source, emitted by the build for embedding elsewhere. Not tracked. |

## Building

```bash
node docs/reference/build-reference-pdf.mjs
```

Needs Node 18+ and Playwright's Chromium. The script resolves `playwright` from a local
`node_modules/` or from the global install, whichever it finds; if neither is present,
`npm i -g playwright && npx playwright install chromium`.

The build runs two passes. The first renders each chapter prefix on its own to count pages —
because every chapter begins on a forced page break, those counts give exact chapter start
pages — and writes them into the table of contents. The second pass renders the finished
document. The script fails with a warning if the two disagree, which is the signal that a
chapter has stopped starting on a fresh page.

## Printing and assembly

- **US Letter, single-sided, colour.** The left margin is 24&nbsp;mm — the binding edge — and no
  text sits within 20&nbsp;mm of it, so a three-hole punch clears everything.
- Appendix&nbsp;E holds printable divider tabs (one per chapter, colour-matched to the swatches in
  the contents) and two spine labels. Print that page on card stock.
- Prose is set to a narrow measure on purpose: the blank column on the right of every detail page
  is sized for a handwritten margin note.

Every chapter starts on a fresh page, so a single chapter can be reprinted and swapped without
disturbing the rest of the folder.

## Editing

Edit `gametools-reference.html` and rebuild. Conventions in the source:

- One `<section class="chapter" data-ch="chN">` per chapter, carrying its accent colour as inline
  `--accent` / `--accent-tint` custom properties. The `data-ch` value is what the contents'
  `<span class="pageref" data-pg="chN">` placeholders resolve against.
- `<div class="band">` opens the quick-reference layer; `<div class="band detail-band">` opens the
  prose layer, whose content is wrapped in `<div class="detail">` to get the narrow measure.
- Figures are hand-written inline SVG on a 680-unit-wide viewBox, with their styles in a local
  `<style>` block.
- Code samples inside a two-column grid must stay under about 48 characters per line or they wrap;
  full-width samples have room for about 105.

The folder is written against `master`. Where it and the source disagree, the source is right.
