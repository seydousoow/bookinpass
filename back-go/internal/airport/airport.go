// Package airport : référentiel des aéroports servi au champ de saisie origine / destination.
package airport

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"bookinpass/back-go/internal/cache"
	"bookinpass/back-go/internal/httpx"
)

// Airport : mêmes champs JSON que l'ancien airports.json du front.
type Airport struct {
	IATA        string `json:"iata"`
	Name        string `json:"name"`
	CityName    string `json:"cityName"`
	CityCode    string `json:"cityCode"`
	State       string `json:"state"`
	CountryName string `json:"countryName"`
	CountryCode string `json:"countryCode"`
}

type Handler struct {
	db    *pgxpool.Pool
	cache *cache.Cache
	ttl   time.Duration
}

func NewHandler(db *pgxpool.Pool, c *cache.Cache, ttl time.Duration) *Handler {
	return &Handler{db: db, cache: c, ttl: ttl}
}

func (h *Handler) Register(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/airports", h.all)
}

// all : liste complète, le champ de saisie filtre côté navigateur (~2 400 aéroports, ~80 Ko compressés).
// On met en cache le JSON déjà sérialisé : une lecture de cache = une écriture sur la socket.
func (h *Handler) all(w http.ResponseWriter, r *http.Request) {
	body, err := h.cache.GetOrLoad(r.Context(), "airports::all", cache.Fixed(h.ttl), func(ctx context.Context) ([]byte, error) {
		airports, err := h.findAll(ctx)
		if err != nil {
			return nil, err
		}
		return json.Marshal(airports)
	})
	if err != nil {
		httpx.InternalError(w, r, err)
		return
	}
	httpx.WriteRawJSON(w, body)
}

func (h *Handler) findAll(ctx context.Context) ([]Airport, error) {
	rows, err := h.db.Query(ctx, `
		SELECT iata, name, city_name, city_code, coalesce(state, ''), country_name, country_code
		FROM airport
		ORDER BY country_name, city_name, iata`)
	if err != nil {
		return nil, err
	}
	// RowToStructByPos : colonnes lues dans l'ordre des champs de la struct.
	return pgx.CollectRows(rows, pgx.RowToStructByPos[Airport])
}
