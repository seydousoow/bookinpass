# syntax=docker/dockerfile:1.7
#
# Front Book In Pass : build Angular statique servi par nginx. Une seule image pour tous les environnements :
# l'URL de l'API est donnée au lancement du conteneur, pas au build.
#
#   docker build -t bookinpass/frontend .
#   docker run -p 8081:80 -e API_BASE_URL=https://api.bookinpass.com/api bookinpass/frontend
#
# Variables d'exécution :
#   API_BASE_URL  base de l'API vue par le navigateur (défaut `/api`, même origine que le front).
#   API_UPSTREAM  si défini (ex. `http://backend:8080`), nginx relaie `/api/` vers ce backend : à utiliser avec
#                 API_BASE_URL=/api pour éviter le CORS.

FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci
COPY . .
RUN npx ng build

FROM nginx:1.29-alpine
COPY docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY docker/40-env.sh /docker-entrypoint.d/40-env.sh
RUN chmod +x /docker-entrypoint.d/40-env.sh
COPY --from=build /app/dist/frontend/browser /usr/share/nginx/html
ENV API_BASE_URL=/api
EXPOSE 80
