# syntax=docker/dockerfile:1.7

# ---------------------------------------------------------------------------
# 1. Compilation : binaire statique (CGO désactivé), ~20 Mo, en quelques secondes.
# ---------------------------------------------------------------------------
FROM golang:1.27.2-trixie AS build
WORKDIR /src

# Modules d'abord : la couche reste en cache tant que go.mod / go.sum ne changent pas.
COPY go.mod go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download

COPY . .
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/api ./cmd/api

# ---------------------------------------------------------------------------
# 2. Exécution : distroless « static » — ni shell ni gestionnaire de paquets, utilisateur non-root.
#    Les certificats CA y sont fournis (appels HTTPS vers Duffel).
# ---------------------------------------------------------------------------
FROM gcr.io/distroless/static-debian12:nonroot
COPY --from=build /out/api /api
EXPOSE 8081
# Pas de curl dans l'image : le binaire sait se sonder lui-même.
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 CMD ["/api", "healthcheck"]
ENTRYPOINT ["/api"]
