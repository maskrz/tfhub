const MAX_REST_SECONDS = 2_147_483_647;

export function parseRestDuration(input: string): number | null {
  const value = input.trim();
  if (!value) return null;

  const match = /^(\d+(?:,\d+)?)(s)?$/i.exec(value);
  if (!match) return null;

  const amount = Number(match[1].replace(',', '.'));
  const seconds = match[2] ? amount : amount * 60;
  if (!Number.isSafeInteger(seconds) || seconds < 0 || seconds > MAX_REST_SECONDS) return null;
  return seconds;
}

export function formatRestDuration(seconds: number | null): string {
  if (seconds === null) return '';
  return seconds % 60 === 0 ? String(seconds / 60) : `${seconds}s`;
}
