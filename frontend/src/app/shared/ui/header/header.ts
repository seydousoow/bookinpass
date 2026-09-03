import { Component, input, model, signal } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';
import { TranslocoDirective } from '@jsverse/transloco';
import { Badge, Button } from '@shared/ui';

export interface NavEntry {
  readonly labelKey: string;
  readonly route: string;
  readonly disabled?: boolean;
}

@Component({
  selector: 'bip-header',
  imports: [RouterLink, RouterLinkActive, TranslocoDirective, Badge, Button],
  host: { class: 'block' },
  templateUrl: './header.html',
})
export class Header {
  readonly currentLang = model.required<string>();
  readonly entries = input<readonly NavEntry[]>([]);
  readonly cartCount = input(0);
  protected readonly languages: readonly string[] = ['fr', 'en'];
  protected readonly mobileOpen = signal(false);
}
