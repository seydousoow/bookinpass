import { Component, input, model, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { CurrencyOption } from '@shared/models/money.model';
import { Badge, Button, CurrencySelector } from '@shared/ui';

export interface NavEntry {
  readonly labelKey: string;
  readonly route: string;
  readonly disabled?: boolean;
}

@Component({
  selector: 'bip-header',
  imports: [RouterLink, RouterLinkActive, TranslocoDirective, Badge, Button, CurrencySelector],
  host: { class: 'block' },
  templateUrl: './header.html',
})
export class Header {
  readonly currentLang = model.required<string>();
  /** Devise d'affichage — indépendante de la langue. */
  readonly currentCurrency = model.required<string>();
  readonly currencies = input<readonly CurrencyOption[]>([]);
  readonly entries = input<readonly NavEntry[]>([]);
  readonly cartCount = input(0);
  protected readonly languages: readonly string[] = ['fr', 'en'];
  protected readonly mobileOpen = signal(false);
}
