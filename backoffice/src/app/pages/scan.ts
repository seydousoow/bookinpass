import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { finalize } from 'rxjs';
import { TranslocoDirective } from '@jsverse/transloco';
import { errorCode, StaffApi } from '@core/api';
import { dakarDateTime, dakarTime } from '@core/dakar';
import { TicketCheck } from '@core/models';

type Camera = 'off' | 'starting' | 'on' | 'denied' | 'unsupported';
type Decoder = (data: Uint8ClampedArray, width: number, height: number, options?: { inversionAttempts?: string }) => { data: string } | null;

/** Code de billet : 10 caractères, sans 0/O ni 1/I. */
const TICKET_CODE = /^[A-HJ-NP-Z2-9]{10}$/;
/** Le même code relu par la caméra n'est renvoyé qu'après ce délai. */
const SAME_CODE_MS = 4000;

/**
 * Contrôle des billets : la caméra du téléphone lit le QR code (jsQR, chargé à la première ouverture), ou le code se
 * tape à la main. Chaque lecture valide le billet côté serveur (une seule fois) et affiche le verdict en grand.
 */
@Component({
  selector: 'bo-scan',
  imports: [FormsModule, TranslocoDirective],
  template: `
    <section *transloco="let t" class="mx-auto max-w-lg space-y-4">
      <h1 class="text-xl font-bold">{{ t('scan.title') }}</h1>

      <div class="bo-card space-y-3">
        <div [class.hidden]="camera() !== 'on'" class="relative overflow-hidden rounded-card bg-black">
          <video #video aria-hidden="true" class="aspect-square w-full object-cover" muted playsinline></video>
          <div class="pointer-events-none absolute inset-[15%] rounded-card border-4 border-white/80"></div>
        </div>
        @switch (camera()) {
          @case ('on') {
            <button (click)="stop()" class="bo-btn-outline w-full" type="button">{{ t('scan.stop-camera') }}</button>
          }
          @case ('starting') {
            <p class="text-muted text-sm">{{ t('scan.starting') }}</p>
          }
          @default {
            <button (click)="start()" class="bo-btn-primary w-full" type="button">{{ t('scan.start-camera') }}</button>
            @if (camera() === 'denied') {
              <p class="text-state-error text-sm" role="alert">{{ t('scan.denied') }}</p>
            } @else if (camera() === 'unsupported') {
              <p class="text-state-error text-sm" role="alert">{{ t('scan.unsupported') }}</p>
            }
          }
        }
        <form (ngSubmit)="submitManual()" class="flex gap-2">
          <label class="flex-1">
            <span class="sr-only">{{ t('scan.code') }}</span>
            <input [(ngModel)]="manual" [placeholder]="t('scan.code')" autocapitalize="characters" autocomplete="off" class="bo-input font-mono uppercase"
                   maxlength="10" name="code"/>
          </label>
          <button [disabled]="busy() || manual.trim().length !== 10" class="bo-btn-primary" type="submit">{{ t('scan.check') }}</button>
        </form>
      </div>

      @if (busy()) {
        <p class="text-muted" role="status">{{ t('common.loading') }}</p>
      }
      @if (error(); as code) {
        <p class="text-state-error" role="alert">{{ t('errors.' + code) }}</p>
      }
      @if (result(); as r) {
        <div [class]="tone(r)" aria-live="assertive" class="rounded-card p-5" role="status">
          <p class="text-2xl font-bold">{{ t('scan.status.' + r.status) }}</p>
          <p class="font-mono">{{ r.code }}</p>
          @if (r.family_name) {
            <p class="mt-2 text-lg font-semibold">{{ r.given_name }} {{ r.family_name }}</p>
          }
          @if (r.origin) {
            <p>{{ r.origin }} → {{ r.destination }} · {{ dateTime(r.departs_at) }}</p>
          }
          @if (r.status === 'already_scanned') {
            <p class="mt-2 font-semibold">{{ t('scan.scanned-by', {time: time(r.scanned_at), name: r.scanned_by}) }}</p>
          }
        </div>
      }
    </section>
  `,
})
export class ScanPage {
  protected readonly camera = signal<Camera>('off');
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly result = signal<TicketCheck | null>(null);
  protected manual = '';
  protected readonly time = dakarTime;
  protected readonly dateTime = dakarDateTime;
  private readonly video = viewChild.required<ElementRef<HTMLVideoElement>>('video');
  private readonly api = inject(StaffApi);
  private stream: MediaStream | null = null;
  private decode: Decoder | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;
  private readonly canvas = document.createElement('canvas');
  private last = { code: '', at: 0 };

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  protected async start(): Promise<void> {
    if (!navigator.mediaDevices?.getUserMedia) {
      this.camera.set('unsupported');
      return;
    }
    this.camera.set('starting');
    try {
      this.decode ??= ((await import('jsqr')).default as unknown as Decoder);
      this.stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' } }, audio: false });
      const video = this.video().nativeElement;
      video.srcObject = this.stream;
      await video.play();
      this.camera.set('on');
      this.tick();
    } catch {
      this.stop();
      this.camera.set('denied');
    }
  }

  protected stop(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.stream?.getTracks().forEach((track) => track.stop());
    this.stream = null;
    if (this.camera() === 'on' || this.camera() === 'starting') {
      this.camera.set('off');
    }
  }

  protected submitManual(): void {
    this.submit(this.manual.trim().toUpperCase());
    this.manual = '';
  }

  protected tone(r: TicketCheck): string {
    if (r.status === 'scanned') {
      return 'bg-state-success text-onbrand';
    }
    return r.status === 'already_scanned' ? 'bg-state-warning text-ink' : 'bg-state-error text-onbrand';
  }

  /** Une image toutes les 200 ms : assez pour viser, sans chauffer le téléphone. */
  private tick(): void {
    this.timer = setTimeout(() => {
      if (this.camera() !== 'on') {
        return;
      }
      const code = this.read();
      if (code && TICKET_CODE.test(code) && !this.busy() && (code !== this.last.code || Date.now() - this.last.at > SAME_CODE_MS)) {
        this.last = { code, at: Date.now() };
        this.submit(code);
      }
      this.tick();
    }, 200);
  }

  private read(): string | null {
    const video = this.video().nativeElement;
    if (!this.decode || video.readyState < video.HAVE_ENOUGH_DATA || !video.videoWidth) {
      return null;
    }
    const scale = Math.min(1, 640 / video.videoWidth);
    const width = Math.round(video.videoWidth * scale);
    const height = Math.round(video.videoHeight * scale);
    this.canvas.width = width;
    this.canvas.height = height;
    const context = this.canvas.getContext('2d', { willReadFrequently: true });
    if (!context) {
      return null;
    }
    context.drawImage(video, 0, 0, width, height);
    const image = context.getImageData(0, 0, width, height);
    return this.decode(image.data, width, height, { inversionAttempts: 'dontInvert' })?.data.trim().toUpperCase() ?? null;
  }

  private submit(code: string): void {
    this.busy.set(true);
    this.error.set(null);
    this.api
      .scanTicket(code)
      .pipe(finalize(() => this.busy.set(false)))
      .subscribe({
        next: (result) => {
          this.result.set(result);
          navigator.vibrate?.(result.status === 'scanned' ? 80 : [80, 60, 80]);
        },
        error: (error: unknown) => this.error.set(errorCode(error)),
      });
  }
}
