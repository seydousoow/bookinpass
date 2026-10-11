import { afterNextRender, Service, signal } from '@angular/core';
import { IPersistedFlightSearch } from '@shared/models';

const STORAGE_KEY = 'bip.search';

/**
 * Dernière recherche vol validée, mémorisée pour préremplir l'accueil au
 * retour. Restauration différée à `afterNextRender` : ni `localStorage` ni
 * un rendu dépendant de lui au rendu serveur (plan §10.3).
 */
@Service()
export class SearchStore {
  readonly lastSearch = signal<IPersistedFlightSearch | null>(null);

  constructor() {
    afterNextRender(() => this.restore());
  }

  save(search: IPersistedFlightSearch): void {
    this.lastSearch.set(search);
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(search));
    } catch (e) {
      console.error(e);
      // Navigation privée ou stockage refusé : la recherche vaut pour la session.
    }
  }

  private restore(): void {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        this.lastSearch.set(JSON.parse(saved) as IPersistedFlightSearch);
      }
    } catch {
      // JSON corrompu ou stockage inaccessible : pas de préremplissage.
    }
  }
}
