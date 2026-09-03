# Book In Pass — front

Angular 22 · Tailwind 4 · SSR · signaux · zoneless. Ce dépôt met en œuvre le lot 0 du plan `../docs/02-plan-projet.md`.

## Démarrer

```bash
npm start          # http://localhost:4200
npm test           # Vitest via @angular/build:unit-test
npm run lint       # ESLint + règles de dépendance
npm run build      # build production + SSR
npm run serve:ssr  # sert le build SSR
```

## Arborescence

```
src/
  styles/
    theme.css         # @theme Tailwind — SOURCE UNIQUE des tokens
    styles.css        # import tailwind + theme + réglages de base
  assets/
    i18n/{fr,en}.json # AC19 — aucun libellé en dur
    airports.json     # FIXTURE, en attendant le point d'entrée backend
  app/
    core/             # services, une seule instance : places, money, i18n, config
    shared/
      models/         # contrats purs : SystemState, AppError, place, select
      ui/             # les composants de présentation
      directives/
    features/         # une route paresseuse par feature
```

## Les trois règles de dépendance

Empilement strict et acyclique : **shared ← core ← features**.

| Depuis       | Peut importer                  |
|--------------|--------------------------------|
| `shared`     | `shared` uniquement            |
| `core`       | `core`, `shared`               |
| `features/x` | `core`, `shared`, `features/x` |

Ces règles ne reposent pas sur la discipline : `eslint-plugin-boundaries` les applique, `npm run lint` échoue sur toute violation. `shared` étant la
couche basse, un composant partagé **ne peut pas** appeler l'API ni connaître une route.

> Écart assumé par rapport au plan §2.1 : `SystemState` et `AppError` sont des
> contrats purs, ils vivent dans `shared/models` et non dans `core/`. Les
> services qui les produisent (client API, mapping d'erreurs) resteront dans
> `core/`. Sans cela, `shared/ui/system-state` devrait remonter dans `core`, ce
> qui créerait un cycle entre les deux couches.

## Design system

Toutes les couleurs, tailles de police, rayons et espacements sont déclarés une seule fois dans `src/styles/theme.css`, en `@theme` Tailwind. **Aucune
valeur brute ailleurs.** Les composants ne composent que des classes utilitaires adossées à ces tokens (`bg-brand-primary`, `rounded-card`,
`text-small`…).

Planche de validation : **`/ui-kit`** — chaque composant partagé y figure dans tous ses états.

## Accessibilité

Les patrons interactifs sont bâtis sur **`@angular/aria`** : `ngCombobox`,
`ngListbox`, `ngOption` fournissent les rôles, `aria-expanded`,
`aria-activedescendant`, le focus roving, la navigation clavier et la recherche par frappe. Le code du dépôt ne porte que la présentation.

Cible tactile de 44 px minimum, anneau de focus unique (`.bip-focusable`),
`prefers-reduced-motion` respecté.

## Rendu serveur (SSR)

Le mode de rendu est déclaré **par route** dans `src/app/app.routes.server.ts`
(plan §10.2). L'application n'est donc pas « en SSR » en bloc :

| Route | Mode | Effet |
|---|---|---|
| `/` | `Prerender` | HTML figé au build, servi en statique |
| `/ui-kit` | `Client` | coquille vide, tout est rendu par le navigateur |
| `**` | `Server` | rendu à la demande |

Deux comportements vérifiés, utiles à connaître avant d'ajouter une route
`Server` :

- **Les URL relatives fonctionnent au rendu serveur.** Un `http.get('/x.json')`
  exécuté pendant le SSR est résolu contre la requête entrante.
- **Les réponses GET sont sérialisées dans le HTML.** `provideClientHydration()`
  active le cache de transfert par défaut : le navigateur ne refait pas l'appel
  après hydratation. Ne pas l'utiliser pour des données propres à un
  utilisateur : elles se retrouveraient dans une page potentiellement mise en
  cache.

### Hôtes autorisés — à configurer avant tout déploiement

Le serveur SSR rejette toute requête dont l'en-tête `Host` n'est pas déclaré,
avec un **400 Bad Request**. C'est une protection contre le SSRF, pas un bug —
mais un déploiement sans configuration ressemble à une panne totale.

`angular.json` n'autorise que les hôtes locaux :

```jsonc
"security": { "allowedHosts": ["localhost", "127.0.0.1", "[::1]"] }
```

En production, déclarer les domaines réels via la variable d'environnement
`NG_ALLOWED_HOSTS`, qui **prime sur** `angular.json` :

```bash
NG_ALLOWED_HOSTS=bookinpass.sn,www.bookinpass.sn node dist/frontend/server/server.mjs
```

Derrière un reverse proxy, `NG_TRUST_PROXY_HEADERS` indique les en-têtes de
transfert à prendre en compte.


## Ce qui n'est pas encore là

| Sujet                          | Raison                                                                                                                                              |
|--------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| Authentification               | Reportée jusqu'à ce que l'auth backend existe. Landing, recherche, réservation et paiement n'en ont pas besoin.                                     |
| Stripe / Wave / Orange Money   | Reporté.                                                                                                                                            |
| Tests E2E                      | Hors périmètre pour l'instant.                                                                                                                      |
| Types générés depuis l'OpenAPI | `openapi-typescript@7` exige TypeScript ^5 ; Angular 22 embarque TypeScript 6. À rebrancher quand le contrat existera (ou via un autre générateur). |
| Données aéroports              | `assets/airports.json` est une fixture (273 points, Afrique de l'Ouest + hubs).                                                                     |
