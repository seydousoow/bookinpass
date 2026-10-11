# Book In Pass — backend Spring Boot

API consommée par le front Angular (`../frontend`). Java 25, Spring Boot 4.1, Spring Data JPA (Hibernate 7),
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
(répertoire de travail `backend/`). Le profil fournit les identifiants locaux : `application.yaml` n'en contient
aucun, ils viennent toujours de l'environnement.

Tests : `./mvnw test`. Les tests JPA démarrent un PostgreSQL 18 jetable (Testcontainers) : Docker doit tourner.

Le front (`npm start` dans `../frontend`) relaie `/api` vers `localhost:8080` (`proxy.conf.json`).

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
| `GET /api/flights/offers/{id}` | Détail d'une offre | Duffel `GET /air/offers/{id}` | jusqu'à `expires_at` (15 min max) |
| `GET /api/airports` | Aéroports pour la saisie | Postgres | 24 h |
| `GET /api/rates` | Taux de change depuis XOF | Postgres | 1 h |
| `GET /actuator/health/readiness` | Sonde | — | — |

Les paramètres de recherche reprennent ceux de l'URL du front (`/search?t=round&o=DSS…`).
Les réponses Duffel sont relayées telles quelles : le front lit déjà ce format.
Toutes les erreurs sortent en RFC 7807 (`application/problem+json`) avec un `code` stable, sans détail fournisseur.

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
  client/       DuffelClient : HttpDuffelClient (RestClient) ou FixtureDuffelClient (mode démo)
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

- Demander la compression à Duffel : le client JDK ne décompresse pas seul. Passer à Apache HttpClient 5 ou Jetty.
- Test d'intégration complet (`@SpringBootTest`) avec Redis en Testcontainers : le cache n'est testé
  aujourd'hui que par sa fonction de durée de vie.
- Avant toute mise en ligne (revue `springboot-security`) : Spring Security, avec refus par défaut, et en-têtes de
  sécurité ; CORS limité à l'origine du front ; limitation de débit sur `/api/flights/search` (chaque appel coûte
  une requête Duffel) ; analyse des dépendances (OWASP Dependency-Check) en CI.
- Réservation (`POST /air/orders`), paiement, idempotence : le cœur du lot 1.
