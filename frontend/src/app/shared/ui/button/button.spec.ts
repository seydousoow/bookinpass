import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Button } from './button';

/**
 * L'hôte de test expose des signaux, pas des champs simples : en mode zoneless,
 * un champ ordinaire ne marque pas la vue comme sale et `detectChanges()` ne
 * repropage rien.
 */
@Component({
  imports: [Button],
  template: `
    <button bipButton [variant]="variant()" [loading]="loading()" [disabled]="disabled()" (click)="clicks.set(clicks() + 1)">Payer</button>
  `,
})
class Host {
  readonly variant = signal<'primary' | 'danger'>('primary');
  readonly loading = signal(false);
  readonly disabled = signal(false);
  readonly clicks = signal(0);
}

describe('Button', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;
  let element: HTMLButtonElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [Host] }).compileComponents();
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    element = fixture.debugElement.query(By.css('button')).nativeElement;
    fixture.detectChanges();
  });

  it('reste un vrai <button> : la sémantique native n’est pas recopiée', () => {
    expect(element.tagName).toBe('BUTTON');
    expect(element.getAttribute('role')).toBeNull();
  });

  it('applique la variante demandée', () => {
    expect(element.className).toContain('bg-brand-primary');
    host.variant.set('danger');
    fixture.detectChanges();
    expect(element.className).toContain('bg-state-error');
  });

  it('respecte la cible tactile de 44 px en taille md', () => {
    expect(element.className).toContain('min-h-11');
  });

  // AC04 — première barrière contre le double clic. La clé d'idempotence reste
  // indispensable : deux onglets contournent tout verrou d'interface.
  it('bloque nativement le clic pendant le chargement', () => {
    host.loading.set(true);
    fixture.detectChanges();
    expect(element.disabled).toBe(true);
    expect(element.getAttribute('aria-busy')).toBe('true');
    element.click();
    expect(host.clicks()).toBe(0);
  });

  it('bloque nativement le clic quand il est désactivé', () => {
    host.disabled.set(true);
    fixture.detectChanges();
    expect(element.disabled).toBe(true);
    element.click();
    expect(host.clicks()).toBe(0);
  });

  it('émet un clic dans l’état normal', () => {
    expect(element.disabled).toBe(false);
    element.click();
    expect(host.clicks()).toBe(1);
  });
});
