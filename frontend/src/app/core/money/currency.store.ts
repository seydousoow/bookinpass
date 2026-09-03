import { afterNextRender, computed, inject, Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CONTRACT_CURRENCY, CurrencyOption, ExchangeRates } from '@shared/models/money.model';

const STORAGE_KEY = 'bip.currency';

/**
 * Devise d'affichage choisie par l'utilisateur, indépendante de la langue :
 * un client peut lire le site en français et voir les prix en euros.
 *
 * Le montant contractuel reste le XOF (cadrage §11) ; la conversion n'est
 * qu'un confort de lecture. Les taux viennent du backend — ici une FIXTURE
 * `rates.json` en attendant le point d'entrée réel.
 */
@Injectable({ providedIn: 'root' })
export class CurrencyStore {
  private readonly http = inject(HttpClient);

  private readonly ratesData = signal<ExchangeRates | null>(null);
  readonly selected = signal<string>(CONTRACT_CURRENCY);

  /** Date du taux appliqué, à afficher partout où un prix converti est montré. */
  readonly updatedAt = computed(() => this.ratesData()?.updatedAt ?? null);
  readonly isContractCurrency = computed(() => this.selected() === CONTRACT_CURRENCY);

  /** Les devises proposées viennent de la charge utile : le backend décide. */
  readonly currencies = computed<readonly CurrencyOption[]>(() => {
    const data = this.ratesData();
    const codes = data ? Object.keys(data.rates) : [CONTRACT_CURRENCY];
    return codes.map((code) => ({ code, label: currencyLabel(code) }));
  });

  constructor() {
    // Ni `localStorage` ni le réseau au rendu serveur (plan §10.3).
    afterNextRender(() => {
      this.restore();
      this.loadRates();
    });
  }

  select(code: string): void {
    this.selected.set(code);
    try {
      localStorage.setItem(STORAGE_KEY, code);
    } catch {
      // Navigation privée ou stockage refusé : le choix vaut pour la session.
    }
  }

  /**
   * Convertit un montant exprimé dans la devise contractuelle. Tant que les
   * taux ne sont pas chargés, on rend le montant d'origine plutôt qu'un prix
   * faux : mieux vaut un XOF juste qu'un euro inventé.
   */
  convert(amountXof: number, currency = this.selected()): number | null {
    if (currency === CONTRACT_CURRENCY) {
      return amountXof;
    }
    const rate = this.ratesData()?.rates[currency];
    return rate === undefined ? null : amountXof * rate;
  }

  private restore(): void {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        this.selected.set(saved);
      }
    } catch {
      // Idem : on garde la devise contractuelle par défaut.
    }
  }

  private loadRates(): void {
    this.http.get<ExchangeRates>('/rates.json').subscribe({
      next: (data) => {
        this.ratesData.set(data);
        // Une devise mémorisée que le backend ne sert plus doit être oubliée.
        if (!(this.selected() in data.rates)) {
          this.selected.set(CONTRACT_CURRENCY);
        }
      },
      error: () => this.ratesData.set(null),
    });
  }
}

/** « EUR » → « euro ». Repli sur le code si la locale ne le connaît pas. */
export function currencyLabel(code: string, locale = 'fr'): string {
  try {
    return new Intl.DisplayNames([locale], { type: 'currency' }).of(code) ?? code;
  } catch {
    return code;
  }
}
