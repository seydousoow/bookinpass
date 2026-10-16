// noinspection ES6PreferShortImport

import { DOCUMENT } from '@angular/common';
import { booleanAttribute, Component, DestroyRef, effect, inject, input, model, output } from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Overlay } from '../overlay/overlay';
import { Button } from '../button/button';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroXMarkSolid } from '@ng-icons/heroicons/solid';
import { TranslocoPipe } from '@jsverse/transloco';

@Component({
  selector: 'bip-modal',
  imports: [Overlay, CdkTrapFocus, Button, NgIcon, TranslocoPipe],
  host: { '(document:keydown.escape)': 'onEscape()' },
  providers: [provideIcons({ heroXMarkSolid })],
  templateUrl: './modal.html',
})
export class Modal {
  readonly opened = model(false);
  readonly title = input.required<string>();
  readonly showTitle = input(true, { transform: booleanAttribute });
  readonly showFooter = input(true, { transform: booleanAttribute });
  readonly subtitle = input<string>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly dismissible = input(true, { transform: booleanAttribute });
  /** Un clic sur le voile ferme — désactivé pour les décisions engageantes. */
  readonly backdropDismissible = input(true, { transform: booleanAttribute });
  readonly closed = output<void>();

  private readonly document = inject(DOCUMENT);
  /** Vrai tant que cette modale tient le verrou de défilement du body. */
  private locked = false;

  constructor() {
    // Verrou de défilement du body tant que la modale est ouverte.
    effect(() => {
      this.locked = this.opened();
      this.document.body.style.overflow = this.locked ? 'hidden' : '';
    });
    // Une modale retirée du DOM (ex. sous un `@if`) ne repasse jamais par l'effet : on libère le verrou ici.
    inject(DestroyRef).onDestroy(() => {
      if (this.locked) {
        this.document.body.style.overflow = '';
      }
    });
  }

  open(): void {
    this.opened.set(true);
  }

  close(): void {
    this.opened.set(false);
    // Libération immédiate : le parent peut détruire la modale avant que l'effet ne repasse.
    if (this.locked) {
      this.locked = false;
      this.document.body.style.overflow = '';
    }
    this.closed.emit();
  }

  protected panelClass(): string {
    const sizes = { sm: 'max-w-md', md: 'sm:max-w-lg', lg: 'sm:max-w-2xl' } as const;
    return [
      'relative z-50 flex max-h-[90vh] w-full flex-col bg-surface-card shadow-panel rounded-panel', sizes[this.size()],
    ].join(' ');
  }

  protected onBackdrop(): void {
    if (this.backdropDismissible()) {
      this.close();
    }
  }

  protected onEscape(): void {
    if (this.opened() && this.dismissible()) {
      this.close();
    }
  }
}
