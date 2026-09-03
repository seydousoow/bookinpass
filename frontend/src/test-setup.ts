/**
 * Amorçage commun des tests unitaires (Vitest via `@angular/build:unit-test`).
 * Angular 22 initialise l'environnement de test tout seul ; ce fichier ne porte
 * que les stubs d'API navigateur absentes de jsdom.
 */
if (typeof globalThis.ResizeObserver === 'undefined') {
  /* eslint-disable @typescript-eslint/no-empty-function */
  globalThis.ResizeObserver = class {
    observe(): void {}
    unobserve(): void {}
    disconnect(): void {}
  } as unknown as typeof ResizeObserver;
  /* eslint-enable @typescript-eslint/no-empty-function */
}
