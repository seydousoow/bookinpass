// noinspection ES6PreferShortImport

import { booleanAttribute, Component, computed, ElementRef, HostListener, inject, input, output } from '@angular/core';
import { Spinner } from '../spinner/spinner';
import { translateSignal } from '@jsverse/transloco';

export type ButtonVariant = 'primary' | 'secondary' | 'outline' | 'ghost' | 'accent' | 'success' | 'danger' | 'warning' | 'link';

export type ButtonSize = 'sm' | 'md' | 'lg';

const sizes = {
  sm: 'min-h-8 rounded-button px-3 py-1.5 text-xs',
  md: 'min-h-10 rounded-button px-4 py-2 text-sm',
  lg: 'min-h-12 rounded-button px-6 py-3 text-base',
} as const;

const variants = {
  primary: 'border-brand-primary bg-brand-primary text-onbrand hover:bg-brand-dark hover:border-brand-dark',
  secondary: 'border-brand-soft bg-brand-soft text-brand-dark hover:bg-brand-primary hover:border-brand-primary hover:text-onbrand',
  outline: 'border-border-strong text-brand-primary bg-surface-card hover:border-brand-primary hover:bg-onbrand',
  ghost: 'border-transparent bg-transparent text-ink hover:bg-brand-soft hover:text-brand-dark',
  accent: 'border-brand-accent bg-brand-accent text-onbrand hover:brightness-95',
  success: 'border-state-success bg-state-success text-onbrand hover:brightness-95',
  danger: 'border-state-error bg-state-error text-onbrand hover:brightness-95',
  warning: 'border-state-warning bg-state-warning text-ink hover:brightness-95',
  link: '',
} as const;

/**
 * Bouton — s'applique sur un vrai `<button>` ou `<a>` : la sémantique native,
 * le focus et l'état `disabled` viennent du navigateur, pas d'un `role`
 * recopié. Toutes les valeurs viennent des tokens (plan §3).
 * Hauteur minimale 44px sur `md'/'lg` — cible tactile du cahier §3.2.
 */
@Component({
  selector: 'bip-button, button[bipButton], a[bipButton]',
  imports: [Spinner],
  host: {
    '[class]': 'hostClass()',
    '[class.w-full]': 'block()',
    '[attr.aria-busy]': 'loading() || null',
    '[attr.tabindex]': '!isButton && nonInteractive() ? -1 : 0',
    '[attr.aria-expanded]': 'expanded()',
    '[attr.type]': 'type()',
    'attr.role': 'button'
  },
  template: `
    @if (loading()) {
      <bip-spinner [label]="loadingLabel()" [size]="size() === 'sm' ? 'xs' : 'sm'" />
    }
    <ng-content />
  `,
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');
  readonly size = input<ButtonSize>('md');
  readonly loading = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly block = input(false, { transform: booleanAttribute });
  readonly loadingLabel = input(translateSignal('common.loading')());
  readonly expanded = input(false, { transform: booleanAttribute });
  readonly type = input<'submit' | 'reset' | 'button'>('button');
  readonly clicked = output();

  /** '<a bipButton>` n'accepte pas `disabled` : il faut alors bloquer à la main. */
  protected readonly isButton = (inject(ElementRef).nativeElement as HTMLElement).tagName === 'BUTTON';

  /** Ni cliquable ni soumettable : désactivé, ou soumission déjà en cours. */
  protected readonly nonInteractive = computed(() => this.disabled() || this.loading());

  protected readonly hostClass = computed(() => {
    const base = ['inline-flex items-center justify-center gap-2 border font-semibold transition-colors bip-focusable select-none whitespace-nowrap'];

    if (this.variant() === 'link') {
      base.push(
        'border-transparent bg-transparent p-0 text-brand-primary underline underline-offset-4 hover:text-brand-dark min-h-0 rounded-button',
        this.size() === 'sm' ? 'text-sm' : 'text-base',
      );
    } else {
      base.push(sizes[this.size()]);
      base.push(variants[this.variant()]);
    }

    if (this.disabled()) {
      base.push('cursor-not-allowed opacity-50');
    } else if (this.loading()) {
      base.push('cursor-wait opacity-90');
    } else {
      base.push('cursor-pointer');
    }

    return base.join(' ');
  });

  @HostListener('click', ['$event'])
  protected onClick(event: MouseEvent): void {
    if (this.nonInteractive()) {
      event.preventDefault();
      event.stopPropagation();
      return;
    }

    this.clicked.emit();
  }

  @HostListener('keydown', ['$event'])
  protected onKeyDown(event: KeyboardEvent): void {
    if (this.nonInteractive()) {
      return;
    }

    if (event.key === 'Enter') {
      event.preventDefault();
      this.clicked.emit();
    } else if (event.key === ' ' || event.key === 'Spacebar') {
      event.preventDefault();
    }
  }

  @HostListener('keyup', ['$event'])
  protected onKeyUp(event: KeyboardEvent): void {
    if (this.nonInteractive()) {
      return;
    }

    if (event.key === ' ' || event.key === 'Spacebar') {
      event.preventDefault();
      this.clicked.emit();
    }
  }
}
