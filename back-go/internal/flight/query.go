// Package flight : recherche de vols et détail d'offre, adossés à Duffel et au cache Redis.
package flight

import (
	"fmt"
	"net/url"
	"regexp"
	"slices"
	"strconv"
	"strings"
	"time"

	"bookinpass/back-go/internal/duffel"
)

// SearchQuery : recherche vol, lue depuis les paramètres courts de l'URL du front
// (/search?t=round&o=DSS&d=CDG&from=…&to=…&adt=1&chd=0&inf=0&cabin=economy).
type SearchQuery struct {
	TripType      string // round | oneway
	Origin        string
	Destination   string
	DepartureDate duffel.Date
	ReturnDate    *duffel.Date
	Adults        int
	Children      int
	Infants       int
	Cabin         string
}

var (
	iata        = regexp.MustCompile(`^[A-Z]{3}$`)
	tripTypes   = []string{"round", "oneway"}
	cabinValues = []string{"economy", "premium_economy", "business", "first"}
)

// ParseSearchQuery lit et valide la requête. Comme le backend Spring, elle rassemble toutes les erreurs
// au lieu de s'arrêter à la première : le client corrige tout en un aller-retour.
func ParseSearchQuery(v url.Values, today time.Time) (SearchQuery, []string) {
	var errs []string
	q := SearchQuery{
		TripType:    v.Get("t"),
		Origin:      v.Get("o"),
		Destination: v.Get("d"),
		Cabin:       orDefault(v.Get("cabin"), "economy"),
	}

	if !slices.Contains(tripTypes, q.TripType) {
		errs = append(errs, "t must be one of "+strings.Join(tripTypes, ", "))
	}
	if !iata.MatchString(q.Origin) {
		errs = append(errs, `o must match "[A-Z]{3}"`)
	}
	if !iata.MatchString(q.Destination) {
		errs = append(errs, `d must match "[A-Z]{3}"`)
	}
	if q.Origin != "" && q.Origin == q.Destination {
		errs = append(errs, "origin and destination must differ")
	}
	if !slices.Contains(cabinValues, q.Cabin) {
		errs = append(errs, "cabin must be one of "+strings.Join(cabinValues, ", "))
	}

	from, err := duffel.ParseDate(v.Get("from"))
	switch {
	case err != nil:
		errs = append(errs, "from must be a date (YYYY-MM-DD)")
	case from.Before(time.Date(today.Year(), today.Month(), today.Day(), 0, 0, 0, 0, time.UTC)):
		errs = append(errs, "must be a date in the present or in the future")
	}
	q.DepartureDate = from

	if q.TripType == "round" {
		to, err := duffel.ParseDate(v.Get("to"))
		if err != nil || to.Before(from.Time) {
			errs = append(errs, "a round trip needs a return date on or after the departure")
		} else {
			q.ReturnDate = &to
		}
	}

	q.Adults, errs = intParam(v, "adt", 1, 1, 9, errs)
	q.Children, errs = intParam(v, "chd", 0, 0, 8, errs)
	q.Infants, errs = intParam(v, "inf", 0, 0, 9, errs)
	if q.Infants > q.Adults {
		// Règle des compagnies : un bébé voyage sur les genoux d'un adulte.
		errs = append(errs, "each infant must travel with an adult")
	}
	if q.Adults+q.Children+q.Infants > 9 {
		errs = append(errs, "at most 9 passengers")
	}
	return q, errs
}

// CacheKey : deux recherches identiques partagent les mêmes offres. Même format que Spring.
func (q SearchQuery) CacheKey() string {
	back := "-"
	if q.ReturnDate != nil {
		back = q.ReturnDate.String()
	}
	return fmt.Sprintf("%s:%s:%s:%s:%s:%d-%d-%d:%s",
		q.TripType, q.Origin, q.Destination, q.DepartureDate, back, q.Adults, q.Children, q.Infants, q.Cabin)
}

func (q SearchQuery) OfferRequest() duffel.OfferRequest {
	slices := []duffel.Slice{{Origin: q.Origin, Destination: q.Destination, DepartureDate: q.DepartureDate}}
	if q.ReturnDate != nil {
		slices = append(slices, duffel.Slice{Origin: q.Destination, Destination: q.Origin, DepartureDate: *q.ReturnDate})
	}
	passengers := make([]duffel.Passenger, 0, q.Adults+q.Children+q.Infants)
	for range q.Adults {
		passengers = append(passengers, duffel.Passenger{Type: "adult"})
	}
	for range q.Children {
		passengers = append(passengers, duffel.Passenger{Type: "child"})
	}
	for range q.Infants {
		passengers = append(passengers, duffel.Passenger{Type: "infant_without_seat"})
	}
	return duffel.OfferRequest{Data: duffel.OfferRequestData{Slices: slices, Passengers: passengers, CabinClass: q.Cabin}}
}

func intParam(v url.Values, name string, def, min, max int, errs []string) (int, []string) {
	raw := v.Get(name)
	if raw == "" {
		return def, errs
	}
	n, err := strconv.Atoi(raw)
	if err != nil || n < min || n > max {
		return def, append(errs, fmt.Sprintf("%s must be between %d and %d", name, min, max))
	}
	return n, errs
}

func orDefault(v, def string) string {
	if v == "" {
		return def
	}
	return v
}
