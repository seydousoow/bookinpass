-- Vente de billets de car : départs datés avec leurs places, réservations, un billet par passager.
-- Heures : Dakar et Banjul sont à GMT toute l'année (pas d'heure d'été) ; un départ est l'instant UTC de la date et de
-- l'heure locale de son horaire (`transport_schedule`).

-- Places d'un car, à confirmer avec l'exploitant. Services continus (Thiès, Express AIBD) : pas de limite par départ.
ALTER TABLE transport_route
    ADD COLUMN capacity SMALLINT NOT NULL DEFAULT 50 CHECK (capacity > 0);

-- Départ daté, créé à la première réservation. `held` : places bloquées le temps du paiement ; `sold` : vendues.
-- `capacity` nulle : service continu, places non comptées (billet valable de `departs_at` à `last_departure_at`).
CREATE TABLE transport_departure
(
    id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id          INTEGER     NOT NULL REFERENCES transport_route (id),
    departs_at        TIMESTAMPTZ NOT NULL,
    last_departure_at TIMESTAMPTZ,
    capacity          SMALLINT CHECK (capacity > 0),
    sold              SMALLINT    NOT NULL DEFAULT 0 CHECK (sold >= 0),
    held              SMALLINT    NOT NULL DEFAULT 0 CHECK (held >= 0),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (route_id, departs_at),
    CHECK (capacity IS NULL OR sold + held <= capacity)
);

-- Réservation : places bloquées (`held`) jusqu'à `hold_expires_at`, confirmée une fois le paiement autorisé puis
-- encaissé, ou libérée (paiement abandonné, session expirée). `request_id` (choisi par le navigateur) rend la
-- création rejouable : un double clic ne bloque pas deux fois les places.
CREATE TABLE transport_booking
(
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference           VARCHAR(8)     NOT NULL UNIQUE,
    request_id          UUID           NOT NULL UNIQUE,
    departure_id        INTEGER        NOT NULL REFERENCES transport_departure (id),
    status              VARCHAR(20)    NOT NULL CHECK (status IN ('held', 'confirmed', 'released')),
    passengers          SMALLINT       NOT NULL CHECK (passengers BETWEEN 1 AND 9),
    unit_price          NUMERIC(12, 2) NOT NULL,
    total               NUMERIC(12, 2) NOT NULL,
    currency            CHAR(3)        NOT NULL,
    email               TEXT           NOT NULL,
    phone               TEXT           NOT NULL,
    lang                VARCHAR(2)     NOT NULL CHECK (lang IN ('fr', 'en')),
    checkout_session_id TEXT UNIQUE,
    hold_expires_at     TIMESTAMPTZ    NOT NULL,
    confirmed_at        TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX transport_booking_departure_idx ON transport_booking (departure_id);
-- Libération des places dont le délai de paiement est passé.
CREATE INDEX transport_booking_held_idx ON transport_booking (hold_expires_at) WHERE status = 'held';

-- Billet d'un passager ; son code est présenté à l'embarquement (valable une fois la réservation confirmée).
CREATE TABLE transport_ticket
(
    id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id  INTEGER     NOT NULL REFERENCES transport_booking (id) ON DELETE CASCADE,
    code        VARCHAR(12) NOT NULL UNIQUE,
    given_name  TEXT        NOT NULL,
    family_name TEXT        NOT NULL,
    scanned_at  TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX transport_ticket_booking_idx ON transport_ticket (booking_id);

-- Paiement Stripe d'une réservation de car : rattaché à elle plutôt qu'à une commande de vol.
ALTER TABLE stripe_payment
    ADD COLUMN transport_booking_id INTEGER REFERENCES transport_booking (id),
    ADD CHECK (order_id IS NULL OR transport_booking_id IS NULL);

CREATE INDEX stripe_payment_transport_booking_idx ON stripe_payment (transport_booking_id);
