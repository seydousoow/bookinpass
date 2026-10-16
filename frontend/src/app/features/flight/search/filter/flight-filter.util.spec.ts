import { IFlightOffer } from '@shared/models/flight.model';
import { applyFlightFilter, isFlightFiltering, NO_FLIGHT_FILTER, resetFlightFilter, toggleAirline } from './flight-filter.util';

type Bag = 'carry_on' | 'checked';
const DAKAR = { iata_country_code: 'SN', time_zone: 'Africa/Dakar' };
const PARIS = { iata_country_code: 'FR', time_zone: 'Europe/Paris' };
const segment = (bags: Bag[], stops = 0) => ({ stops: Array(stops).fill({}), passengers: [{ baggages: bags.map((type) => ({ type, quantity: 1 })) }] });
const offer = (id: string, airline: string, price: string, slices: ReturnType<typeof segment>[][]): IFlightOffer =>
  ({ id, owner: { iata_code: airline }, total_amount: price, total_currency: 'EUR', slices: slices.map((segments) => ({ segments, origin: DAKAR, destination: PARIS })) }) as unknown as IFlightOffer;

const offers = [
  offer('direct', 'AF', '300.00', [[segment(['carry_on', 'checked'])]]),
  offer('one-stop', 'SN', '200.00', [[segment(['carry_on']), segment(['carry_on'])]]),
  offer('technical', 'AT', '150.00', [[segment([], 1)]]),
];
const ids = (list: readonly IFlightOffer[]) => list.map((o) => o.id);

describe('applyFlightFilter', () => {
  it('garde tout sans critère', () => {
    expect(ids(applyFlightFilter(offers, NO_FLIGHT_FILTER))).toEqual(['direct', 'one-stop', 'technical']);
  });

  it('compte correspondances et arrêts techniques comme escales', () => {
    expect(ids(applyFlightFilter(offers, { ...NO_FLIGHT_FILTER, maxStops: 0 }))).toEqual(['direct']);
    expect(ids(applyFlightFilter(offers, { ...NO_FLIGHT_FILTER, maxStops: 1 }))).toEqual(['direct', 'one-stop', 'technical']);
  });

  it('exige le bagage sur chaque segment', () => {
    expect(ids(applyFlightFilter(offers, { ...NO_FLIGHT_FILTER, cabinBag: true }))).toEqual(['direct', 'one-stop']);
    expect(ids(applyFlightFilter(offers, { ...NO_FLIGHT_FILTER, holdBag: true }))).toEqual(['direct']);
  });

  it('filtre par compagnie et par prix de vente converti (frais BIP compris)', () => {
    expect(ids(applyFlightFilter(offers, { ...NO_FLIGHT_FILTER, airlines: ['SN', 'AT'] }))).toEqual(['one-stop', 'technical']);
    const toXof = (amount: number) => amount * 655.957;
    // Prix de vente (+5 % Dakar → Paris) : 315, 210 et 157,50 € ; 210 € ≈ 137 751 XOF.
    expect(ids(applyFlightFilter(offers, { ...NO_FLIGHT_FILTER, price: { min: 0, max: 130_000 } }, toXof))).toEqual(['technical']);
    expect(ids(applyFlightFilter(offers, { ...NO_FLIGHT_FILTER, price: { min: 0, max: 140_000 } }, toXof))).toEqual(['one-stop', 'technical']);
  });
});

describe('resetFlightFilter', () => {
  it('lève tout sauf la limite d’escales, qui ne compte pas comme filtre', () => {
    const filter = { ...NO_FLIGHT_FILTER, maxStops: 0, holdBag: true, airlines: ['AF'] };
    expect(resetFlightFilter(filter)).toEqual({ ...NO_FLIGHT_FILTER, maxStops: 0 });
    expect(isFlightFiltering({ ...NO_FLIGHT_FILTER, maxStops: 0 })).toBe(false);
  });
});

describe('toggleAirline', () => {
  const all = ['AF', 'AT', 'SN'];

  it('décocher une compagnie quand toutes sont cochées garde les autres', () => {
    expect(toggleAirline([], all, 'AF', false)).toEqual(['AT', 'SN']);
  });

  it('tout recocher revient à « toutes »', () => {
    expect(toggleAirline(['AT', 'SN'], all, 'AF', true)).toEqual([]);
  });
});
