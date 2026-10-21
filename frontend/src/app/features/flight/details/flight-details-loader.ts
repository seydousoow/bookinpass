import { Component } from '@angular/core';
import { Spinner } from '@shared/ui/spinner/spinner';

@Component({
  selector: 'bip-flight-details-loader',
  imports: [Spinner],
  template: ` <div class="grid min-h-96 grid-cols-1 py-4 md:grid-cols-4">
    <div class="col-span-3 flex items-center justify-center">
      <bip-spinner label="search.loading-flight" showLabel size="md" />
    </div>
    <div class="animate-pulse space-y-4 pt-6 max-md:hidden">
      <div class="grid grid-cols-3 gap-x-4">
        <div class="col-span-2 h-2.5 rounded bg-gray-300"></div>
        <div class="h-2.5 rounded bg-gray-300"></div>
      </div>
      <div class="grid grid-cols-3 gap-x-4">
        <div class="col-span-2 h-2.5 rounded bg-gray-300"></div>
        <div class="h-2.5 rounded bg-gray-300"></div>
      </div>
      <div class="grid grid-cols-3 gap-x-4">
        <div class="col-span-2 h-2.5 rounded bg-gray-300"></div>
        <div class="h-2.5 rounded bg-gray-300"></div>
      </div>
      <div class="grid grid-cols-3 gap-x-4">
        <div class="col-span-2 h-2.5 rounded bg-gray-300"></div>
        <div class="h-2.5 rounded bg-gray-300"></div>
      </div>
      <div class="mt-6 grid grid-cols-1 space-y-3">
        <div class="h-2.5 rounded bg-gray-300"></div>
        <div class="h-2.5 rounded bg-gray-300"></div>
        <div class="h-2.5 rounded bg-gray-300"></div>
        <div class="h-2.5 rounded bg-gray-300"></div>
      </div>
    </div>
  </div>`,
})
export class FlightDetailsLoader {}
