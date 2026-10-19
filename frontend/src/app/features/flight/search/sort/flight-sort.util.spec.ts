import { IFlightOffer } from '@shared/models/flight.model';
import { durationMinutes, offerMinutes, sortFlights } from './flight-sort.util';

/** Offre réduite à ce que le tri lit : prix (frais BIP 5 %, Dakar → Paris), durée, escales, bagage en soute. */
function offer(id: string, amount: number, duration: string, segments = 1, checkedBag = false): IFlightOffer {
  const segment = {
    stops: [],
    passengers: [{ baggages: checkedBag ? [{ type: 'checked', quantity: 1 }] : [] }],
  };
  return {
    id,
    total_amount: amount.toFixed(2),
    total_currency: 'EUR',
    slices: [
      {
        duration,
        origin: { iata_country_code: 'SN' },
        destination: { iata_country_code: 'FR' },
        segments: Array.from({ length: segments }, () => segment),
      },
    ],
  } as unknown as IFlightOffer;
}

describe('flight sort', () => {
  const cheapSlow = offer('cheap', 208, 'PT8H20M', 2);
  const best = offer('best', 363, 'PT6H', 1, true);
  const fastDear = offer('fast', 851, 'PT5H45M', 1, true);
  const offers = [best, fastDear, cheapSlow];

  it('lit les durées ISO de Duffel, jours compris', () => {
    expect(durationMinutes('PT8H20M')).toBe(500);
    expect(durationMinutes('P1DT2H5M')).toBe(1565);
    expect(durationMinutes('PT45M')).toBe(45);
    expect(durationMinutes('n/a')).toBe(0);
    expect(offerMinutes(cheapSlow)).toBe(500);
  });

  it('trie par prix, puis par durée', () => {
    expect(sortFlights(offers, 'cheapest').map((o) => o.id)).toEqual(['cheap', 'best', 'fast']);
    expect(sortFlights(offers, 'fastest').map((o) => o.id)).toEqual(['fast', 'best', 'cheap']);
  });

  it('le meilleur choix équilibre prix, durée, escales et bagages', () => {
    // Le moins cher a une escale et pas de bagage ; le plus rapide coûte quatre fois plus : l'offre du milieu gagne.
    expect(sortFlights(offers, 'best')[0].id).toBe('best');
  });

  it('ne modifie pas la liste reçue et compare dans la devise d\'affichage', () => {
    const before = offers.map((o) => o.id);
    const sorted = sortFlights(offers, 'cheapest', (amount) => amount * 655.957);
    expect(offers.map((o) => o.id)).toEqual(before);
    expect(sorted[0].id).toBe('cheap');
  });

  it('à égalité de score, le moins cher passe devant', () => {
    const same = [offer('b', 300, 'PT6H'), offer('a', 300, 'PT6H')];
    expect(sortFlights(same, 'best').map((o) => o.id)).toEqual(['b', 'a']);
  });
});
