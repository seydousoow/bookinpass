import { IFlightOfferDetails } from '@shared/models';
import { extraBagGroups, extraBagLines, extraBagsAmount, toOrderServices } from './extra-bags.util';

const segment = (id: string, from: string, to: string) => ({ id, origin: { iata_code: from }, destination: { iata_code: to } });
const bag = (id: string, segmentIds: string[], passengerIds = ['pas_1'], max = 1, amount = '20.00') => ({
  id,
  type: 'baggage',
  segment_ids: segmentIds,
  passenger_ids: passengerIds,
  maximum_quantity: max,
  total_amount: amount,
  total_currency: 'EUR',
  metadata: { type: 'checked', maximum_weight_kg: 23 },
});
const offer = (services: unknown[]) =>
  ({
    total_currency: 'EUR',
    available_services: services,
    slices: [{ segments: [segment('seg_1', 'DSS', 'LIS'), segment('seg_2', 'LIS', 'CDG')] }, { segments: [segment('seg_3', 'CDG', 'DSS')] }],
  }) as unknown as IFlightOfferDetails;

describe('extraBagGroups', () => {
  it('un groupe par segment, vide quand rien n’est proposé au voyageur', () => {
    const groups = extraBagGroups(offer([bag('ase_1', ['seg_1']), bag('ase_x', ['seg_3'], ['pas_2'])]), 'pas_1');
    expect(groups.map((g) => [g.route, g.options.map((o) => o.service.id)])).toEqual([
      ['DSS → LIS', ['ase_1']],
      ['LIS → CDG', []],
      ['CDG → DSS', []],
    ]);
  });

  it('un service sur plusieurs segments est rangé sous le premier', () => {
    const groups = extraBagGroups(offer([bag('ase_1', ['seg_1', 'seg_2'])]), 'pas_1');
    expect(groups.map((g) => g.route)).toEqual(['DSS → CDG', 'CDG → DSS']);
  });
});

describe('extraBagLines', () => {
  it('écarte les services disparus et plafonne la quantité', () => {
    const lines = extraBagLines(offer([bag('ase_1', ['seg_1'], ['pas_1'], 2)]), { ase_1: 5, ase_gone: 1 });
    expect(toOrderServices(lines)).toEqual([{ id: 'ase_1', quantity: 2 }]);
    // Prix de vente : 20 € + 20 % = 24 €, deux fois.
    expect(extraBagsAmount(lines, 'EUR')).toBe(48);
  });
});
