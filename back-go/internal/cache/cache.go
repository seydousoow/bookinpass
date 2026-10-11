// Package cache : lecture-ou-chargement sur Redis, avec une durée de vie par entrée.
package cache

import (
	"context"
	"errors"
	"log/slog"
	"time"

	"github.com/redis/go-redis/v9"
	"golang.org/x/sync/singleflight"
)

// TTLFunc calcule la durée de vie à partir de la valeur chargée (ex. jusqu'à l'expiration d'une offre).
type TTLFunc func(value []byte) time.Duration

// Fixed : même durée de vie pour toutes les entrées.
func Fixed(d time.Duration) TTLFunc { return func([]byte) time.Duration { return d } }

type Cache struct {
	rdb   *redis.Client
	group singleflight.Group
}

func New(rdb *redis.Client) *Cache { return &Cache{rdb: rdb} }

// GetOrLoad rend la valeur en cache, ou la charge, la stocke et la rend.
//
// Deux choix explicites, que Spring ne fait pas par défaut :
//   - singleflight : 50 requêtes simultanées sur la même recherche déclenchent un seul appel Duffel ;
//   - Redis indisponible = cache manqué, pas une erreur : l'API reste servie, juste plus lentement.
func (c *Cache) GetOrLoad(ctx context.Context, key string, ttl TTLFunc, load func(context.Context) ([]byte, error)) ([]byte, error) {
	key = "bip:" + key
	cached, err := c.rdb.Get(ctx, key).Bytes()
	switch {
	case err == nil:
		return cached, nil
	case !errors.Is(err, redis.Nil):
		slog.Warn("cache read failed, loading directly", "key", key, "err", err)
	}

	v, err, _ := c.group.Do(key, func() (any, error) {
		value, err := load(ctx)
		if err != nil {
			return nil, err
		}
		if err := c.rdb.Set(ctx, key, value, ttl(value)).Err(); err != nil {
			slog.Warn("cache write failed", "key", key, "err", err)
		}
		return value, nil
	})
	if err != nil {
		return nil, err
	}
	return v.([]byte), nil
}
