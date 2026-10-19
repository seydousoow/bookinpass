// Configuration lue au démarrage, avant l'application. En développement : l'API derrière le proxy (`/api`).
// L'image Docker réécrit ce fichier au lancement du conteneur à partir de API_BASE_URL (voir docker/40-env.sh).
window.__env = { apiBaseUrl: '/api' };
