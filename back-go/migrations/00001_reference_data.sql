-- Copie de backend/src/main/resources/db/migration/V1__reference_data.sql, au format goose.
-- +goose Up
-- Données de référence servies au front : aéroports (saisie origine / destination) et taux de change.

CREATE TABLE airport
(
    iata         CHAR(3) PRIMARY KEY,
    name         TEXT    NOT NULL,
    city_name    TEXT    NOT NULL,
    city_code    CHAR(3) NOT NULL,
    state        TEXT,
    country_name TEXT    NOT NULL,
    country_code CHAR(2) NOT NULL
);

CREATE INDEX airport_city_code_idx ON airport (city_code);

-- Un taux = combien vaut 1 unité de `base` dans `currency`. Montants exacts : NUMERIC, jamais de flottant.
CREATE TABLE exchange_rate
(
    base       CHAR(3)        NOT NULL,
    currency   CHAR(3)        NOT NULL,
    rate       NUMERIC(20, 10) NOT NULL CHECK (rate > 0),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT now(),
    PRIMARY KEY (base, currency)
);

-- +goose Down
DROP TABLE exchange_rate;
DROP TABLE airport;
