import { ICondition } from '@shared/models/flight.model';

export type TConditionFlag = 'included' | 'not-included' | 'unknown';

/** Libellé d'une règle tarifaire : clé sous `flight.offer.conditions.values`, et frais éventuels. */
export interface IConditionLabel {
  readonly key: string;
  readonly amount?: string;
  readonly currency?: string;
}

/** Règle Duffel → libellé. `null` : la compagnie ne communique pas la règle ; `penalty_amount` nul : frais inconnus. */
export function conditionLabel(kind: 'refund' | 'change', condition: ICondition | null | undefined): IConditionLabel {
  if (!condition) {
    return { key: 'unknown' };
  }
  if (!condition.allowed) {
    return { key: `${kind}-no` };
  }
  if (condition.penalty_amount == null) {
    return { key: `${kind}-fee-unknown` };
  }
  if (Number(condition.penalty_amount) === 0) {
    return { key: `${kind}-free` };
  }
  return { key: `${kind}-fee`, amount: condition.penalty_amount, currency: condition.penalty_currency ?? undefined };
}

/**
 * Modification du billet entier. Si elle est refusée mais qu'un vol, lui, est modifiable (aller seul d'un
 * aller-retour…), on le dit plutôt qu'un « non modifiable » qui contredirait le détail par vol.
 */
export function offerChangeLabel(
  condition: ICondition | null | undefined,
  sliceConditions: readonly (ICondition | null | undefined)[],
): IConditionLabel {
  const label = conditionLabel('change', condition);
  return label.key === 'change-no' && sliceConditions.some((c) => c?.allowed) ? { key: 'change-partial' } : label;
}

/** Avantage d'une tranche (embarquement prioritaire…) : Duffel le donne en booléen, parfois en chaîne, ou `null` si inconnu. */
export function conditionFlag(value: unknown): TConditionFlag {
  if (value == null) {
    return 'unknown';
  }
  return value === true || value === 'true' ? 'included' : 'not-included';
}
