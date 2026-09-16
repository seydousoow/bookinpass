import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { provideTransloco, TranslocoConfig } from '@jsverse/transloco';
import { provideTranslocoLocale, TranslocoLocaleConfig } from '@jsverse/transloco-locale';
import { cookiesStorage, GetLangParams, provideTranslocoPersistLang } from '@jsverse/transloco-persist-lang';
import { CONTRACT_CURRENCY } from '@shared/models/money.model';
import { environment } from '@env';
import { HttpTranslocoLoader } from './transloco-loader';
import { provideTranslocoMessageformat } from '@jsverse/transloco-messageformat';

/** Langue de référence : un libellé fr manquant doit se voir, pas se déguiser. */
export const DEFAULT_LANG = 'fr';
export const AVAILABLE_LANGS: readonly string[] = [DEFAULT_LANG, 'en'];

/**
 * Le français du site est celui du Sénégal, pas celui de France : séparateurs
 * de milliers, format de date et devise s'en déduisent.
 */
export const LANG_TO_LOCALE: Record<string, string> = { fr: 'fr-SN', en: 'en-US' };
const DEFAULT_LOCALE = LANG_TO_LOCALE[DEFAULT_LANG];

/** Trois mois, exprimés en heures — l'unité attendue par `cookiesStorage`. */
const LANG_COOKIE_HOURS = 24 * 30 * 3;

const translocoConfig: Partial<TranslocoConfig> = {
  availableLangs: [...AVAILABLE_LANGS],
  defaultLang: DEFAULT_LANG,
  fallbackLang: DEFAULT_LANG,
  reRenderOnLangChange: true,
  prodMode: environment.production,
  flatten: { aot: environment.production },
};

/** Langue mémorisée, sinon celle du navigateur si elle est servie, sinon le défaut. */
const getLangFn = ({ cachedLang, browserLang, defaultLang }: GetLangParams): string => {
  if (cachedLang) {
    return cachedLang;
  }
  const browser = browserLang?.toLowerCase();
  return browser && AVAILABLE_LANGS.includes(browser) ? browser : defaultLang;
};

export const LOCALE_TO_CURRENCY: Record<string, string> = Object.fromEntries(
  Object.values(LANG_TO_LOCALE).map((locale) => [locale, CONTRACT_CURRENCY]),
);

const localeConfig: TranslocoLocaleConfig = {
  defaultLocale: DEFAULT_LOCALE,
  langToLocaleMapping: LANG_TO_LOCALE,
  defaultCurrency: CONTRACT_CURRENCY,
  /**
   * `localeToCurrencyMapping` l'emporte sur `defaultCurrency` : le service lit
   * `mapping[locale] || defaultCurrency`. Or la table livrée par la librairie
   * associe 'fr-SN' ET 'fr-FR' à EUR — sans cette correction, `translocoCurrency`
   * affiche des euros. Book In Pass facture en francs CFA, quelle que soit la
   * langue de lecture.
   */
  localeToCurrencyMapping: LOCALE_TO_CURRENCY,
  localeConfig: {
    global: {
      date: { dateStyle: 'long', timeStyle: 'short' },
      // Le XOF n'a pas de sous-unité ; l'afficher avec des décimales est une faute.
      currency: { minimumFractionDigits: 0, maximumFractionDigits: 0 },
    },
  },
};

/**
 * i18n complète : traductions, mémorisation de la langue, formats locaux.
 * Regroupée ici pour que `app.config.ts` reste une liste de fournisseurs lisible.
 */
export function provideI18n(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideTransloco({ config: translocoConfig, loader: HttpTranslocoLoader }),
    provideTranslocoMessageformat({locales: ['fr', 'en']}),
    provideTranslocoPersistLang({ getLangFn, storage: { useValue: cookiesStorage(LANG_COOKIE_HOURS) } }),
    provideTranslocoLocale(localeConfig),
  ]);
}
