import { dakarDateTime, dakarTime, dakarToday } from './dakar';

describe('Dakar time', () => {
  it('uses UTC whatever the device time zone', () => {
    expect(dakarToday(new Date('2026-10-25T23:30:00Z'))).toBe('2026-10-25');
    expect(dakarTime('2026-10-25T07:00:00Z')).toBe('07:00');
    expect(dakarDateTime('2026-10-25T07:05:00Z')).toBe('25/10/2026 07:05');
  });

  it('shows nothing without a date', () => {
    expect(dakarTime(null)).toBe('');
    expect(dakarDateTime(undefined)).toBe('');
  });
});
