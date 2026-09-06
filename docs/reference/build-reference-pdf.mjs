// Renders docs/reference/gametools-reference.html to a print-ready PDF.
//
//   node docs/reference/build-reference-pdf.mjs
//
// Two passes. The first renders each chapter on its own to count how many pages it
// occupies; because every chapter begins with a forced page break, those counts sum
// exactly to the chapter start pages of the whole document, which are written into the
// table of contents. The second pass renders the finished document.
//
// Requires Playwright's Chromium. In this repository's dev container that is already
// installed at PLAYWRIGHT_BROWSERS_PATH; elsewhere run `npx playwright install chromium`.

import { createRequire } from 'node:module';
import { execSync } from 'node:child_process';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { dirname, join } from 'node:path';
import { readFile, writeFile, mkdir } from 'node:fs/promises';

// Playwright may be a local dependency or, as in this repository's dev container, a global
// install. Resolve it either way rather than forcing a node_modules/ into a Gradle project.
const require = createRequire(import.meta.url);
const { chromium } = (() => {
  try { return require('playwright'); }
  catch { return require(join(execSync('npm root -g').toString().trim(), 'playwright')); }
})();

const here = dirname(fileURLToPath(import.meta.url));
const source = join(here, 'gametools-reference.html');
const output = join(here, 'GameTools-Reference-Folder.pdf');

/** Page box. The left margin is the binding edge and is deliberately the widest. */
const MARGIN = { top: '15mm', bottom: '15mm', left: '24mm', right: '16mm' };

const FOOTER = `
<div style="font-family:Helvetica,Arial,sans-serif;font-size:7pt;color:#8a929c;
            width:100%;padding:0 16mm 0 24mm;display:flex;justify-content:space-between;">
  <span>GameTools&nbsp;&middot;&nbsp;Reference Folder&nbsp;&middot;&nbsp;v3.1.0</span>
  <span class="pageNumber"></span>
</div>`;

const EMPTY = '<div></div>';

const pdfOptions = {
  format: 'Letter',
  printBackground: true,
  displayHeaderFooter: true,
  headerTemplate: EMPTY,
  footerTemplate: FOOTER,
  margin: MARGIN,
};

/** Counts `/Type /Page` objects in a PDF buffer, discounting the `/Pages` tree nodes. */
const pageCount = (buffer) =>
  (buffer.toString('latin1').match(/\/Type\s*\/Page(?![s])/g) ?? []).length;

const browser = await chromium.launch();
const page = await browser.newPage();
await page.goto(pathToFileURL(source).href, { waitUntil: 'load' });

// ---- pass one: how long is each chapter, and everything before it? ---------------
const chapters = await page.$$eval('section.chapter[data-ch]', (sections) =>
  sections.map((section) => section.dataset.ch));

const measure = async (visible) => {
  const style = await page.addStyleTag({
    content: `@media print{ section{display:none !important} ${visible}{display:block !important} }`,
  });
  const pages = pageCount(await page.pdf(pdfOptions));
  await page.evaluate((element) => element.remove(), style);
  return pages;
};

// The start page of a chapter is one past the length of everything in front of it. Measuring
// prefixes rather than chapters individually keeps the arithmetic exact even where a chapter's
// own pagination depends on what precedes it.
const FRONT = '.cover, .frontpage';
const startPages = {};
let before = await measure(FRONT);
const frontMatter = before;
const lengths = [];
for (let index = 0; index < chapters.length; index++) {
  startPages[chapters[index]] = before + 1;
  const upToHere = [FRONT, ...chapters.slice(0, index + 1).map((c) => `section[data-ch="${c}"]`)].join(', ');
  const after = await measure(upToHere);
  lengths.push(after - before);
  before = after;
}

// ---- write the numbers into the contents ----------------------------------------
await page.evaluate((pages) => {
  for (const reference of document.querySelectorAll('.pageref')) {
    reference.textContent = pages[reference.dataset.pg] ?? '—';
  }
}, startPages);

// ---- pass two: the document itself ----------------------------------------------
const pdf = await page.pdf({ ...pdfOptions, path: output });
const total = pageCount(pdf);

// ---- a body-only copy, for publishing the same source as a web page --------------
const html = await readFile(source, 'utf8');
const body = html.slice(html.indexOf('<body>') + 6, html.lastIndexOf('</body>'));
const head = html.slice(html.indexOf('<title>'), html.indexOf('</head>'));
await mkdir(join(here, 'dist'), { recursive: true });
await writeFile(join(here, 'dist', 'reference-body.html'), `${head}\n${body}\n`, 'utf8');

await browser.close();

const predicted = before;
console.log(`front matter        ${String(frontMatter).padStart(3)} page(s)`);
chapters.forEach((chapter, index) =>
  console.log(`${chapter.padEnd(20)}${String(lengths[index]).padStart(3)} page(s)   starts on p.${startPages[chapter]}`));
console.log(`\n${output}\n${total} pages`);
if (total !== predicted) {
  console.warn(`WARNING: contents predicted ${predicted} pages but the document rendered ${total}. ` +
               `A chapter is not starting on a fresh page.`);
  process.exitCode = 1;
}
