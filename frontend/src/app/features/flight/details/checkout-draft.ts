import { ISeatChoice } from '@shared/models/seat-map.model';
import { ITravelerInfo } from './traveler-form/traveler-form';
import { TBagSelection } from './extra-bags/extra-bags.util';
import { IContact } from '@core/session/contact';

export const STEPS = ['info', 'ticket', 'extras', 'seat', 'pay'] as const;
export type TStep = (typeof STEPS)[number];

export type { IContact } from '@core/session/contact';

/**
 * Réservation en cours, relue après un rechargement de la page : Duffel ne garde que les noms des voyageurs,
 * le reste (sexe, date de naissance, coordonnées, étape, tarif, sièges) n'existe que dans la page.
 *
 * `sessionStorage` : propre à l'onglet et effacé à sa fermeture — ces données personnelles ne s'attardent pas.
 */
export interface ICheckoutDraft {
  readonly step: TStep;
  /** Montée en gamme retenue ; `null` : l'offre choisie en recherche. */
  readonly fareOfferId: string | null;
  readonly travelers: Readonly<Record<number, ITravelerInfo>>;
  readonly contact: IContact;
  /** Sièges retenus ; revalidés sur les plans de cabine au retour sur l'étape. */
  readonly seats: readonly ISeatChoice[];
  /** Bagages supplémentaires retenus ; ceux que l'offre ne propose plus sont ignorés à la relecture. */
  readonly bags: TBagSelection;
  /** Rang du paiement : le même rouvre la même session Stripe (retour de redirection) ; augmenté quand un paiement est libéré. */
  readonly paymentAttempt: number;
}

const key = (offerId: string): string => `bip.checkout.${offerId}`;

/** Brouillon de l'offre, ou `null` s'il n'y en a pas, s'il est illisible ou si le stockage est bloqué. */
export function loadDraft(offerId: string): ICheckoutDraft | null {
  try {
    const raw = sessionStorage.getItem(key(offerId));
    const draft = raw ? (JSON.parse(raw) as Partial<ICheckoutDraft>) : null;
    if (!draft || !STEPS.includes(draft.step as TStep) || typeof draft.travelers !== 'object' || typeof draft.contact !== 'object') {
      return null;
    }
    return {
      step: draft.step as TStep,
      fareOfferId: typeof draft.fareOfferId === 'string' ? draft.fareOfferId : null,
      travelers: draft.travelers ?? {},
      contact: { email: draft.contact?.email ?? '', phone: draft.contact?.phone ?? '' },
      seats: Array.isArray(draft.seats) ? draft.seats : [],
      bags: draft.bags && typeof draft.bags === 'object' && !Array.isArray(draft.bags) ? draft.bags : {},
      paymentAttempt: Number.isInteger(draft.paymentAttempt) ? draft.paymentAttempt! : 0,
    };
  } catch {
    return null;
  }
}

export function clearDraft(offerId: string): void {
  try {
    sessionStorage.removeItem(key(offerId));
  } catch {
    // Stockage bloqué : rien à effacer.
  }
}

export function saveDraft(offerId: string, draft: ICheckoutDraft): void {
  try {
    sessionStorage.setItem(key(offerId), JSON.stringify(draft));
  } catch {
    // Stockage plein ou bloqué (navigation privée…) : la page marche sans, on perd juste la reprise.
  }
}
