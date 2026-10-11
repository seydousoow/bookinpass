package flight

import (
	"context"
	"encoding/json"
	"time"

	"bookinpass/back-go/internal/cache"
	"bookinpass/back-go/internal/duffel"
)

type Service struct {
	duffel      duffel.Gateway
	cache       *cache.Cache
	searchTTL   time.Duration
	offerMaxTTL time.Duration
}

func NewService(gw duffel.Gateway, c *cache.Cache, searchTTL, offerMaxTTL time.Duration) *Service {
	return &Service{duffel: gw, cache: c, searchTTL: searchTTL, offerMaxTTL: offerMaxTTL}
}

func (s *Service) Search(ctx context.Context, q SearchQuery) ([]byte, error) {
	return s.cache.GetOrLoad(ctx, "flight-searches::"+q.CacheKey(), cache.Fixed(s.searchTTL), func(ctx context.Context) ([]byte, error) {
		return s.duffel.SearchOffers(ctx, q.OfferRequest())
	})
}

func (s *Service) Offer(ctx context.Context, offerID string) ([]byte, error) {
	return s.cache.GetOrLoad(ctx, "flight-offers::"+offerID, UntilOfferExpiry(s.offerMaxTTL), func(ctx context.Context) ([]byte, error) {
		return s.duffel.GetOffer(ctx, offerID)
	})
}

// UntilOfferExpiry : une offre ne doit jamais être servie depuis le cache après son expiration chez Duffel.
// Durée de vie = temps restant avant expires_at, plafonné à max.
func UntilOfferExpiry(max time.Duration) cache.TTLFunc {
	return func(value []byte) time.Duration {
		var offer struct {
			Data struct {
				ExpiresAt time.Time `json:"expires_at"`
			} `json:"data"`
		}
		if err := json.Unmarshal(value, &offer); err != nil || offer.Data.ExpiresAt.IsZero() {
			return max
		}
		remaining := time.Until(offer.Data.ExpiresAt)
		if remaining <= 0 {
			// Déjà expirée (ex. fixture de démo) : une seconde, car 0 signifierait « sans expiration » pour Redis.
			return time.Second
		}
		return min(remaining, max)
	}
}
