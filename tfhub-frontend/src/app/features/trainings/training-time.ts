export function formatResult(seconds: number, precision: 2 | 3 = 2): string {
  const units = Math.round(seconds * 10 ** precision);
  const scale = 10 ** precision;
  const minutes = Math.floor(units / (60 * scale));
  const secondsWithinMinute = Math.floor((units % (60 * scale)) / scale);
  const fraction = units % scale;
  const secondsText = `${String(secondsWithinMinute).padStart(2, '0')},${String(fraction).padStart(precision, '0')}`;
  return minutes > 0 ? `${minutes}:${secondsText}` : secondsText;
}

export function normalizeTimeInput(input: string, precision: 2 | 3 = 2): string | null {
  let value = input.trim();
  if (!value) return '';
  if (value.toUpperCase() === 'X') return 'X';
  if (value === '-') return '-';

  value = value.replace(/;/g, ':');
  const separators = [...value.matchAll(/[,.]/g)];
  let singleDotFraction = false;
  if (separators.length >= 2) {
    const first = separators[0].index!;
    const second = separators[1].index!;
    value = `${value.slice(0, first)}:${value.slice(first + 1, second)},${value.slice(second + 1)}`;
  } else if (separators.length === 1) {
    const index = separators[0].index!;
    singleDotFraction = value[index] === '.';
    value = `${value.slice(0, index)},${value.slice(index + 1)}`;
  }

  const match = /^(?:(\d+):)?(\d+)(?:,(\d{1,3}))?$/.exec(value);
  if (!match) return null;
  const [, minutes, seconds, fraction] = match;
  if ((fraction && fraction.length > precision) || (minutes !== undefined && Number(seconds) > 59)) return null;
  const secondsText = minutes !== undefined ? seconds.padStart(2, '0') : seconds;
  const fractionText = fraction && fraction.length === 1 && !singleDotFraction ? `${fraction}0` : fraction;
  return `${minutes === undefined ? '' : `${minutes}:`}${secondsText}${fractionText ? `,${fractionText}` : ''}`;
}

export function parseTimeSeconds(input: string, precision: 2 | 3 = 2): number | null {
  const normalized = normalizeTimeInput(input, precision);
  if (!normalized || normalized === 'X' || normalized === '-') return null;
  const match = /^(?:(\d+):)?(\d+)(?:,(\d{1,3}))?$/.exec(normalized);
  if (!match) return null;
  const [, minutes, seconds, fraction] = match;
  return Number(minutes ?? 0) * 60 + Number(seconds) + Number(`0.${fraction ?? '0'}`);
}
