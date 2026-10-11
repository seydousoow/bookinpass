package duffel

import (
	"context"
	"errors"
	"io/fs"
	"net/http"
	"os"
	"regexp"
)

// Fixtures : mode démo, sans jeton Duffel. Réponses enregistrées (Dakar ⇄ Paris) lues dans un dossier —
// le même que le backend Spring, monté en lecture seule par Docker. Quelle que soit la recherche,
// on rend l'aller simple ou l'aller-retour enregistré.
type Fixtures struct {
	dir fs.FS
}

const (
	oneWayFixture    = "flight-dss-cdg-oneway-21_10.json"
	roundTripFixture = "flight-dss-cdg-return-21_10-21_11.json"
)

// Identifiants Duffel : off_ suivi d'alphanumériques — rien qui puisse sortir du dossier.
var offerID = regexp.MustCompile(`^off_[A-Za-z0-9]+$`)

func NewFixtures(dir string) *Fixtures { return &Fixtures{dir: os.DirFS(dir)} }

func (f *Fixtures) SearchOffers(_ context.Context, req OfferRequest) ([]byte, error) {
	if len(req.Data.Slices) > 1 {
		return fs.ReadFile(f.dir, roundTripFixture)
	}
	return fs.ReadFile(f.dir, oneWayFixture)
}

func (f *Fixtures) GetOffer(_ context.Context, id string) ([]byte, error) {
	if !offerID.MatchString(id) {
		return nil, &Error{Status: http.StatusNotFound, Op: "demo offer " + id}
	}
	data, err := fs.ReadFile(f.dir, id+".json")
	if errors.Is(err, fs.ErrNotExist) {
		return nil, &Error{Status: http.StatusNotFound, Op: "demo offer " + id}
	}
	return data, err
}
