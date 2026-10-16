import { Component } from '@angular/core';
import { Spinner } from '@shared/ui';

/**
 * Attente des offres : même gabarit que la page de résultats (filtres à gauche sur grand écran, cartes d'offre),
 * pour que rien ne saute à l'arrivée des résultats. Seul le spinner est annoncé aux lecteurs d'écran.
 */
@Component({
  selector: 'bip-search-skeleton',
  imports: [Spinner],
  host: { class: 'block w-full flex-1 min-h-0 overflow-hidden' },
  template: `
    <div class="absolute z-90 flex justify-center items-center h-screen w-screen overflow-hidden bg-white/5 top-0 left-0">
      <bip-spinner label="search.loading" showLabel size="md" />
    </div>
    <div class="mx-auto flex max-w-6xl gap-2 py-2 md:flex-row">
      <!-- Filtres -->
      <div aria-hidden="true" class="hidden animate-pulse space-y-5 border-r border-r-zinc-100 p-3 md:block md:w-1/4">
        <div class="bg-border-subtle h-5 w-1/2 rounded"></div>
        @for (group of filterGroups; track group) {
          <div class="space-y-3">
            <div class="bg-border-subtle h-3 w-1/3 rounded"></div>
            @for (row of filterRows; track row) {
              <div class="flex items-center gap-2">
                <div class="bg-surface-muted size-4 rounded"></div>
                <div class="bg-surface-muted h-3 flex-1 rounded"></div>
              </div>
            }
          </div>
        }
      </div>

      <!-- Offres -->
      <div class="flex flex-1 flex-col gap-3 not-md:px-2 md:pr-2">
        @for (card of cards; track card) {
          <div aria-hidden="true" class="border-border-subtle bg-surface-card flex animate-pulse flex-col gap-3 rounded-2xl border p-3 md:flex-row">
            <div class="md:border-r-border-subtle flex-1 space-y-4 md:border-r md:pr-4">
              @for (slice of slices; track slice) {
                <div class="grid grid-cols-[auto_1fr] items-center gap-5">
                  <div class="bg-surface-muted size-10 rounded-xl"></div>
                  <div class="grid grid-cols-[1fr_auto_1fr] items-center gap-6">
                    <div class="space-y-2">
                      <div class="bg-border-subtle h-4 w-14 rounded"></div>
                      <div class="bg-surface-muted h-3 w-20 rounded"></div>
                    </div>
                    <div class="flex w-24 flex-col items-center gap-1.5">
                      <div class="bg-surface-muted h-2.5 w-12 rounded"></div>
                      <div class="bg-border-subtle h-px w-full"></div>
                      <div class="bg-surface-muted h-2.5 w-10 rounded"></div>
                    </div>
                    <div class="flex flex-col items-end space-y-2">
                      <div class="bg-border-subtle h-4 w-14 rounded"></div>
                      <div class="bg-surface-muted h-3 w-20 rounded"></div>
                    </div>
                  </div>
                </div>
              }
            </div>
            <div class="flex items-center justify-between gap-3 md:w-40 md:flex-col md:items-end md:justify-center">
              <div class="bg-border-subtle h-6 w-24 rounded"></div>
              <div class="bg-surface-muted rounded-button h-9 w-28"></div>
            </div>
          </div>
        }
      </div>
    </div>
  `,
})
export class SearchSkeleton {
  protected readonly filterGroups = [1, 2, 3];
  protected readonly filterRows = [1, 2, 3];
  protected readonly cards = [1, 2, 3, 4];
  protected readonly slices = [1, 2];
}
