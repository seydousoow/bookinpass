import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoTestingModule } from '@jsverse/transloco';
import { isValidPhone, PhoneInput } from './phone-input';

@Component({
  imports: [PhoneInput],
  template: `<bip-phone-input [(value)]="phone" label="phone"/>`,
})
class Host {
  readonly phone = signal('');
}

describe('PhoneInput', () => {
  const setup = async (initial = '') => {
    await TestBed.configureTestingModule({
      imports: [Host, TranslocoTestingModule.forRoot({ langs: { fr: {} }, translocoConfig: { availableLangs: ['fr'], defaultLang: 'fr' } })],
    }).compileComponents();
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.phone.set(initial);
    await fixture.whenStable();
    const element: HTMLElement = fixture.nativeElement;
    const field = element.querySelector<HTMLInputElement>('input[type="tel"]')!;
    const type = async (text: string) => {
      field.value = text;
      field.dispatchEvent(new InputEvent('input', { inputType: 'insertText' }));
      await fixture.whenStable();
    };
    return { fixture, element, field, type, host: fixture.componentInstance };
  };

  it('met en forme à la frappe et rend le numéro E.164 du pays choisi', async () => {
    const { field, type, host, element } = await setup();
    expect(element.textContent).toContain('+221');

    await type('771234567');

    expect(field.value).toBe('77 123 45 67');
    expect(host.phone()).toBe('+221771234567');
    expect(isValidPhone(host.phone())).toBe(true);
  });

  it('bascule de pays sur un numéro international collé', async () => {
    const { field, type, host, element } = await setup();

    await type('+33 6 12 34 56 78');

    expect(host.phone()).toBe('+33612345678');
    expect(element.textContent).toContain('+33');
    expect(field.value).toBe('06 12 34 56 78');
  });

  it('relit une valeur venue du formulaire (coordonnées mémorisées)', async () => {
    const { field, element } = await setup('+14155552671');

    expect(element.textContent).toContain('+1');
    expect(field.value).toBe('(415) 555-2671');
  });

  it('émet un numéro incomplet, refusé par la validation, et rien sans chiffre', async () => {
    const { type, host } = await setup();

    await type('77');
    expect(host.phone()).toBe('+22177');
    expect(isValidPhone(host.phone())).toBe(false);

    await type('');
    expect(host.phone()).toBe('');
  });
});
