import { Component, computed, inject, input, linkedSignal, output, viewChild } from '@angular/core';
import { form, FormField, FormRoot, maxLength, metadata, pattern, REQUIRED, required, validate } from '@angular/forms/signals';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroXMark } from '@ng-icons/heroicons/outline';
import { Button, Modal, Select, TextInput, todayIso } from '@shared/ui';
import { COUNTRY_CODES, ILoyaltyProgrammeAccount, ISelectOption, TPassengerType } from '@shared/models';
import { AirlineStore } from '@core/reference/airline.store';

export type TPassengerTitle = 'mr' | 'ms' | 'miss';
export type TPassengerGender = 'm' | 'f';

/** Identité d'un voyageur, au format attendu par la création de commande Duffel. */
export interface ITravelerInfo {
  title: TPassengerTitle;
  gender: TPassengerGender;
  given_name: string;
  family_name: string;
  /** `YYYY-MM-DD` — exigé par Duffel pour chaque voyageur à la création de la commande. */
  born_on: string;
  loyalty_programme_accounts: ILoyaltyProgrammeAccount[];
  /** Passeport, quand l'offre l'exige (`passenger_identity_documents_required`) : transmis à la création de commande. */
  identity_documents?: IIdentityDocument[];
}

/** Pièce d'identité au format Duffel (`passengers[].identity_documents[]` de `POST /air/orders`). */
export interface IIdentityDocument {
  type: 'passport';
  /** Numéro du passeport. */
  unique_identifier: string;
  /** Pays de délivrance, ISO 3166-1 alpha-2. */
  issuing_country_code: string;
  /** `YYYY-MM-DD`. */
  expires_on: string;
}

interface IDateParts {
  day: string;
  month: number[];
  year: string;
}

/** Modèle de saisie : les listes déroulantes travaillent sur des tableaux, la date est éclatée en trois champs. */
interface ITravelerFormModel {
  given_name: string;
  family_name: string;
  gender: TPassengerGender[];
  birth: IDateParts;
  loyalty: { airline: string[]; number: string };
  passport: { number: string; country: string[]; expiry: IDateParts };
}

/**
 * Règle Duffel : espaces, apostrophes, traits d'union et lettres ASCII, Latin-1 et Latin étendu A,
 * sauf Æ æ Ĳ ĳ Œ œ Þ ð. Le backend applique la même.
 */
const NAME_PATTERN =
  /^[A-Za-z\u00C0-\u00C5\u00C7-\u00D6\u00D8-\u00DD\u00DF-\u00E5\u00E7-\u00EF\u00F1-\u00F6\u00F8-\u00FF\u0100-\u0131\u0134-\u0151\u0154-\u017F' -]+$/;
/** Duffel : 20 caractères au plus pour le prénom comme pour le nom. */
const NAME_MAX_LENGTH = 20;
const LOYALTY_NUMBER_PATTERN = /^[A-Za-z0-9-]{1,40}$/;
/** Numéro de passeport : lettres et chiffres, sans espace. */
const PASSPORT_NUMBER_PATTERN = /^[A-Za-z0-9]{5,20}$/;

/** Âge accepté pour chaque type de voyageur, à la date du dernier vol. */
const AGE_RANGES: Record<TPassengerType, { min: number; max: number }> = {
  adult: { min: 12, max: 130 },
  child: { min: 2, max: 11 },
  infant_without_seat: { min: 0, max: 1 },
};

@Component({
  selector: 'bip-traveler-form',
  templateUrl: './traveler-form.html',
  imports: [Modal, Button, TextInput, Select, FormField, FormRoot, TranslocoDirective, NgIcon],
  providers: [provideIcons({ heroXMark })],
})
export class TravelerForm {
  /** Libellé accessible de la modale, ex. « Adulte n° 1 ». */
  readonly heading = input.required<string>();
  readonly passengerType = input.required<TPassengerType>();
  /** Date du dernier vol (ISO) — référence pour le contrôle d'âge. */
  readonly lastFlightDate = input.required<string>();
  /** Informations déjà connues (saisies ici, ou nom déjà enregistré chez Duffel) : la modale s'ouvre préremplie. */
  readonly initial = input<Partial<ITravelerInfo> | undefined>();
  /** Codes IATA des programmes de fidélité acceptés par l'offre. Vide : pas de champ fidélité. */
  readonly loyaltyProgrammes = input<readonly string[]>([]);
  /** L'offre exige une pièce d'identité (`passenger_identity_documents_required`) : passeport demandé. */
  readonly documentsRequired = input(false);
  /** Voyageur validé : la page l'enregistre chez Duffel en arrière-plan, la modale se ferme aussitôt. */
  readonly saved = output<ITravelerInfo>();
  /** Émis à chaque fermeture (✕, Échap ou après « OK »). */
  readonly closed = output<void>();

  private readonly transloco = inject(TranslocoService);
  private readonly lang = this.transloco.activeLang;
  protected readonly genders = computed((): readonly ISelectOption<TPassengerGender>[] => {
    const lang = this.lang();
    return (['m', 'f'] as const).map((value) => ({
      label: this.transloco.translate(`flight.offer.traveler-form.genders.${value}`, {}, lang),
      value,
    }));
  });
  protected readonly months = computed((): readonly ISelectOption<number>[] => {
    const lang = this.lang();
    return Array.from({ length: 12 }, (_, i) => ({ label: this.transloco.translate(`common.months.${i + 1}`, {}, lang), value: i + 1 }));
  });

  /** Pays de délivrance du passeport, nommés dans la langue active et triés par nom. */
  protected readonly countries = computed((): readonly ISelectOption[] => {
    const names = new Intl.DisplayNames([this.lang()], { type: 'region' });
    return COUNTRY_CODES.map((code) => ({ label: names.of(code) ?? code, value: code })).sort((a, b) => a.label.localeCompare(b.label, this.lang()));
  });

  private readonly airlines = inject(AirlineStore).byCode;
  /** « AF · Air France » ; le code seul si la compagnie est absente du référentiel. */
  protected readonly loyaltyOptions = computed((): readonly ISelectOption[] =>
    this.loyaltyProgrammes().map((code) => {
      const name = this.airlines().get(code)?.name;
      return { label: name ? `${code} · ${name}` : code, value: code };
    }),
  );

  /** Fermer passe toujours par la modale : elle seule libère le verrou de défilement du body. */
  private readonly modal = viewChild.required(Modal);

  /** Copie de travail : repart des données enregistrées, rien n'est propagé avant « OK ». */
  private readonly model = linkedSignal<ITravelerFormModel>(() => toFormModel(this.initial()));

  protected readonly travelerForm = form(
    this.model,
    (path) => {
      required(path.given_name, { message: 'common.required-field' });
      required(path.family_name, { message: 'common.required-field' });
      pattern(path.given_name, NAME_PATTERN, { message: 'flight.offer.traveler-form.errors.name' });
      pattern(path.family_name, NAME_PATTERN, { message: 'flight.offer.traveler-form.errors.name' });
      maxLength(path.given_name, NAME_MAX_LENGTH, { message: 'flight.offer.traveler-form.errors.name-length' });
      maxLength(path.family_name, NAME_MAX_LENGTH, { message: 'flight.offer.traveler-form.errors.name-length' });
      // `required()` ne considère pas un tableau vide comme manquant : on pose l'astérisque et l'erreur à la main.
      metadata(path.gender, REQUIRED, () => true);
      validate(path.gender, ({ value }) => (value().length ? null : { kind: 'required', message: 'common.required-field' }));

      maxLength(path.birth.day, 2);
      maxLength(path.birth.year, 4);
      validate(path.birth, ({ value }) => {
        const { day, month, year } = value();
        if (!day || !month.length || !year) {
          return { kind: 'required', message: 'common.required-field' };
        }
        const bornOn = toIsoDate(day, month[0], year);
        if (!bornOn) {
          return { kind: 'invalid-date', message: 'flight.offer.traveler-form.errors.invalid-date' };
        }
        if (bornOn > todayIso()) {
          return { kind: 'future-birth-date', message: 'flight.offer.traveler-form.errors.future' };
        }
        const age = ageOn(bornOn, this.lastFlightDate());
        const { min, max } = AGE_RANGES[this.passengerType()];
        if (age < min || age > max) {
          return { kind: 'age-range', message: `flight.offer.traveler-form.errors.age-${this.passengerType()}` };
        }
        return null;
      });

      // Passeport : exigé seulement si l'offre le demande ; valide au moins jusqu'au dernier vol.
      validate(path.passport.number, ({ value }) => {
        if (!this.documentsRequired()) {
          return null;
        }
        const number = value().trim();
        if (!number) {
          return { kind: 'required', message: 'common.required-field' };
        }
        return PASSPORT_NUMBER_PATTERN.test(number) ? null : { kind: 'pattern', message: 'flight.offer.traveler-form.errors.passport-number' };
      });
      validate(path.passport.country, ({ value }) =>
        this.documentsRequired() && !value().length ? { kind: 'required', message: 'common.required-field' } : null,
      );
      validate(path.passport.expiry, ({ value }) => {
        if (!this.documentsRequired()) {
          return null;
        }
        const { day, month, year } = value();
        if (!day || !month.length || !year) {
          return { kind: 'required', message: 'common.required-field' };
        }
        const expiresOn = toIsoDate(day, month[0], year);
        if (!expiresOn) {
          return { kind: 'invalid-date', message: 'flight.offer.traveler-form.errors.invalid-date' };
        }
        return expiresOn < this.lastFlightDate().slice(0, 10)
          ? { kind: 'expired', message: 'flight.offer.traveler-form.errors.passport-expired' }
          : null;
      });

      // Fidélité facultative, mais compagnie et numéro vont ensemble.
      validate(path.loyalty.airline, ({ value, valueOf }) =>
        !value().length && valueOf(path.loyalty.number).trim() ? { kind: 'required', message: 'common.required-field' } : null,
      );
      validate(path.loyalty.number, ({ value, valueOf }) => {
        const number = value().trim();
        if (!number) {
          return valueOf(path.loyalty.airline).length ? { kind: 'required', message: 'common.required-field' } : null;
        }
        return LOYALTY_NUMBER_PATTERN.test(number) ? null : { kind: 'pattern', message: 'flight.offer.traveler-form.errors.loyalty-number' };
      });
    },
    {
      submission: {
        action: async (f) => {
          this.saved.emit(this.toTravelerInfo(f().value()));
          this.modal().close();
          return null;
        },
        ignoreValidators: 'none',
        onInvalid: (field) => field().errorSummary()[0]?.fieldTree().focusBoundControl(),
      },
    },
  );

  protected close(): void {
    this.modal().close();
  }

  private toTravelerInfo({ given_name, family_name, gender, birth, loyalty, passport }: ITravelerFormModel): ITravelerInfo {
    const sex = gender[0];
    const identity_documents: IIdentityDocument[] | undefined = this.documentsRequired()
      ? [
          {
            type: 'passport',
            unique_identifier: passport.number.trim().toUpperCase(),
            issuing_country_code: passport.country[0],
            expires_on: toIsoDate(passport.expiry.day, passport.expiry.month[0], passport.expiry.year) ?? '',
          },
        ]
      : undefined;
    return {
      ...(identity_documents ? { identity_documents } : {}),
      // Civilité déduite du sexe : « Miss » pour une enfant, « Ms » pour une adulte.
      title: sex === 'm' ? 'mr' : this.passengerType() === 'adult' ? 'ms' : 'miss',
      gender: sex,
      given_name: given_name.trim(),
      family_name: family_name.trim(),
      born_on: toIsoDate(birth.day, birth.month[0], birth.year) ?? '',
      loyalty_programme_accounts:
        loyalty.airline.length && loyalty.number.trim() ? [{ airline_iata_code: loyalty.airline[0], account_number: loyalty.number.trim() }] : [],
    };
  }
}

function toFormModel(info: Partial<ITravelerInfo> | undefined): ITravelerFormModel {
  const account = info?.loyalty_programme_accounts?.[0];
  const passport = info?.identity_documents?.[0];
  return {
    given_name: info?.given_name ?? '',
    family_name: info?.family_name ?? '',
    gender: info?.gender ? [info.gender] : [],
    birth: toDateParts(info?.born_on),
    loyalty: { airline: account ? [account.airline_iata_code] : [], number: account?.account_number ?? '' },
    passport: {
      number: passport?.unique_identifier ?? '',
      country: passport?.issuing_country_code ? [passport.issuing_country_code] : [],
      expiry: toDateParts(passport?.expires_on),
    },
  };
}

/** `YYYY-MM-DD` éclatée pour les trois champs jour / mois / année. */
function toDateParts(iso: string | undefined): IDateParts {
  const [year = '', month = '', day = ''] = iso ? iso.split('-') : [];
  return { day: day ? String(Number(day)) : '', month: month ? [Number(month)] : [], year };
}

/** `YYYY-MM-DD` si les trois parties forment une date réelle (pas de 31 février), sinon `null`. */
function toIsoDate(day: string, month: number, year: string): string | null {
  if (!/^\d{1,2}$/.test(day.trim()) || !/^\d{4}$/.test(year.trim())) {
    return null;
  }
  const d = Number(day);
  const y = Number(year);
  const date = new Date(Date.UTC(y, month - 1, d));
  if (date.getUTCFullYear() !== y || date.getUTCMonth() !== month - 1 || date.getUTCDate() !== d) {
    return null;
  }
  return `${y}-${String(month).padStart(2, '0')}-${String(d).padStart(2, '0')}`;
}

/** Âge révolu à une date donnée, à partir de dates ISO `YYYY-MM-DD`. */
function ageOn(bornOn: string, onDate: string): number {
  const [by, bm, bd] = bornOn.slice(0, 10).split('-').map(Number);
  const [y, m, d] = onDate.slice(0, 10).split('-').map(Number);
  return y - by - (m < bm || (m === bm && d < bd) ? 1 : 0);
}
