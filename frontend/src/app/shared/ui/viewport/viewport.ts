import { inject, Service, Signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { BreakpointObserver } from '@angular/cdk/layout';
import { map } from 'rxjs';

/**
 * Le point de rupture `md:` de Tailwind (`src/styles/theme.css`) — pas les
 * presets `Breakpoints.Handset` etc. de `@angular/cdk/layout`, qui répondent
 * à des seuils Material (599.98px, 959.98px…) sans rapport avec la bascule
 * CSS réellement utilisée ici. Le JS et le CSS doivent s'accorder sur la
 * même limite.
 */
const MOBILE_QUERY = '(max-width: 767.98px)';

/**
 * Détection de point de rupture réutilisable, pour la sémantique qui ne peut
 * pas être exprimée en CSS seul (rôle de dialogue, piège à focus, verrou de
 * défilement…) — le layout lui-même reste piloté par Tailwind, jamais par ce
 * service.
 */
@Service()
export class Viewport {
  private readonly breakpointObserver = inject(BreakpointObserver);

  /**
   * Signal réactif pour une ou plusieurs media queries arbitraires (largeur,
   * orientation…). Chaque appel crée son propre abonnement, nettoyé avec le
   * composant appelant — à invoquer depuis un contexte d'injection (champ ou
   * constructeur), comme `inject()`.
   */
  observe(query: string | readonly string[]): Signal<boolean> {
    return toSignal(
      this.breakpointObserver.observe(query).pipe(map((state) => state.matches)),
      { initialValue: this.breakpointObserver.isMatched(query) },
    );
  }

  /** En dessous de `md:` — SSR : `false` tant qu'aucun `matchMedia` réel n'est disponible. */
  readonly isMobile = this.observe(MOBILE_QUERY);
}
