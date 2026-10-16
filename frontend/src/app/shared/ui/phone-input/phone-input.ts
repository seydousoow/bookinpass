// noinspection ES6PreferShortImport

import {
  afterNextRender,
  afterRenderEffect,
  booleanAttribute,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  model,
  output,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { Combobox, ComboboxPopup, ComboboxWidget } from '@angular/aria/combobox';
import { Listbox, Option } from '@angular/aria/listbox';
import { CdkConnectedOverlay } from '@angular/cdk/overlay';
import { FormValueControl, ValidationError, WithOptionalFieldTree } from '@angular/forms/signals';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroCheckSolid, heroChevronDownSolid } from '@ng-icons/heroicons/solid';
import { AsYouType, CountryCode, getCountries, getCountryCallingCode, isValidPhoneNumber, parsePhoneNumberFromString } from 'libphonenumber-js/min';
import { Field } from '../field/field';

/** Numéro E.164 valide (`+221771234567`) : à utiliser dans le schéma du formulaire, avec `validate`. */
export function isValidPhone(e164: string | null | undefined): boolean {
  return !!e164 && isValidPhoneNumber(e164);
}

interface ICountryOption {
  readonly code: CountryCode;
  readonly name: string;
  /** Indicatif, sans le `+`. */
  readonly dial: string;
}

const digits = (text: string): string => text.replace(/\D/g, '');
const normalize = (text: string): string =>
  text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase();

/** Numéro national affichable : forme officielle s'il est complet (`06 12 34 56 78`), sinon mise en forme partielle. */
function formatNational(national: string, country: CountryCode): string {
  const parsed = national ? parsePhoneNumberFromString(national, country) : undefined;
  return parsed?.isPossible() ? parsed.formatNational() : new AsYouType(country).input(national);
}

/**
 * Téléphone international : indicatif (drapeau + `+221`) à gauche, numéro mis en forme à la frappe à droite
 * (libphonenumber-js). La valeur est le numéro au format E.164 (`+221771234567`), vide sans chiffre ; elle peut être
 * invalide pendant la saisie — la validation reste dans le schéma (`isValidPhone`).
 *
 * Un numéro collé avec son indicatif (`+33 6 12…`) bascule le pays. Drapeaux : SVG de country-flag-icons servis en
 * fichiers (`/svg/SN.svg`, asset de `angular.json`), pas embarqués dans le bundle.
 */
@Component({
  selector: 'bip-phone-input',
  imports: [Field, Combobox, ComboboxPopup, ComboboxWidget, Listbox, Option, CdkConnectedOverlay, TranslocoPipe, NgIcon],
  host: { class: 'block' },
  templateUrl: './phone-input.html',
  providers: [provideIcons({ heroCheckSolid, heroChevronDownSolid })],
})
export class PhoneInput implements FormValueControl<string> {
  readonly label = input.required<string>();
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly hint = input<string>();
  /** Pays proposé tant que le numéro n'en indique pas un autre. */
  readonly defaultCountry = input<CountryCode>('SN');
  /** `tel` : la saisie automatique du navigateur fournit le numéro international, dont l'indicatif fixe le pays. */
  readonly autocomplete = input('tel');

  readonly name = input('phone');
  readonly touched = input(false, { transform: booleanAttribute });
  readonly invalid = input(false, { transform: booleanAttribute });
  readonly required = input(false, { transform: booleanAttribute });
  readonly disabled = input(false, { transform: booleanAttribute });
  readonly errors = input<readonly WithOptionalFieldTree<ValidationError>[]>([]);
  readonly touch = output<void>();
  readonly value = model<string>('');

  protected readonly open = signal(false);
  protected readonly country = signal<CountryCode | null>(null);
  protected readonly selected = computed(() => this.country() ?? this.defaultCountry());
  protected readonly dial = computed(() => getCountryCallingCode(this.selected()));
  /** Numéro national tel qu'affiché (mis en forme). */
  protected readonly national = signal('');
  protected readonly showErrors = computed(() => this.touched() && this.invalid());
  /** Recherche dans la liste : nom (sans accents ni casse), indicatif (`33`, `+33`) ou code ISO (`FR`). */
  protected readonly query = signal('');
  protected readonly filtered = computed(() => {
    const query = normalize(this.query().trim());
    if (!query) {
      return this.countries();
    }
    const dial = digits(query);
    return this.countries().filter(
      (c) =>
        normalize(c.name).includes(query) || c.code.toLowerCase() === query || (!!dial && dial === query.replace('+', '') && c.dial.startsWith(dial)),
    );
  });
  protected readonly selectedName = computed(() => this.countries().find((c) => c.code === this.selected())?.name ?? this.selected());
  private readonly lang = inject(TranslocoService).activeLang;
  protected readonly countries = computed((): ICountryOption[] => {
    const names = new Intl.DisplayNames([this.lang()], { type: 'region' });
    return getCountries()
      .map((code) => ({ code, name: names.of(code) ?? code, dial: getCountryCallingCode(code) }))
      .sort((a, b) => a.name.localeCompare(b.name, this.lang()));
  });
  private readonly field = viewChild.required<ElementRef<HTMLInputElement>>('field');
  private readonly listBox = viewChild(Listbox);
  private readonly searchField = viewChild<ElementRef<HTMLInputElement>>('searchField');
  private readonly trigger = viewChild.required<ElementRef<HTMLButtonElement>>('trigger');
  private readonly injector = inject(Injector);
  /** Dernière valeur émise : une valeur venue de l'extérieur (brouillon, saisie automatique) est relue, pas celle-ci. */
  private emitted = '';

  constructor() {
    afterRenderEffect(() => this.listBox()?.scrollActiveItemIntoView());
    effect(() => {
      const value = this.value() ?? '';
      if (value !== this.emitted) {
        untracked(() => this.readValue(value));
      }
    });
  }

  protected toggle(): void {
    if (this.open()) {
      this.close();
      return;
    }
    this.query.set('');
    this.open.set(true);
    afterNextRender(() => this.searchField()?.nativeElement.focus(), { injector: this.injector });
  }

  /** Clic hors de la liste ; celui sur le bouton est déjà traité par `toggle` (il l'aurait sinon refermée aussitôt). */
  protected onOutsideClick(event: MouseEvent): void {
    if (!this.trigger().nativeElement.contains(event.target as Node)) {
      this.close();
    }
  }

  /** @param refocus Échap : le focus revient sur le bouton de l'indicatif. */
  protected close(refocus = false): void {
    if (!this.open()) {
      return;
    }
    this.open.set(false);
    if (refocus) {
      this.trigger().nativeElement.focus();
    }
  }

  /**
   * Pays choisi : le numéro est remis en forme pour lui. Une sélection vide (pays courant masqué par la recherche)
   * est ignorée — elle ne vaut pas choix.
   */
  protected pick(codes: readonly CountryCode[]): void {
    const code = codes[0];
    if (!code || code === this.selected()) {
      return;
    }
    this.country.set(code);
    this.national.set(formatNational(digits(this.national()), code));
    this.emit();
  }

  /** Clic ou Entrée sur un pays : après la sélection (traitée par la liste), on ferme et la saisie reprend. */
  protected commit(): void {
    setTimeout(() => {
      if (this.open()) {
        this.open.set(false);
        this.field().nativeElement.focus();
      }
    });
  }

  protected onInput(event: Event): void {
    const element = event.target as HTMLInputElement;
    const text = element.value;
    if (text.trim().startsWith('+')) {
      // Numéro international collé ou tapé : son indicatif décide du pays.
      const typed = new AsYouType();
      typed.input(text);
      const detected = typed.getCountry();
      if (detected) {
        this.country.set(detected);
        this.setDisplay(element, formatNational(typed.getNumber()?.nationalNumber ?? '', detected));
      } else {
        this.national.set(text);
      }
      this.emit();
      return;
    }
    // Effacement ou saisie au milieu : pas de remise en forme (le curseur sauterait) ; elle se fait en quittant le champ.
    const atEnd = element.selectionStart === text.length;
    const deleting = (event as InputEvent).inputType?.startsWith('delete');
    if (atEnd && !deleting) {
      this.setDisplay(element, new AsYouType(this.selected()).input(digits(text)));
    } else {
      this.national.set(text);
    }
    this.emit();
  }

  protected onBlur(): void {
    this.national.set(formatNational(digits(this.national()), this.selected()));
    this.touch.emit();
  }

  protected controlClass(): string {
    const base = [
      'relative flex min-h-11 w-full items-stretch rounded-field border bg-surface-card transition-colors focus-within:ring-2 focus-within:ring-brand-primary/40',
    ];
    if (this.showErrors()) {
      base.push('border-state-error');
    } else {
      base.push('border-border-strong hover:border-brand-primary/60');
    }
    if (this.disabled()) {
      base.push('bg-surface-muted cursor-not-allowed opacity-60');
    }
    return base.join(' ');
  }

  protected optionClass(active: boolean): string {
    return ['flex items-center gap-2 p-2 text-xs', active ? 'bg-brand-soft text-brand-dark' : 'hover:bg-brand-soft/60'].join(' ');
  }

  private setDisplay(element: HTMLInputElement, formatted: string): void {
    this.national.set(formatted);
    element.value = formatted;
  }

  private emit(): void {
    const national = digits(this.national());
    const parsed = national ? parsePhoneNumberFromString(national, this.selected()) : undefined;
    // Numéro incomplet : on émet tout de même indicatif + chiffres, pour que la validation le signale.
    this.emitted = national ? (parsed?.number ?? `+${this.dial()}${national}`) : '';
    this.value.set(this.emitted);
  }

  private readValue(value: string): void {
    this.emitted = value;
    const parsed = value ? parsePhoneNumberFromString(value) : undefined;
    if (parsed?.country) {
      this.country.set(parsed.country);
      this.national.set(formatNational(parsed.nationalNumber, parsed.country));
    } else {
      this.national.set(value.startsWith('+') ? value : formatNational(digits(value), this.selected()));
    }
  }
}
