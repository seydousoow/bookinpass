# Book In Pass — back-office des gares

Application Angular séparée du site public : personnel des gares (admin, responsable, chef de gare, contrôleur).
Départs du jour, passagers, contrôle des billets (caméra ou saisie du code), bagages, incidents, lignes et personnel.

## Développement

```bash
npm ci
npm start            # http://localhost:4300, /api relayé vers http://localhost:8080 (proxy.conf.json)
npm test
```

Backend : `BACKOFFICE_URL=http://localhost:4300` (valeur par défaut) ; profil `dev` (cookie non `Secure` en http).
Premier administrateur : `BOOTSTRAP_ADMIN_USERNAME` et `BOOTSTRAP_ADMIN_PASSWORD` au démarrage du backend (mot de passe
provisoire, à changer à la première connexion).

## Déploiement (Vercel)

- Projet Vercel avec `backoffice/` comme dossier racine ; `vercel.json` construit et réécrit `env.js`.
- `API_BASE_URL` : URL de l'API, ex. `https://bip-back.dicortex.com/api`.
- **Domaine sur le même site que l'API** (ex. `bip-admin.dicortex.com` avec `bip-back.dicortex.com`) : le cookie de session
  est `SameSite=Lax`. Sur `*.vercel.app`, Safari et les navigateurs qui bloquent les cookies tiers refuseraient la session.
- Côté API (Render) : `BACKOFFICE_URL` = l'origine exacte du back-office (CORS avec cookies et contrôle `Origin` des écritures).
- La caméra demande HTTPS (ou `localhost`).
