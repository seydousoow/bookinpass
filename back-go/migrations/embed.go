// Package migrations embarque les scripts SQL dans le binaire : rien à copier à côté au déploiement.
package migrations

import "embed"

//go:embed *.sql
var FS embed.FS
