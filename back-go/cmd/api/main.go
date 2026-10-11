// Commande api : le backend Book In Pass en Go. Même contrat HTTP que le backend Spring (dossier backend/).
//
//	api              démarre le serveur
//	api healthcheck  interroge /healthz (sonde Docker : l'image distroless n'a pas de curl)
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"
	"github.com/redis/go-redis/v9"

	"bookinpass/back-go/internal/airport"
	"bookinpass/back-go/internal/cache"
	"bookinpass/back-go/internal/config"
	"bookinpass/back-go/internal/duffel"
	"bookinpass/back-go/internal/flight"
	"bookinpass/back-go/internal/httpx"
	"bookinpass/back-go/internal/rate"
	"bookinpass/back-go/migrations"
)

func main() {
	slog.SetDefault(slog.New(slog.NewJSONHandler(os.Stdout, nil)))

	cfg, err := config.Load()
	if err != nil {
		slog.Error("invalid configuration", "err", err)
		os.Exit(1)
	}
	if len(os.Args) > 1 && os.Args[1] == "healthcheck" {
		os.Exit(healthcheck(cfg.Port))
	}
	if err := run(cfg); err != nil {
		slog.Error("server stopped", "err", err)
		os.Exit(1)
	}
}

func run(cfg config.Config) error {
	// Annulé sur SIGINT / SIGTERM (docker stop) : déclenche l'arrêt propre plus bas.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	db, err := pgxpool.New(ctx, cfg.DatabaseURL)
	if err != nil {
		return fmt.Errorf("postgres: %w", err)
	}
	defer db.Close()
	if err := migrate(ctx, db); err != nil {
		return err
	}

	rdb := redis.NewClient(&redis.Options{Addr: cfg.RedisAddr})
	defer rdb.Close()
	if err := rdb.Ping(ctx).Err(); err != nil {
		slog.Warn("redis unreachable at startup, running without cache until it is back", "addr", cfg.RedisAddr, "err", err)
	}
	c := cache.New(rdb)

	mux := http.NewServeMux()
	flight.NewHandler(flight.NewService(newGateway(cfg.Duffel), c, cfg.Cache.FlightSearchTTL, cfg.Cache.FlightOfferMaxTTL)).Register(mux)
	airport.NewHandler(db, c, cfg.Cache.AirportsTTL).Register(mux)
	rate.NewHandler(db, c, cfg.Cache.RatesTTL).Register(mux)
	mux.HandleFunc("GET /healthz", func(w http.ResponseWriter, r *http.Request) {
		if err := db.Ping(r.Context()); err != nil {
			http.Error(w, "database unavailable", http.StatusServiceUnavailable)
			return
		}
		_, _ = w.Write([]byte("ok"))
	})

	srv := &http.Server{
		Addr:              fmt.Sprintf(":%d", cfg.Port),
		Handler:           httpx.Chain(mux, httpx.Recover, httpx.Log, httpx.Gzip),
		ReadHeaderTimeout: 5 * time.Second,
		// Une recherche peut attendre les compagnies ~20 s puis écrire ~1 Mo : large marge.
		WriteTimeout: cfg.Duffel.ReadTimeout + 20*time.Second,
		IdleTimeout:  2 * time.Minute,
	}

	errc := make(chan error, 1)
	go func() {
		slog.Info("listening", "addr", srv.Addr, "duffelDemoMode", cfg.Duffel.DemoMode())
		errc <- srv.ListenAndServe()
	}()

	select {
	case err := <-errc:
		return err
	case <-ctx.Done():
		slog.Info("shutting down")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
		defer cancel()
		// Laisse finir les requêtes en cours, refuse les nouvelles.
		if err := srv.Shutdown(shutdownCtx); err != nil && !errors.Is(err, http.ErrServerClosed) {
			return err
		}
		return nil
	}
}

// newGateway : réel ou démo, choisi à l'exécution selon la présence du jeton.
func newGateway(cfg config.Duffel) duffel.Gateway {
	if cfg.DemoMode() {
		slog.Warn("DUFFEL_ACCESS_TOKEN is not set: serving recorded demo responses", "dir", cfg.FixturesDir)
		return duffel.NewFixtures(cfg.FixturesDir)
	}
	return duffel.NewClient(cfg.BaseURL, cfg.AccessToken, cfg.APIVersion, cfg.SupplierTimeout, cfg.ReadTimeout)
}

// migrate applique les scripts embarqués — le rôle de Flyway côté Spring.
func migrate(ctx context.Context, pool *pgxpool.Pool) error {
	db := stdlib.OpenDBFromPool(pool)
	defer db.Close()
	provider, err := goose.NewProvider(goose.DialectPostgres, db, migrations.FS)
	if err != nil {
		return fmt.Errorf("migrations: %w", err)
	}
	results, err := provider.Up(ctx)
	if err != nil {
		return fmt.Errorf("migrations: %w", err)
	}
	for _, r := range results {
		slog.Info("migration applied", "version", r.Source.Version, "file", r.Source.Path, "duration", r.Duration)
	}
	return nil
}

func healthcheck(port int) int {
	client := http.Client{Timeout: 3 * time.Second}
	res, err := client.Get(fmt.Sprintf("http://localhost:%d/healthz", port))
	if err != nil {
		return 1
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return 1
	}
	return 0
}
