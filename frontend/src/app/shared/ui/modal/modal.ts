import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import {
  booleanAttribute,
  Component,
  effect,
  inject,
  input,
  model,
  output,
  PLATFORM_ID,
} from '@angular/core';
import { CdkTrapFocus } from '@angular/cdk/a11y';
import { Overlay } from '../overlay/overlay';
import { Button } from '../button/button';

@Component({
  selector: 'bip-modal',
  imports: [Overlay, CdkTrapFocus, Button],
  host: { '(document:keydown.escape)': 'onEscape()' },
  template: `
    @if (opened()) {
      <div class="fixed inset-0 z-50 flex items-end justify-center sm:items-center" role="presentation">
        <bip-overlay (clicked)="onBackdrop()" />
        <div
          cdkTrapFocus
          [cdkTrapFocusAutoCapture]="true"
          role="dialog"
          aria-modal="true"
          [attr.aria-label]="title()"
          [class]="panelClass()">
          <header class="flex items-start justify-between gap-4 px-5 pt-5">
            <div class="min-w-0">
              <h2 class="text-h3 text-muted">{{ title() }}</h2>
              @if (subtitle()) {
                <p class="text-small text-muted mt-1">{{ subtitle() }}</p>
              }
            </div>
            @if (dismissible()) {
              <button bipButton variant="ghost" size="sm" type="button" (click)="close()">
                <span class="sr-only">Fermer</span>
                <svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                  <path
                    d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
                </svg>
              </button>
            }
          </header>

          <div class="flex-1 overflow-y-auto px-5 py-4">
            <ng-content />
          </div>

          <footer class="border-border-subtle flex flex-wrap justify-end gap-2 border-t px-5 py-4">
            <ng-content select="[modalFooter]" />
          </footer>
        </div>
      </div>
    }
  `,
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

  protected panelClass(): string {
    const sizes = { sm: 'sm:max-w-md', md: 'sm:max-w-lg', lg: 'sm:max-w-2xl' } as const;
    return [
      'relative z-50 flex max-h-[90vh] w-full flex-col rounded-t-panel bg-surface-card',
      'shadow-panel sm:rounded-panel',
      sizes[this.size()],
    ].join(' ');
  }

  open(): void {
    this.opened.set(true);
  }

  close(): void {
    this.opened.set(false);
    this.closed.emit();
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
