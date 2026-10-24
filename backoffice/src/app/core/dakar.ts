/** Jour civil à Dakar (GMT toute l'année) : la date UTC, quel que soit le fuseau de l'appareil. */
export function dakarToday(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}

/** Heure de Dakar d'un instant ISO (`2026-10-25T07:00:00Z` → `07:00`). */
export function dakarTime(iso: string | null | undefined): string {
  return iso ? new Date(iso).toISOString().slice(11, 16) : '';
}

/** Date et heure de Dakar (`25/10/2026 07:00`). */
export function dakarDateTime(iso: string | null | undefined): string {
  if (!iso) {
    return '';
  }
  const value = new Date(iso).toISOString();
  return `${value.slice(8, 10)}/${value.slice(5, 7)}/${value.slice(0, 4)} ${value.slice(11, 16)}`;
}
