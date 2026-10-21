import { IStayRate, IStayResult, IStayRoom } from '@shared/models/stay.model';

/** Ce qu'une annulation rembourse aujourd'hui, d'après la frise Duffel. */
export type TCancellation =
  | { readonly kind: 'free'; readonly until: string }
  | { readonly kind: 'partial'; readonly until: string; readonly amount: string; readonly currency: string }
  | { readonly kind: 'none' };

/**
 * Prochaine échéance encore à venir : remboursement total, partiel, ou rien.
 * Duffel trie la frise par date croissante ; sans frise (ou toutes dépassées), le tarif n'est pas remboursable.
 */
export function cancellationPolicy(rate: IStayRate, now = new Date()): TCancellation {
  const next = rate.cancellation_timeline.find((step) => new Date(step.before) > now);
  if (!next || Number(next.refund_amount) <= 0) {
    return { kind: 'none' };
  }
  return Number(next.refund_amount) >= Number(rate.total_amount)
    ? { kind: 'free', until: next.before }
    : { kind: 'partial', until: next.before, amount: next.refund_amount, currency: next.currency };
}

export function cheapestRate(room: IStayRoom): IStayRate | undefined {
  return room.rates.reduce<IStayRate | undefined>(
    (best, rate) => (!best || Number(rate.total_amount) < Number(best.total_amount) ? rate : best),
    undefined,
  );
}

/** Chambres triées par leur tarif le moins cher. */
export function roomsByPrice(rooms: readonly IStayRoom[]): IStayRoom[] {
  const price = (room: IStayRoom): number => Number(cheapestRate(room)?.total_amount ?? Infinity);
  return [...rooms].sort((a, b) => price(a) - price(b));
}

export interface IRateChoice {
  readonly room: IStayRoom;
  readonly rate: IStayRate;
}

export function findRate(result: IStayResult | undefined, rateId: string | null): IRateChoice | undefined {
  if (!result || !rateId) {
    return undefined;
  }
  for (const room of result.accommodation.rooms) {
    const rate = room.rates.find((r) => r.id === rateId);
    if (rate) {
      return { room, rate };
    }
  }
  return undefined;
}
