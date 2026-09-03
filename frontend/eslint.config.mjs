import eslint from '@eslint/js';
import tseslint from 'typescript-eslint';
import angular from 'angular-eslint';
import boundaries from 'eslint-plugin-boundaries';

export default tseslint.config(
  { ignores: ['dist/**', '.angular/**', 'node_modules/**', 'src/app/core/api/generated/**'] },
  {
    files: ['**/*.ts'],
    extends: [eslint.configs.recommended, ...tseslint.configs.recommended, ...tseslint.configs.stylistic, ...angular.configs.tsRecommended],
    processor: angular.processInlineTemplates,
    plugins: { boundaries },
    settings: {
      // Sans résolveur, les alias `@core/*` / `@shared/*` / `@features/*` ne sont
      // pas résolus et la règle de dépendance ne voit rien passer.
      'import/resolver': {
        typescript: { alwaysTryTypes: true, project: './tsconfig.json' },
      },
      'boundaries/include': ['src/**/*'],
      'boundaries/elements': [
        { type: 'core', pattern: 'src/app/core/*' },
        { type: 'shared', pattern: 'src/app/shared/*' },
        { type: 'feature', pattern: 'src/app/features/*', capture: ['name'] },
        { type: 'app', pattern: 'src/app/*.ts', mode: 'file' },
        { type: 'bootstrap', pattern: 'src/*.ts', mode: 'file' },
      ],
    },
    rules: {
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: 'bip', style: 'camelCase' }],
      '@angular-eslint/component-selector': ['error', { type: 'element', prefix: 'bip', style: 'kebab-case' }],
      '@angular-eslint/prefer-standalone': 'error',
      '@angular-eslint/use-lifecycle-interface': 'error',

      // Les trois règles de dépendance du plan §2.2, rendues mécaniques.
      'boundaries/dependencies': [
        'error',
        {
          default: 'disallow',
          policies: [
            // Empilement strict et acyclique : shared ← core ← features.
            // `shared` est la couche basse : contrats purs (models) et
            // composants de présentation. Elle ne remonte jamais dans `core`,
            // ce qui garantit qu'un composant partagé ne peut ni appeler l'API
            // ni connaître une route (plan §2.2 règle 2).
            { from: [{ element: { type: 'shared' } }], allow: [{ to: { element: { type: 'shared' } } }] },
            { from: [{ element: { type: 'core' } }], allow: [{ to: { element: { type: 'core' } } }, { to: { element: { type: 'shared' } } }] },
            // une feature ne dépend jamais d'une autre feature.
            {
              from: [{ element: { type: 'feature' } }],
              allow: [
                { to: { element: { type: 'core' } } },
                { to: { element: { type: 'shared' } } },
                { to: { element: { type: 'feature', captured: { name: '{{from.name}}' } } } },
              ],
            },
            {
              from: [{ element: { type: 'app' } }, { element: { type: 'bootstrap' } }],
              allow: ['core', 'shared', 'feature', 'app', 'bootstrap'].map((type) => ({ to: { element: { type } } })),
            },
          ],
        },
      ],
    },
  },
  {
    // `button[bipButton]` s'applique en attribut sur un vrai <button> : la
    // sémantique native, le focus et `disabled` restent ceux du navigateur.
    files: ['src/app/shared/ui/button/button.ts'],
    rules: { '@angular-eslint/component-selector': ['error', { type: 'attribute', prefix: 'bip', style: 'camelCase' }], },
  },
  {
    files: ['**/*.html'],
    extends: [...angular.configs.templateRecommended, ...angular.configs.templateAccessibility],
    rules: {},
  },
);
