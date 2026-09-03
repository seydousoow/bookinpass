import {
  booleanAttribute,
  Component,
  computed,
  input,
  model,
  output,
} from '@angular/core';
import { OutsideClickDirective } from '@shared/directives/outside-click.directive';

/**
 * Surcouche générique : un déclencheur, un panneau. Positionnée en absolu dans
 * un conteneur `relative` — pas de CDK Overlay ici, la largeur du panneau doit
 * suivre celle du déclencheur dans la barre de recherche.
 * La fermeture au clic extérieur passe par `bipOutsideClick`.
 */
@Component({
  selector: 'bip-dropdown',
  imports: [OutsideClickDirective],
  host: { class: 'block', '(keydown.escape)': 'close()' },
  template: `
    <div
      class="relative"
      bipOutsideClick
      [outsideClickEnabled]="open()"
      (outsideClick)="close()">
      <button
        type="button"
        [class]="triggerClass()"
        [disabled]="disabled()"
        [attr.aria-expanded]="open()"
        [attr.aria-haspopup]="'listbox'"
        (click)="toggle()">
        <span class="flex min-w-0 flex-1 flex-col items-start text-left">
          @if (label()) {
            <span class="text-label text-muted font-semibold">{{ label() }}</span>
          }
          <span class="text-small w-full truncate font-medium" [class.text-muted]="!value()">
            {{ value() || placeholder() }}
          </span>
        </span>
        <svg
          class="text-muted h-4 w-4 shrink-0 transition-transform"
          [class.rotate-180]="open()"
          viewBox="0 0 20 20"
          fill="currentColor"
          aria-hidden="true">
          <path
            fill-rule="evenodd"
            d="M5.22 8.22a.75.75 0 0 1 1.06 0L10 11.94l3.72-3.72a.75.75 0 1 1 1.06 1.06l-4.25 4.25a.75.75 0 0 1-1.06 0L5.22 9.28a.75.75 0 0 1 0-1.06Z"
            clip-rule="evenodd" />
        </svg>
      </button>

      @if (open()) {
        <div [class]="panelClass()">
          <ng-content />
        </div>
      }
    </div>
  `,
})
export class Dropdown {
  readonly open = model(false);
  readonly label = input<string>();
  readonly value = input<string>();
  readonly placeholder = input('Sélectionner');
  readonly align = input<'start' | 'end'>('start');
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly error = input(false, { transform: booleanAttribute });
  /** Le panneau prend la largeur du déclencheur, ou sa largeur naturelle. */
  readonly panelWidth = input<'trigger' | 'auto'>('trigger');
  readonly closed = output<void>();

  protected readonly triggerClass = computed(() => {
    const base = [
      'flex min-h-11 w-full items-center gap-2 rounded-field border bg-surface-card px-3 py-2',
      'text-left transition-colors bip-focusable',
    ];
    base.push(
      this.error()
        ? 'border-state-error'
        : this.open()
          ? 'border-brand-primary'
          : 'border-border-strong hover:border-brand-primary/60',
    );
    base.push(this.disabled() ? 'cursor-not-allowed opacity-60' : 'cursor-pointer');
    return base.join(' ');
  });

  protected readonly panelClass = computed(() =>
    [
      'absolute top-full z-30 mt-1 rounded-panel border border-border-subtle bg-surface-card shadow-panel',
      this.panelWidth() === 'trigger' ? 'w-full' : 'w-max min-w-full',
      this.align() === 'end' ? 'right-0' : 'left-0',
    ].join(' '),
  );

  toggle(): void {
    if (this.disabled()) {
      return;
    }
    this.open.update((v) => !v);
  }

  close(): void {
    if (this.open()) {
      this.open.set(false);
      this.closed.emit();
    }
  }
}
