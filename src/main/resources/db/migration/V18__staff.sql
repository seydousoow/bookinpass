-- Personnel des gares (back-office) : comptes, sessions, contrôle des billets, bagages et incidents.

-- Rôles : admin (tout), manager (lignes, prix, personnel hors admins, toutes les gares), station_chief (sa gare :
-- départs, bagages, incidents, contrôle), inspector (contrôle des billets, signalement d'incidents).
CREATE TABLE staff_user
(
    id                   INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Identifiant de connexion, en minuscules.
    username             VARCHAR(50) NOT NULL UNIQUE CHECK (username = lower(username)),
    password_hash        TEXT        NOT NULL,
    full_name            TEXT        NOT NULL,
    phone                TEXT,
    role                 VARCHAR(20) NOT NULL CHECK (role IN ('admin', 'manager', 'station_chief', 'inspector')),
    station_id           INTEGER REFERENCES transport_station (id),
    active               BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Mot de passe provisoire (création, réinitialisation) : à changer avant toute autre action.
    must_change_password BOOLEAN     NOT NULL DEFAULT TRUE,
    -- 5 échecs de suite : compte bloqué 15 minutes.
    failed_logins        SMALLINT    NOT NULL DEFAULT 0,
    locked_until         TIMESTAMPTZ,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Chef de gare et contrôleur travaillent dans une gare.
    CHECK (role IN ('admin', 'manager') OR station_id IS NOT NULL)
);

-- Session : seul le condensé SHA-256 du jeton du cookie est gardé (une fuite de la table ne donne aucune session).
CREATE TABLE staff_session
(
    token_hash   CHAR(64)    PRIMARY KEY,
    staff_id     INTEGER     NOT NULL REFERENCES staff_user (id) ON DELETE CASCADE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX staff_session_staff_idx ON staff_session (staff_id);

-- Contrôle : qui a validé le billet.
ALTER TABLE transport_ticket
    ADD COLUMN scanned_by INTEGER REFERENCES staff_user (id);

-- Départ effectif et arrivée, saisis par le chef de gare.
ALTER TABLE transport_departure
    ADD COLUMN departed_at TIMESTAMPTZ,
    ADD COLUMN arrived_at  TIMESTAMPTZ;

-- Bagage enregistré à la gare, payé sur place ; son code est écrit sur l'étiquette.
CREATE TABLE transport_luggage
(
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code            VARCHAR(10)    NOT NULL UNIQUE,
    departure_id    INTEGER        NOT NULL REFERENCES transport_departure (id),
    sender_name     TEXT           NOT NULL,
    sender_phone    TEXT           NOT NULL,
    -- Pièce d'identité de l'expéditeur, telle que présentée au guichet.
    sender_id_number TEXT,
    receiver_name   TEXT           NOT NULL,
    receiver_phone  TEXT           NOT NULL,
    description     TEXT,
    weight_kg       NUMERIC(6, 1)  NOT NULL CHECK (weight_kg > 0),
    price           NUMERIC(12, 2) NOT NULL CHECK (price >= 0),
    currency        CHAR(3)        NOT NULL DEFAULT 'XOF',
    status          VARCHAR(20)    NOT NULL DEFAULT 'registered' CHECK (status IN ('registered', 'delivered')),
    registered_by   INTEGER        NOT NULL REFERENCES staff_user (id),
    delivered_at    TIMESTAMPTZ,
    delivered_by    INTEGER REFERENCES staff_user (id),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX transport_luggage_departure_idx ON transport_luggage (departure_id);

-- Incident sur une ligne, rattaché à un départ quand il en concerne un.
CREATE TABLE transport_incident
(
    id           INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id     INTEGER     NOT NULL REFERENCES transport_route (id),
    departure_id INTEGER REFERENCES transport_departure (id),
    type         VARCHAR(20) NOT NULL CHECK (type IN ('delay', 'breakdown', 'accident', 'passenger', 'luggage', 'other')),
    description  TEXT        NOT NULL,
    reported_by  INTEGER     NOT NULL REFERENCES staff_user (id),
    reported_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at  TIMESTAMPTZ,
    resolved_by  INTEGER REFERENCES staff_user (id),
    resolution   TEXT
);

CREATE INDEX transport_incident_route_idx ON transport_incident (route_id);
CREATE INDEX transport_incident_open_idx ON transport_incident (reported_at) WHERE resolved_at IS NULL;
