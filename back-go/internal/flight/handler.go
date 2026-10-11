package flight

import (
	"context"
	"errors"
	"net/http"
	"regexp"
	"strconv"
	"time"

	"bookinpass/back-go/internal/duffel"
	"bookinpass/back-go/internal/httpx"
)

// Searcher : ce dont les handlers ont besoin — une interface côté consommateur, pour les tests.
type Searcher interface {
	Search(ctx context.Context, q SearchQuery) ([]byte, error)
	Offer(ctx context.Context, offerID string) ([]byte, error)
}

type Handler struct {
	flights Searcher
	now     func() time.Time
}

func NewHandler(s Searcher) *Handler { return &Handler{flights: s, now: time.Now} }

var offerIDPattern = regexp.MustCompile(`^off_[A-Za-z0-9]+$`)

func (h *Handler) Register(mux *http.ServeMux) {
	mux.HandleFunc("GET /api/flights/search", h.search)
	mux.HandleFunc("GET /api/flights/offers/{offerID}", h.offer)
}

func (h *Handler) search(w http.ResponseWriter, r *http.Request) {
	q, errs := ParseSearchQuery(r.URL.Query(), h.now())
	if len(errs) > 0 {
		httpx.WriteProblem(w, r, httpx.Problem{Status: http.StatusBadRequest, Detail: "The request is invalid.", Code: "invalid-request", Errors: errs})
		return
	}
	body, err := h.flights.Search(r.Context(), q)
	if err != nil {
		writeProviderError(w, r, err)
		return
	}
	httpx.WriteRawJSON(w, body)
}

func (h *Handler) offer(w http.ResponseWriter, r *http.Request) {
	id := r.PathValue("offerID")
	if !offerIDPattern.MatchString(id) {
		httpx.WriteProblem(w, r, httpx.Problem{Status: http.StatusBadRequest, Detail: "The request is invalid.", Code: "invalid-request",
			Errors: []string{`must match "off_[A-Za-z0-9]+"`}})
		return
	}
	body, err := h.flights.Offer(r.Context(), id)
	if err != nil {
		writeProviderError(w, r, err)
		return
	}
	httpx.WriteRawJSON(w, body)
}

// writeProviderError traduit un échec Duffel — mêmes statuts et codes que le backend Spring.
// 404/410 : offre inconnue ou retirée. 422 : plus vendable. Le reste est un problème de notre côté : 502.
func writeProviderError(w http.ResponseWriter, r *http.Request, err error) {
	if errors.Is(err, context.DeadlineExceeded) {
		httpx.WriteProblem(w, r, httpx.Problem{Status: http.StatusGatewayTimeout, Detail: "The flight provider did not answer in time.", Code: "flight-provider-timeout"})
		return
	}
	var de *duffel.Error
	if !errors.As(err, &de) {
		httpx.InternalError(w, r, err)
		return
	}
	p := httpx.Problem{Status: http.StatusBadGateway, Detail: "The flight provider is unavailable. Please try again."}
	switch de.Status {
	case http.StatusNotFound, http.StatusGone:
		p.Status, p.Detail = http.StatusNotFound, "This flight offer does not exist or is no longer available."
	case http.StatusUnprocessableEntity:
		p.Status, p.Detail = http.StatusUnprocessableEntity, "This flight offer can no longer be booked."
	case http.StatusTooManyRequests:
		p.Status = http.StatusServiceUnavailable
	}
	p.Code = "flight-provider-" + strconv.Itoa(p.Status)
	httpx.WriteProblem(w, r, p)
}
