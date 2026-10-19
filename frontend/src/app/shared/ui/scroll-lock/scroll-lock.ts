import { DOCUMENT } from '@angular/common';
import { DestroyRef, effect, inject, Injectable } from '@angular/core';

/** Compteur partagé : le body reste bloqué tant qu'au moins un panneau (modale, feuille, récapitulatif) le demande. */
@Injectable({ providedIn: 'root' })
export class ScrollLock {
  private readonly document = inject(DOCUMENT);
  private holders = 0;

  acquire(): void {
    if (this.holders++ === 0) {
      this.document.body.style.overflow = 'hidden';
    }
  }

  release(): void {
    if (this.holders > 0 && --this.holders === 0) {
      this.document.body.style.overflow = '';
    }
  }
}

/**
 * Bloque le défilement de la page tant que `active()` est vrai. Le verrou est rendu à la destruction du composant :
 * une navigation depuis un panneau ouvert (ex. « Continuer » du récapitulatif de vol) ne laisse plus la page figée.
 * À appeler dans un contexte d'injection.
 */
export function lockScrollWhile(active: () => boolean): void {
  const lock = inject(ScrollLock);
  let held = false;
  const set = (next: boolean): void => {
    if (next !== held) {
      held = next;
      if (next) {
        lock.acquire();
      } else {
        lock.release();
      }
    }
  };
  effect(() => set(active()));
  inject(DestroyRef).onDestroy(() => set(false));
}
