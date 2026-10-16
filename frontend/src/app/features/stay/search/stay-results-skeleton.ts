import { Component } from '@angular/core';
import { Spinner } from '@shared/ui';

/** Attente des résultats : trois cartes fantômes et l'annonce du chargement. */
@Component({
  selector: 'bip-stay-results-skeleton',
  imports: [Spinner],
  template: `
    <div class="flex flex-col gap-3 py-4">
      <bip-spinner class="self-center" label="stay.search.loading" showLabel size="md"/>
      @for (card of cards; track card) {
        <div aria-hidden="true" class="rounded-card border-border-subtle flex animate-pulse overflow-hidden border max-sm:flex-col">
          <div class="bg-surface-muted h-40 sm:w-56"></div>
          <div class="flex-1 space-y-3 p-4">
            <div class="bg-border-subtle h-4 w-2/3 rounded"></div>
            <div class="bg-border-subtle h-3 w-1/2 rounded"></div>
            <div class="bg-border-subtle h-3 w-1/3 rounded"></div>
          </div>
        </div>
      }
    </div>
  `,
})
export class StayResultsSkeleton {
  protected readonly cards = [1, 2, 3];
}
