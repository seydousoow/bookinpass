import { Component, computed, input, signal } from '@angular/core';
import { TranslocoDirective } from '@jsverse/transloco';
import { IStayPhoto } from '@shared/models/stay.model';
import { StayPhoto } from '../../ui/stay-photo';

const THUMBNAILS = 4;

/** Grande photo et vignettes ; une vignette cliquée devient la grande photo. À n'afficher que s'il y a des photos. */
@Component({
  selector: 'bip-stay-gallery',
  imports: [StayPhoto, TranslocoDirective],
  template: `
    <div *transloco="let t; prefix: 'stay.gallery'" class="grid grid-cols-4 gap-2">
      <bip-stay-photo
        [alt]="t('photo', { name: name(), index: current() + 1 })"
        [class.md:col-span-3]="thumbnails().length > 0"
        [url]="photos()[current()]?.url"
        class="rounded-card col-span-4 aspect-video md:row-span-2 md:aspect-auto md:min-h-80" />
      @for (photo of thumbnails(); track photo.url; let i = $index) {
        <button
          (click)="current.set(i)"
          [attr.aria-label]="t('show', { index: i + 1 })"
          [attr.aria-pressed]="current() === i"
          class="bip-focusable rounded-card overflow-hidden"
          type="button">
          <bip-stay-photo [url]="photo.url" class="aspect-video size-full" />
        </button>
      }
    </div>
  `,
})
export class StayGallery {
  readonly photos = input.required<readonly IStayPhoto[]>();
  readonly name = input.required<string>();
  protected readonly current = signal(0);
  protected readonly thumbnails = computed(() => (this.photos().length > 1 ? this.photos().slice(0, THUMBNAILS) : []));
}
