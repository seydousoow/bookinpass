// Package config lit la configuration depuis l'environnement — mêmes noms de variables que le backend Spring.
package config

import (
	"fmt"
	"os"
	"strconv"
	"time"
)

type Config struct {
	Port        int
	DatabaseURL string
	RedisAddr   string

	Duffel Duffel
	Cache  Cache
}

type Duffel struct {
	BaseURL         string
	AccessToken     string // vide = mode démo
	APIVersion      string
	SupplierTimeout time.Duration
	ReadTimeout     time.Duration
	FixturesDir     string // réponses enregistrées servies en mode démo
}

// DemoMode vaut vrai sans jeton Duffel : les réponses viennent des fixtures.
func (d Duffel) DemoMode() bool { return d.AccessToken == "" }

type Cache struct {
	FlightSearchTTL   time.Duration
	FlightOfferMaxTTL time.Duration
	AirportsTTL       time.Duration
	RatesTTL          time.Duration
}

func Load() (Config, error) {
	port, err := intEnv("PORT", 8081)
	if err != nil {
		return Config{}, err
	}
	cfg := Config{
		Port: port,
		DatabaseURL: env("DATABASE_URL", fmt.Sprintf("postgres://%s:%s@localhost:5433/bookinpass?sslmode=disable",
			env("DATABASE_USER", "bookinpass"), env("DATABASE_PASSWORD", "bookinpass"))),
		RedisAddr: env("REDIS_HOST", "localhost") + ":" + env("REDIS_PORT", "6381"),
		Duffel: Duffel{
			BaseURL:     env("DUFFEL_BASE_URL", "https://api.duffel.com"),
			AccessToken: os.Getenv("DUFFEL_ACCESS_TOKEN"),
			APIVersion:  "v2",
			FixturesDir: env("FIXTURES_DIR", "../backend/fixtures/duffel"),
		},
	}
	durations := []struct {
		target *time.Duration
		name   string
		def    time.Duration
	}{
		{&cfg.Duffel.SupplierTimeout, "DUFFEL_SUPPLIER_TIMEOUT", 20 * time.Second},
		{&cfg.Duffel.ReadTimeout, "DUFFEL_READ_TIMEOUT", 40 * time.Second},
		{&cfg.Cache.FlightSearchTTL, "CACHE_FLIGHT_SEARCH_TTL", 10 * time.Minute},
		{&cfg.Cache.FlightOfferMaxTTL, "CACHE_FLIGHT_OFFER_MAX_TTL", 15 * time.Minute},
		{&cfg.Cache.AirportsTTL, "CACHE_AIRPORTS_TTL", 24 * time.Hour},
		{&cfg.Cache.RatesTTL, "CACHE_RATES_TTL", time.Hour},
	}
	for _, d := range durations {
		if *d.target, err = durationEnv(d.name, d.def); err != nil {
			return Config{}, err
		}
	}
	return cfg, nil
}

func env(name, def string) string {
	if v, ok := os.LookupEnv(name); ok && v != "" {
		return v
	}
	return def
}

func intEnv(name string, def int) (int, error) {
	v := env(name, "")
	if v == "" {
		return def, nil
	}
	n, err := strconv.Atoi(v)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", name, err)
	}
	return n, nil
}

func durationEnv(name string, def time.Duration) (time.Duration, error) {
	v := env(name, "")
	if v == "" {
		return def, nil
	}
	d, err := time.ParseDuration(v)
	if err != nil {
		return 0, fmt.Errorf("%s: %w", name, err)
	}
	return d, nil
}
