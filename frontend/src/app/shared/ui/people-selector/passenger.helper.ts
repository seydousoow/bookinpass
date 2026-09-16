export interface PassengerCount {
  readonly adults: number;
  readonly children: number;
  readonly infants: number;
}

/**
 * Passenger composition rules:
 *
 * - At least one adult is required.
 * - One adult can accompany at most two minors.
 * - An adult can accompany:
 *     - 2 children
 *     - 1 child + 1 infant
 *     - 1 infant
 * - An adult can never accompany 2 infants.
 * - Total passengers cannot exceed maxTotal.
 */
export function isValidPassengerCount(count: PassengerCount, maxTotal: number = MAX_PASSENGER): boolean {
  const adults = Number(count.adults);
  const children = Number(count.children ?? 0);
  const infants = Number(count.infants ?? 0);
  return (
    adults >= 1 && children >= 0 && infants >= 0 && infants <= adults && children + infants <= adults * 2 && adults + children + infants <= maxTotal
  );
}

/**
 * Minimum adults required for the current number of
 * children and infants.
 */
export function minimumAdults(children: number, infants: number): number {
  return Math.max(1, infants, Math.ceil((children + infants) / 2));
}

/**
 * Maximum number of children allowed for the current
 * number of adults and infants.
 */
export function maximumChildren(adults: number, infants: number, maxTotal: number = MAX_PASSENGER): number {
  return Math.max(0, Math.min(maxTotal - adults - infants, adults * 2 - infants));
}

/**
 * Maximum number of infants allowed for the current
 * number of adults and children.
 */
export function maximumInfants(adults: number, children: number, maxTotal: number = MAX_PASSENGER): number {
  return Math.max(0, Math.min(maxTotal - adults - children, adults, adults * 2 - children));
}

/**
 * Maximum number of adults allowed by the total passenger limit.
 */
export function maximumAdults(children: number, infants: number, maxTotal: number = MAX_PASSENGER): number {
  return Math.max(1, maxTotal - children - infants);
}

export const MAX_PASSENGER = 9;
