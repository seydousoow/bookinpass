import { Service, signal } from '@angular/core';
import { IStaySearch } from '@shared/models/stay.model';

const STORAGE_KEY = 'bip.stay-search';

/**
 * Dernière recherche d'hébergement validée : préremplit le formulaire et sert au retour vers les résultats.
 */
@Service()
export class StaySearchStore {
  readonly lastSearch = signal<IStaySearch | null>(null);

  constructor() {
    this.restore();
  }

  save(search: IStaySearch): void {
    this.lastSearch.set(search);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(search));
    } catch {
      // Navigation privée ou stockage refusé : la recherche vaut pour la session.
    }
  }

  private restore(): void {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        this.lastSearch.set(JSON.parse(saved) as IStaySearch);
      }
    } catch {
      // JSON corrompu ou stockage inaccessible : pas de préremplissage.
    }
  }
}
