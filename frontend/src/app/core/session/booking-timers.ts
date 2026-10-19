import { Signal } from '@angular/core';
import { toObservable } from '@angular/core/rxjs-interop';
import { EMPTY, fromEvent, merge, Observable, startWith, switchMap, timer } from 'rxjs';

/** Offre ou tarif relu chez le fournisseur à ce rythme : prix et disponibilité restent à jour pendant la saisie. */
export const REVALIDATE_MS = 10 * 60_000;
/** Sans interaction pendant ce délai, la réservation est abandonnée : retour à la recherche. */
export const IDLE_MS = 15 * 60_000;

const ACTIVITY_EVENTS = ['pointerdown', 'keydown', 'scroll', 'touchstart'] as const;

/**
 * Émet une fois quand l'échéance (ISO) est atteinte, aussitôt si elle est passée ; repart si elle change.
 * À appeler dans un contexte d'injection (`toObservable`), côté navigateur.
 */
export function deadline(expiresAt: Signal<string | null | undefined>): Observable<number> {
  return toObservable(expiresAt).pipe(switchMap((at) => (at ? timer(new Date(at)) : EMPTY)));
}

/** Émet après `ms` sans clic, frappe, défilement ni toucher ; chaque interaction relance le compte à rebours. */
export function inactivity(document: Document, ms = IDLE_MS): Observable<number> {
  return merge(...ACTIVITY_EVENTS.map((type) => fromEvent(document, type, { passive: true }))).pipe(
    startWith(null),
    switchMap(() => timer(ms)),
  );
}
