const dateTime = new Intl.DateTimeFormat(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
const integer = new Intl.NumberFormat();

export function formatDate(iso: string | null | undefined): string {
  return iso ? dateTime.format(new Date(iso)) : '—';
}

export function formatNumber(n: number | null | undefined): string {
  return n == null ? '—' : integer.format(n);
}

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null) return '—';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  let value = bytes;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value.toFixed(unit === 0 ? 0 : 1)} ${units[unit]}`;
}

/** "3 min ago" / "in 5 h" relative to now. */
export function formatRelative(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return '—';
  const diffSec = Math.round((new Date(iso).getTime() - now) / 1000);
  const abs = Math.abs(diffSec);
  const [value, unit] =
    abs < 60 ? [abs, 's'] :
    abs < 3600 ? [Math.round(abs / 60), 'min'] :
    abs < 86400 ? [Math.round(abs / 3600), 'h'] :
    [Math.round(abs / 86400), 'd'];
  return diffSec >= 0 ? `in ${value} ${unit}` : `${value} ${unit} ago`;
}
