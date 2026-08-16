import { useMemo, useState } from 'react';

import api from '../../api';

const STATUSES = [
  { key: 'applied', label: 'Applied' },
  { key: 'interviewing', label: 'Interviewing' },
  { key: 'offer', label: 'Offer' },
  { key: 'rejected', label: 'Rejected' },
];

const fmtDate = (iso) => {
  if (!iso) return '';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString();
};

const isToday = (iso) => {
  const d = new Date(iso);
  return !Number.isNaN(d.getTime()) && d.toDateString() === new Date().toDateString();
};

const emptyForm = { company: '', title: '', url: '', status: 'applied' };

// `apps` is owned by Dashboard so the feed's "Tracked" state stays in sync;
// `reload` refetches it after any change here.
const Tracker = ({ apps, reload }) => {
  const [filter, setFilter] = useState(null);   // null = all, else a status key
  const [error, setError] = useState(null);
  const [adding, setAdding] = useState(false);
  const [form, setForm] = useState(emptyForm);

  const stats = useMemo(() => ({
    totalLifetime: apps.length,
    appliedToday: apps.filter((a) => isToday(a.appliedAt)).length,
    interviewing: apps.filter((a) => a.status === 'interviewing').length,
    offers: apps.filter((a) => a.status === 'offer').length,
    rejected: apps.filter((a) => a.status === 'rejected').length,
  }), [apps]);

  const cards = [
    { key: null, label: 'Applied (lifetime)', value: stats.totalLifetime, tone: 'total' },
    { key: null, label: 'Applied today', value: stats.appliedToday, tone: 'today' },
    { key: 'interviewing', label: 'Interviewing', value: stats.interviewing, tone: 'interviewing' },
    { key: 'offer', label: 'Offers', value: stats.offers, tone: 'offer' },
    { key: 'rejected', label: 'Rejected', value: stats.rejected, tone: 'rejected' },
  ];

  const visible = useMemo(
    () => (filter ? apps.filter((a) => a.status === filter) : apps),
    [apps, filter]
  );

  const changeStatus = async (id, status) => {
    try {
      await api.patch(`/api/applications/${id}`, { status });
      await reload();
    } catch (err) {
      setError(err?.response?.data?.message || 'Could not update status');
    }
  };

  const remove = async (id) => {
    try {
      await api.delete(`/api/applications/${id}`);
      await reload();   // also untracks the job back in the feed
    } catch (err) {
      setError(err?.response?.data?.message || 'Could not remove that application');
    }
  };

  const submitAdd = async (e) => {
    e.preventDefault();
    if (!form.company.trim() && !form.title.trim()) return;
    try {
      await api.post('/api/applications', form);
      setForm(emptyForm);
      setAdding(false);
      await reload();
    } catch (err) {
      setError(err?.response?.data?.message || 'Could not add that application');
    }
  };

  return (
    <div className='tracker'>
      <div className='tracker-stats'>
        {cards.map((c) => (
          <button
            key={c.label}
            className={`stat-card stat-card--${c.tone} ${filter === c.key && c.key ? 'stat-card--active' : ''}`}
            onClick={() => c.key && setFilter(filter === c.key ? null : c.key)}
            disabled={!c.key}
            title={c.key ? 'Click to filter' : ''}
          >
            <span className='stat-value'>{c.value}</span>
            <span className='stat-label'>{c.label}</span>
          </button>
        ))}
      </div>

      <div className='tracker-toolbar'>
        <div className='tracker-filters'>
          <button
            className={filter === null ? 'filter-chip filter-chip--active' : 'filter-chip'}
            onClick={() => setFilter(null)}
          >
            All ({apps.length})
          </button>
          {STATUSES.map((s) => (
            <button
              key={s.key}
              className={filter === s.key ? 'filter-chip filter-chip--active' : 'filter-chip'}
              onClick={() => setFilter(filter === s.key ? null : s.key)}
            >
              {s.label}
            </button>
          ))}
        </div>
        <button className='resume-btn resume-btn--primary tracker-add-btn' onClick={() => setAdding((v) => !v)}>
          {adding ? 'Cancel' : '+ Add application'}
        </button>
      </div>

      {adding && (
        <form className='tracker-add-form' onSubmit={submitAdd}>
          <input
            className='tracker-input' placeholder='Company'
            value={form.company} onChange={(e) => setForm({ ...form, company: e.target.value })}
          />
          <input
            className='tracker-input' placeholder='Role / title'
            value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })}
          />
          <input
            className='tracker-input' placeholder='URL (optional)'
            value={form.url} onChange={(e) => setForm({ ...form, url: e.target.value })}
          />
          <select
            className='tracker-input' value={form.status}
            onChange={(e) => setForm({ ...form, status: e.target.value })}
          >
            {STATUSES.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
          </select>
          <button className='resume-btn resume-btn--primary' type='submit'>Save</button>
        </form>
      )}

      {error && <p className='resume-error'>{error}</p>}

      {visible.length === 0 ? (
        <div className='feed-empty'>
          <p>{filter ? 'Nothing in this stage yet.' : 'No applications tracked yet.'}</p>
          <p>Hit “Track” on any job in the feed, or add one manually above.</p>
        </div>
      ) : (
        <ul className='app-list'>
          {visible.map((a) => (
            <li key={a.id} className='app-row'>
              <div className='app-main'>
                {a.url ? (
                  <a className='app-title' href={a.url} target='_blank' rel='noreferrer'>
                    {a.title || 'Untitled role'}
                  </a>
                ) : (
                  <span className='app-title'>{a.title || 'Untitled role'}</span>
                )}
                <p className='app-meta'>
                  <span className='app-company'>{a.company || '—'}</span>
                  {a.location && <span> · {a.location}</span>}
                  <span className='app-date'> · applied {fmtDate(a.appliedAt)}</span>
                </p>
              </div>
              <div className='app-actions'>
                <select
                  className={`app-status app-status--${a.status}`}
                  value={a.status}
                  onChange={(e) => changeStatus(a.id, e.target.value)}
                >
                  {STATUSES.map((s) => <option key={s.key} value={s.key}>{s.label}</option>)}
                </select>
                <button className='app-delete' onClick={() => remove(a.id)} title='Remove'>✕</button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
};

export default Tracker;
