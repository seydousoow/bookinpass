import { IStayRate, IStayResult, IStayRoom } from '@shared/models';
import { cancellationPolicy, findRate, roomsByPrice } from './rate.util';

const rate = (id: string, total: string, timeline: { before: string; refund_amount: string }[] = []): IStayRate =>
  ({ id, total_amount: total, cancellation_timeline: timeline.map((t) => ({ ...t, currency: 'EUR' })) }) as unknown as IStayRate;
const room = (name: string, rates: IStayRate[]): IStayRoom => ({ name, beds: null, photos: null, rates });

describe('cancellationPolicy', () => {
  const now = new Date('2026-11-01T10:00:00Z');

  it('sans frise : non remboursable', () => {
    expect(cancellationPolicy(rate('r', '300.00'), now)).toEqual({ kind: 'none' });
  });

  it('remboursement total jusqu’à la prochaine échéance', () => {
    const r = rate('r', '300.00', [{ before: '2026-11-05T12:00:00Z', refund_amount: '300.00' }]);
    expect(cancellationPolicy(r, now)).toEqual({ kind: 'free', until: '2026-11-05T12:00:00Z' });
  });

  it('échéance dépassée : on passe à la suivante', () => {
    const r = rate('r', '300.00', [
      { before: '2026-10-30T12:00:00Z', refund_amount: '300.00' },
      { before: '2026-11-05T12:00:00Z', refund_amount: '150.00' },
    ]);
    expect(cancellationPolicy(r, now)).toEqual({ kind: 'partial', until: '2026-11-05T12:00:00Z', amount: '150.00', currency: 'EUR' });
  });

  it('toutes les échéances dépassées : non remboursable', () => {
    expect(cancellationPolicy(rate('r', '300.00', [{ before: '2026-10-30T12:00:00Z', refund_amount: '300.00' }]), now)).toEqual({ kind: 'none' });
  });
});

describe('chambres et tarifs', () => {
  const rooms = [room('Suite', [rate('s1', '500.00')]), room('Double', [rate('d1', '200.00'), rate('d2', '150.00')])];

  it('trie les chambres par leur tarif le moins cher', () => {
    expect(roomsByPrice(rooms).map((r) => r.name)).toEqual(['Double', 'Suite']);
  });

  it('retrouve un tarif et sa chambre', () => {
    const result = { accommodation: { rooms } } as unknown as IStayResult;
    expect(findRate(result, 'd2')?.room.name).toBe('Double');
    expect(findRate(result, 'zz')).toBeUndefined();
    expect(findRate(result, null)).toBeUndefined();
  });
});
