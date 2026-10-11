package duffel

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"strconv"
	"time"
)

// Client appelle réellement api.duffel.com.
type Client struct {
	http            *http.Client
	baseURL         string
	token           string
	version         string
	supplierTimeout time.Duration
}

func NewClient(baseURL, token, version string, supplierTimeout, readTimeout time.Duration) *Client {
	return &Client{
		// Le Transport par défaut gère seul le gzip : il demande la compression et décompresse la réponse.
		http:            &http.Client{Timeout: readTimeout},
		baseURL:         baseURL,
		token:           token,
		version:         version,
		supplierTimeout: supplierTimeout,
	}
}

func (c *Client) SearchOffers(ctx context.Context, req OfferRequest) ([]byte, error) {
	body, err := json.Marshal(req)
	if err != nil {
		return nil, err
	}
	q := url.Values{
		"return_offers":    {"true"},
		"supplier_timeout": {strconv.FormatInt(c.supplierTimeout.Milliseconds(), 10)},
	}
	return c.do(ctx, http.MethodPost, "/air/offer_requests?"+q.Encode(), bytes.NewReader(body), "offer request")
}

func (c *Client) GetOffer(ctx context.Context, offerID string) ([]byte, error) {
	path := "/air/offers/" + url.PathEscape(offerID) + "?return_available_services=true"
	return c.do(ctx, http.MethodGet, path, nil, "offer "+offerID)
}

func (c *Client) do(ctx context.Context, method, path string, body io.Reader, op string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, method, c.baseURL+path, body)
	if err != nil {
		return nil, err
	}
	req.Header.Set("Authorization", "Bearer "+c.token)
	req.Header.Set("Duffel-Version", c.version)
	req.Header.Set("Accept", "application/json")
	if body != nil {
		req.Header.Set("Content-Type", "application/json")
	}

	res, err := c.http.Do(req)
	if err != nil {
		return nil, fmt.Errorf("duffel %s: %w", op, err)
	}
	defer res.Body.Close()

	if res.StatusCode >= 400 {
		// Le corps peut contenir des détails fournisseur : on ne journalise que le statut.
		slog.Warn("duffel call failed", "op", op, "status", res.StatusCode)
		_, _ = io.Copy(io.Discard, res.Body)
		return nil, &Error{Status: res.StatusCode, Op: op}
	}
	return io.ReadAll(res.Body)
}
