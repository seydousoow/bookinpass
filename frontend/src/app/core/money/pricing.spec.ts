import { ancillaryPrice, IPricedOffer, sellingTotal, serviceFee, serviceFeeRate, ticketAmount } from './pricing';

const DAKAR = { iata_country_code: 'SN', time_zone: 'Africa/Dakar' };
const ZIGUINCHOR = { iata_country_code: 'SN', time_zone: 'Africa/Dakar' };
const ABIDJAN = { iata_country_code: 'CI', time_zone: 'Africa/Abidjan' };
const PARIS = { iata_country_code: 'FR', time_zone: 'Europe/Paris' };

const offer = (origin: object, destination: object, total = '184.47'): IPricedOffer => ({
  total_amount: total,
  total_currency: 'EUR',
  slices: [{ origin, destination }],
});

describe('frais de service BIP', () => {
  it('3 % dans un même pays, 5 % sinon ou si le pays est inconnu', () => {
    expect(serviceFeeRate(offer(DAKAR, ZIGUINCHOR))).toBe(0.03);
    expect(serviceFeeRate(offer(DAKAR, ABIDJAN))).toBe(0.05);
    expect(serviceFeeRate(offer(DAKAR, PARIS))).toBe(0.05);
    expect(serviceFeeRate(offer(DAKAR, { iata_country_code: null }))).toBe(0.05);
  });

  it('arrondit les frais au plus loin de zéro et les ajoute au total', () => {
    // 184.47 × 5 % = 9.2235 → 9.22 ; 184.50 × 3 % = 5.535 → 5.54
    expect(serviceFee(offer(DAKAR, PARIS))).toBe(9.22);
    expect(sellingTotal(offer(DAKAR, PARIS))).toBe(193.69);
    expect(serviceFee(offer(DAKAR, ZIGUINCHOR, '184.50'))).toBe(5.54);
  });
});

describe('offre tarifée', () => {
  it('ne compte pas les services prévus dans le billet : ils sont majorés à part', () => {
    const priced: IPricedOffer = {
      ...offer(DAKAR, PARIS, '224.47'),
      available_services: [{ id: 'ase_1', total_amount: '20.00' }],
      intended_services: [{ id: 'ase_1', quantity: 2 }],
    };
    expect(ticketAmount(priced)).toBe(184.47);
    expect(sellingTotal(priced)).toBe(193.69);
  });

  it('retire aussi les sièges, absents de available_services : leur prix vient des plans de cabine', () => {
    // Cas réel : 245.16 € = billet 225.16 + siège 20 ; vendu 225.16 + 11.26 (5 %) + 24 = 260.42.
    const priced: IPricedOffer = {
      ...offer(DAKAR, PARIS, '245.16'),
      available_services: [],
      intended_services: [{ id: 'ase_seat', quantity: 1 }],
    };
    const seats = new Map([['ase_seat', 20]]);
    expect(ticketAmount(priced, seats)).toBe(225.16);
    expect(sellingTotal(priced, seats)).toBe(236.42);
    expect(ancillaryPrice('20.00', 'EUR')).toBe(24);
  });
});

describe('ancillaryPrice', () => {
  it('ajoute 20 % au prix Duffel, arrondi aux décimales de la devise', () => {
    expect(ancillaryPrice('20.00', 'EUR')).toBe(24);
    expect(ancillaryPrice('12.50', 'EUR')).toBe(15);
    expect(ancillaryPrice('10.0125', 'EUR')).toBe(12.02);
    expect(ancillaryPrice('1001', 'XOF')).toBe(1201);
    expect(ancillaryPrice('0.00', 'EUR')).toBe(0);
  });
});
