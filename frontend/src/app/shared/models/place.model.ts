/**
 * Point de départ / d'arrivée. Aucune sémantique fournisseur ici (plan §2.2
 * règle 3) : le front ne connaît que le contrat Book In Pass.
 */
export type PlaceKind = 'city' | 'airport';

export interface Airport {
  readonly iata: string;
  readonly name: string;
  readonly cityName: string;
  /** Code de regroupement : partagé par les aéroports d'une même ville (DKR = DKR + DSS). */
  readonly cityCode: string;
  /** Région / état — utile à la recherche, jamais affiché. */
  readonly state: string;
  readonly countryName: string;
  readonly countryCode: string;
}

/** Une ville et ses aéroports — la forme affichée dans le champ de recherche. */
export interface CityGroup {
  readonly cityCode: string;
  readonly cityName: string;
  readonly countryName: string;
  readonly countryCode: string;
  readonly airports: readonly Airport[];
}

/** Ce que le champ émet : soit une ville entière, soit un aéroport précis. */
export interface PlaceSelection {
  readonly kind: PlaceKind;
  /** Code IATA ville (kind = city) ou aéroport (kind = airport). */
  readonly code: string;
  readonly label: string;
  readonly subLabel: string;
  readonly countryCode: string;
}
