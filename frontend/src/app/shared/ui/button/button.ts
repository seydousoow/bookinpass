import {
  booleanAttribute,
  Component,
  computed,
  ElementRef,
  inject,
  input,
} from '@angular/core';
import { Spinner } from '../spinner/spinner';

export type ButtonVariant =
  | 'primary'
  | 'secondary'
  | 'outline'
  | 'ghost'
  | 'accent'
  | 'success'
  | 'danger'
  | 'warning'
  | 'link';

export type ButtonSize = 'sm' | 'md' | 'lg';

/**
 * Bouton — s'applique sur un vrai `<button>` ou `<a>` : la sémantique native,
 * le focus et l'état `disabled` viennent du navigateur, pas d'un `role`
 * recopié. Toutes les valeurs viennent des tokens (plan §3).
 * Hauteur minimale 44px sur `md`/`lg` — cible tactile du cahier §3.2.
 */
@Component({
  selector: 'button[bipButton], a[bipButton]',
  imports: [Spinner],
  host: {
    '[class]': 'hostClass()',
    '[attr.aria-busy]': 'loading() || null',
    '[attr.aria-disabled]': 'inert() || null',
    // Sur un <button>, on repasse par l'attribut natif : un signal input nommé
    // `disabled` masque la propriété DOM, le navigateur ne bloquerait rien.
    '[attr.disabled]': 'isButton && inert() ? "" : null',
    '[attr.tabindex]': '!isButton && inert() ? -1 : null',
    '(click)': 'onClick($event)',
  },
  template: `
    @if (loading()) {
      <bip-spinner [size]="size() === 'sm' ? 'xs' : 'sm'" [label]="loadingLabel()" />
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
  readonly loadingLabel = input('Traitement en cours');

  /** `<a bipButton>` n'accepte pas `disabled` : il faut alors bloquer à la main. */
  protected readonly isButton =
    (inject(ElementRef).nativeElement as HTMLElement).tagName === 'BUTTON';

  /** Ni cliquable ni soumettable : désactivé, ou soumission déjà en cours. */
  protected readonly inert = computed(() => this.disabled() || this.loading());

  protected readonly hostClass = computed(() => {
    const base = [
      'inline-flex items-center justify-center gap-2 border font-semibold',
      'transition-colors bip-focusable select-none whitespace-nowrap',
    ];

    if (this.block()) {
      base.push('w-full');
    }

    if (this.variant() === 'link') {
      base.push(
        'border-transparent bg-transparent p-0 text-brand-primary underline underline-offset-4',
        'hover:text-brand-dark min-h-0 rounded-button',
        this.size() === 'sm' ? 'text-small' : 'text-body',
      );
    } else {
      const sizes = {
        sm: 'min-h-8 rounded-button px-3 py-1.5 text-small',
        md: 'min-h-11 rounded-button px-4 py-2.5 text-small',
        lg: 'min-h-12 rounded-button px-6 py-3 text-body',
      } as const;
      base.push(sizes[this.size()]);

      const variants = {
        primary:
          'border-brand-primary bg-brand-primary text-onbrand hover:bg-brand-dark hover:border-brand-dark',
        secondary:
          'border-brand-soft bg-brand-soft text-brand-dark hover:bg-brand-primary hover:border-brand-primary hover:text-onbrand',
        outline:
          'border-border-strong bg-surface-card text-ink hover:border-brand-primary hover:text-brand-primary',
        ghost:
          'border-transparent bg-transparent text-ink hover:bg-brand-soft hover:text-brand-dark',
        accent:
          'border-brand-accent bg-brand-accent text-onbrand hover:brightness-95',
        success:
          'border-state-success bg-state-success text-onbrand hover:brightness-95',
        danger: 'border-state-error bg-state-error text-onbrand hover:brightness-95',
        warning: 'border-state-warning bg-state-warning text-ink hover:brightness-95',
        link: '',
      } as const;
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

  /**
   * AC04 — le verrouillage d'interface ne suffit pas à lui seul (deux onglets le
   * contournent : la clé d'idempotence reste indispensable), mais il est la
   * première barrière contre le double clic. Sur `<button>` le blocage est
   * natif ; ce garde-fou couvre le cas `<a bipButton>`.
   */
  protected onClick(event: Event): void {
    if (this.inert()) {
      event.preventDefault();
      event.stopImmediatePropagation();
    }
  }
}
