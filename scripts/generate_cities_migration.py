#!/usr/bin/env python3
"""Génère la migration Flyway des villes depuis un export GeoNames (citiesXXXX.txt).

    python3 scripts/generate_cities_migration.py ~/Downloads/cities5000.txt \
        src/main/resources/db/migration/V6__cities.sql

Format GeoNames : https://download.geonames.org/export/dump/readme.txt (tabulations, UTF-8, sans en-tête).
Seuls les noms alternatifs en alphabet latin sont gardés (Londres, Moskva…) : le site est servi en français et en
anglais. Chaque nom est normalisé comme `DestinationService.normalize` (sans accents, minuscules, ponctuation en
espaces) : la normalisation Java et celle-ci doivent rester identiques.
"""
import csv
import re
import sys
import unicodedata

LATIN = re.compile(r"^[\u0000-\u024F\u1E00-\u1EFF\u2019]+$")


def normalize(value: str) -> str:
    value = unicodedata.normalize("NFKD", value)
    value = "".join(c for c in value if not unicodedata.combining(c)).lower()
    return re.sub(r"[^a-z0-9]+", " ", value).strip()


def copy_text(value: str) -> str:
    """Échappement du format texte de COPY."""
    return value.replace("\\", "\\\\").replace("\t", " ").replace("\n", " ").replace("\r", " ")


def main(source: str, target: str) -> None:
    rows = []
    with open(source, encoding="utf-8", newline="") as f:
        for r in csv.reader(f, delimiter="\t", quoting=csv.QUOTE_NONE):
            geoname_id, name, ascii_name, alternates = r[0], r[1], r[2], r[3]
            latitude, longitude, country_code, population, timezone = r[4], r[5], r[8], r[14] or "0", r[17]
            key = normalize(ascii_name or name)
            names = {key, normalize(name)}
            names.update(normalize(a) for a in alternates.split(",") if a and LATIN.match(a))
            names.discard("")
            if not key or len(country_code) != 2:
                continue
            # Espace en tête et « | » entre les noms : `LIKE '% dak%'` trouve un début de mot, jamais à cheval sur deux noms.
            search = " " + " | ".join([key] + sorted(names - {key}))
            rows.append((geoname_id, name, country_code, latitude, longitude, population, timezone, key, search))

    rows.sort(key=lambda row: int(row[0]))
    with open(target, "w", encoding="utf-8", newline="\n") as out:
        out.write(f"-- Généré par scripts/generate_cities_migration.py depuis GeoNames {source.rsplit('/', 1)[-1]} "
                  f"({len(rows)} villes, licence CC BY 4.0, https://www.geonames.org).\n")
        out.write("-- Destinations d'hébergement : autocomplétion (`search_names`) et coordonnées envoyées à Duffel Stays.\n\n")
        out.write("""CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE city
(
    id           INTEGER PRIMARY KEY,
    name         TEXT             NOT NULL,
    country_code CHAR(2)          NOT NULL,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    population   BIGINT           NOT NULL,
    timezone     TEXT             NOT NULL,
    -- Nom principal normalisé (sans accents, minuscules) : classe les correspondances exactes en tête.
    name_key     TEXT             NOT NULL,
    -- Tous les noms normalisés, « | » entre eux, précédés d'une espace.
    search_names TEXT             NOT NULL
);

""")
        out.write("COPY city (id, name, country_code, latitude, longitude, population, timezone, name_key, search_names) FROM STDIN;\n")
        for row in rows:
            out.write("\t".join(copy_text(v) for v in row) + "\n")
        out.write("\\.\n\n")
        out.write("CREATE INDEX city_search_names_trgm_idx ON city USING GIN (search_names gin_trgm_ops);\n")
        out.write("CREATE INDEX city_name_key_idx ON city (name_key text_pattern_ops);\n")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
