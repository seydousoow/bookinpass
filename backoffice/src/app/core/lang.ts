const KEY = 'bo.lang';

/** Langue choisie sur cet appareil (français par défaut) ; stockage bloqué : français. */
export function savedLang(): 'fr' | 'en' {
  try {
    return localStorage.getItem(KEY) === 'en' ? 'en' : 'fr';
  } catch {
    return 'fr';
  }
}

export function saveLang(lang: 'fr' | 'en'): void {
  try {
    localStorage.setItem(KEY, lang);
  } catch {
    // Stockage bloqué : la langue vaut pour la session seulement.
  }
}
