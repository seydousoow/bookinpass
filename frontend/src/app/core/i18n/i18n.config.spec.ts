import { CONTRACT_CURRENCY } from '@shared/models/money.model';
import { AVAILABLE_LANGS, DEFAULT_LANG, LANG_TO_LOCALE, LOCALE_TO_CURRENCY } from './i18n.config';

describe('configuration i18n', () => {
  it('déclare une locale pour chaque langue servie', () => {
    for (const lang of AVAILABLE_LANGS) {
      expect(LANG_TO_LOCALE[lang]).toBeDefined();
    }
  });

  it('utilise le français du Sénégal, pas celui de France', () => {
    expect(LANG_TO_LOCALE['fr']).toBe('fr-SN');
  });

  /**
   * Régression déjà survenue deux fois : la table livrée par transloco-locale
   * associe 'fr-SN' ET 'fr-FR' à EUR, et elle l'emporte sur `defaultCurrency`.
   * Sans surcharge explicite, les prix s'affichent en euros.
   */
  it('force le XOF sur toutes les locales servies', () => {
    for (const locale of Object.values(LANG_TO_LOCALE)) {
      expect(LOCALE_TO_CURRENCY[locale]).toBe(CONTRACT_CURRENCY);
    }
  });

  it('ne laisse aucune locale sans devise', () => {
    expect(Object.keys(LOCALE_TO_CURRENCY).sort()).toEqual(
      Object.values(LANG_TO_LOCALE).sort(),
    );
  });

  it('prend le français comme langue de référence et de repli', () => {
    expect(DEFAULT_LANG).toBe('fr');
    expect(AVAILABLE_LANGS).toContain(DEFAULT_LANG);
  });
});
