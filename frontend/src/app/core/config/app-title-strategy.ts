import { inject, Service } from '@angular/core';
import { ActivatedRouteSnapshot, RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { Title } from '@angular/platform-browser';
import { fromIso, IsoDate } from '@shared/ui/date-picker/date-utils';

@Service()
export class AppTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);

  override updateTitle(routerState: RouterStateSnapshot): void {
    const route = routerState.root;
    const search = this.findRoute(route, 'search');

    if (search) {
      const title = this.buildSearchTitle(search);
      if (title) {
        this.title.setTitle(title);
        return;
      }
    }
    this.setDefaultTitle(routerState);
  }

  private buildSearchTitle(route: ActivatedRouteSnapshot): string | null {
    const params = route.queryParamMap;
    const origin = params.get('o');
    const destination = params.get('d');
    const from = params.get('from');

    if (!origin || !destination || !from) {
      return null;
    }

    const fromDate = this.formatDate(from);
    const to = params.get('to');
    if (to) {
      return `${origin} → ${destination}, ${fromDate} - ${this.formatDate(to)} | Book In Pass`;
    }
    return `${origin} → ${destination}, ${fromDate} | Book In Pass`;
  }

  private formatDate(d: string): string {
    const from = fromIso(d as IsoDate);
    return `${from.getDate()}/${from.getMonth() + 1}`;
  }

  private findRoute(route: ActivatedRouteSnapshot, path: string): ActivatedRouteSnapshot | null {
    if (route.routeConfig?.path === path) {
      return route;
    }

    for (const child of route.children) {
      const result = this.findRoute(child, path);
      if (result) {
        return result;
      }
    }

    return null;
  }

  private setDefaultTitle(routerState: RouterStateSnapshot): void {
    const title = this.buildTitle(routerState);
    this.title.setTitle(title || 'Book In Pass — Vols, séjours et activités');
  }
}
