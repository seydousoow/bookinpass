// Package rate : taux de change depuis la devise contractuelle (XOF), servis au front.
package rate

import (
	"context"
	"encoding/json"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"bookinpass/back-go/internal/cache"
	"bookinpass/back-go/internal/httpx"
)

// ContractCurrency : devise dans laquelle les contrats et les paiements sont exprimés.
const ContractCurrency = "XOF"

// ExchangeRates : même forme que l'ancien rates.json du front. Rates : montant de la devise pour 1 XOF.
type ExchangeRates struct {
	Base      string                 `json:"base"`
	UpdatedAt time.Time              `json:"updatedAt"`
	Rates     map[string]json.Number `json:"rates"`
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
	mux.HandleFunc("GET /api/rates", h.current)
}

func (h *Handler) current(w http.ResponseWriter, r *http.Request) {
	body, err := h.cache.GetOrLoad(r.Context(), "rates::"+ContractCurrency, cache.Fixed(h.ttl), func(ctx context.Context) ([]byte, error) {
		rates, err := h.load(ctx)
		if err != nil {
			return nil, err
		}
		return json.Marshal(rates)
	})
	if err != nil {
		httpx.InternalError(w, r, err)
		return
	}
	httpx.WriteRawJSON(w, body)
}

func (h *Handler) load(ctx context.Context) (ExchangeRates, error) {
	// rate::text + trim_scale : le NUMERIC exact part tel quel en JSON, sans passer par un float64
	// (0.00152449 et non 0.0015244900 ni 0.0015244899999999).
	rows, err := h.db.Query(ctx, `
		SELECT currency, trim_scale(rate)::text, updated_at
		FROM exchange_rate WHERE base = $1 ORDER BY currency`, ContractCurrency)
	if err != nil {
		return ExchangeRates{}, err
	}
	defer rows.Close()

	out := ExchangeRates{Base: ContractCurrency, Rates: map[string]json.Number{}}
	for rows.Next() {
		var currency, value string
		var updatedAt time.Time
		if err := rows.Scan(&currency, &value, &updatedAt); err != nil {
			return ExchangeRates{}, err
		}
		out.Rates[currency] = json.Number(value)
		if updatedAt.After(out.UpdatedAt) {
			out.UpdatedAt = updatedAt.UTC()
		}
	}
	return out, rows.Err()
}
