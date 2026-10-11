# Book In Pass — backend Go

Même API que le backend Spring (`../backend`), écrite en Go 1.27 pour comparer les deux approches.
PostgreSQL 18, Redis 8, image distroless de ~25 Mo.

## Lancer

```bash
cp .env.example .env               # facultatif : DUFFEL_ACCESS_TOKEN=duffel_test_…
docker compose up --build          # API sur http://localhost:8081
```

Postgres (5433) et Redis (6381) sont séparés de ceux du backend Spring : les deux tournent en même temps.
Les fixtures de démo sont celles du backend Spring (`../backend/fixtures/duffel`), montées en lecture seule.

Sans Go installé, tout passe par Docker :

```bash
docker run --rm -v "$PWD":/src -w /src golang:1.27.2-trixie go test ./...
```

Avec Go installé : `docker compose up -d postgres redis`, puis `go run ./cmd/api`.

Pour brancher le front sur ce backend, changer la cible de `../frontend/proxy.conf.json` en `http://localhost:8081`.

## Organisation

```
cmd/api/main.go       assemblage : config, pool Postgres, migrations, Redis, routes, arrêt propre
internal/config       variables d'environnement (mêmes noms que Spring)
internal/duffel       interface Gateway : Client (HTTP) ou Fixtures (mode démo)
internal/flight       ParseSearchQuery (validation), Service (cache + Duffel), Handler (HTTP)
internal/airport      requête pgx + handler
internal/rate         requête pgx + handler
internal/cache        GetOrLoad sur Redis, avec singleflight
internal/httpx        problem+json, middlewares (gzip, journalisation, recover)
migrations/           scripts goose, embarqués dans le binaire (go:embed)
```

## De Spring à Go

| Spring (`../backend`) | Go (ici) | Ce qui change |
|---|---|---|
| Paquets par couche (`controller/`, `service/`, `repository/`…) | Paquets par domaine (`internal/flight`, `internal/airport`…) | L'usage Go : un paquet regroupe ce qui change ensemble. |
| `@RestController`, `@GetMapping` | `http.ServeMux` : `mux.HandleFunc("GET /api/…/{id}", h)` | Routage de la bibliothèque standard (Go ≥ 1.22) : verbe et variables de chemin, sans framework. |
| Injection de dépendances, `@Service` | Constructeurs `NewX(deps…)` appelés dans `main.go` | Le graphe d'objets est du code qu'on lit de haut en bas. |
| `@ConfigurationProperties` | `config.Load()` lit `os.Getenv` | Pas de liaison magique : chaque variable est nommée, avec sa valeur par défaut. |
| Bean Validation (`@Pattern`, `@AssertTrue`) | `ParseSearchQuery` renvoie `(SearchQuery, []string)` | La validation est une fonction. Toutes les erreurs sont rassemblées, comme côté Spring. |
| `@ExceptionHandler`, `ProblemDetail` | Erreurs en valeurs (`*duffel.Error`), `errors.As`, `httpx.WriteProblem` | Pas d'exceptions : chaque appel rend son erreur, traitée sur place. |
| `@Cacheable` + `RedisCacheManager` | `cache.GetOrLoad(ctx, key, ttl, load)` | Explicite. En plus : *singleflight* (50 requêtes identiques = 1 appel Duffel) et Redis en panne = cache manqué, pas d'erreur. |
| Spring Data JPA (`JpaRepository`, entités, requêtes dérivées) | `pgxpool` + SQL écrit à la main + `pgx.CollectRows(rows, pgx.RowToStructByPos[T])` | Pas d'ORM : la requête est visible, le mapping est positionnel. |
| Flyway | goose, scripts embarqués via `//go:embed` | Le binaire contient ses migrations. |
| `server.compression` | middleware `httpx.Gzip` | Une vingtaine de lignes, avec un `sync.Pool` de compresseurs. |
| Threads virtuels | Goroutines (une par requête, gérées par `net/http`) | Même modèle : code bloquant, concurrence bon marché. |
| GraalVM native : AOT, indices de réflexion, ~7 Go pour compiler (avec JPA) | `go build` : quelques secondes, binaire statique | Pas de réflexion à déclarer. Pas de JVM à éliminer. |
| Actuator + `curl` dans l'image | `api healthcheck` : le binaire se sonde lui-même | L'image distroless n'a ni shell ni curl. |

Chiffres mesurés en local, en mode démo sur la même machine (Spring natif : binaire macOS, son image Docker n'a pas été construite faute de mémoire) :

| | Spring JVM | Spring natif | Go |
|---|---|---|---|
| Démarrage | ~1,6 s | ~0,3 s | ~0,1 s |
| Mémoire après une recherche | non mesurée | ~215 Mo RSS | ~50 Mo |
| Image Docker | ~570 Mo | ~290 Mo (estimé : binaire 203 Mo + Debian slim) | ~25 Mo |
| Build | ~1 min | ~7 min, ~7 Go de RAM | ~50 s (premier build, modules compris) |

## Tests

`internal/flight/flight_test.go` couvre la validation, la construction de la requête Duffel, la clé de cache
(identique à Spring), la durée de vie d'une offre et les handlers via `httptest`, avec un faux service.
C'est l'interface `Searcher`, définie côté consommateur, qui permet de remplacer le service.
