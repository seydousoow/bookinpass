import { ICondition } from '@shared/models';
import { conditionLabel, offerChangeLabel } from './conditions.util';

const condition = (allowed: boolean, penalty_amount: string | null = null, penalty_currency: string | null = null) =>
  ({ allowed, penalty_amount, penalty_currency }) as ICondition;

describe('conditionLabel', () => {
  it('suit le guide Duffel : inconnu, refusé, frais inconnus, gratuit, frais', () => {
    expect(conditionLabel('refund', null).key).toBe('unknown');
    expect(conditionLabel('refund', condition(false)).key).toBe('refund-no');
    expect(conditionLabel('refund', condition(true)).key).toBe('refund-fee-unknown');
    expect(conditionLabel('refund', condition(true, '0.00', 'GBP')).key).toBe('refund-free');
    expect(conditionLabel('change', condition(true, '50.00', 'GBP'))).toEqual({ key: 'change-fee', amount: '50.00', currency: 'GBP' });
  });
});

describe('offerChangeLabel', () => {
  it('signale un billet modifiable en partie quand un vol seul peut changer', () => {
    expect(offerChangeLabel(condition(false), [condition(true, '25.00', 'GBP'), condition(false)]).key).toBe('change-partial');
    expect(offerChangeLabel(condition(false), [condition(false), null]).key).toBe('change-no');
    expect(offerChangeLabel(null, [condition(true)]).key).toBe('unknown');
  });
});
