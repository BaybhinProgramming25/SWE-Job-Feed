import { useRef, useState } from 'react';

import api from '../../api';
import { mdToHtml, printResume, stripCodeFence } from './resumeDocument';

const ResumeDocument = ({ markdown }) => (
  <div className='resume-doc-rendered' dangerouslySetInnerHTML={{ __html: mdToHtml(markdown) }} />
);

// Score + rationale header, shared by the analysis and the final tailored view.
const ScoreBlock = ({ score, rationale }) => (
  <>
    <div className={`score-ring ${scoreClass(score)}`}>
      <span className='score-num'>{score}</span>
      <span className='score-den'>/100</span>
    </div>
    <p className='score-caption'>Match of your current resume to this posting</p>
    {rationale && <p className='tailor-rationale'>{rationale}</p>}
  </>
);

// Side-by-side requirement gap analysis: what the job wants vs. what you have.
const GapAnalysis = ({ requirements }) => {
  const reqs = requirements || [];
  const have = reqs.filter((r) => r.matched);
  const missing = reqs.filter((r) => !r.matched);

  const Column = ({ title, items, kind }) => (
    <div className={`gap-col gap-col--${kind}`}>
      <p className='gap-col-head'>
        {title} <span className='gap-count'>{items.length}</span>
      </p>
      {items.length === 0 ? (
        <p className='gap-empty'>—</p>
      ) : (
        <ul className='gap-list'>
          {items.map((r, i) => (
            <li key={i} className='gap-item' title={r.note || ''}>
              <span className='gap-skill'>{r.skill}</span>
              {r.category && <span className='gap-tag'>{r.category}</span>}
            </li>
          ))}
        </ul>
      )}
    </div>
  );

  return (
    <div className='gap-wrap'>
      <p className='gap-title'>Requirements breakdown</p>
      <div className='gap-cols'>
        <Column title='You have' items={have} kind='have' />
        <Column title='To fill' items={missing} kind='missing' />
      </div>
    </div>
  );
};

const errText = (err) =>
  err?.response?.data?.message ||
  err?.response?.data?.error ||
  err?.message ||
  'Something went wrong';

const scoreClass = (score) => {
  if (score >= 70) return 'score-ring--good';
  if (score >= 40) return 'score-ring--mid';
  return 'score-ring--low';
};

// Read a PDF as a data URI (backend strips the prefix) or text as plain string.
const readFile = (file) =>
  new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('Could not read that file'));
    const isPdf = file.type === 'application/pdf' || /\.pdf$/i.test(file.name);
    reader.onload = () =>
      resolve(isPdf ? { pdfBase64: reader.result, filename: file.name } : { content: reader.result, filename: file.name });
    if (isPdf) reader.readAsDataURL(file);
    else reader.readAsText(file);
  });

const MIN_WIDTH = 340;
const MAX_WIDTH = 1000;

const ResumePanel = ({ resume, onSaved, tailoring, onGenerate, onClose }) => {
  const fileRef = useRef(null);
  const [editing, setEditing] = useState(false);
  const [pasteText, setPasteText] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const [width, setWidth] = useState(() => {
    const saved = Number(localStorage.getItem('resumePanelWidth'));
    return saved >= MIN_WIDTH && saved <= MAX_WIDTH ? saved : 520;
  });
  const widthRef = useRef(width);

  // Drag the left edge to resize; the width is remembered across sessions.
  const startResize = (e) => {
    e.preventDefault();
    const startX = e.clientX;
    const startWidth = widthRef.current;
    document.body.style.userSelect = 'none';
    document.body.style.cursor = 'col-resize';

    const onMove = (ev) => {
      const next = Math.min(MAX_WIDTH, Math.max(MIN_WIDTH, startWidth + (startX - ev.clientX)));
      widthRef.current = next;
      setWidth(next);
    };
    const onUp = () => {
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('mouseup', onUp);
      document.body.style.userSelect = '';
      document.body.style.cursor = '';
      localStorage.setItem('resumePanelWidth', String(widthRef.current));
    };
    window.addEventListener('mousemove', onMove);
    window.addEventListener('mouseup', onUp);
  };

  const showImport = editing || !resume;

  const submit = async (body) => {
    setSaving(true);
    setError(null);
    try {
      const res = await api.post('/api/resume', body);
      onSaved(res.data);
      setEditing(false);
      setPasteText('');
    } catch (err) {
      setError(errText(err));
    } finally {
      setSaving(false);
    }
  };

  const onFile = async (e) => {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    try {
      submit(await readFile(file));
    } catch (err) {
      setError(err.message);
    }
  };

  const t = tailoring;

  return (
    <aside className='resume-panel' style={{ width, minWidth: width }}>
      <div
        className='resume-resize'
        onMouseDown={startResize}
        role='separator'
        aria-orientation='vertical'
        title='Drag to resize'
      />
      <header className='resume-panel-head'>
        <h2 className='resume-panel-title'>Resume</h2>
        <button className='resume-close' onClick={onClose} aria-label='Close resume panel'>
          ✕
        </button>
      </header>

      <div className='resume-panel-body'>
        {/* Tailored result for the selected job */}
        {t && (
          <section className='resume-card tailor-card'>
            <p className='resume-card-label'>Tailored for</p>
            <p className='tailor-job-title'>{t.job.title || 'this role'}</p>
            <p className='tailor-job-sub'>
              {t.job.company}
              {t.job.location ? ` · ${t.job.location}` : ''}
            </p>

            {t.status === 'analyzing' && (
              <p className='tailor-loading'>Analyzing the posting against your resume…</p>
            )}
            {t.status === 'error' && <p className='resume-error'>{t.error}</p>}

            {/* Phase 1 result: confidence score + requirement gap analysis. */}
            {t.analysis && (
              <>
                <ScoreBlock score={t.analysis.score} rationale={t.analysis.rationale} />
                <GapAnalysis requirements={t.analysis.requirements} />

                {/* The generate step is opt-in — shown until it's done. */}
                {t.status !== 'done' && (
                  <div className='tailor-actions'>
                    <button
                      className='resume-btn resume-btn--primary'
                      disabled={t.status === 'generating'}
                      onClick={() => onGenerate(t.job, t.analysis)}
                    >
                      {t.status === 'generating' ? 'Generating…' : 'Generate tailored resume'}
                    </button>
                  </div>
                )}
                {t.status === 'generating' && (
                  <p className='tailor-loading'>Tailoring your resume… this can take up to a minute.</p>
                )}
              </>
            )}

            {/* Phase 2 result: the tailored one-page resume. */}
            {t.status === 'done' && t.result && (
              <>
                <div className='tailor-actions'>
                  <button
                    className='resume-btn resume-btn--primary'
                    onClick={() => printResume(t.result.tailoredResume, `${t.job.title || 'resume'} - tailored`, { onePage: true })}
                  >
                    Download PDF
                  </button>
                  <button
                    className='resume-btn'
                    onClick={() => navigator.clipboard?.writeText(stripCodeFence(t.result.tailoredResume))}
                  >
                    Copy
                  </button>
                </div>
                <ResumeDocument markdown={t.result.tailoredResume} />
              </>
            )}
          </section>
        )}

        {/* Import / view the stored resume */}
        <section className='resume-card'>
          <div className='resume-card-head'>
            <p className='resume-card-label'>{showImport ? 'Import resume' : 'My resume'}</p>
            {resume && !editing && (
              <button className='resume-link' onClick={() => setEditing(true)}>
                Replace
              </button>
            )}
            {editing && resume && (
              <button className='resume-link' onClick={() => { setEditing(false); setError(null); }}>
                Cancel
              </button>
            )}
          </div>

          {showImport ? (
            <>
              <input
                ref={fileRef}
                type='file'
                accept='.pdf,.txt,.md,.markdown'
                onChange={onFile}
                hidden
              />
              <button
                className='resume-btn resume-btn--primary'
                onClick={() => fileRef.current?.click()}
                disabled={saving}
              >
                {saving ? 'Importing…' : 'Upload PDF / text'}
              </button>

              <p className='resume-or'>or paste it</p>
              <textarea
                className='resume-textarea'
                placeholder='Paste your resume (plain text or Markdown)…'
                value={pasteText}
                onChange={(e) => setPasteText(e.target.value)}
              />
              <button
                className='resume-btn'
                disabled={saving || !pasteText.trim()}
                onClick={() => submit({ content: pasteText, filename: 'pasted.md' })}
              >
                {saving ? 'Saving…' : 'Save pasted resume'}
              </button>
              {error && <p className='resume-error'>{error}</p>}
            </>
          ) : (
            <>
              <div className='resume-view-head'>
                {resume.filename && <p className='resume-filename'>{resume.filename}</p>}
                <button
                  className='resume-link'
                  onClick={() => printResume(resume.content, resume.filename || 'resume', { onePage: true })}
                >
                  Download PDF
                </button>
              </div>
              <ResumeDocument markdown={resume.content} />
            </>
          )}
        </section>
      </div>
    </aside>
  );
};

export default ResumePanel;
