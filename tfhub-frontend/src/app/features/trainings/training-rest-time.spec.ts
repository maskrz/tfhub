import { formatRestDuration, formatRestMinutes, parseRestDuration } from './training-rest-time';

describe('training rest duration', () => {
  it.each([
    ['5', 300],
    ['1,5', 90],
    ['45s', 45],
    ['4m 10s', 250],
    ['4m', 240],
    ['4M10S', 250],
    ['0', 0],
  ])('parses %s as %i seconds', (input, expected) => {
    expect(parseRestDuration(input)).toBe(expected);
  });

  it.each(['1.5', '-1', '1,001s', '35791395', '5 min', '4m 60s', '35791394m 8s'])(
    'rejects invalid duration %s',
    (input) => {
      expect(parseRestDuration(input)).toBeNull();
    },
  );

  it('formats persisted durations as minutes when exact or seconds otherwise', () => {
    expect(formatRestDuration(null)).toBe('');
    expect(formatRestDuration(300)).toBe('5');
    expect(formatRestDuration(90)).toBe('90s');
  });

  it('formats minute and second values without losing precision', () => {
    expect(formatRestMinutes(null)).toBe('');
    expect(formatRestMinutes(0)).toBe('0m');
    expect(formatRestMinutes(45)).toBe('45s');
    expect(formatRestMinutes(240)).toBe('4m');
    expect(formatRestMinutes(250)).toBe('4m 10s');
    expect(parseRestDuration(formatRestMinutes(2147483647))).toBe(2147483647);
  });
});
