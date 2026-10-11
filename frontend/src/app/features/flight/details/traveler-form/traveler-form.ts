import { Component, computed, inject, input, linkedSignal, output, viewChild } from '@angular/core';
import { form, FormField, FormRoot, maxLength, metadata, pattern, REQUIRED, required, validate } from '@angular/forms/signals';
import { TranslocoDirective, TranslocoService } from '@jsverse/transloco';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroXMark } from '@ng-icons/heroicons/outline';
import { Button, Modal, Select, TextInput, todayIso } from '@shared/ui';
import { ISelectOption, TPassengerType } from '@shared/models';

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
}

/** Modèle de saisie : les listes déroulantes travaillent sur des tableaux, la date est éclatée en trois champs. */
interface ITravelerFormModel {
  given_name: string;
  family_name: string;
  gender: TPassengerGender[];
  birth: { day: string; month: number[]; year: string };
}

/** Lettres latines (accents compris), espaces, apostrophes et traits d'union — contrainte des compagnies. */
const NAME_PATTERN = /^[A-Za-zÀ-ÖØ-öø-ÿ' -]+$/;

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
  /** Informations déjà saisies : la modale s'ouvre préremplie. */
  readonly initial = input<ITravelerInfo | undefined>();

  readonly saved = output<ITravelerInfo>();
  /** Émis à chaque fermeture (✕, Échap ou après enregistrement). */
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
    },
    {
      submission: {
        action: (f) => {
          this.saved.emit(this.toTravelerInfo(f().value()));
          this.modal().close();
          return Promise.resolve(null);
        },
        ignoreValidators: 'none',
        onInvalid: (field) => field().errorSummary()[0]?.fieldTree().focusBoundControl(),
      },
    },
  );

  protected close(): void {
    this.modal().close();
  }

  private toTravelerInfo({ given_name, family_name, gender, birth }: ITravelerFormModel): ITravelerInfo {
    const sex = gender[0];
    return {
      // Civilité déduite du sexe : « Miss » pour une enfant, « Ms » pour une adulte.
      title: sex === 'm' ? 'mr' : this.passengerType() === 'adult' ? 'ms' : 'miss',
      gender: sex,
      given_name: given_name.trim(),
      family_name: family_name.trim(),
      born_on: toIsoDate(birth.day, birth.month[0], birth.year) ?? '',
    };
  }
}

function toFormModel(info: ITravelerInfo | undefined): ITravelerFormModel {
  const [year = '', month = '', day = ''] = info?.born_on ? info.born_on.split('-') : [];
  return {
    given_name: info?.given_name ?? '',
    family_name: info?.family_name ?? '',
    gender: info ? [info.gender] : [],
    birth: { day: day ? String(Number(day)) : '', month: month ? [Number(month)] : [], year },
  };
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
