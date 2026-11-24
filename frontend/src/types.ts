// Mirrors com.example.helloworld.status.DatabaseStatus (served by /api/status/database).

export type Health = 'OK' | 'WARNING' | 'DOWN';

export interface Pool {
  active: number;
  idle: number;
  total: number;
  max: number;
  waiting: number;
}

export interface Connection {
  up: boolean;
  error: string | null;
  latencyMs: number | null;
  serverVersion: string | null;
  databaseName: string | null;
  databaseSizeBytes: number | null;
  serverStartedAt: string | null;
  pool: Pool | null;
}

export interface Migration {
  version: string | null;
  description: string;
  state: string;
  installedOn: string | null;
  executionTimeMs: number | null;
}

export interface Migrations {
  currentVersion: string | null;
  applied: number;
  pending: number;
  failed: number;
  items: Migration[];
  error: string | null;
}

export interface TableStat {
  name: string;
  estimatedRows: number;
  totalBytes: number;
  lastAnalyzedAt: string | null;
}

export interface ProviderRetention {
  code: string;
  displayName: string;
  contentRetentionDays: number | null;
  disconnectedPurgeDays: number;
  liveRecords: number;
  purgedRecords: number;
  dueForPurge: number;
}

export interface Retention {
  enabled: boolean;
  cron: string;
  nextRunAt: string | null;
  lastRunAt: string | null;
  lastRunCounts: Record<string, number> | null;
  lastRunError: string | null;
  providers: ProviderRetention[];
  syncRunDays: number;
  syncErrorDays: number;
  auditEventDays: number;
}

export interface RecentError {
  occurredAt: string;
  providerCode: string | null;
  errorCode: string | null;
  httpStatus: number | null;
  message: string;
}

export interface Sync {
  pollingConfigs: number;
  enabledConfigs: number;
  dueNow: number;
  failingConfigs: number;
  runsLast24h: Record<string, number>;
  errorsLast24h: number;
  recentErrors: RecentError[];
}

export interface DatabaseStatus {
  checkedAt: string;
  health: Health;
  warnings: string[];
  connection: Connection;
  migrations: Migrations | null;
  tables: TableStat[];
  retention: Retention;
  sync: Sync | null;
}
