// noinspection ES6PreferShortImport

import { Component, computed, input, output } from '@angular/core';
import { AppError } from '../../models/app-error';
import { SystemStatus } from '../../models/system-state';
import { Button } from '../button/button';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroExclamationCircleSolid } from '@ng-icons/heroicons/solid';

/**
 * Rendu des huit états du cahier §11. Deux règles portées ici, pas ailleurs :
 *  - le squelette a la MÊME taille que le contenu qu'il remplace (sinon CLS) ;
 *  - un message d'erreur dit ce qui s'est passé, ce qui reste enregistré et
 *    l'action possible ; jamais la couleur seule.
 */
@Component({
  selector: 'bip-system-state',
  imports: [Button, NgIcon],
  host: { class: 'block' },
  providers: [provideIcons({ heroExclamationCircleSolid })],
  template: `
    @switch (status()) {
      @case ('success') {
        <ng-content />
      }
      @case ('partial') {
        <div class="border-state-warning bg-state-warning-soft rounded-card mb-3 border p-3">
          <p class="text-sm text-ink font-semibold">{{ title() || 'Confirmation partielle' }}</p>
          <p class="text-sm text-ink/80 mt-1">{{ message() }}</p>
        </div>
        <ng-content />
      }
      @case ('loading') {
        <div [attr.aria-busy]="true" aria-live="polite" class="animate-pulse space-y-3">
          <span class="sr-only">Chargement en cours</span>
          @for (line of skeletonLines(); track $index) {
            <div [style.height.px]="skeletonHeight()" class="bg-border-subtle rounded-card"></div>
          }
        </div>
      }
      @default {
        <div [class]="panelClass()" role="status">
          <ng-icon name="heroExclamationCircleSolid" aria-hidden="true" [color]="iconClass()" />
          <div class="min-w-0 flex-1">
            <p class="text-sm text-ink font-semibold">{{ resolvedTitle() }}</p>
            <p class="text-sm text-muted mt-1">{{ resolvedMessage() }}</p>
            @if (error()?.preserved; as preserved) {
              <p class="text-xs text-muted mt-1.5 italic">{{ preserved }}</p>
            }
          </div>
          @if (actionLabel()) {
            <bip-button (clicked)="retry.emit()" size="sm" variant="outline">{{ actionLabel() }}</bip-button>
          }
        </div>
      }
    }
  `,
})
export class SystemState {
  readonly status = input.required<SystemStatus>();
  readonly title = input<string>();
  readonly message = input<string>();
  readonly error = input<AppError | null>(null);
  /** Le squelette doit imiter le contenu remplacé : nombre et hauteur des blocs. */
  readonly skeletonCount = input(3);
  readonly skeletonHeight = input(96);
  readonly retry = output<void>();

  protected readonly skeletonLines = computed(() => Array.from({ length: this.skeletonCount() }, (_, i) => i));

  protected readonly resolvedTitle = computed(() => {
    if (this.title()) {
      return this.title();
    }
    const defaults: Record<string, string> = {
      idle: 'Lancez une recherche',
      empty: 'Aucun résultat',
      expired: 'Cette offre a expiré',
      error: 'Une action a échoué',
      offline: 'Vous êtes hors ligne',
    };
    return defaults[this.status()] ?? '';
  });

  protected readonly resolvedMessage = computed(() => {
    if (this.error()) {
      return this.error()!.what;
    }
    if (this.message()) {
      return this.message();
    }
    const defaults: Record<string, string> = {
      idle: 'Renseignez vos critères pour afficher des offres.',
      empty: 'Aucune offre ne correspond à ces critères. Élargissez les dates ou retirez un filtre.',
      expired: 'Le prix n’est plus garanti. Nous relançons la recherche en conservant votre saisie.',
      error: 'Rien n’a été perdu. Vous pouvez réessayer.',
      offline: 'Les billets déjà téléchargés restent accessibles.',
    };
    return defaults[this.status()] ?? '';
  });

  protected readonly actionLabel = computed(
    () => this.error()?.action.label ?? (this.status() === 'error' || this.status() === 'expired' ? 'Réessayer' : null),
  );

  protected panelClass(): string {
    const tones: Record<string, string> = {
      idle: 'border-border-subtle bg-surface-card text-muted',
      empty: 'border-border-subtle bg-surface-card',
      expired: 'border-state-warning/40 bg-state-warning-soft',
      error: 'border-state-error/40 bg-state-error-soft',
      offline: 'border-border-strong bg-surface-muted',
    };
    return `flex items-start gap-3 rounded-card border p-4 ${tones[this.status()] ?? 'border-border-subtle bg-surface-card'}`;
  }

  protected iconClass(): string {
    const tones: Record<string, string> = {
      expired: '--color-state-warning',
      error: '--color-state-error',
      offline: '--color-muted',
    };
    return `var(--${tones[this.status()] ?? '--color-muted'})`;
  }
}
