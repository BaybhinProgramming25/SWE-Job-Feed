// Minimal, dependency-free Markdown -> HTML for resumes. The whole input is
// HTML-escaped first, so the only tags in the output are ones we emit — safe to
// drop into innerHTML without a sanitizer.

const esc = (s) =>
  s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

const safeHref = (url) => {
  const u = url.trim();
  return /^(https?:|mailto:)/i.test(u) ? u : '#';
};

// Inline formatting on already-escaped text.
const inline = (text) =>
  text
    .replace(/\[([^\]]+)\]\(([^)]+)\)/g,
      (_m, label, url) => `<a href="${esc(safeHref(url))}" target="_blank" rel="noreferrer">${label}</a>`)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/__([^_]+)__/g, '<strong>$1</strong>')
    .replace(/(^|[^*])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>');

// The model sometimes wraps its whole answer in a ```markdown … ``` fence.
// Strip an opening fence at the very start and its matching closing fence.
export function stripCodeFence(md) {
  let t = (md || '').trim();
  const open = t.match(/^```[^\n]*\n/);
  if (open) {
    t = t.slice(open[0].length).replace(/\n?```\s*$/, '');
  }
  return t;
}

// The header block (name + contact links) is centered and never uses the
// two-column row treatment.
function renderHeader(lines) {
  let html = '';
  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '');
    if (line.trim() === '') continue;
    let m;
    if ((m = line.match(/^(#{1,6})\s+(.*)$/))) {
      html += `<h${m[1].length}>${inline(m[2])}</h${m[1].length}>`;
    } else if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
      html += '<hr/>';
    } else {
      html += `<p>${inline(line)}</p>`;
    }
  }
  return html;
}

export function mdToHtml(md) {
  const allLines = esc(stripCodeFence(md)).split(/\r?\n/);
  // Everything before the first "## section" heading is the centered header.
  const firstSection = allLines.findIndex((l) => /^\s*##\s+/.test(l));
  const headerLines = firstSection > 0 ? allLines.slice(0, firstSection) : [];
  const lines = firstSection > 0 ? allLines.slice(firstSection) : allLines;

  let html = firstSection > 0 ? `<div class="rz-header">${renderHeader(headerLines)}</div>` : '';
  let listType = null; // 'ul' | 'ol'

  const closeList = () => {
    if (listType) {
      html += `</${listType}>`;
      listType = null;
    }
  };

  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '');
    if (line.trim() === '') {
      closeList();
      continue;
    }

    let m;
    if ((m = line.match(/^(#{1,6})\s+(.*)$/))) {
      closeList();
      const lvl = m[1].length;
      html += `<h${lvl}>${inline(m[2])}</h${lvl}>`;
    } else if (/^(-{3,}|\*{3,}|_{3,})$/.test(line.trim())) {
      closeList();
      html += '<hr/>';
    } else if ((m = line.match(/^\s*[-*+]\s+(.*)$/))) {
      if (listType !== 'ul') { closeList(); html += '<ul>'; listType = 'ul'; }
      html += `<li>${inline(m[1])}</li>`;
    } else if ((m = line.match(/^\s*\d+[.)]\s+(.*)$/))) {
      if (listType !== 'ol') { closeList(); html += '<ol>'; listType = 'ol'; }
      html += `<li>${inline(m[1])}</li>`;
    } else if ((m = line.match(/^(.+?)\s\|\s(.+)$/))) {
      // "left | right" → a two-column row: left-aligned, right-aligned.
      // Used for resume entries (Company | Dates, then Title | Location).
      closeList();
      html += `<div class="rz-row"><span class="rz-left">${inline(m[1].trim())}</span>` +
        `<span class="rz-right">${inline(m[2].trim())}</span></div>`;
    } else {
      closeList();
      html += `<p>${inline(line)}</p>`;
    }
  }
  closeList();
  return html;
}

// Print CSS scoped to a US-Letter page — the browser's "Save as PDF" produces a
// clean resume from this. The .sheet is exactly one printable page (letter minus
// the 0.6in margins); in one-page mode we clip to it and scale the content to
// fit, guaranteeing a single page.
const PRINT_CSS = `
  @page { size: letter; margin: 0.6in; }
  * { box-sizing: border-box; }
  html, body { margin: 0; padding: 0; }
  body { color: #111; font-family: Georgia, 'Times New Roman', serif; }
  .sheet { width: 7.3in; }
  .sheet--onepage { height: 9.8in; overflow: hidden; }
  /* Everything below is sized in em, so setting #content font-size scales the
     whole resume; width stays fixed so it always fills the page width. */
  #content { width: 7.3in; font-size: 11pt; line-height: 1.4; }
  #content h1 { font-size: 1.85em; margin: 0 0 0.12em; line-height: 1.15; }
  #content .rz-header { text-align: center; margin-bottom: 0.4em; }
  #content h2 { font-size: 1.18em; margin: 0.9em 0 0.35em; border-bottom: 1px solid #999; padding-bottom: 0.18em; text-transform: uppercase; letter-spacing: 0.04em; }
  #content h3 { font-size: 1.05em; margin: 0.55em 0 0.12em; }
  #content p { margin: 0.14em 0; }
  #content .rz-row { display: flex; justify-content: space-between; align-items: baseline; gap: 1em; margin: 0.14em 0; }
  #content .rz-right { text-align: right; white-space: nowrap; }
  #content ul, #content ol { margin: 0.14em 0 0.5em; padding-left: 1.4em; }
  #content li { margin: 0.07em 0; }
  #content a { color: #111; text-decoration: none; }
  #content hr { border: none; border-top: 1px solid #ccc; margin: 0.55em 0; }
  #content code { font-family: monospace; }
`;

// Render the resume into a hidden iframe and open the print dialog (Save as PDF).
// The iframe avoids popup blockers that a new window would hit. With
// `onePage: true`, the content is shrunk to fit a single letter page.
export function printResume(md, title, { onePage = false } = {}) {
  const body = mdToHtml(md);
  const iframe = document.createElement('iframe');
  Object.assign(iframe.style, {
    position: 'fixed', right: '0', bottom: '0', width: '0', height: '0', border: '0',
  });
  document.body.appendChild(iframe);

  const sheetClass = onePage ? 'sheet sheet--onepage' : 'sheet';
  const doc = iframe.contentWindow.document;
  doc.open();
  doc.write(
    `<!doctype html><html><head><meta charset="utf-8"><title>${esc(title || 'resume')}</title>` +
    `<style>${PRINT_CSS}</style></head><body>` +
    `<div class="${sheetClass}"><div id="content">${body}</div></div></body></html>`
  );
  doc.close();

  const win = iframe.contentWindow;
  setTimeout(() => {
    if (onePage) {
      const sheet = doc.querySelector('.sheet');
      const content = doc.getElementById('content');
      const avail = sheet.clientHeight; // one printable page, in px

      // Binary-search the largest font size (pt) whose content still fits on one
      // page. Growing the font fills the page vertically while the fixed width
      // keeps it filling the page horizontally too.
      let lo = 7;
      let hi = 16;
      let best = lo;
      for (let i = 0; i < 14; i++) {
        const mid = (lo + hi) / 2;
        content.style.fontSize = `${mid}pt`;
        if (content.scrollHeight <= avail) {
          best = mid;
          lo = mid;
        } else {
          hi = mid;
        }
      }
      content.style.fontSize = `${best}pt`;
    }
    win.focus();
    win.print();
    setTimeout(() => iframe.remove(), 1000);
  }, 200);
}
