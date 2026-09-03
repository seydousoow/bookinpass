import { Component, inject, signal } from '@angular/core';
import { JsonPipe } from '@angular/common';
import { FormControl, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { AirportStore } from '@core/places/airport.store';
import { appError } from '@shared/models/app-error';
import { CurrencyStore } from '@core/money/currency.store';
import { PricePipe } from '@core/money/price.pipe';
import { TranslocoCurrencyPipe, TranslocoDatePipe } from '@jsverse/transloco-locale';
import { formatXof } from '@core/money/money';
import { SystemStatus } from '@shared/models/system-state';
import { SelectOption } from '@shared/models/select.model';
import { PlaceSelection } from '@shared/models/place.model';
import {
  Badge,
  BadgeTone,
  Button,
  ButtonSize,
  ButtonVariant,
  Card,
  Checkbox,
  DatePicker,
  DateRangeValue,
  Dropdown,
  LocationField,
  Modal,
  PassengerCount,
  PeopleSelector,
  RadioGroup,
  Select,
  Spinner,
  SystemState,
  Textarea,
  TextInput,
} from '@shared/ui';

/**
 * Planche de validation du socle. Chaque composant partagé y figure dans TOUS
 * ses états — c'est la revue visuelle avant que la moindre feature ne démarre
 * (plan §4 : « tant que le lot 0 n'est pas stabilisé, aucune feature ne commence »).
 */
@Component({
  selector: 'bip-ui-kit-page',
  imports: [
    JsonPipe,
    PricePipe,
    TranslocoCurrencyPipe,
    TranslocoDatePipe,
    FormsModule,
    ReactiveFormsModule,
    Badge,
    Button,
    Card,
    Checkbox,
    DatePicker,
    Dropdown,
    TextInput,
    LocationField,
    Modal,
    PeopleSelector,
    RadioGroup,
    Select,
    Spinner,
    SystemState,
    Textarea,
  ],
  templateUrl: './ui-kit.page.html',
})
export class UiKitPage {
  protected readonly airports = inject(AirportStore);
  protected readonly currency = inject(CurrencyStore);

  protected readonly price = 185_000;
  protected readonly oldPrice = 185_000;
  protected readonly newPrice = 197_500;

  protected readonly swatches = [
    { name: 'brand-primary', hex: '#5125D7', class: 'bg-brand-primary' },
    { name: 'brand-dark', hex: '#32106F', class: 'bg-brand-dark' },
    { name: 'brand-accent', hex: '#FF6B1A', class: 'bg-brand-accent' },
    { name: 'brand-soft', hex: '#EEE9FF', class: 'bg-brand-soft' },
    { name: 'surface-page', hex: '#F5F3FB', class: 'bg-surface-page' },
    { name: 'ink', hex: '#171428', class: 'bg-ink' },
    { name: 'muted', hex: '#706B82', class: 'bg-muted' },
    { name: 'success', hex: '#158C69', class: 'bg-state-success' },
    { name: 'warning', hex: '#F6B83F', class: 'bg-state-warning' },
    { name: 'error', hex: '#D64557', class: 'bg-state-error' },
  ];

  protected readonly radii = [
    { name: 'button 10', class: 'rounded-button' },
    { name: 'field 12', class: 'rounded-field' },
    { name: 'card 14', class: 'rounded-card' },
    { name: 'panel 18', class: 'rounded-panel' },
    { name: 'pill', class: 'rounded-pill' },
  ];

  protected readonly buttonVariants: readonly ButtonVariant[] = [
    'primary',
    'secondary',
    'outline',
    'ghost',
    'accent',
    'success',
    'danger',
    'warning',
    'link',
  ];
  protected readonly buttonSizes: readonly ButtonSize[] = ['sm', 'md', 'lg'];
  protected readonly buttonStates = ['normal', 'chargement', 'désactivé'] as const;
  protected readonly badgeTones: readonly BadgeTone[] = ['neutral', 'brand', 'accent', 'success', 'warning', 'error', 'info'];

  protected readonly cabins: readonly SelectOption[] = [
    { label: 'Économique', value: 'economy' },
    { label: 'Premium', value: 'premium', hint: 'Plus d’espace' },
    { label: 'Affaires', value: 'business' },
    { label: 'Première', value: 'first', disabled: true },
  ];

  protected readonly tripTypes: readonly SelectOption[] = [
    { label: 'Aller-retour', value: 'round' },
    { label: 'Aller simple', value: 'oneway' },
  ];

  protected readonly airlines: readonly SelectOption[] = [
    { label: 'Air Sénégal', value: 'HC' },
    { label: 'Air France', value: 'AF' },
    { label: 'Brussels Airlines', value: 'SN' },
    { label: 'Royal Air Maroc', value: 'AT' },
    { label: 'Turkish Airlines', value: 'TK' },
    { label: 'TAP Air Portugal', value: 'TP' },
  ];

  protected readonly sortOptions: readonly SelectOption[] = [
    { label: 'Prix croissant', value: 'price-asc' },
    { label: 'Durée la plus courte', value: 'duration' },
    { label: 'Départ le plus tôt', value: 'departure' },
  ];

  protected readonly allStates: readonly SystemStatus[] = ['idle', 'loading', 'success', 'empty', 'expired', 'partial', 'error', 'offline'];

  protected readonly sampleError = appError(
    'PAYMENT_DECLINED',
    'Le paiement a été refusé par votre banque.',
    'Votre commande et vos voyageurs sont enregistrés.',
    { label: 'Réessayer', kind: 'retry' },
  );

  protected readonly sampleOffers = [
    { id: 1, carrier: 'HC', route: 'DSS → CDG', detail: 'Direct · 5 h 45', price: formatXof(385000) },
    { id: 2, carrier: 'AT', route: 'DSS → CDG', detail: '1 escale · 9 h 10', price: formatXof(298000) },
  ];

  protected readonly blockedDates = ['2026-09-10', '2026-09-11', '2026-09-24'];

  protected readonly giftMessage = signal('');
  protected readonly directOnly = signal(true);
  protected readonly cabinRadio = signal<string | null>('economy');
  protected readonly tripType = signal<string | null>('round');
  protected readonly cabinSelect = signal<string[]>(['economy']);
  protected readonly airlineSelect = signal<string[]>(['HC', 'AF']);
  protected readonly sort = signal('price-asc');
  protected readonly singleDate = signal<DateRangeValue>({ start: null, end: null });
  protected readonly rangeDate = signal<DateRangeValue>({ start: null, end: null });
  protected readonly passengers = signal<PassengerCount>({ adults: 2, children: 1, infants: 0 });
  protected readonly from = signal<PlaceSelection | null>(null);
  protected readonly to = signal<PlaceSelection | null>(null);
  protected readonly demoState = signal<SystemStatus>('success');
  protected readonly reactiveName = new FormControl('Aminata Diallo');

  protected sortLabel(): string {
    return this.sortOptions.find((o) => o.value === this.sort())?.label ?? '';
  }
}
