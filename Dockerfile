# syntax=docker/dockerfile:1.7
#
# Deux images au choix, même application :
#   native  (défaut) binaire GraalVM : démarrage ~0,3 s, ~215 Mo de RAM. Build lent (~7 min) et gourmand (10 Go pour Docker).
#   jvm              JRE 25 classique : build en ~1 min avec peu de mémoire, démarrage ~2 s.
#
#   docker build --target native -t bookinpass/backend:native .
#   docker build --target jvm    -t bookinpass/backend:jvm .
# (compose.yaml choisit via BACKEND_TARGET.)

# ---------------------------------------------------------------------------
# Dépendances Maven, communes aux deux cibles : la couche reste en cache tant que le pom ne change pas.
# ---------------------------------------------------------------------------
FROM ghcr.io/graalvm/native-image-community:25 AS deps
# L'image déclare en_US.UTF-8 sans l'installer : mvnw afficherait « setlocale: cannot change locale » à chaque ligne.
ENV LANG=C.UTF-8 LC_ALL=C.UTF-8
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src

# ---------------------------------------------------------------------------
# Compilation native (AOT Spring + native-image).
# Mémoire : avec JPA/Hibernate, le build a culminé à 7 Go de RSS sur la machine de développement (7 min).
# Allouer au moins 10 Go à Docker Desktop. Le réglage par défaut plafonne le tas à 8 Go et active -Ob
# (« quick build » : binaire un peu moins optimisé, compilation moins longue). Build de production pleinement
# optimisé, si Docker dispose de 12 Go ou plus :
#   docker compose build --build-arg NATIVE_IMAGE_OPTIONS="-J-Xmx10g"
# ---------------------------------------------------------------------------
FROM deps AS native-build
ARG NATIVE_IMAGE_OPTIONS="-J-Xmx8g -Ob"
ENV NATIVE_IMAGE_OPTIONS=${NATIVE_IMAGE_OPTIONS}
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -Pnative -DskipTests native:compile

# ---------------------------------------------------------------------------
# Compilation JVM : jar exécutable, extrait en couches (dépendances / application) pour des images légères.
# ---------------------------------------------------------------------------
FROM deps AS jvm-build
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -DskipTests package \
 && java -Djarmode=tools -jar target/bookinpass.jar extract --layers --launcher --destination target/extracted

# ---------------------------------------------------------------------------
# Image JVM.
# ---------------------------------------------------------------------------
FROM eclipse-temurin:25-jre AS jvm
# curl : uniquement pour la sonde HEALTHCHECK (absente de l'image Temurin).
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 app

WORKDIR /app

COPY --from=jvm-build /workspace/target/extracted/dependencies/ ./
COPY --from=jvm-build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=jvm-build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=jvm-build /workspace/target/extracted/application/ ./
# Réponses de démo (Duffel sans jeton, Duffel Stays désactivé) : dans l'image, au chemin que compose.yaml monte aussi.
COPY fixtures/duffel /fixtures/duffel
ENV DUFFEL_FIXTURES_DIR=file:/fixtures/duffel/

USER app
EXPOSE 8080

HEALTHCHECK --interval=10s --timeout=3s --start-period=20s --retries=5 \
  CMD curl -fsS http://localhost:8080/api/actuator/health/readiness || exit 1
# Options JVM remplaçables par l'hébergeur (ex. profil petite mémoire sur Render, voir render.yaml).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

# ---------------------------------------------------------------------------
# Image native (cible par défaut, donc en dernier) : un binaire, ni JVM ni Maven. glibc requise (Debian slim).
# ---------------------------------------------------------------------------
FROM debian:trixie-slim AS native
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --system --uid 10001 app
COPY --from=native-build /workspace/target/backend /app/backend
# Réponses de démo, comme pour l'image JVM (sans elles, l'hébergement de démo échoue : « Missing demo fixture »).
COPY fixtures/duffel /fixtures/duffel
ENV DUFFEL_FIXTURES_DIR=file:/fixtures/duffel/
WORKDIR /app
USER app
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=10s --retries=5 \
  CMD curl -fsS http://localhost:8080/api/actuator/health/readiness || exit 1
ENTRYPOINT ["/app/backend"]
