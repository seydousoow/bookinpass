[![backend-image](https://github.com/seydousoow/bookinpass/actions/workflows/backend-image.yml/badge.svg)](https://github.com/seydousoow/bookinpass/actions/workflows/backend-image.yml)
[![Qodana](https://github.com/seydousoow/bookinpass/actions/workflows/qodana_code_quality.yml/badge.svg)](https://github.com/seydousoow/bookinpass/actions/workflows/qodana_code_quality.yml)
[![CodeQL](https://github.com/seydousoow/bookinpass/actions/workflows/github-code-scanning/codeql/badge.svg)](https://github.com/seydousoow/bookinpass/actions/workflows/github-code-scanning/codeql)

# Book In Pass — backend Spring Boot

API consommée par le site et le back-office Angular du dépôt [bookinpass-nx](https://github.com/seydousoow/bookinpass-nx). Java 25, Spring Boot 4.1, Spring Data JPA (Hibernate 7),
Lombok, image native GraalVM, PostgreSQL 18 (données de référence) et Redis 8 (cache).
Le même contrat est implémenté en Go dans `../back-go`.

## Lancer

```bash
cp .env.example .env               # facultatif : DUFFEL_ACCESS_TOKEN=duffel_test_…
docker compose up --build          # API sur http://localhost:8080
```

Sans jeton Duffel, l'API sert les réponses enregistrées de `fixtures/duffel/` (Dakar ⇄ Paris) : tout fonctionne hors ligne.

**Mémoire Docker.** Avec JPA, la compilation native culmine à 7 Go de RSS (7 min, mesuré sur la machine de
développement). Avec les 5 Go par défaut de Docker Desktop, le build échoue par manque de mémoire. Deux options :

- Docker Desktop → Settings → Resources → Memory : **10 Go**, puis `docker compose up --build` ;
- ou l'image JVM, qui se construit en une minute : `BACKEND_TARGET=jvm docker compose up --build`.

Binaire natif hors Docker (macOS, avec GraalVM 25) : `./mvnw -Pnative native:compile`, puis
`./target/backend --spring.profiles.active=dev`.

Depuis l'IDE : `docker compose up -d postgres redis`, puis lancer `BackendApplication` avec le profil `dev`
(répertoire de travail : la racine du dépôt). Le profil fournit les identifiants locaux : `application.yaml` n'en contient
aucun, ils viennent toujours de l'environnement.

Tests : `./mvnw test`. Les tests JPA démarrent un PostgreSQL 18 jetable (Testcontainers) : Docker doit tourner.

Le site (`bookinpass-nx`, `npx nx serve front`) relaie `/api` vers `localhost:8080` (`proxy.conf.json`).

## Documentation OpenAPI (Swagger)

- Swagger UI : http://localhost:8080/swagger-ui.html — chaque route est testable (« Try it out » déjà actif,
  exemples Dakar → Paris préremplis).
- Contrat JSON : http://localhost:8080/v3/api-docs — à partager avec le front (« le contrat avant l'écran »).
- Désactivation (production publique) : `API_DOCS_ENABLED=false`.

Généré par springdoc à partir des annotations : `@Operation`/`@ApiResponse` sur les contrôleurs, `@Parameter`
sur les composants de `FlightSearchRequest`. Sans ces `@Parameter(name = …)`, la recherche serait documentée
comme un objet unique `request`, avec les noms Java (`tripType`…) au lieu des paramètres réels (`t`…).
La coloration syntaxique de Swagger UI est coupée : une réponse Duffel de 17 Mo figeait le navigateur.

## API

| Méthode et chemin | Rôle | Source | Cache Redis |
|---|---|---|---|
| `GET /api/flights/search?t&o&d&from&to&adt&chd&inf&cabin` | Recherche de vols | Duffel `POST /air/offer_requests` | 10 min |
| `GET /api/flights/offers/{id}` | Détail d'une offre | Duffel `GET /air/offers/{id}` | aucun |
| `PATCH /api/flights/offers/{id}/passengers/{pid}` | Nom et fidélité d'un voyageur — rend l'offre relue | Duffel `PATCH /air/offers/{id}/passengers/{pid}` puis `GET /air/offers/{id}` | aucun |
| `GET /api/flights/offers/{id}/upsells` | Montées en gamme (vide si la compagnie n'en propose pas) | Duffel `POST /air/offers/{id}/upsell_offers` | jusqu'à `expires_at` (15 min max) |
| `GET /api/flights/offers/{id}/seat-maps` | Plans de cabine (sièges et prix par voyageur) | Duffel `GET /air/seat_maps?offer_id=` | 10 min |
| `POST /api/flights/offers/{id}/price` | Prix avec les services choisis | Duffel `POST /air/offers/{id}/actions/price` | aucun |
| `GET /api/stays/search?l&in&out&adt&chd&rooms` | Hébergements autour d'une ville (tarif le moins cher) | Duffel `POST /stays/search` | 10 min |
| `GET /api/stays/results/{id}/rates` | Chambres et tarifs d'un résultat | Duffel `POST /stays/search_results/{id}/actions/fetch_all_rates` | jusqu'à `expires_at` (5 min max) |
| `POST /api/stays/quotes` | Vérification finale d'un tarif (prix, acompte) | Duffel `POST /stays/quotes` | aucun |
| `GET /api/stays/quotes/{id}` | Relire un devis | Duffel `GET /stays/quotes/{id}` | aucun |
| `GET /api/stays/accommodations/{id}/reviews?after&limit` | Avis clients, paginés | Duffel `GET /stays/accommodation/{id}/reviews` | 24 h |
| `GET /api/airports` | Aéroports pour la saisie | Postgres | 24 h |
| `GET /api/airlines` | Compagnies (nom et logos d'un code IATA) | Postgres | 24 h |
| `GET /api/rates` | Taux de change depuis XOF | Postgres | 1 h |
| `GET /actuator/health/readiness` | Sonde | — | — |

Les paramètres de recherche reprennent ceux de l'URL du front (`/search?t=round&o=DSS…`, `/stays/search?l=DKR&in=…`).

**Paiement.** Stripe Checkout, formulaire intégré (`ui_mode=form`, iframe Stripe : aucune donnée de carte sur ce serveur).
`POST /payments/flights/{offerId}/checkout-session` (`{"services":[{"id","quantity"}]}`) relit l'offre et les plans de
cabine chez Duffel, recalcule le prix de vente (billet + frais de service BIP 3 % même pays / 5 % sinon + bagages et
sièges +20 %, `FlightPricing`, mêmes règles que le front) et crée une session Checkout en autorisation seule
(`payment_intent_data[capture_method]=manual`, réglages Checkout Studio, clé d'idempotence, API `2026-03-25.dahlia`).
Clés : `STRIPE_SECRET_KEY`, `STRIPE_PUBLISHABLE_KEY` ; vides = 503 `payments-unavailable`. `FRONTEND_URL` : base du
retour après un moyen à redirection. Webhook signé : `POST /webhooks/stripe` (`STRIPE_WEBHOOK_SECRET`,
en-tête `Stripe-Signature` vérifié). Reste à faire : voir [STRIPE_INTEGRATION_TODO.md](STRIPE_INTEGRATION_TODO.md).

**Webhooks Duffel.** `POST /webhooks/duffel` (déclaré chez Duffel : `https://bip-back.dicortex.com/api/webhooks/duffel`) reçoit les événements Duffel (commandes, changements de vol décidés
par la compagnie, paiements, `ping.triggered`), vérifie `X-Duffel-Signature` avec `DUFFEL_WEBHOOK_SECRET` et répond
`{"success": true}`. Ils mettent à jour la commande enregistrée (`flight_order` : changée, annulation en cours, annulée, remboursement dans `flight_order_event`) et sont journalisés (changement de vol en WARN : le voyageur devra être prévenu).
Un seul webhook par mode (test / production), en HTTPS, jamais `localhost` (en développement : tunnel ngrok).
Le secret n'est rendu **qu'une fois**, à la création :

```bash
curl -X POST https://api.duffel.com/air/webhooks \
  -H "Authorization: Bearer $DUFFEL_ACCESS_TOKEN" -H "Duffel-Version: v2" -H "Content-Type: application/json" \
  -d '{"data":{"url":"https://<domaine>/api/webhooks/duffel","events":["order.created","order.creation_failed",
       "order.airline_initiated_change_detected","air.order.changed","order_cancellation.created",
       "order_cancellation.confirmed","air.payment.succeeded","air.payment.failed"]}}'
# → data.secret dans DUFFEL_WEBHOOK_SECRET, puis test : POST /air/webhooks/{id}/actions/ping
```

**Réservations enregistrées.** Après chaque commande : voyageurs (`users`, retrouvés par e-mail + prénom, nom, date de
naissance et sexe), commande (`flight_order`, prix détaillé en JSONB : billet et frais BIP, services et marge),
passagers, historique (`flight_order_event`) et paiement Stripe (`stripe_payment`, de l'autorisation au remboursement,
`stripe_payment_event`). Le titulaire de l'e-mail (premier adulte) reçoit un client Duffel (`icu_…`, un seul par
e-mail chez Duffel), rattaché à la commande. Un échec d'enregistrement ne fait jamais échouer une réservation.

**Activités touristiques.** Fiches gérées au back-office (`/staff/activities`, admin et manager) : contenu, photos
(adresses HTTPS), itinéraire, options (prix adulte, enfant, prix barré), créneaux datés ouverts par jours de la semaine
et heures locales (fuseau de la ville GeoNames), mise en vente. Le site lit `GET /activities`, `/activities/{slug}`,
`/{slug}/dates`, `/{slug}/availability` et `/{slug}/reviews`. La vente suit celle des billets de car : places bloquées
36 min, Stripe Checkout en autorisation seule (`kind=activity`) ou PayDunya (XOF), capture, bon d'échange par e-mail.
Annulation gratuite par le client jusqu'à `free_cancellation_hours` avant le début ; annulation et remboursements
(partiels, gestes commerciaux, créneau annulé) par un responsable. Carte : `POST /v1/refunds` chez Stripe ; mobile
money : remboursement à la main, noté au back-office (`/staff/activity-refunds`). L'IPN PayDunya est
`/webhooks/paydunya` : la facture dit si elle paie une activité ou des billets de car.

**PayDunya (car et activités).** Montants en francs CFA entiers. `GET …/payment-methods` rend la carte Stripe (toutes
cartes, American Express comprise) et les moyens PayDunya de `PAYDUNYA_METHODS` : carte PayDunya (page PayDunya, la
saisie d'une carte exige PCI-DSS), Wave et Orange Money au Sénégal, Wave et Orange Money en Côte d'Ivoire, Orange Money
au Burkina Faso et au Mali — chacun avec son pays et son indicatif imposé. Le site envoie moyen, numéro national et code
OTP éventuel (Orange Money Côte d'Ivoire et Burkina Faso) en JSON à `POST …/bookings/{ref}/mobile-payment`, en HTTPS ;
le serveur revérifie le moyen, le numéro (pays, et opérateur pour Orange Money) et le code, prend le montant de la
réservation, crée la facture (`checkout-invoice/create`) puis la paie (`/softpay/{moyen}`) avec les clés du compte, qui
ne quittent jamais le serveur. Réponse : `redirect` (lien Wave, page carte), `qr` (QR code et liens Orange Money / Max
it), `otp` (code accepté, paiement relu aussitôt) ou `phone` (validation sur le téléphone) ; le site relit ensuite
`…/mobile-payment/confirmation`. Cinq tentatives au plus par réservation en 20 minutes. Le numéro est conservé chiffré
(AES-256-GCM, `PAYMENT_DATA_KEYS`, rotation par identifiant de clé) avec une version masquée ; le code OTP n'est jamais
conservé ni journalisé. IPN : `POST /webhooks/paydunya`, refusée (403) sans `data[hash]` = SHA-512 de la clé
principale, puis facture relue chez PayDunya.

**Webhooks.** Tous dans `WebhookController` : `/webhooks/stripe`, `/webhooks/duffel`, `/webhooks/paydunya`.

**Avis et Claude.** Un avis par réservation payée, après le créneau ; en attente jusqu'à modération. Toutes les 2 heures
(`REVIEW_MODERATION_INTERVAL`, durée ISO-8601, `PT2H` par défaut), les avis en attente partent chez Claude en un seul
appel (20 au plus) ; pour chacun, il rend `approve`, `reject` ou `review` (réponse imposée par un schéma JSON). En
modération automatique (réglage du back-office, `app_setting.reviews.auto_moderation`), `approve` publie et `reject`
rejette ; `review` attend toujours un responsable. Le résumé « Ce qu'en disent les voyageurs » (100 derniers avis,
français et anglais) est écrit à la demande, depuis la fiche de l'activité. Coût réduit au minimum : modèle
`claude-haiku-5-5` (`ANTHROPIC_MODEL`), réflexion coupée (`ANTHROPIC_THINKING=false`), effort `low`, réponse plafonnée.

Clé : `ANTHROPIC_API_KEY` (vide = tous les avis attendent un responsable). Une clé de compte de service limitée à un
espace de travail dédié, avec expiration, et une limite de dépense mensuelle sur cet espace.

**Hébergements.** Duffel Stays cherche autour de coordonnées : `l` est l'identifiant GeoNames d'une ville
(`2253354` = Dakar), dont les coordonnées viennent de la table `city` ; rayon `stays-radius-km` (10 km).
`GET /stays/destinations?q=dak` propose les villes (nom ou variante en alphabet latin, sans accents ni casse, les plus
peuplées d'abord) et `GET /stays/destinations/{id}` en relit une. La table vient de la migration V6, générée depuis
l'export GeoNames `cities5000.txt` (villes de plus de 5 000 habitants, licence CC BY 4.0) :

```bash
python3 scripts/generate_cities_migration.py ~/Downloads/cities5000.txt src/main/resources/db/migration/V6__cities.sql
```

Pour une mise à jour des données, générer une nouvelle migration (V7…) plutôt que réécrire V6, déjà appliquée. Avec un jeton, l'API appelle Duffel Stays (produit à faire
activer par Duffel, sinon 403). `DUFFEL_STAYS_ENABLED=false` (ou l'absence de jeton) sert à la place les hébergements
de démo de `fixtures/duffel/stays/` (six établissements fictifs à Dakar, prix rapportés aux nuits et chambres demandées).
Les réponses Duffel sont relayées telles quelles : le front lit déjà ce format.
Toutes les erreurs sortent en RFC 7807 (`application/problem+json`) avec un `code` stable, sans détail fournisseur.

Erreurs Duffel (lues dans `errors[0].type/code`, journalisées avec `request_id` à citer au support Duffel) :

| Duffel | API | `code` |
|---|---|---|
| 404 | 404 | `offer-not-found` |
| 422 `offer_expired`, `offer_no_longer_available`, `rate_unavailable`, `result_no_longer_available`… (ou sans code connu) | 422 | `offer-unavailable` |
| 422 `price_changed`, `ancillary_service_not_available` | 409 | `offer-changed` |
| 422 `validation_error`, `invalid_*`, `loyalty_programme_*` (nom refusé…) | 400 | `provider-rejected` |
| 429, 503, 504 | 503 + `Retry-After` (`ratelimit-reset`) | `provider-unavailable` |
| 500, 502, 400/401/403 (notre intégration, en ERROR) | 502 | `provider-error` |
| 422 `unsupported_action` (montées en gamme), `seat_map_unavailable` | 200 `{"data":[]}` | — |

Le corps de chaque erreur porte aussi `providerRequestId` (le `x-request-id` Duffel).

## Organisation

Paquets par type de composant (couches) :

```
src/main/java/com/bookinpass/backend/
  controller/   FlightController, AirportController, ExchangeRateController — HTTP uniquement
  service/      FlightService, AirportService, ExchangeRateService — @Cacheable, @Transactional(readOnly = true)
  repository/   AirportRepository, ExchangeRateRepository — Spring Data JPA (JpaRepository, requêtes dérivées)
  entity/       Airport, ExchangeRate, ExchangeRateId (@EmbeddedId) — @Immutable, Lombok @Getter
  dto/
    request/    FlightSearchRequest — paramètres de recherche validés (Bean Validation)
    response/   AirportResponse, ExchangeRatesResponse — ce que l'API expose et ce que Redis met en cache
    duffel/     DuffelOfferRequest — corps envoyé à Duffel
  client/       DuffelClient (vols) et StaysClient (hébergements) : Http… (RestClient) ou Fixture… (démo) ;
                DuffelErrors lit les erreurs Duffel pour les deux
  config/       CacheConfig, DuffelConfig, *Properties — câblage et configuration typée
  exception/    ApiExceptionHandler (→ ProblemDetail), DuffelException
src/main/resources/db/migration/   Flyway — schéma + données (générées depuis les anciens JSON du front)
fixtures/duffel/                   réponses Duffel enregistrées, montées par Docker (hors image)
```

Le chemin d'une requête : `controller` → `service` → `repository` (Postgres) ou `client` (Duffel).
Les entités ne sortent jamais du `service` : il les convertit en DTO de `dto/response`.

## Points à retenir

- **Native et conditions.** L'AOT fige les `@Conditional…` à la compilation. Le choix démo / Duffel réel se fait
  donc dans une méthode `@Bean` à l'exécution (`HttpClientConfig`), sinon l'image ne pourrait plus changer de mode.
- **Native et réflexion.** Ce que Spring ne voit pas à la compilation doit être déclaré : liaison par
  constructeur et méthodes `@AssertTrue` de `FlightSearchQuery` (`@RegisterReflection`), records sérialisés
  dans Redis (`@RegisterReflectionForBinding`), ressource interne de Jedis (`META-INF/native-image/…`).
  Le symptôme d'un oubli : une `UnsupportedFeatureError` au premier appel, alors que les tests JVM passent.
  Il faut tester le binaire natif lui-même.
- **Jedis plutôt que Lettuce.** Client bloquant, adapté aux threads virtuels. Sans Netty, l'analyse native
  a nettement moins de code à parcourir.
- **Compression.** Le client JDK ne décompresse pas seul : `HttpClientConfig` envoie `Accept-Encoding: gzip`
  et décompresse la réponse.
- **TTL d'une offre.** `CacheConfig.untilOfferExpiry` lit `expires_at` : une offre n'est jamais servie depuis
  le cache après son expiration. Une offre déjà expirée reçoit 1 s, car 0 voudrait dire « sans expiration ».
- **`@Validated` absent du contrôleur.** Spring MVC valide lui-même `@Pattern` sur `@PathVariable` (400).
  Le proxy AOP de `@Validated` lèverait une `ConstraintViolationException`, donc une 500.
- **`@EnableCaching` sur `CacheConfig`**, pas sur l'application : sinon les tests `@WebMvcTest` réclament un cache.
- **Flyway possède le schéma, Hibernate le vérifie.** `ddl-auto: validate` : au démarrage, une entité qui ne
  correspond pas à la table fait échouer l'application. Les colonnes `CHAR(n)` exigent `@JdbcTypeCode(SqlTypes.CHAR)`,
  sinon Hibernate attend un `VARCHAR`.
- **Lombok sur les entités : `@Getter` et `@NoArgsConstructor(access = PROTECTED)`, jamais `@Data`.** `@Data`
  génère `equals`/`hashCode`/`toString` sur tous les champs, ce qui casse les collections, les proxys et les
  associations paresseuses. Seule la clé composée `ExchangeRateId` a besoin de `@EqualsAndHashCode`.
- **On met en cache des DTO, pas des entités.** Une entité sérialisée dans Redis embarquerait l'état Hibernate.
- **`open-in-view: false`.** Aucune session Hibernate n'est tenue pendant l'écriture de la réponse : un accès
  paresseux hors service échoue immédiatement au lieu de lancer des requêtes cachées.
- **Amélioration du bytecode Hibernate** (`hibernate-maven-plugin`, phase `process-classes`) : suivi des
  modifications sans proxy, recommandé pour l'image native.
- **Le coût natif de JPA.** Sans JPA : build 2,5 min, pic 4,3 Go, binaire 127 Mo. Avec : 7 min, 7 Go, 203 Mo.
  Le démarrage reste à ~0,3 s et la mémoire à ~215 Mo après une recherche.

## Pistes

- Commande (`POST /air/orders`) : délai de lecture d'au moins 130 s (Duffel), contre 40 s aujourd'hui ;
  en-têtes `x-duffel-device-ip` / `x-duffel-device-user-agent` (anti-fraude) ; ne jamais réessayer une 500/502/202.
- Test d'intégration complet (`@SpringBootTest`) avec Redis en Testcontainers : le cache n'est testé
  aujourd'hui que par sa fonction de durée de vie.
- Avant toute mise en ligne (revue `springboot-security`) : Spring Security, avec refus par défaut, et en-têtes de
  sécurité ; CORS limité à l'origine du front ; limitation de débit sur `/api/flights/search` (chaque appel coûte
  une requête Duffel) ; analyse des dépendances (OWASP Dependency-Check) en CI.
- Réservation (`POST /air/orders`), paiement, idempotence : le cœur du lot 1.
