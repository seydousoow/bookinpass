import { Component, output } from '@angular/core';

/** Voile de fond. Cliquable : la fermeture reste décidée par le parent. */
@Component({
  selector: 'bip-overlay',
  host: {
    class: 'fixed inset-0 z-40 bg-ink/40 backdrop-blur-[2px]',
    '(click)': 'clicked.emit()',
    'aria-hidden': 'true',
  },
  template: '',
})
export class Overlay {
  readonly clicked = output<void>();
}
