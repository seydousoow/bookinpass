# Book In Pass — Plan de développement front

**Objet :** plan d'exécution du front web, du socle technique au découpage en lots.
**Destinataire :** équipe technique.
**Prérequis :** la note de cadrage `01-cadrage-analyse.md`, dont ce document reprend les décisions.
**Date :** 30 août 2026.
**Révision :** 2 septembre 2026 — §2 aligné sur le socle réalisé (lot 0).

---

## 1. Stack

Toutes les versions ci-dessous ont été vérifiées sur le registre npm.

| Brique | Version | Motif |
|---|---|---|
| Angular | **22.1** (`@angular/core` 22.1.4, `@angular/build` 22.1.6) | Base imposée |
| Build & dev server | `@angular/build:application` / `:dev-server` | Pipeline esbuild + Vite intégré, rien à assembler |
| Styles | **Tailwind 4.3** via `@tailwindcss/postcss` | Tokens en `@theme`, source unique |
| Tests unitaires | **Vitest 4.1** via `@angular/build:unit-test` | Voir §1.1 |
| SSR | **`@angular/ssr` 22.1** | Voir §10 |
| État | **Signals Angular**, sans librairie externe | Voir §5 |
| Paiement carte | `@stripe/stripe-js` 9.14 | Voir §7 |

### 1.1 Vitest et non Jest — la raison, une fois pour toutes

Le builder officiel `@angular/build:unit-test` de la v22 n'accepte que deux valeurs pour `runner` :
`karma` ou `vitest` (constaté dans `runner.enum` du schéma en 22.1.6). **Jest n'est plus un runner
supporté par l'outillage Angular.**

Jest resterait techniquement faisable via `jest-preset-angular@17` — dont les peer dependencies
acceptent bien `@angular/core >=20 <23` et `jest ^30` — mais au prix d'une transform `ts-jest`, d'une
seconde configuration et d'un pipeline de compilation **distinct de celui du build**. On maintiendrait
deux chaînes pour un seul projet.

L'API de Vitest étant compatible avec celle de Jest (`describe`, `it`, `expect`, `vi.fn`), une
bascule ultérieure resterait peu coûteuse si le besoin apparaissait. Il n'y a donc aucune raison de
payer le surcoût dès maintenant.

---

## 2. Architecture

### 2.1 Arborescence

```
src/
  app/
    core/                    # Services. Chargés une fois, jamais dupliqués
      api/                   # Client HTTP, types générés depuis l'OpenAPI
      auth/                  # Session, garde de route, rafraîchissement
      errors/                # Normalisation, mapping vers AppError
      analytics/             # Émission des 14 événements du cahier §12
      money/                 # Formatage XOF, conversion
      places/                # Référentiel des points de départ / d'arrivée
      i18n/                  # Chargement des traductions
      config/                # Jeton d'environnement
      offer-expiry/          # Primitive d'expiration — §6
    shared/                  # Couche basse : ne dépend d'aucune autre couche
      models/                # Contrats purs : SystemState, AppError, Place, SelectOption
      ui/                    # Les 16 composants du cahier §6.3
      directives/            # Clic extérieur, surcouches
      a11y/                  # Focus, annonces, préférences de mouvement
    features/
      home/
      flights/               # F01–F07
      tourism/               # X01–X06
      sport/                 # S01–S06
      stays/                 # H01–H06
      ground/                # T01, U01–U06, N01–N06, A01–A05
      gift/                  # G01–G06
      budget/                # P01–P05 — différé
      checkout/              # K01–K06
      account/               # M01–M09
      admin/                 # ADM01–ADM15
  styles/
    theme.css                # @theme Tailwind : la source unique des tokens
```

### 2.2 Trois règles de dépendance

L'empilement est **strict et acyclique** : `shared` ← `core` ← `features`.

| Depuis | Peut importer |
|---|---|
| `shared` | `shared` uniquement |
| `core` | `core`, `shared` |
| `features/x` | `core`, `shared`, `features/x` |

1. **Une feature ne dépend jamais d'une autre feature.** Tout partage remonte dans `shared/` ou
   `core/`. Une exception tolérée casse la règle pour tout le monde.
2. **`shared` ne connaît pas le métier.** Un composant partagé reçoit des données et émet des
   événements ; il n'appelle pas l'API et ne connaît pas les routes. Cette règle n'est pas une
   consigne : `shared` étant la couche basse, un composant partagé **ne peut pas** importer `core`,
   donc pas atteindre le client HTTP ni le routeur.
3. **Aucune sémantique fournisseur ne remonte dans le front.** Le front ne connaît que l'API Book In
   Pass. Le mot « Duffel » ne doit apparaître nulle part dans `src/`. Seule exception assumée :
   `expiresAt`, parce qu'il est visible par l'utilisateur.

Ces règles ne reposent pas sur la discipline de revue : `eslint-plugin-boundaries` les applique et
`npm run lint` échoue sur toute violation.

**Où vivent `SystemState` et `AppError`.** Dans `shared/models`, et non dans `core/` comme le
prévoyait la première version de ce plan. Ce sont des **contrats purs** — un type d'union et une
forme d'erreur, sans dépendance ni comportement. Les placer dans `core/` obligerait
`shared/ui/system-state` à remonter dans `core`, ce qui rendrait l'empilement cyclique et viderait
la règle 2 de sa force. Les **services** qui les produisent — client API, mapping d'erreurs — restent
dans `core/`.

### 2.3 Chargement

Une route paresseuse par feature. Le socle (`shared` + `core`) est dans le bundle initial ; tout
le reste est chargé à la demande. `admin/` est un domaine à part entière, jamais servi aux clients.

---

## 3. Design system

Les tokens sont déclarés **une fois** dans `styles/theme.css`, en `@theme` Tailwind. Aucune valeur
brute — couleur, rayon, espacement, taille de police — n'est écrite ailleurs. C'est la « règle
front-end » du cahier §6.3, et c'est la seule protection contre la dérive.

### 3.1 Couleurs

Palette de la maquette, qui fait foi (cadrage §5.2) :

| Token | Valeur |
|---|---|
| `brand-primary` | `#5125D7` |
| `brand-dark` | `#32106F` |
| `brand-accent` | `#FF6B1A` |
| `brand-soft` | `#EEE9FF` |
| `surface-page` | `#F5F3FB` |
| `text-ink` | `#171428` |
| `text-muted` | `#706B82` |
| `state-success` / `warning` / `error` | `#158C69` / `#F6B83F` / `#D64557` |

À la reprise des tokens, normaliser les trois occurrences de `#4F22D0` codées en dur dans les
dégradés de la maquette (`.scorecard`, `.m-ticket-top`, `.order-thumb`) sur `brand-primary`.

### 3.2 Typographie

Pile complète, à déclarer explicitement — la maquette saute directement à Arial et laisse le rendu
Android au hasard :

```
"Segoe UI", system-ui, -apple-system, Roboto, "Helvetica Neue", Arial, sans-serif
```

Échelle : celle du cadrage §6.2 (Display 34/48, H1 28/36, H2 22/28, H3 18/20, corps 16, réduit 14,
libellé 12 — mobile/desktop). **Plancher absolu 12 px, sans exception.**

**Point de vigilance pour l'équipe :** les maquettes ont été composées avec du texte deux fois trop
petit. Reprendre une planche à la lettre fera déborder la mise en page. Règle : *chaque écran perd
environ un niveau d'information par rapport à la planche ; ce qui tombe passe en second niveau ou
dans l'écran de détail.* Ce n'est pas une trahison de la maquette, c'est la condition pour la rendre
lisible.

### 3.3 Rayons et espacement

Rayons normalisés en quatre valeurs : **bouton 10 · champ 12 · carte 14 · panneau 18**, plus `pill`
pour les badges. Espacement : 4, 8, 12, 16, 24, 32, 48, 64.

### 3.4 Cibles tactiles

44 × 44 px minimum partout, conformément au cahier §3.2. C'est une contrainte de dimensionnement des
composants, pas une finition d'accessibilité : elle se vérifie au moment où le composant est écrit.

---

## 4. Les 16 composants communs

C'est le contrat du lot 0. Tant qu'il n'est pas stabilisé, aucune feature ne démarre — chaque
composant écrit deux fois est une dette payée sur les six verticales.

| Composant | Responsabilité | États à couvrir | AC associé |
|---|---|---|---|
| `AppHeader` / `MobileNav` | Navigation, panier, compte, retour | idle | AC02, AC16 |
| `CategorySwitcher` | 4 rubriques + sous-modes transport | idle | AC02 |
| `SearchShell` | Formulaire contextuel, état de recherche | idle, loading, error | AC13 |
| `LocationField` | Ville, gare, port, aéroport, adresse | idle, loading, empty, error | AC13, AC16 |
| `DatePicker` | Date simple, plage, indisponibilités | idle, loading | AC16 |
| `PeopleSelector` | Adultes, enfants, bébés, chambres | idle | AC16 |
| `OfferCard` | Visuel, attributs, badges, prix, CTA | idle, loading, expired | AC03, AC05 |
| `FilterPanel` | Filtres, compteur, reset, tiroir mobile | idle, empty | AC01, AC16 |
| `PriceSummary` | Sous-total, frais, remise, total XOF | idle, loading, expired | **AC03** |
| `ProgressStepper` | Étapes d'achat | idle | — |
| `GiftConfigurator` | Bénéficiaire, message, thème, remise | idle, error | AC08 |
| `BudgetPlanCard` | Profil, contenu, total, reste, compatibilité | idle, empty | AC11 |
| `TicketPass` | QR, référence, date, lieu, partage | idle, loading, offline | **AC07** |
| `BookingCard` | Service, statut, montant, référence | idle, partial | **AC06** |
| `SystemState` | Skeleton, vide, expiré, partiel, erreur, hors ligne | **les 8** | **AC13** |
| `AdminTable` | Filtres, tri, pagination, export, actions | idle, loading, empty, error | AC15 |

### 4.1 `SystemState` est le composant central

Le cahier impose huit états (§11) : `idle`, `loading`, `success`, `empty`, `expired`, `partial`,
`error`, `offline`. AC13 exige que **chaque appel API** gère au minimum loading, success, empty et
error.

Conséquence d'architecture : ces états ne sont pas un composant d'affichage parmi d'autres, c'est le
**type de retour** de toute donnée distante. Un écran ne manipule pas « des offres », il manipule
« un état de chargement d'offres ». Écrit ainsi dès le socle, AC13 devient structurellement vrai
plutôt que vérifié écran par écran.

Deux règles associées, tirées du cahier :

- Le squelette de chargement a **la même taille** que le contenu qu'il remplace — sinon CLS, et le
  cahier vise ≤ 0,1.
- Un message d'erreur dit **ce qui s'est passé, ce qui reste enregistré et l'action possible**. Jamais
  la couleur seule (§11).

---

## 5. État par signals

### 5.1 Correspondance avec le modèle du cahier §10

| Objet | Portée | Forme |
|---|---|---|
| `SearchSession` | Feature, persistée dans l'URL | Signal + synchronisation query params |
| `Offer` | Feature, volatile | Signal + échéance — §6 |
| `Order` / `Booking` | `core`, partagée | Store à signaux |
| `Ticket` | `core`, lecture hors ligne | Store + cache |
| `Payment` | `core`, machine à états | Voir §7 |
| `BudgetPlan` | Feature, différée | — |

### 5.2 Deux principes

**L'URL est la source de vérité de toute recherche.** Le cahier l'exige (C02 « URL partageable avec
filtres », F01 « recherche persistée dans l'URL », AC17). Les signaux dérivent de l'URL, pas
l'inverse. Cela règle gratuitement le partage, le retour arrière, le rechargement et le SSR.

**Le navigateur ne décide jamais qu'une transaction a réussi.** Règle explicite du cahier §4. Le
statut de commande n'est jamais calculé côté client : il est **reçu**. Les dix statuts du §10
(`draft`, `pending_payment`, `paid`, `booking_pending`, `confirmed`, `partially_confirmed`, `failed`,
`cancelled`, `refund_pending`, `refunded`) forment une machine à états dont le front n'est que le
spectateur. Aucune transition n'est déclenchée par un clic.

---

## 6. Expiration d'offre : la primitive du socle

C'est le mécanisme qui distingue ce produit d'un catalogue. Il est construit **au lot 0**, dans
`core/offer-expiry`, et réutilisé par les six verticales.

### 6.1 Pourquoi maintenant et pas plus tard

Duffel expire ses offres en ~30 minutes et ne garantit pas le prix jusqu'à la commande (cadrage §7).
Ce n'est pas un cas limite, c'est le chemin nominal. Un mécanisme ajouté après coup obligerait à
reprendre chaque écran de sélection, de vérification et de panier déjà écrits.

### 6.2 Comportement attendu

| Situation | Comportement |
|---|---|
| Offre sélectionnée | Échéance visible dès que le temps restant descend sous un seuil |
| Approche de l'échéance | Revalidation silencieuse en arrière-plan |
| Prix inchangé | Aucune interruption, on continue |
| **Prix modifié** | **Écran de re-confirmation, acceptation explicite, ancien et nouveau prix affichés** |
| Offre expirée | Nouvelle recherche relancée automatiquement, **saisie intégralement conservée** |
| Échec de revalidation | `SystemState.expired` avec action de reprise |

La règle non négociable, qui est AC05 : **aucune donnée saisie n'est perdue**, quel que soit le
chemin. Voyageurs, options, coordonnées survivent à une revalidation, à une expiration et à un
changement de prix.

### 6.3 Ce que le contrat d'API doit exposer

`expiresAt` sur l'objet `Offer`, un point d'entrée de revalidation qui renvoie l'offre à jour **et**
l'écart de prix éventuel, et deux champs distincts pour le bagage : **inclus** (filtre F02, P0) et
**payant** (options F05, P1). Ne pas les fusionner — c'est une reprise de contrat garantie sinon.

---

## 7. Paiement

### 7.1 Les trois canaux

| Canal | Mécanisme front | Contrainte |
|---|---|---|
| **Stripe** (carte) | Composant Stripe monté dans le navigateur, ou redirection vers page hébergée | Ne s'exécute jamais au rendu serveur |
| **Wave** | Redirection ou lien, confirmation par webhook serveur | Le front ne voit pas la confirmation |
| **Orange Money** | Idem | Idem |

### 7.2 Les règles qui portent les critères de recette

- **AC18 — aucune donnée de carte** ne transite par le backend Book In Pass ni n'entre dans les logs
  applicatifs. C'est la raison d'être du composant Stripe côté navigateur : les données vont
  directement à Stripe.
- **AC04 — double clic.** Le bouton se verrouille à la première soumission, et la requête porte une
  clé d'idempotence générée **avant** l'envoi et **rejouée** en cas de reprise. Le verrouillage
  d'interface seul ne suffit pas : deux onglets ouverts le contournent.
- **AC14 — statut incertain.** K05 affiche une progression et **interroge le statut serveur**. Aucun
  message d'échec n'est affiché avant décision du serveur. `pending_payment` et `booking_pending` sont
  des états normaux, pas des erreurs.
- **AC06 — confirmation partielle.** K06 présente un statut **par ligne** de commande. Un vol confirmé
  et une activité échouée est un cas nominal : le client choisit, ligne par ligne, entre conserver et
  annuler.

### 7.3 K05 et K06 sont à concevoir

Aucun des deux n'est maquetté (cadrage §8), et ce sont eux qui portent AC04, AC06 et AC14. Ils sont à
traiter comme des écrans de plein exercice, pas comme des transitions.

---

## 8. Contrat d'API et travail en parallèle du backend

Le backend est développé en même temps que le front. Sans discipline de contrat, cela signifie que le
front attend — ou pire, qu'il devine et refait.

### 8.1 Trois règles

1. **Le contrat avant l'écran.** L'OpenAPI d'une verticale est figé et versionné avant que son
   premier écran ne soit écrit.
2. **Les types sont générés, jamais écrits à la main.** Un type recopié diverge du contrat au premier
   changement, silencieusement.
3. **Le mock vient du contrat.** Le même fichier sert de source au serveur de mock. Le front se
   développe et se teste contre le mock ; le passage au vrai backend n'est qu'un changement d'URL.

### 8.2 Rupture de contrat

Elle arrivera. La procédure : le backend publie une version, les deux versions coexistent le temps
que le front migre, le front ne bloque jamais sur une rupture non annoncée. Un contrat qui change
sans version est un contrat qui n'existe pas.

---

## 9. Découpage en lots

Reprise de l'ordre du cahier §14, rééquilibré par la recoupe du cadrage §4.

| Lot | Contenu | Écrans | Dépendance backend |
|---|---|---|---|
| **0** | Socle : tokens, design system, 16 composants, `SystemState`, expiration d'offre, client API, i18n, analytics, SSR | — | Contrat OpenAPI figé |
| **1** | **Vol de bout en bout** — socle public, F01–F07, K01–K06, compte, back-office minimal | 33 | Vol + paiement + commandes |
| **2** | Tourisme et sport | 14 | Catalogue interne |
| **3** | Hébergement | 7 | Hébergement |
| **4** | Cadeau | 8 | Cadeau, génération PDF |
| **5** | Transport local — bus, bateau, AIBD | 20 | Bus, bateau, zones AIBD |
| **6** | Budget (IA) | 6 | Moteur de recommandation |
| **7** | Stabilisation — E2E, accessibilité, performance, charge, recette | — | — |

### 9.1 Pourquoi le lot 1 est le plus important

Il ne livre pas « le vol ». Il livre **tous les mécanismes transverses** : recherche, offre,
expiration, panier, paiement, billet, compte, support, administration. Les lots 2 à 6 déclinent ce
patron. Un lot 1 bâclé se paie six fois.

Corollaire : **on ne démarre pas le lot 2 avant que le lot 1 ne passe ses critères de recette.** Le
cahier §15 dit la même chose — « terminer un flux de bout en bout avant de le dupliquer ».

### 9.2 Définition de « terminé », pour chaque lot

Un lot est terminé quand : les écrans passent aux 6 largeurs, les 8 états sont implémentés sur chaque
appel API, les critères AC du lot sont vérifiés, le parcours fonctionne au clavier avec focus
visible, et il a été testé **sur un téléphone Android réel** — pas seulement en émulation.

### 9.3 Ce qui a bougé par rapport au cahier

Le transport local passe de la 4ᵉ à la 5ᵉ position. Motif : 17 écrans, **aucun maquetté**, et les
mécanismes les plus éloignés du vol (plans de sièges, cabines, véhicules, zones tarifaires). C'est le
lot le plus coûteux du dossier, pas le plus rapide. Le placer tôt bloquerait la chaîne.

---

## 10. SSR

### 10.1 Pourquoi

Le cahier exige un contenu SEO stable (X03) et AC17 impose « URL stable, titre et description » sur
les pages publiques. Une SPA pure ne le fournit pas. Le SSR est donc dans le lot 0 : ajouté après
coup, il obligerait à reprendre tout accès au navigateur déjà écrit.

### 10.2 Mode de rendu par route

Angular 22 permet de déclarer un mode par route, via `provideServerRendering(withRoutes(...))` et
`RenderMode.{Prerender, Server, Client}`. Répartition retenue :

| Routes | Mode | Motif |
|---|---|---|
| `/`, `/help`, `/legal/:slug`, `/contact` | **Prerender** | Contenu stable, LCP optimal |
| `/tourism`, `/sport`, `/stays` et fiches `:id` | **Server** | SEO nécessaire, contenu variable |
| `/explore`, tous les `**/results` | **Client** | Dépend d'une recherche, non indexable, volatil |
| `/cart`, `/checkout/**` | **Client** | Stripe.js, données sensibles |
| `/account/**`, `/booking/**`, `/gift/redeem/:token` | **Client** | Authentifié ou tokenisé |
| `/admin/**` | **Client** | Jamais rendu côté serveur |

Les fiches à `:id` en `Prerender` avec paramètres restent une option d'optimisation ultérieure, pour
les contenus les plus consultés.

### 10.3 La règle d'équipe

Tout ce qui touche `window`, `document`, le stockage local ou une API navigateur **ne s'exécute pas
au rendu serveur**. L'exemple canonique est Stripe.js : chargé à l'initialisation d'un composant,
jamais à la construction. Cette règle est à écrire dans le guide de contribution du lot 0, parce
qu'elle se viole silencieusement — le développement local en CSR ne révèle rien, la production casse.

---

## 11. Tests

### 11.1 Répartition

| Niveau | Périmètre | Outil |
|---|---|---|
| Unitaire | Logique pure : formatage XOF, calculs de prix, machine d'expiration, mapping d'erreurs | Vitest |
| Composant | Les 16 composants partagés, chacun dans ses états | Vitest |
| E2E | Les parcours complets et les critères de recette transverses | À choisir au lot 0 |
| Manuel | Rendu sur appareil réel, accessibilité clavier, lisibilité | Recette |

### 11.2 Les cas prioritaires

Par ordre de risque, ce sont ceux qui cassent en production sans casser en développement :

1. **Expiration d'offre** pendant la saisie des voyageurs — la saisie est-elle conservée ?
2. **Variation de prix** à la revalidation — le client accepte-t-il explicitement ?
3. **Double soumission** du paiement, y compris depuis deux onglets (AC04).
4. **Paiement resté incertain** — aucun message d'échec prématuré (AC14).
5. **Confirmation partielle** — statut par ligne, action par ligne (AC06).
6. **Mode hors ligne** sur le billet (AC07) — le QR reste-t-il accessible ?

### 11.3 Couverture des critères de recette

Les 20 critères AC01–AC20 sont répartis explicitement entre les trois niveaux au lot 0, et chaque
critère a un propriétaire. Un critère sans test nommé est un critère qui ne sera pas vérifié.

Largeurs de test, imposées par le cahier §11 : **360, 390, 768, 1024, 1280 et 1440 px**. AC01 — aucun
scroll horizontal — se vérifie aux six.

---

## 12. Internationalisation et formatage

### 12.1 i18n dès le premier libellé

AC19 impose que les libellés viennent d'un fichier de traduction, **même avec le français comme seule
langue**. Le choix — i18n natif Angular ou Transloco — est à figer au lot 0, avant que le premier
libellé ne soit écrit en dur. Une reprise a posteriori sur 93 écrans est le pire scénario du projet,
et il est parfaitement évitable.

### 12.2 Formatage monétaire

XOF, **zéro décimale**. Un seul utilitaire dans `core/money`, utilisé partout, testé une fois.

### 12.3 Conversion

Le taux est **servi par le backend**, y compris tant qu'il est statique. Jamais codé dans le front :
sinon, le branchement futur d'une source réelle imposera un redéploiement front à chaque variation.
Reste à trancher côté produit (cadrage §11, point 5) : quel montant fait foi contractuellement, et
si le taux et sa date sont affichés au client.

---

## 13. Analytics

Les 14 événements du cahier §12 sont émis depuis `core/analytics`, jamais depuis un composant.
AC20 impose qu'ils soient émis **une seule fois** — ce qui, avec le SSR et l'hydratation, n'est pas
gratuit : un événement émis au rendu serveur *et* à l'hydratation compte double. À traiter au lot 0,
en même temps que le SSR, pas après.

---

## 14. Ce que ce plan ne couvre pas

Prérequis externes au front. Chacun a besoin d'un porteur et d'une date, sans quoi le planning
ci-dessus est théorique.

| Sujet | Nature |
|---|---|
| Backend et adaptateurs fournisseurs | Développement parallèle — dont l'intégration Duffel |
| Contrat OpenAPI | **Bloque le lot 0** |
| Choix du PSP côté Wave et Orange Money | Bloque le lot 1 |
| Design des **78 écrans** non maquettés | Bloque chaque lot, en amont |
| Contenu du catalogue tourisme et sport | Bloque le lot 2 |
| Moteur de recommandation Budget | Bloque le lot 6 |
| Génération des PDF cadeau (A4 et 10 × 15) | Bloque le lot 4 |

Le point le plus urgent n'est pas technique : c'est le **design des 78 écrans manquants**. Au rythme
d'un lot, il devient bloquant dès la fin du lot 1.
