import { IBusPassenger } from '@shared/models/bus.model';

export const BUS_STEPS = ['travellers', 'pay', 'done'] as const;
export type TBusStep = (typeof BUS_STEPS)[number];

export interface IBusTravellerDetails {
  passengers: IBusPassenger[];
  email: string;
  phone: string;
}

/**
 * Réservation de car en cours, relue après un rechargement ou au retour d'un moyen de paiement à redirection (l'URL
 * de retour ne garde pas le départ choisi). `sessionStorage` : propre à l'onglet, effacé à sa fermeture.
 *
 * `requestId` est rejoué tel quel : la même demande retrouve les mêmes places bloquées et la même session de paiement.
 */
export interface IBusDraft {
  readonly route: string;
  readonly departs: string;
  readonly from: string;
  readonly to: string;
  readonly passengers: number;
  readonly requestId: string;
  readonly details: IBusTravellerDetails;
  /** Réservation dont les places sont bloquées. */
  readonly reference: string | null;
}

const KEY = 'bip.bus-checkout';

export function loadBusDraft(): IBusDraft | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    const draft = raw ? (JSON.parse(raw) as Partial<IBusDraft>) : null;
    if (!draft?.route || !draft.departs || !draft.requestId || !Array.isArray(draft.details?.passengers)) {
      return null;
    }
    return draft as IBusDraft;
  } catch {
    return null;
  }
}

export function saveBusDraft(draft: IBusDraft): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify(draft));
  } catch {
    // Stockage plein ou bloqué : la page marche sans, on perd juste la reprise.
  }
}

export function clearBusDraft(): void {
  try {
    sessionStorage.removeItem(KEY);
  } catch {
    // Stockage bloqué : rien à effacer.
  }
}

/** Liste de passagers ajustée au nombre demandé (saisies conservées dans l'ordre). */
export function fitPassengers(passengers: readonly IBusPassenger[], count: number): IBusPassenger[] {
  return Array.from({ length: count }, (_, i) => passengers[i] ?? { given_name: '', family_name: '' });
}
