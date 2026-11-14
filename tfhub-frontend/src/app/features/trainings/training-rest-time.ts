const MAX_REST_SECONDS = 2_147_483_647;

export function parseRestDuration(input: string): number | null {
  const value = input.trim();
  if (!value) return null;

  const mixed = /^(\d+)\s*m(?:\s*(\d+)\s*s)?$/i.exec(value);
  if (mixed) {
    const secondsPart = Number(mixed[2] ?? 0);
    const seconds = Number(mixed[1]) * 60 + secondsPart;
    return secondsPart < 60 && Number.isSafeInteger(seconds) && seconds <= MAX_REST_SECONDS
      ? seconds : null;
  }

  const match = /^(\d+(?:,\d+)?)(s)?$/i.exec(value);
  if (!match) return null;

  const amount = Number(match[1].replace(',', '.'));
  const seconds = match[2] ? amount : amount * 60;
  if (!Number.isSafeInteger(seconds) || seconds < 0 || seconds > MAX_REST_SECONDS) return null;
  return seconds;
}

export function formatRestMinutes(seconds: number | null): string {
  if (seconds === null) return '';
  const minutes = Math.floor(seconds / 60);
  const remainder = seconds % 60;
  if (minutes === 0 && remainder !== 0) return `${remainder}s`;
  return remainder === 0 ? `${minutes}m` : `${minutes}m ${remainder}s`;
}

export function formatRestDuration(seconds: number | null): string {
  if (seconds === null) return '';
  return seconds % 60 === 0 ? String(seconds / 60) : `${seconds}s`;
}
