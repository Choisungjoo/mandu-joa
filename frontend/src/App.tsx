import { useCallback, useEffect, useState, type ReactNode } from 'react';
import type { DatabaseStatus, Health, Migrations, Retention, Sync, TableStat } from './types';
import { formatBytes, formatDate, formatNumber, formatRelative } from './format';

const API_URL = '/api/status/database';
const REFRESH_MS = 15_000;
const AUTO_REFRESH_KEY = 'dbStatus.autoRefresh';

type LoadState =
  | { kind: 'loading' }
  | { kind: 'error'; message: string }
  | { kind: 'ready'; status: DatabaseStatus };

function readAutoRefresh(): boolean {
  try {
    return localStorage.getItem(AUTO_REFRESH_KEY) !== 'false';
  } catch {
    return true;
  }
}

export function App() {
  const [state, setState] = useState<LoadState>({ kind: 'loading' });
  const [refreshing, setRefreshing] = useState(false);
  const [autoRefresh, setAutoRefresh] = useState(readAutoRefresh);

  const load = useCallback(async () => {
    setRefreshing(true);
    try {
      const res = await fetch(API_URL, { headers: { Accept: 'application/json' }, cache: 'no-store' });
      // 503 still carries a status body describing the outage.
      if (!res.ok && res.status !== 503) throw new Error(`The app answered HTTP ${res.status}`);
      setState({ kind: 'ready', status: (await res.json()) as DatabaseStatus });
    } catch (e) {
      setState({ kind: 'error', message: e instanceof Error ? e.message : String(e) });
    } finally {
      setRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  useEffect(() => {
    try {
      localStorage.setItem(AUTO_REFRESH_KEY, String(autoRefresh));
    } catch {
      /* storage unavailable: setting just won't persist */
    }
    if (!autoRefresh) return;
    const id = window.setInterval(() => void load(), REFRESH_MS);
    return () => window.clearInterval(id);
  }, [autoRefresh, load]);

  const status = state.kind === 'ready' ? state.status : null;

  return (
    <main className="page">
      <header className="header">
        <div>
          <div className="tag">Spring Boot + PostgreSQL</div>
          <h1>Database status</h1>
          <p className="muted">
            {status ? <>Checked {formatDate(status.checkedAt)}</> : 'Checking…'}
          </p>
        </div>
        <div className="controls">
          {status && <HealthBadge health={status.health} />}
          <label className="toggle">
            <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} />
            Auto-refresh every {REFRESH_MS / 1000}s
          </label>
          <button type="button" onClick={() => void load()} disabled={refreshing}>
            {refreshing ? 'Refreshing…' : 'Refresh'}
          </button>
        </div>
      </header>

      {state.kind === 'loading' && <p className="muted">Loading status…</p>}

      {state.kind === 'error' && (
        <div className="banner banner-down">
          <strong>Could not load the status.</strong> {state.message}. Is the app running?
        </div>
      )}

      {status && (
        <>
          {status.warnings.length > 0 && (
            <div className={`banner ${status.health === 'DOWN' ? 'banner-down' : 'banner-warn'}`}>
              <ul>
                {status.warnings.map((w) => <li key={w}>{w}</li>)}
              </ul>
            </div>
          )}

          <div className="grid">
            <ConnectionCard status={status} />
            {status.migrations && <MigrationsCard migrations={status.migrations} />}
            <RetentionCard retention={status.retention} />
            {status.sync && <SyncCard sync={status.sync} />}
            {status.tables.length > 0 && <TablesCard tables={status.tables} />}
          </div>
        </>
      )}
    </main>
  );
}

function HealthBadge({ health }: { health: Health }) {
  const label = { OK: 'Healthy', WARNING: 'Needs attention', DOWN: 'Down' }[health];
  return <span className={`badge badge-${health.toLowerCase()}`}>{label}</span>;
}

function Card({ title, wide, children }: { title: string; wide?: boolean; children: ReactNode }) {
  return (
    <section className={`card${wide ? ' card-wide' : ''}`}>
      <h2>{title}</h2>
      {children}
    </section>
  );
}

function Stats({ items }: { items: [label: string, value: ReactNode][] }) {
  return (
    <dl className="stats">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{value}</dd>
        </div>
      ))}
    </dl>
  );
}

function ConnectionCard({ status }: { status: DatabaseStatus }) {
  const c = status.connection;
  if (!c.up) {
    return (
      <Card title="Connection">
        <p className="error-text">{c.error ?? 'Unreachable'}</p>
      </Card>
    );
  }
  const pool = c.pool;
  return (
    <Card title="Connection">
      <Stats
        items={[
          ['Database', c.databaseName],
          ['PostgreSQL', c.serverVersion],
          ['Size', formatBytes(c.databaseSizeBytes)],
          ['Query latency', c.latencyMs == null ? '—' : `${c.latencyMs} ms`],
          ['Server started', <span title={formatDate(c.serverStartedAt)}>{formatRelative(c.serverStartedAt)}</span>],
        ]}
      />
      {pool && (
        <div className="pool">
          <div className="pool-label">
            <span>Connection pool</span>
            <span className="muted">
              {pool.active} active · {pool.idle} idle · {pool.max} max
              {pool.waiting > 0 && <> · <strong>{pool.waiting} waiting</strong></>}
            </span>
          </div>
          <div className="meter" role="meter" aria-valuemin={0} aria-valuemax={pool.max} aria-valuenow={pool.active}>
            <div className="meter-active" style={{ width: `${(pool.active / pool.max) * 100}%` }} />
            <div className="meter-idle" style={{ width: `${(pool.idle / pool.max) * 100}%` }} />
          </div>
        </div>
      )}
    </Card>
  );
}

function MigrationsCard({ migrations: m }: { migrations: Migrations }) {
  return (
    <Card title="Migrations (Flyway)">
      {m.error ? (
        <p className="error-text">{m.error}</p>
      ) : (
        <>
          <Stats
            items={[
              ['Schema version', m.currentVersion ? `v${m.currentVersion}` : 'empty'],
              ['Applied', m.applied],
              ['Pending', <span className={m.pending ? 'warn-text' : ''}>{m.pending}</span>],
              ['Failed', <span className={m.failed ? 'error-text' : ''}>{m.failed}</span>],
            ]}
          />
          <div className="table-wrap">
            <table>
              <thead>
                <tr><th>Version</th><th>Description</th><th>State</th><th>Installed</th></tr>
              </thead>
              <tbody>
                {m.items.map((it) => (
                  <tr key={`${it.version}-${it.description}`}>
                    <td className="mono">{it.version ?? '—'}</td>
                    <td>{it.description}</td>
                    <td><span className={`pill pill-${stateTone(it.state)}`}>{it.state}</span></td>
                    <td title={formatDate(it.installedOn)}>{formatRelative(it.installedOn)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </Card>
  );
}

function stateTone(state: string): 'ok' | 'warn' | 'bad' {
  const s = state.toLowerCase();
  if (s.includes('fail')) return 'bad';
  if (s === 'success' || s === 'baseline') return 'ok';
  return 'warn';
}

function RetentionCard({ retention: r }: { retention: Retention }) {
  const lastRun = r.lastRunError
    ? <span className="error-text">failed {formatRelative(r.lastRunAt)}</span>
    : r.lastRunAt
      ? <span title={formatDate(r.lastRunAt)}>{formatRelative(r.lastRunAt)}</span>
      : <span className="muted">not since app start</span>;
  return (
    <Card title="Retention">
      <Stats
        items={[
          ['Scheduled', r.enabled ? 'On' : <span className="warn-text">Off</span>],
          ['Next run', <span title={`cron: ${r.cron}`}>{formatRelative(r.nextRunAt)}</span>],
          ['Last run', lastRun],
          ['Logs kept', `errors ${r.syncErrorDays}d · runs ${r.syncRunDays}d · audit ${r.auditEventDays}d`],
        ]}
      />
      {r.lastRunCounts && (
        <p className="muted small">
          Last run purged or deleted:{' '}
          {Object.entries(r.lastRunCounts).map(([k, v]) => `${k} ${v}`).join(' · ')}
        </p>
      )}
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Provider</th><th>Keep content</th><th className="num">Held</th><th className="num">Purged</th><th className="num">Past limit</th></tr>
          </thead>
          <tbody>
            {r.providers.map((p) => (
              <tr key={p.code}>
                <td>{p.displayName}</td>
                <td>{p.contentRetentionDays ? `${p.contentRetentionDays} days` : 'no limit'}</td>
                <td className="num">{formatNumber(p.liveRecords)}</td>
                <td className="num">{formatNumber(p.purgedRecords)}</td>
                <td className={`num${p.dueForPurge ? ' warn-text' : ''}`}>{formatNumber(p.dueForPurge)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted small">“Past limit” rows are cleared on the next retention run.</p>
    </Card>
  );
}

function SyncCard({ sync: s }: { sync: Sync }) {
  const runs = s.runsLast24h;
  return (
    <Card title="Synchronization">
      <Stats
        items={[
          ['Polling configs', `${s.enabledConfigs} enabled of ${s.pollingConfigs}`],
          ['Due now', s.dueNow],
          ['Failing', <span className={s.failingConfigs ? 'error-text' : ''}>{s.failingConfigs}</span>],
          ['Errors (24 h)', <span className={s.errorsLast24h ? 'warn-text' : ''}>{s.errorsLast24h}</span>],
        ]}
      />
      <div className="runs">
        <span className="muted small">Runs in the last 24 h</span>
        <div className="run-pills">
          <span className="pill pill-ok">{runs.SUCCEEDED ?? 0} succeeded</span>
          <span className="pill pill-warn">{runs.PARTIAL ?? 0} partial</span>
          <span className="pill pill-bad">{runs.FAILED ?? 0} failed</span>
          <span className="pill">{runs.RUNNING ?? 0} running</span>
        </div>
      </div>
      {s.recentErrors.length > 0 ? (
        <ul className="errors">
          {s.recentErrors.map((e, i) => (
            <li key={i}>
              <div className="error-meta">
                <span className="mono">{e.errorCode ?? 'error'}{e.httpStatus ? ` · HTTP ${e.httpStatus}` : ''}</span>
                <span className="muted" title={formatDate(e.occurredAt)}>{formatRelative(e.occurredAt)}</span>
              </div>
              <div>{e.message}</div>
            </li>
          ))}
        </ul>
      ) : (
        <p className="muted small">No sync errors recorded.</p>
      )}
    </Card>
  );
}

function TablesCard({ tables }: { tables: TableStat[] }) {
  const totalBytes = tables.reduce((sum, t) => sum + t.totalBytes, 0);
  return (
    <Card title="Tables" wide>
      <div className="table-wrap">
        <table>
          <thead>
            <tr><th>Table</th><th className="num">Rows (est.)</th><th className="num">Size</th><th>Last analyzed</th></tr>
          </thead>
          <tbody>
            {tables.map((t) => (
              <tr key={t.name}>
                <td className="mono">{t.name}</td>
                <td className="num">{formatNumber(t.estimatedRows)}</td>
                <td className="num">{formatBytes(t.totalBytes)}</td>
                <td title={formatDate(t.lastAnalyzedAt)}>{formatRelative(t.lastAnalyzedAt)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr><td>{tables.length} tables</td><td /><td className="num">{formatBytes(totalBytes)}</td><td /></tr>
          </tfoot>
        </table>
      </div>
    </Card>
  );
}
