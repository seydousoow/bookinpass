import { dakarToday, parseBusSearch, shiftDay } from './bus-search-params';

describe('bus search params', () => {
  it('reads a complete search', () => {
    expect(parseBusSearch({ from: '2253354', to: '2244322', date: '2026-10-26', passengers: '2' })).toEqual({
      from: '2253354',
      to: '2244322',
      date: '2026-10-26',
      passengers: 2,
    });
  });

  it('defaults to one passenger and accepts an airport', () => {
    expect(parseBusSearch({ from: '2253354', to: 'DSS', date: '2026-10-26' })?.passengers).toBe(1);
  });

  it('rejects missing, malformed or identical places and too many passengers', () => {
    expect(parseBusSearch({ from: '2253354', date: '2026-10-26' })).toBeNull();
    expect(parseBusSearch({ from: 'dakar', to: '2244322', date: '2026-10-26' })).toBeNull();
    expect(parseBusSearch({ from: '2253354', to: '2253354', date: '2026-10-26' })).toBeNull();
    expect(parseBusSearch({ from: '2253354', to: '2244322', date: '26/10/2026' })).toBeNull();
    expect(parseBusSearch({ from: '2253354', to: '2244322', date: '2026-10-26', passengers: '10' })).toBeNull();
  });

  it('uses the UTC day, Dakar time, whatever the browser time zone', () => {
    expect(dakarToday(new Date('2026-10-25T23:30:00Z'))).toBe('2026-10-25');
  });

  it('shifts a day across months', () => {
    expect(shiftDay('2026-10-31', 1)).toBe('2026-11-01');
    expect(shiftDay('2026-11-01', -1)).toBe('2026-10-31');
  });
});
