// Package httpx : réponses JSON, erreurs RFC 7807 et middlewares HTTP communs.
package httpx

import (
	"encoding/json"
	"log/slog"
	"net/http"
)

// Problem : corps application/problem+json (RFC 7807), même forme que le ProblemDetail de Spring.
type Problem struct {
	Title    string   `json:"title"`
	Status   int      `json:"status"`
	Detail   string   `json:"detail"`
	Instance string   `json:"instance,omitempty"`
	Code     string   `json:"code"`
	Errors   []string `json:"errors,omitempty"`
}

func WriteProblem(w http.ResponseWriter, r *http.Request, p Problem) {
	p.Title = http.StatusText(p.Status)
	p.Instance = r.URL.Path
	w.Header().Set("Content-Type", "application/problem+json")
	w.WriteHeader(p.Status)
	_ = json.NewEncoder(w).Encode(p)
}

// WriteRawJSON envoie un JSON déjà sérialisé (réponse Duffel ou entrée de cache) sans le relire.
func WriteRawJSON(w http.ResponseWriter, body []byte) {
	w.Header().Set("Content-Type", "application/json")
	if _, err := w.Write(body); err != nil {
		slog.Debug("client went away", "err", err)
	}
}

// InternalError : 500 générique, l'erreur détaillée ne part que dans les journaux.
func InternalError(w http.ResponseWriter, r *http.Request, err error) {
	slog.Error("request failed", "path", r.URL.Path, "err", err)
	WriteProblem(w, r, Problem{Status: http.StatusInternalServerError, Detail: "An unexpected error occurred.", Code: "internal-error"})
}
