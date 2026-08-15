import { useState, useEffect, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { Client } from '@stomp/stompjs';

import api from '../../api';
import { getToken, getUsername, clearAuth } from '../../auth';
import ResumePanel from './ResumePanel';
import './Dashboard.css';

const jobKey = (job) => job.url || `${job.company}|${job.title}`;

// `posted` comes straight from each ATS and the format varies: ISO strings
// (greenhouse/ashby), epoch millis/seconds (lever), relative text like
// "Posted Today" / "Posted 2 Days Ago" (workday), or empty.
const DAY_MS = 24 * 60 * 60 * 1000;

const parsePostedMs = (posted) => {
  if (!posted) return NaN;
  if (/^\d{13,}$/.test(posted)) return Number(posted);
  if (/^\d{10}$/.test(posted)) return Number(posted) * 1000;
  const lower = posted.toLowerCase();
  if (lower.includes('today')) return Date.now();
  if (lower.includes('yesterday')) return Date.now() - DAY_MS;
  const rel = lower.match(/(\d+)\+? days? ago/);
  if (rel) return Date.now() - Number(rel[1]) * DAY_MS;
  return Date.parse(posted);
};

const formatPosted = (posted) => {
  if (!posted) return null;
  const ms = parsePostedMs(posted);
  if (Number.isNaN(ms)) return posted; // unparseable — show the raw value
  return new Date(ms).toLocaleDateString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
  });
};

// Seniority is inferred from the job title, checked most-specific first so
// "Senior Software Engineer II" lands on Senior, not II.
const LEVEL_ORDER = [
  'Intern', 'Entry / Junior', 'I', 'II', 'III',
  'Senior', 'Staff', 'Principal', 'Lead / Manager', 'Other',
];

const levelOf = (title) => {
  const t = (title || '').toLowerCase();
  if (/\bintern(ship)?\b/.test(t)) return 'Intern';
  if (/\bprincipal\b/.test(t)) return 'Principal';
  if (/\bstaff\b/.test(t)) return 'Staff';
  if (/\b(lead|manager|head|director|vp)\b/.test(t)) return 'Lead / Manager';
  if (/\b(senior|sr)\b/.test(t)) return 'Senior';
  if (/\b(iii|3)\b/.test(t)) return 'III';
  if (/\b(ii|2)\b/.test(t)) return 'II';
  if (/\b(i|1)\b/.test(t)) return 'I';
  if (/\b(junior|jr|entry|graduate|new grad|early career)\b/.test(t)) return 'Entry / Junior';
  return 'Other';
};

const US_STATES = new Set([
  'al','ak','az','ar','ca','co','ct','de','fl','ga','hi','id','il','in','ia',
  'ks','ky','la','me','md','ma','mi','mn','ms','mo','mt','ne','nv','nh','nj',
  'nm','ny','nc','nd','oh','ok','or','pa','ri','sc','sd','tn','tx','ut','vt',
  'va','wa','wv','wi','wy','dc',
]);

const COUNTRY_ALIASES = {
  'us': 'United States', 'usa': 'United States', 'u.s.': 'United States',
  'usa only': 'United States', 'united states': 'United States',
  'united states of america': 'United States',
  'uk': 'United Kingdom', 'united kingdom': 'United Kingdom', 'england': 'United Kingdom',
  'deutschland': 'Germany', 'the netherlands': 'Netherlands', 'holland': 'Netherlands',
};

const titleCase = (s) => s.replace(/\S+/g, (w) => w.charAt(0).toUpperCase() + w.slice(1));

// Location strings are free text and vary wildly by ATS ("San Francisco, CA",
// "Berlin, Germany", "Remote", "USA Only", "2 Locations"). Best effort: scan
// comma/slash segments right-to-left for a known country or US state, fall
// back to Remote, then to the last segment as-is.
const countryOf = (location) => {
  const loc = (location || '').trim();
  if (!loc) return 'Unspecified';
  const segs = loc.split(/[,/|]| - /).map((s) => s.trim().toLowerCase()).filter(Boolean);
  for (let i = segs.length - 1; i >= 0; i--) {
    if (COUNTRY_ALIASES[segs[i]]) return COUNTRY_ALIASES[segs[i]];
    if (US_STATES.has(segs[i])) return 'United States';
  }
  if (!segs.length) return 'Unspecified';
  const last = segs[segs.length - 1];
  if (/remote|worldwide|anywhere|global/.test(last)) return 'Remote';
  return titleCase(last);
};

const sortKeys = {
  found: (job) => (job.firstSeen ? Date.parse(job.firstSeen) || 0 : 0),
  posted: (job) => {
    const ms = parsePostedMs(job.posted);
    return Number.isNaN(ms) ? 0 : ms;
  },
};

const formatFound = (iso) => {
  if (!iso) return null;
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return null;
  return d.toLocaleString(undefined, {
    year: 'numeric', month: 'short', day: 'numeric',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
};

const Dashboard = () => {
  const navigate = useNavigate();
  const username = getUsername();

  const [jobs, setJobs] = useState([]);
  const [connected, setConnected] = useState(false);
  const [filterAts, setFilterAts] = useState(null);          // null = all ATSes
  const [filterLevel, setFilterLevel] = useState('');        // '' = all levels
  const [filterCountry, setFilterCountry] = useState('');    // '' = all locations
  const [sortBy, setSortBy] = useState('found');    // 'found' | 'posted', newest first
  const [resume, setResume] = useState(null);
  const [resumeOpen, setResumeOpen] = useState(false);
  const [tailoring, setTailoring] = useState(null); // { job, status, result?, error? }

  // Initial snapshot over REST
  useEffect(() => {
    api.get('/api/dashboard')
      .then((res) => setJobs(res.data))
      .catch(() => {});

    // A 204 (no résumé yet) resolves with empty data — leave resume null.
    api.get('/api/resume')
      .then((res) => setResume(res.data || null))
      .catch(() => {});
  }, []);

  const handleTailor = (job) => {
    setResumeOpen(true);
    if (!resume) {
      setTailoring(null);
      return; // panel shows the import form
    }
    setTailoring({ job, status: 'loading' });
    api.post('/api/resume/tailor', {
      company: job.company, ats: job.ats, title: job.title,
      location: job.location, department: job.department, url: job.url,
    })
      .then((res) => setTailoring({ job, status: 'done', result: res.data }))
      .catch((err) => setTailoring({
        job, status: 'error',
        error: err?.response?.data?.message || 'Failed to tailor your résumé',
      }));
  };

  // Live updates over STOMP/WebSocket
  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      connectHeaders: { Authorization: `Bearer ${getToken()}` },
      reconnectDelay: 5000,
      onConnect: () => {
        setConnected(true);
        client.subscribe('/user/queue/jobs', (msg) => {
          // Live events carry no firstSeen — stamp the moment it reached the dashboard
          const job = { ...JSON.parse(msg.body), live: true, firstSeen: new Date().toISOString() };
          setJobs((prev) => [job, ...prev.filter((j) => jobKey(j) !== jobKey(job))]);
        });
      },
      onWebSocketClose: () => setConnected(false),
    });

    client.activate();
    return () => { client.deactivate(); };
  }, []);

  // Dropdown options come from the jobs actually on the dashboard,
  // so you never pick a filter that matches nothing.
  const levelOptions = useMemo(() => {
    const present = new Set(jobs.map((j) => levelOf(j.title)));
    return LEVEL_ORDER.filter((l) => present.has(l));
  }, [jobs]);

  const countryOptions = useMemo(
    () => [...new Set(jobs.map((j) => countryOf(j.location)))].sort(),
    [jobs]
  );

  // ATS filter options come from the ATSes actually present in the feed.
  const atsOptions = useMemo(
    () => [...new Set(jobs.map((j) => j.ats).filter(Boolean))].sort(),
    [jobs]
  );

  const visibleJobs = useMemo(() => {
    const filtered = jobs.filter((j) =>
      (!filterAts || j.ats === filterAts) &&
      (!filterLevel || levelOf(j.title) === filterLevel) &&
      (!filterCountry || countryOf(j.location) === filterCountry)
    );
    const key = sortKeys[sortBy];
    return [...filtered].sort((a, b) => key(b) - key(a));
  }, [jobs, filterAts, filterLevel, filterCountry, sortBy]);

  const handleLogout = () => {
    clearAuth();
    navigate('/login');
  };

  return (
    <div className='dashboard'>
      <aside className='dashboard-sidebar'>
        <div className='dashboard-sidebar-top'>
          <p className='sidebar-tagline'>
            US software-engineering roles from across the web, updated live as they’re posted.
          </p>
        </div>

        <div className='dashboard-sidebar-bottom'>
          <div className='sidebar-user'>
            <span className='sidebar-avatar'>{(username || '?').charAt(0).toUpperCase()}</span>
            <span className='sidebar-username'>{username}</span>
          </div>
          <button className='sidebar-logout' onClick={handleLogout}>Log out</button>
        </div>
      </aside>

      <div className='dashboard-feed'>
        <header className='feed-header'>
          <h1 className='feed-title'>Job Feed</h1>
          <div className='feed-header-right'>
            <button
              className={resumeOpen ? 'resume-toggle resume-toggle--active' : 'resume-toggle'}
              onClick={() => setResumeOpen((v) => !v)}
            >
              {resume ? 'Résumé' : 'Add résumé'}
            </button>
            <span className={connected ? 'feed-status feed-status--live' : 'feed-status'}>
              <span className='feed-status-dot' />
              {connected ? 'Live' : 'Connecting...'}
            </span>
          </div>
        </header>

        <div className='feed-toolbar'>
          {atsOptions.length > 0 && (
            <div className='feed-filters'>
              <button
                className={filterAts === null ? 'filter-chip filter-chip--active' : 'filter-chip'}
                onClick={() => setFilterAts(null)}
              >
                All
              </button>
              {atsOptions.map((name) => (
                <button
                  key={name}
                  className={filterAts === name ? 'filter-chip filter-chip--active' : 'filter-chip'}
                  onClick={() => setFilterAts(filterAts === name ? null : name)}
                >
                  {name}
                </button>
              ))}
            </div>
          )}
          <div className='feed-sort'>
            <label className='feed-select-wrap'>
              <span className='feed-sort-label'>Level</span>
              <select
                className='feed-select'
                value={filterLevel}
                onChange={(e) => setFilterLevel(e.target.value)}
              >
                <option value=''>All</option>
                {levelOptions.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
            </label>
            <label className='feed-select-wrap'>
              <span className='feed-sort-label'>Location</span>
              <select
                className='feed-select'
                value={filterCountry}
                onChange={(e) => setFilterCountry(e.target.value)}
              >
                <option value=''>All</option>
                {countryOptions.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </label>
            <span className='feed-sort-label'>Sort by</span>
            <button
              className={sortBy === 'found' ? 'filter-chip filter-chip--active' : 'filter-chip'}
              onClick={() => setSortBy('found')}
            >
              Newest found
            </button>
            <button
              className={sortBy === 'posted' ? 'filter-chip filter-chip--active' : 'filter-chip'}
              onClick={() => setSortBy('posted')}
            >
              Newest posted
            </button>
          </div>
        </div>

        {visibleJobs.length === 0 ? (
          <div className='feed-empty'>
            <p>No jobs yet.</p>
            <p>New US software-engineering roles will appear here the moment a worker finds them.</p>
          </div>
        ) : (
          <ul className='feed-list'>
            {visibleJobs.map((job) => (
              <li key={jobKey(job)} className={job.live ? 'job-card job-card--live' : 'job-card'}>
                <div className='job-card-main'>
                  <a className='job-title' href={job.url} target='_blank' rel='noreferrer'>
                    {job.title || 'Untitled role'}
                  </a>
                  <p className='job-meta'>
                    <span className='job-company'>{job.company}</span>
                    {job.location && <span> · {job.location}</span>}
                    {job.department && <span> · {job.department}</span>}
                  </p>
                </div>
                <div className='job-card-side'>
                  <button
                    className='job-tailor-btn'
                    onClick={() => handleTailor(job)}
                    title='Score and tailor your résumé to this job'
                  >
                    Tailor
                  </button>
                  <div className='job-badges'>
                    {job.live && <span className='job-new-badge'>NEW</span>}
                    {job.ats && <span className='job-ats-badge'>{job.ats}</span>}
                  </div>
                  <div className='job-dates'>
                    {formatPosted(job.posted) && (
                      <span className='job-time'>Posted: {formatPosted(job.posted)}</span>
                    )}
                    {formatFound(job.firstSeen) && (
                      <span className='job-time'>Found: {formatFound(job.firstSeen)}</span>
                    )}
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {resumeOpen && (
        <ResumePanel
          resume={resume}
          onSaved={setResume}
          tailoring={tailoring}
          onClose={() => setResumeOpen(false)}
        />
      )}
    </div>
  );
};

export default Dashboard;
