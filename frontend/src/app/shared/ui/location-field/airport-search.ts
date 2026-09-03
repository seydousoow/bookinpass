import Fuse, { IFuseOptions } from 'fuse.js';
import { Airport } from '@shared/models/place.model';

/**
 * Ligatures et lettres barrées que la décomposition NFD ne sépare pas : elles
 * n'ont pas d'accent à retirer, il faut une correspondance explicite. Absentes
 * du jeu de données actuel, mais le référentiel viendra du backend — mieux vaut
 * couvrir « Malmö », « Ørsta » ou « Łódź » dès maintenant.
 */
const LIGATURES: Record<string, string> = {
  œ: 'oe', æ: 'ae', ø: 'o', ß: 'ss', đ: 'd', ð: 'd', þ: 'th',
  ł: 'l', ħ: 'h', ı: 'i', ŋ: 'n', ſ: 's', ŧ: 't', ƶ: 'z',
};

/** Apostrophes et tirets typographiques : « Côte d’Ivoire » doit se trouver en tapant « Cote d'Ivoire ». */
const PUNCTUATION: Record<string, string> = {
  'ʼ': "'", '‘': "'", '’': "'", '‚': "'", '‛': "'",
  '“': '"', '”': '"', '–': '-', '—': '-', '‑': '-',
};

/**
 * Forme de comparaison : minuscules, sans accent, sans ligature, ponctuation
 * ramenée à l'ASCII. Appliquée à l'index ET à la saisie — c'est la seule façon
 * d'obtenir une recherche symétrique : « Senegal » trouve Sénégal, et
 * « Sénégal » aussi.
 */
export function normalizeForSearch(value: string): string {
  return value
    .toLowerCase()
    .replace(/[œæøßđðþłħıŋſŧƶ]/g, (c) => LIGATURES[c] ?? c)
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/[ʼ‘’‚‛“”–—‑]/g, (c) => PUNCTUATION[c] ?? c)
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Enregistrement indexé : les champs portés par Fuse sont normalisés, tandis
 * que `airport` conserve la forme accentuée, seule affichée au client.
 */
interface IndexedAirport {
  readonly airport: Airport;
  readonly iata: string;
  readonly cityName: string;
  readonly name: string;
  readonly state: string;
  readonly countryName: string;
}

/**
 * Recherche d'aéroports — Fuse.js, réglages repris de l'ancien front et corrigés
 * sur deux points, mesurés sur les 2 089 entrées du jeu de données :
 *
 * 1. `ignoreLocation: true` au lieu de `location: 0` / `distance: 100`.
 *    L'ancien réglage exigeait que la correspondance soit près du DÉBUT de la
 *    chaîne. Or presque tous les noms commencent par « Aéroport … » : chercher
 *    « Blaise Diagne » ou « Senghor » remontait **zéro résultat**.
 * 2. `threshold: 0.3` au lieu de `0.1`. À 0.1, aucune faute de frappe n'est
 *    tolérée (« Dakr » ne trouve pas Dakar). À 0.4, le bruit devient visible :
 *    « CDG » remonte CBG et CAG. 0.3 est le point où la tolérance apparaît sans
 *    que le bruit ne remonte au-dessus de la bonne réponse.
 *
 * L'option native `ignoreDiacritics` de Fuse n'est pas utilisée : elle
 * dépouillerait les accents de tout le corpus à CHAQUE frappe. La normalisation
 * est faite une fois, à la construction de l'index.
 *
 * Les clés sont pondérées pour que le code IATA et la ville l'emportent sur le
 * nom de pays — sinon « DKR » remonte d'abord des pays contenant ces lettres.
 */
export const AIRPORT_FUSE_OPTIONS: IFuseOptions<IndexedAirport> = {
  shouldSort: true,
  includeScore: true,
  threshold: 0.3,
  ignoreLocation: true,
  minMatchCharLength: 1,
  keys: [
    { name: 'iata', weight: 3 },
    { name: 'cityName', weight: 2 },
    { name: 'name', weight: 1.5 },
    { name: 'state', weight: 0.5 },
    { name: 'countryName', weight: 0.5 },
  ],
};

/**
 * Indexe une fois pour toutes. Réindexer — ou renormaliser — à chaque frappe sur
 * 2 000 entrées est la seule façon de rendre ce champ lent.
 */
export function createAirportIndex(airports: readonly Airport[]): Fuse<IndexedAirport> {
  const indexed = airports.map<IndexedAirport>((airport) => ({
    airport,
    iata: normalizeForSearch(airport.iata),
    cityName: normalizeForSearch(airport.cityName),
    name: normalizeForSearch(airport.name),
    state: normalizeForSearch(airport.state),
    countryName: normalizeForSearch(airport.countryName),
  }));
  return new Fuse(indexed, AIRPORT_FUSE_OPTIONS);
}

/**
 * Poids du rang de hub dans le classement final.
 *
 * Le score Fuse va de 0 (parfait) à `threshold` (0,3). La pénalité de rang vaut
 * au plus 3 × 0,02 = 0,06, soit un cinquième de l'échelle : elle départage des
 * résultats de pertinence voisine — « Côte d'Ivoire », où tous les aéroports du
 * pays obtiennent le même score — sans jamais faire remonter un grand aéroport
 * devant une correspondance textuelle franchement meilleure.
 */
export const HUB_RANK_WEIGHT = 0.02;

/**
 * Fuse tronque AVANT son propre tri : on récupère un vivier plus large, on
 * reclasse, puis on coupe. Sinon un hub légèrement moins bien noté serait
 * écarté avant d'avoir pu remonter.
 */
const CANDIDATE_FACTOR = 4;
const MAX_CANDIDATES = 240;

/**
 * Une correspondance exacte de code IATA ou de code ville doit toujours arriver
 * en tête : c'est la saisie la plus intentionnelle qu'un utilisateur puisse
 * faire, et aucun rang de hub ne doit la déclasser.
 */
export function searchAirports(
  index: Fuse<IndexedAirport>,
  query: string,
  limit: number,
): readonly Airport[] {
  const normalized = normalizeForSearch(query);
  if (!normalized) {
    return [];
  }

  const candidates = Math.min(MAX_CANDIDATES, Math.max(limit, limit * CANDIDATE_FACTOR));
  const ranked = index
    .search(normalized, { limit: candidates })
    .map((hit) => ({
      airport: hit.item.airport,
      rank: (hit.score ?? 0) + (hit.item.airport.hubRank - 1) * HUB_RANK_WEIGHT,
    }))
    .sort((a, b) => a.rank - b.rank)
    .map((r) => r.airport);

  const exactAt = ranked.findIndex(
    (a) =>
      normalizeForSearch(a.iata) === normalized || normalizeForSearch(a.cityCode) === normalized,
  );
  if (exactAt > 0) {
    const [exact] = ranked.splice(exactAt, 1);
    ranked.unshift(exact);
  }

  return ranked.slice(0, limit);
}
