import { runtimeConfig } from './app-config.token';

describe('runtimeConfig', () => {
  const build = { production: true, apiBaseUrl: '/api' };

  it('garde la configuration du build sans configuration de déploiement', () => {
    expect(runtimeConfig(build, undefined)).toEqual(build);
    expect(runtimeConfig(build, { apiBaseUrl: '  ' })).toEqual(build);
    expect(runtimeConfig(build, { apiBaseUrl: 42 })).toEqual(build);
  });

  it("prend l'URL de l'API du déploiement, sans barre finale", () => {
    expect(runtimeConfig(build, { apiBaseUrl: 'https://api.bookinpass.com/api/' })).toEqual({ production: true, apiBaseUrl: 'https://api.bookinpass.com/api' });
  });
});
