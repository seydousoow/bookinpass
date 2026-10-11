package flight

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"net/url"
	"slices"
	"strings"
	"testing"
	"time"

	"bookinpass/back-go/internal/duffel"
)

var today = time.Date(2026, 10, 11, 9, 0, 0, 0, time.UTC)

func TestParseSearchQuery_RoundTripBuildsMirroredSlicesAndPassengers(t *testing.T) {
	q, errs := ParseSearchQuery(url.Values{
		"t": {"round"}, "o": {"DSS"}, "d": {"CDG"}, "from": {"2026-10-21"}, "to": {"2026-11-21"},
		"adt": {"2"}, "chd": {"1"}, "inf": {"1"},
	}, today)
	if len(errs) > 0 {
		t.Fatalf("unexpected errors: %v", errs)
	}

	req := q.OfferRequest()
	if got := len(req.Data.Slices); got != 2 {
		t.Fatalf("slices = %d, want 2", got)
	}
	if back := req.Data.Slices[1]; back.Origin != "CDG" || back.Destination != "DSS" || back.DepartureDate.String() != "2026-11-21" {
		t.Errorf("return slice = %+v", back)
	}
	var types []string
	for _, p := range req.Data.Passengers {
		types = append(types, p.Type)
	}
	if want := []string{"adult", "adult", "child", "infant_without_seat"}; !slices.Equal(types, want) {
		t.Errorf("passengers = %v, want %v", types, want)
	}
	if req.Data.CabinClass != "economy" {
		t.Errorf("cabin = %q, want economy by default", req.Data.CabinClass)
	}
}

func TestOfferRequestSerializesDatesAsCalendarDays(t *testing.T) {
	q, _ := ParseSearchQuery(url.Values{"t": {"oneway"}, "o": {"DSS"}, "d": {"CDG"}, "from": {"2026-10-21"}}, today)
	body, err := json.Marshal(q.OfferRequest())
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(body), `"departure_date":"2026-10-21"`) {
		t.Errorf("body = %s", body)
	}
}

func TestParseSearchQuery_CollectsEveryError(t *testing.T) {
	_, errs := ParseSearchQuery(url.Values{"t": {"round"}, "o": {"DSS"}, "d": {"DSS"}, "from": {"2020-01-01"}, "adt": {"1"}, "inf": {"2"}}, today)

	for _, want := range []string{
		"origin and destination must differ",
		"must be a date in the present or in the future",
		"a round trip needs a return date on or after the departure",
		"each infant must travel with an adult",
	} {
		if !slices.Contains(errs, want) {
			t.Errorf("missing error %q in %v", want, errs)
		}
	}
}

func TestCacheKeyMatchesTheSpringBackend(t *testing.T) {
	q, _ := ParseSearchQuery(url.Values{"t": {"oneway"}, "o": {"DSS"}, "d": {"CDG"}, "from": {"2026-10-21"}, "to": {"2026-11-21"}}, today)
	if got, want := q.CacheKey(), "oneway:DSS:CDG:2026-10-21:-:1-0-0:economy"; got != want {
		t.Errorf("CacheKey() = %q, want %q", got, want)
	}
}

func TestUntilOfferExpiry(t *testing.T) {
	ttl := UntilOfferExpiry(15 * time.Minute)
	offer := func(expiresAt time.Time) []byte {
		return []byte(`{"data":{"expires_at":"` + expiresAt.Format(time.RFC3339) + `"}}`)
	}

	if got := ttl(offer(time.Now().Add(2 * time.Minute))); got < 110*time.Second || got > 2*time.Minute {
		t.Errorf("soon-expiring offer ttl = %v, want ~2m", got)
	}
	if got := ttl(offer(time.Now().Add(time.Hour))); got != 15*time.Minute {
		t.Errorf("long-lived offer ttl = %v, want capped at 15m", got)
	}
	if got := ttl(offer(time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC))); got != time.Second {
		t.Errorf("expired offer ttl = %v, want 1s (never 0 = no expiry)", got)
	}
}

// fakeFlights remplace le service : les handlers sont testés sans Redis ni Duffel.
type fakeFlights struct {
	searchBody []byte
	offerErr   error
}

func (f fakeFlights) Search(context.Context, SearchQuery) ([]byte, error) { return f.searchBody, nil }
func (f fakeFlights) Offer(context.Context, string) ([]byte, error)       { return nil, f.offerErr }

func serve(t *testing.T, s Searcher, target string) *httptest.ResponseRecorder {
	t.Helper()
	mux := http.NewServeMux()
	h := NewHandler(s)
	h.now = func() time.Time { return today }
	h.Register(mux)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, target, nil))
	return rec
}

func TestSearchHandlerPassesDuffelJSONThrough(t *testing.T) {
	rec := serve(t, fakeFlights{searchBody: []byte(`{"data":{"offers":[]}}`)}, "/api/flights/search?t=oneway&o=DSS&d=CDG&from=2026-10-21")

	if rec.Code != http.StatusOK || rec.Header().Get("Content-Type") != "application/json" || rec.Body.String() != `{"data":{"offers":[]}}` {
		t.Errorf("got %d %q %s", rec.Code, rec.Header().Get("Content-Type"), rec.Body)
	}
}

func TestOfferHandlerRejectsMalformedIDs(t *testing.T) {
	if rec := serve(t, fakeFlights{}, "/api/flights/offers/not-an-offer"); rec.Code != http.StatusBadRequest {
		t.Errorf("status = %d, want 400", rec.Code)
	}
}

func TestExpiredOfferMapsTo422WithoutProviderDetails(t *testing.T) {
	rec := serve(t, fakeFlights{offerErr: &duffel.Error{Status: http.StatusUnprocessableEntity, Op: "secret supplier detail"}}, "/api/flights/offers/off_123")

	if rec.Code != http.StatusUnprocessableEntity {
		t.Errorf("status = %d, want 422", rec.Code)
	}
	if ct := rec.Header().Get("Content-Type"); ct != "application/problem+json" {
		t.Errorf("content type = %q", ct)
	}
	if strings.Contains(rec.Body.String(), "secret supplier detail") {
		t.Errorf("provider detail leaked: %s", rec.Body)
	}
}
