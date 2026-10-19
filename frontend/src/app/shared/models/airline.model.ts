/** Compagnie aérienne du référentiel (`GET /api/airlines`). */
export interface IAirlineReference {
  readonly iata: string;
  readonly name: string;
  readonly logoSymbolUrl: string | null;
  readonly logoLockupUrl: string | null;
  readonly conditionsOfCarriageUrl: string | null;
}
