import { Component, computed, DestroyRef, effect, ElementRef, inject, input, output, signal, untracked, viewChild } from '@angular/core';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { TranslocoLocaleService } from '@jsverse/transloco-locale';
import {
  loadStripe,
  Stripe,
  StripeCheckoutForm,
  StripeCheckoutFormConfirmEvent,
  StripeCheckoutFormLoadActionsSuccess,
  StripeCheckoutSession,
} from '@stripe/stripe-js';
import { Spinner } from '@shared/ui';
import { formatMoney } from '@core/money/money';
import { ICheckoutSession } from '../payment.service';

/** Paiement autorisé (pas encore capturé) : la commande est créée avec, puis le paiement capturé. */
export interface IPaymentAuthorisation {
  readonly checkoutSessionId: string;
}

type TPhase = 'loading' | 'ready' | 'processing' | 'authorised' | 'error';

/** Apparence réglée dans Checkout Studio (iframe Stripe : les variables CSS du site n'y sont pas visibles). */
const APPEARANCE = {
  theme: 'stripe' as const,
  labels: 'floating' as const,
  inputs: 'condensed' as const,
  variables: {
    borderRadius: '4px',
    colorBackground: '#ffffff',
    colorDanger: '#df1b41',
    colorPrimary: '#0570de',
    colorSuccess: '#00c853',
    colorText: '#30313d',
    fontFamily: 'Inter',
    fontSizeBase: '14px',
    spacingUnit: '4px',
  },
};

/**
 * Paiement Stripe, commun à toutes les réservations (vols, hébergements…) : formulaire Checkout intégré (iframe
 * Stripe avec son propre bouton « Payer » ; la carte ne passe jamais par Book In Pass), 3DS et moyens à redirection
 * gérés par Stripe.
 *
 * Usage : `<bip-payment-form [session]="session" (authorised)="book($event)"/>` — la session vient du backend, qui
 * en fixe le montant. Une session déjà payée (retour d'un moyen à redirection, rechargement) est reconnue au montage.
 */
@Component({
  selector: 'bip-payment-form',
  imports: [TranslocoDirective, Spinner],
  host: { class: 'block' },
  templateUrl: './payment-form.html',
})
export class PaymentForm {
  readonly session = input.required<ICheckoutSession>();
  readonly authorised = output<IPaymentAuthorisation>();

  protected readonly phase = signal<TPhase>('loading');
  /** Clé de traduction, ou message déjà traduit par Stripe (refus de paiement). */
  protected readonly error = signal<{ key?: string; text?: string } | null>(null);
  private readonly locale = inject(TranslocoLocaleService);
  private readonly lang = inject(TranslocoService).activeLang;
  /** Montant exact débité, dans la devise de l'offre (pas de conversion d'affichage ici). */
  protected readonly amount = computed(() => {
    this.lang();
    return formatMoney(Number(this.session().amount), this.session().currency, this.locale.getLocale());
  });

  private readonly host = viewChild.required<ElementRef<HTMLElement>>('element');
  private stripe: Stripe | null = null;
  private form: StripeCheckoutForm | null = null;
  private destroyed = false;

  constructor() {
    inject(DestroyRef).onDestroy(() => {
      this.destroyed = true;
      this.form?.destroy();
    });
    // Nouvelle session (autre montant, autres services) : formulaire remonté ; même session : rien ne bouge.
    effect(() => {
      const secret = this.session().client_secret;
      untracked(() => void this.mount(secret));
    });
  }

  protected retry(): void {
    void this.mount(this.session().client_secret);
  }

  private async mount(clientSecret: string): Promise<void> {
    this.phase.set('loading');
    this.error.set(null);
    try {
      this.stripe ??= await loadStripe(this.session().publishable_key);
      if (!this.stripe) {
        throw new Error('Stripe.js unavailable');
      }
      if (this.destroyed || clientSecret !== this.session().client_secret) {
        return;
      }
      this.form?.destroy();
      this.form = null;
      const checkout = this.stripe.initCheckoutFormSdk({ clientSecret, appearance: APPEARANCE });
      const loaded = await checkout.loadActions();
      if (this.destroyed || clientSecret !== this.session().client_secret) {
        return;
      }
      if (loaded.type === 'error') {
        this.fail('payment.errors.unavailable');
        return;
      }
      // Déjà payée (retour d'un moyen à redirection) : rien à afficher, on reprend le statut.
      if (this.onStatus(loaded.actions.getSession())) {
        return;
      }
      const form = checkout.createForm({ layout: 'expanded' });
      form.on('ready', () => this.phase() === 'loading' && this.phase.set('ready'));
      form.on('loaderror', () => this.fail('payment.errors.unavailable'));
      form.on('confirm', (event) => void this.confirm(loaded.actions, event));
      form.mount(this.host().nativeElement);
      this.form = form;
    } catch {
      this.fail('payment.errors.unavailable');
    }
  }

  private async confirm(actions: StripeCheckoutFormLoadActionsSuccess, event: StripeCheckoutFormConfirmEvent): Promise<void> {
    this.phase.set('processing');
    this.error.set(null);
    try {
      const result = await actions.confirm({ formConfirmEvent: event, redirect: 'if_required' });
      if (this.destroyed) {
        return;
      }
      if (result.type === 'error') {
        // Message localisé de Stripe (refus, authentification échouée…).
        this.error.set(result.error.message ? { text: result.error.message } : { key: 'payment.errors.declined' });
        this.phase.set('ready');
      } else if (!this.onStatus(result.session)) {
        this.error.set({ key: 'payment.errors.processing' });
        this.phase.set('ready');
      }
    } catch {
      this.error.set({ key: 'payment.errors.declined' });
      this.phase.set('ready');
    }
  }

  /** Session terminée (autorisation obtenue, capture manuelle) : émise une seule fois. Expirée : à recommencer. */
  private onStatus(session: StripeCheckoutSession): boolean {
    if (session.status.type === 'complete') {
      this.form?.destroy();
      this.form = null;
      this.phase.set('authorised');
      this.authorised.emit({ checkoutSessionId: this.session().checkout_session_id });
      return true;
    }
    if (session.status.type === 'expired') {
      this.fail('payment.errors.expired');
      return true;
    }
    return false;
  }

  private fail(key: string): void {
    this.error.set({ key });
    this.phase.set('error');
  }
}
