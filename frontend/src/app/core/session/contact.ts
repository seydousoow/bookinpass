/** Coordonnées du voyageur qui réserve : e-mail, téléphone au format E.164. */
export interface IContact {
  email: string;
  phone: string;
}

const KEY = 'bip.contact';

/**
 * Coordonnées saisies lors d'une réservation, reprises pour la suivante (vol, hébergement) dans le même onglet.
 * `sessionStorage` : effacées à la fermeture de l'onglet, comme le brouillon de réservation.
 */
export function loadContact(): IContact | null {
  try {
    const raw = sessionStorage.getItem(KEY);
    const contact = raw ? (JSON.parse(raw) as Partial<IContact>) : null;
    if (!contact || typeof contact.email !== 'string' || typeof contact.phone !== 'string') {
      return null;
    }
    return { email: contact.email, phone: contact.phone };
  } catch {
    return null;
  }
}

export function saveContact(contact: IContact): void {
  try {
    sessionStorage.setItem(KEY, JSON.stringify({ email: contact.email, phone: contact.phone }));
  } catch {
    // Stockage plein ou bloqué : le formulaire marche sans, il ne sera simplement pas prérempli.
  }
}
