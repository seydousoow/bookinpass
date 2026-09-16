// noinspection ES6PreferShortImport

import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { booleanAttribute, Component, effect, inject, input, model, output, PLATFORM_ID } from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Overlay } from '../overlay/overlay';
import { Button } from '../button/button';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroXMarkSolid } from '@ng-icons/heroicons/solid';

@Component({
  selector: 'bip-modal',
  imports: [Overlay, CdkTrapFocus, Button, NgIcon],
  host: { '(document:keydown.escape)': 'onEscape()' },
  providers: [provideIcons({ heroXMarkSolid })],
  templateUrl: './modal.html',
})
export class Modal {
  readonly opened = model(false);
  readonly title = input.required<string>();
  readonly subtitle = input<string>();
  readonly size = input<'sm' | 'md' | 'lg'>('md');
  readonly dismissible = input(true, { transform: booleanAttribute });
  /** Un clic sur le voile ferme — désactivé pour les décisions engageantes. */
  readonly backdropDismissible = input(true, { transform: booleanAttribute });
  readonly closed = output<void>();

  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  constructor() {
    // Verrou de défilement — navigateur uniquement (plan §10.3).
    effect(() => {
      if (!this.isBrowser) {
        return;
      }
      this.document.body.style.overflow = this.opened() ? 'hidden' : '';
    });
  }

  open(): void {
    this.opened.set(true);
  }

  close(): void {
    this.opened.set(false);
    this.closed.emit();
  }

  protected panelClass(): string {
    const sizes = { sm: 'sm:max-w-md', md: 'sm:max-w-lg', lg: 'sm:max-w-2xl' } as const;
    return [
      'relative z-50 flex max-h-[90vh] w-full flex-col rounded-t-panel bg-surface-card',
      'shadow-panel sm:rounded-panel',
      sizes[this.size()],
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
