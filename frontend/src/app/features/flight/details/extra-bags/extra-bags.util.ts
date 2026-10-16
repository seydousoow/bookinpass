import { IBaggageServiceMetadata, IFlightOfferDetails, IService, TBaggageType } from '@shared/models';
import { ancillaryPrice } from '@core/money/pricing';

/** Bagages supplémentaires retenus : identifiant du service Duffel → quantité. */
export type TBagSelection = Readonly<Record<string, number>>;

/** Bagages payants proposés à un voyageur sur un segment (ou sur plusieurs, quand le service les couvre ensemble). */
export interface IExtraBagGroup {
  readonly segmentId: string;
  /** « DSS → CDG » : premier et dernier aéroport couverts. */
  readonly route: string;
  /** Vide : rien à acheter pour ce voyageur sur ce vol. */
  readonly options: readonly IExtraBagOption[];
}

export interface IExtraBagOption {
  readonly service: IService;
  readonly bagType: TBaggageType;
  readonly weightKg: number | null;
  /** Prix de vente unitaire (prix Duffel majoré), dans `service.total_currency`. */
  readonly price: number;
}

export interface IExtraBagLine {
  readonly service: IService;
  readonly quantity: number;
}

function baggageServices(offer: IFlightOfferDetails): IService[] {
  return (offer.available_services ?? []).filter((s) => s.type === 'baggage');
}

/**
 * Un groupe par segment de l'offre, dans l'ordre du voyage. Un service qui couvre plusieurs segments est rangé sous
 * le premier ; les suivants, déjà couverts, n'ont pas de groupe à eux.
 */
export function extraBagGroups(offer: IFlightOfferDetails, passengerId: string): IExtraBagGroup[] {
  const services = baggageServices(offer).filter((s) => s.passenger_ids.includes(passengerId));
  const segments = offer.slices.flatMap((slice) => slice.segments);
  const airport = (id: string, end: 'origin' | 'destination') => segments.find((s) => s.id === id)?.[end].iata_code ?? '';
  const groups: IExtraBagGroup[] = [];
  for (const segment of segments) {
    const anchored = services.filter((s) => s.segment_ids[0] === segment.id);
    const covered = services.some((s) => s.segment_ids.includes(segment.id));
    if (covered && !anchored.length) {
      continue;
    }
    const lastSegment = anchored[0]?.segment_ids.at(-1) ?? segment.id;
    const options = anchored.map((service) => {
      const metadata = service.metadata as IBaggageServiceMetadata;
      return { service, bagType: metadata.type, weightKg: metadata.maximum_weight_kg, price: ancillaryPrice(service.total_amount, service.total_currency) };
    });
    groups.push({ segmentId: segment.id, route: `${segment.origin.iata_code} → ${airport(lastSegment, 'destination')}`, options });
  }
  return groups;
}

/** Choix encore valables sur l'offre courante : service toujours proposé, quantité ramenée à son maximum. */
export function extraBagLines(offer: IFlightOfferDetails, selection: TBagSelection): IExtraBagLine[] {
  return baggageServices(offer)
    .map((service) => ({ service, quantity: Math.min(selection[service.id] ?? 0, service.maximum_quantity) }))
    .filter((line) => line.quantity > 0);
}

/** Montant de vente des bagages facturés dans la devise de l'offre (le cas Duffel courant) ; `null` s'il n'y en a pas. */
export function extraBagsAmount(lines: readonly IExtraBagLine[], currency: string): number | null {
  const billed = lines.filter((l) => l.service.total_currency === currency);
  return billed.length ? billed.reduce((sum, l) => sum + ancillaryPrice(l.service.total_amount, currency) * l.quantity, 0) : null;
}

/**
 * Champ `services` de `POST /air/orders` (création de commande, pas encore branchée) :
 * `[{ id: ase_…, quantity }]`. Le paiement à Duffel vaut alors `total_amount` Duffel de l'offre + Σ(prix Duffel ×
 * quantité) des services ; le client, lui, paie les prix de vente (frais BIP et majoration des annexes compris). Les bagages achetés n'apparaîtront pas dans `slices[].segments[].passengers[].baggages[]` de la commande,
 * seulement dans son `services` : combiner les deux pour le franchissement complet d'un voyageur.
 */
export function toOrderServices(lines: readonly IExtraBagLine[]): { id: string; quantity: number }[] {
  return lines.map(({ service, quantity }) => ({ id: service.id, quantity }));
}
