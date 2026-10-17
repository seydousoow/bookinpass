import { TCabinClass } from './search.model';

/** Plans de cabine Duffel (`GET /api/flights/offers/{id}/seat-maps`) : un par segment. */
export interface ISeatMapsResponse {
  readonly data: readonly ISeatMap[];
}

export interface ISeatMap {
  readonly id: string;
  readonly segment_id: string;
  readonly slice_id: string;
  /** Par pont (du plus bas au plus haut), puis de l'avant vers l'arrière. */
  readonly cabins: readonly ISeatMapCabin[];
}

export interface ISeatMapCabin {
  /** 1 : chaque rangée a deux sections ; 2 : trois sections. */
  readonly aisles: number;
  readonly cabin_class: TCabinClass;
  /** 0 : pont principal, 1 : pont supérieur. */
  readonly deck: number;
  readonly rows: readonly ISeatMapRow[];
  /** Indices (base 0, sur toutes les rangées) des rangées au-dessus des ailes ; `null` s'il n'y en a pas. */
  readonly wings: { readonly first_row_index: number; readonly last_row_index: number } | null;
}

export interface ISeatMapRow {
  /** Sections séparées par les allées : gauche, (milieu), droite. */
  readonly sections: readonly { readonly elements: readonly TSeatMapElement[] }[];
}

/** Un siège réservable par un voyageur donné : un service Duffel (`ase_…`). */
export interface ISeatService {
  readonly id: string;
  readonly passenger_id: string;
  readonly total_amount: string;
  readonly total_currency: string;
}

export interface ISeatElement {
  readonly type: 'seat';
  /** Rangée et lettre, ex. `14B`. */
  readonly designator: string;
  /** Vide : siège indisponible. Au plus un service par voyageur. */
  readonly available_services: readonly ISeatService[] | null;
  /** Conditions de la compagnie, en anglais (ex. « Passenger must be an adult »). */
  readonly disclosures: readonly string[] | null;
  readonly name: string | null;
}

/** Éléments de largeur fixe (celle d'un siège) : alignement des colonnes. */
export type TSeatSpacerType = 'empty' | 'bassinet';
/** Éléments qui occupent la place restante de la section. */
export type TSeatFacilityType = 'exit_row' | 'lavatory' | 'galley' | 'closet' | 'stairs' | 'restricted_seat_general';

export type TSeatMapElement = ISeatElement | { readonly type: TSeatSpacerType | TSeatFacilityType };

/** Siège retenu pour un voyageur sur un segment. */
export interface ISeatChoice {
  readonly segmentId: string;
  readonly passengerId: string;
  readonly serviceId: string;
  readonly designator: string;
  /** Prix de vente (prix Duffel majoré). */
  readonly amount: string;
  /** Prix Duffel, compris dans le `total_amount` de l'offre tarifée ; absent d'un brouillon antérieur. */
  readonly duffelAmount?: string;
  readonly currency: string;
  readonly disclosures: readonly string[];
}
