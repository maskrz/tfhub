import { formatRestDuration, parseRestDuration } from './training-rest-time';

describe('training rest duration', () => {
  it.each([
    ['5', 300],
    ['1,5', 90],
    ['45s', 45],
    ['0', 0],
  ])('parses %s as %i seconds', (input, expected) => {
    expect(parseRestDuration(input)).toBe(expected);
  });

  it.each(['1.5', '-1', '1,001s', '35791395', '5 min'])(
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
});
