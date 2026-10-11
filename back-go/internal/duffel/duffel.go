// Package duffel donne accès à l'API vol de Duffel. Les réponses restent du JSON brut ([]byte) :
// le front consomme le format Duffel, le backend n'a rien à reformater.
package duffel

import (
	"context"
	"fmt"
	"time"
)

// Gateway est implémentée par le client HTTP réel et par les fixtures du mode démo.
type Gateway interface {
	// SearchOffers : POST /air/offer_requests?return_offers=true — la demande et ses offres.
	SearchOffers(ctx context.Context, req OfferRequest) ([]byte, error)
	// GetOffer : GET /air/offers/{id} — une offre à jour, avec ses services disponibles.
	GetOffer(ctx context.Context, offerID string) ([]byte, error)
}

// Error : échec côté Duffel. Le statut d'origine est conservé pour être traduit en réponse API ;
// le corps fournisseur n'est jamais renvoyé au client.
type Error struct {
	Status int
	Op     string
}

func (e *Error) Error() string { return fmt.Sprintf("duffel %s failed with HTTP %d", e.Op, e.Status) }

// OfferRequest : corps de POST /air/offer_requests, au format Duffel (snake_case, enveloppe data).
type OfferRequest struct {
	Data OfferRequestData `json:"data"`
}

type OfferRequestData struct {
	Slices     []Slice     `json:"slices"`
	Passengers []Passenger `json:"passengers"`
	CabinClass string      `json:"cabin_class"`
}

type Slice struct {
	Origin        string `json:"origin"`
	Destination   string `json:"destination"`
	DepartureDate Date   `json:"departure_date"`
}

// Passenger : type adult, child ou infant_without_seat.
type Passenger struct {
	Type string `json:"type"`
}

// Date est un jour calendaire, sérialisé "2026-10-21" — ce que Duffel attend, pas un horodatage.
type Date struct{ time.Time }

const dateLayout = "2006-01-02"

func (d Date) MarshalJSON() ([]byte, error) { return []byte(`"` + d.Format(dateLayout) + `"`), nil }

func (d Date) String() string { return d.Format(dateLayout) }

func ParseDate(s string) (Date, error) {
	t, err := time.Parse(dateLayout, s)
	return Date{t}, err
}
