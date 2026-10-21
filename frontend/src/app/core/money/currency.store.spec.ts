import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { APP_CONFIG } from '@core/config/app-config.token';
import { HttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { IExchangeRates } from '@shared/models/money.model';
import { CurrencyStore } from './currency.store';

const RATES: IExchangeRates = {
  base: 'XOF',
  updatedAt: '2026-09-03T00:00:00.000Z',
  rates: { XOF: 1, EUR: 0.00152449, USD: 0.00165 },
};

function makeStore(response = of(RATES)) {
  TestBed.configureTestingModule({
    providers: [
      { provide: HttpClient, useValue: { get: () => response } },
      { provide: APP_CONFIG, useValue: { production: false, apiBaseUrl: '/api' } },
      // Seule la langue active sert (libellés des devises).
      { provide: TranslocoService, useValue: { activeLang: signal('fr') } },
    ],
  });
  return TestBed.inject(CurrencyStore);
}

describe('CurrencyStore', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    localStorage.clear();
  });

  it('démarre sur la devise contractuelle', () => {
    expect(makeStore().selected()).toBe('XOF');
  });

  it('rend le montant inchangé en devise contractuelle', () => {
    expect(makeStore().convertFromContract(185000, 'XOF')).toBe(185000);
  });

  it('convertit avec le taux servi', () => {
    const converted = makeStore().convertFromContract(655957, 'EUR');
    // 655,957 XOF = 1 EUR (parité fixe).
    expect(converted).toBeCloseTo(1000, 0);
  });

  it('rend null plutôt qu’un prix inventé si le taux manque', () => {
    expect(makeStore().convertFromContract(1000, 'JPY')).toBeNull();
  });

  it('rend null pour toute devise tant que les taux ne sont pas chargés', () => {
    const store = makeStore(throwError(() => new Error('offline')));
    expect(store.convertFromContract(1000, 'EUR')).toBeNull();
    // La devise contractuelle, elle, reste toujours affichable.
    expect(store.convertFromContract(1000, 'XOF')).toBe(1000);
  });

  it('expose les devises servies par le backend', () => {
    expect(
      makeStore()
        .currencies()
        .map((c) => c.code),
    ).toEqual(['XOF', 'EUR', 'USD']);
  });

  it('mémorise le choix de l’utilisateur', () => {
    const store = makeStore();
    store.select('EUR');
    expect(store.selected()).toBe('EUR');
    expect(localStorage.getItem('bip.currency')).toBe('EUR');
  });

  it('oublie une devise que le backend ne sert plus', () => {
    const store = makeStore();
    store.selected.set('GBP');
    (store as unknown as { loadRates(): void })['loadRates']();
    expect(store.selected()).toBe('XOF');
  });

  it('expose la date du taux appliqué', () => {
    expect(makeStore().updatedAt()).toBe('2026-09-03T00:00:00.000Z');
  });

  it('signale si l’affichage est en devise contractuelle', () => {
    const store = makeStore();
    expect(store.isContractCurrency()).toBe(true);
    store.select('EUR');
    expect(store.isContractCurrency()).toBe(false);
  });
});
