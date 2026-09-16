import { Component, computed, input, model } from '@angular/core';
import { TranslocoCurrencyPipe } from '@jsverse/transloco-locale';

export interface PriceRange {
  readonly min: number;
  readonly max: number;
}

@Component({
  selector: 'bip-slider',
  templateUrl: './slider.html',
  imports: [TranslocoCurrencyPipe],
})
export class Slider {
  readonly min = input.required<number>();
  readonly max = input.required<number>();
  readonly currency = input.required<string>();

  readonly step = input(10);

  readonly value = model.required<PriceRange>();

  protected readonly minPosition = computed(() => this.position(this.value().min));
  protected readonly maxPosition = computed(() => this.position(this.value().max));

  protected onMinInput(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    const current = this.value();

    this.value.set({
      min: Math.min(value, current.max - this.step()),
      max: current.max,
    });
  }

  protected onMaxInput(event: Event): void {
    const value = Number((event.target as HTMLInputElement).value);
    const current = this.value();

    this.value.set({
      min: current.min,
      max: Math.max(value, current.min + this.step()),
    });
  }

  private position(value: number): number {
    const range = this.max() - this.min();

    if (range <= 0) {
      return 0;
    }

    return ((value - this.min()) / range) * 100;
  }
}
