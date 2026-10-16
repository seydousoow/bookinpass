import { computed, inject, Service, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { CONTRACT_CURRENCY, ICurrencyOption, IExchangeRates } from '@shared/models/money.model';
import { TranslocoService } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';

const STORAGE_KEY = 'bip.currency';

/**
 * Devise d'affichage choisie par l'utilisateur, indépendante de la langue :
 * un client peut lire le site en français et voir les prix en euros.
 *
 * Le montant contractuel reste le XOF (cadrage §11) ; la conversion n'est
 * qu'un confort de lecture. Les taux viennent du backend (`GET /api/rates`).
 */
@Service()
export class CurrencyStore {
  readonly selected = signal<string>(CONTRACT_CURRENCY);
  readonly isContractCurrency = computed(() => this.selected() === CONTRACT_CURRENCY);
  private readonly ratesData = signal<IExchangeRates | null>(null);
  /** Date du taux appliqué, à afficher partout où un prix converti est montré. */
  readonly updatedAt = computed(() => this.ratesData()?.updatedAt ?? null);
  private readonly transloco = inject(TranslocoService);
  private readonly apiBaseUrl = inject(APP_CONFIG).apiBaseUrl;
  /** Les devises proposées viennent de la charge utile : le backend décide. */
  readonly currencies = computed<readonly ICurrencyOption[]>(() => {
    const data = this.ratesData();
    const local = this.transloco.activeLang();
    const codes: string[] = [...new Set(data ? Object.keys(data.rates) : [CONTRACT_CURRENCY])];
    return codes.map((code) => ({ code, label: currencyLabel(code, local) }));
  });
  private readonly http = inject(HttpClient);

  constructor() {
    this.restore();
    this.loadRates();
  }

  select(code: string): void {
    this.selected.set(code);
    try {
      localStorage.setItem(STORAGE_KEY, code);
    } catch (e) {
      console.error(e);
      // Navigation privée ou stockage refusé : le choix vaut pour la session.
    }
  }

  /**
   * Convertit un montant exprimé dans la devise contractuelle. Tant que les
   * taux ne sont pas chargés, on rend le montant d'origine plutôt qu'un faux prix.
   * Il vaut mieux un XOF juste qu'un euro inventé.
   */
  convertFromContract(amountXof: number, currency = this.selected()): number | null {
    if (currency === CONTRACT_CURRENCY) {
      return amountXof;
    }
    const rate = this.ratesData()?.rates[currency];
    return rate === undefined ? null : amountXof * rate;
  }

  convert(amount: number, from: string, to = this.selected()): number | null {
    if (from === to) {
      return amount;
    }

    const rates = this.ratesData()?.rates;

    if (!rates) {
      return null;
    }

    // Rates are assumed to be relative to the contract currency (XOF).
    if (from === CONTRACT_CURRENCY) {
      const rate = rates[to];
      return rate === undefined ? null : amount * rate;
    }

    if (to === CONTRACT_CURRENCY) {
      const rate = rates[from];
      return rate === undefined ? null : amount / rate;
    }

    const fromRate = rates[from];
    const toRate = rates[to];

    if (fromRate === undefined || toRate === undefined) {
      return null;
    }
    return (amount / fromRate) * toRate;
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
    this.http.get<IExchangeRates>(`${this.apiBaseUrl}/rates`).subscribe({
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
