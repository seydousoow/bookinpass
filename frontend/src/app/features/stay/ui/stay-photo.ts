import { Component, input, signal } from '@angular/core';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroBuildingOffice2 } from '@ng-icons/heroicons/outline';

/**
 * Photo d'établissement ou de chambre, recadrée sur son conteneur. Sans photo (ou si elle ne charge pas),
 * un aplat neutre avec une icône garde la mise en page.
 */
@Component({
  selector: 'bip-stay-photo',
  imports: [NgIcon],
  providers: [provideIcons({ heroBuildingOffice2 })],
  host: { class: 'bg-surface-muted relative block overflow-hidden' },
  template: `
    @if (url() && !failed()) {
      <img (error)="failed.set(true)" [alt]="alt()" [src]="url()" class="absolute inset-0 size-full object-cover" decoding="async" loading="lazy"/>
    } @else {
      <span aria-hidden="true" class="text-muted absolute inset-0 flex items-center justify-center">
        <ng-icon name="heroBuildingOffice2" size="2rem"/>
      </span>
    }
  `,
})
export class StayPhoto {
  readonly url = input<string | null | undefined>(null);
  readonly alt = input('');
  protected readonly failed = signal(false);
}
