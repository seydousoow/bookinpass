import { Component, computed, input, model, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { LangDefinition, TranslocoDirective } from '@jsverse/transloco';
import { CurrencyOption } from '../../models/money.model';
import { Button } from '../button/button';
import { CurrencySelector } from '../currency-selector/currency-selector';
import { NgIcon, provideIcons } from '@ng-icons/core';
import { heroBars3Solid, heroShoppingCartSolid } from '@ng-icons/heroicons/solid';

export interface NavEntry {
  readonly labelKey: string;
  readonly route: string;
  readonly disabled?: boolean;
}

@Component({
  selector: 'bip-header',
  imports: [RouterLink, RouterLinkActive, TranslocoDirective, Button, CurrencySelector, NgIcon],
  host: { class: 'block' },
  templateUrl: './header.html',
  providers: [provideIcons({ heroBars3Solid, heroShoppingCartSolid })],
})
export class Header {
  readonly currentLang = model.required<string>();
  readonly languages = input.required<string[] | LangDefinition[]>();
  /** Devise d'affichage — indépendante de la langue. */
  currentCurrency = model.required<string>();

  readonly currencies = input<readonly CurrencyOption[]>([]);
  readonly entries = input<readonly NavEntry[]>([]);
  readonly cartCount = input(0);
  protected readonly mobileOpen = signal(false);

  protected readonly langs = computed(() => this.languages().map((language) => (typeof language === 'string' ? language : language.label)));
}
