import { Directive, ElementRef, inject, input, output } from '@angular/core';
import { DOCUMENT } from '@angular/common';

/**
 * Gestion du clic hors élément — utilisée par les panneaux en surcouche
 * (dropdown, select, modal, champ de localisation).
 * `mousedown` et non `click` : le panneau doit se fermer avant qu'un éventuel
 * blur ne déplace le focus.
 */
@Directive({
  selector: '[bipOutsideClick]',
  host: {
    '(document:mousedown)': 'onPointer($event)',
    '(document:touchstart)': 'onPointer($event)',
  },
})
export class OutsideClickDirective {
  /** Permet de suspendre l'écoute quand le panneau est fermé. */
  readonly outsideClickEnabled = input(true);
  readonly outsideClick = output<Event>();

  private readonly host = inject(ElementRef<HTMLElement>);
  private readonly document = inject(DOCUMENT);

  protected onPointer(event: Event): void {
    if (!this.outsideClickEnabled()) {
      return;
    }
    const target = event.target as Node | null;
    if (!target || !this.document.contains(target)) {
      return;
    }
    if (!(this.host.nativeElement as HTMLElement).contains(target)) {
      this.outsideClick.emit(event);
    }
  }
}
