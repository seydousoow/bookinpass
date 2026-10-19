export const STAY_STEPS = ['room', 'guests', 'pay'] as const;
export type TStayStep = (typeof STAY_STEPS)[number];

export interface IGuestName {
  given_name: string;
  family_name: string;
}

/** Coordonnées et noms des voyageurs, au format de la réservation Duffel Stays. */
export interface IStayGuestDetails {
  guests: IGuestName[];
  email: string;
  phone: string;
  /** Demandes à l'établissement (arrivée tardive…), transmises sans garantie. */
  requests: string;
}

/**
 * Réservation en cours, relue après un rechargement. `sessionStorage` : propre à l'onglet et effacé à sa
 * fermeture — ces données personnelles ne s'attardent pas.
 */
export interface IStayDraft {
  readonly step: TStayStep;
  readonly rateId: string | null;
  readonly details: IStayGuestDetails;
}

const key = (resultId: string): string => `bip.stay-checkout.${resultId}`;

export function loadStayDraft(resultId: string): IStayDraft | null {
  try {
    const raw = sessionStorage.getItem(key(resultId));
    const draft = raw ? (JSON.parse(raw) as Partial<IStayDraft>) : null;
    if (!draft || !STAY_STEPS.includes(draft.step as TStayStep) || typeof draft.details !== 'object' || !Array.isArray(draft.details?.guests)) {
      return null;
    }
    const { guests, email, phone, requests } = draft.details;
    return {
      step: draft.step as TStayStep,
      rateId: typeof draft.rateId === 'string' ? draft.rateId : null,
      details: { guests, email: email ?? '', phone: phone ?? '', requests: requests ?? '' },
    };
  } catch {
    return null;
  }
}

export function saveStayDraft(resultId: string, draft: IStayDraft): void {
  try {
    sessionStorage.setItem(key(resultId), JSON.stringify(draft));
  } catch {
    // Stockage plein ou bloqué : la page marche sans, on perd juste la reprise.
  }
}

export function clearStayDraft(resultId: string): void {
  try {
    sessionStorage.removeItem(key(resultId));
  } catch {
    // Stockage bloqué : rien à effacer.
  }
}
