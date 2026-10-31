-- Réservations : voyageurs, commandes Duffel et paiements Stripe, enregistrés après la création de la commande.
-- Montants exacts : NUMERIC (décimal fournisseur) ou BIGINT (plus petite unité Stripe), jamais de flottant.

-- Voyageur. E-mail et téléphone sont ceux du contact de la réservation, partagés par tous ses passagers.
-- `match_key` (e-mail, prénom, nom, date de naissance, sexe, normalisés) retrouve un voyageur d'une réservation à
-- l'autre ; `duffel_user_id` (client Duffel, `icu_…`) n'est posé que sur le titulaire de l'e-mail : Duffel exige un
-- e-mail unique par client.
CREATE TABLE users
(
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    first_name     TEXT        NOT NULL,
    last_name      TEXT        NOT NULL,
    birth_date     DATE        NOT NULL,
    gender         VARCHAR(1)  NOT NULL CHECK (gender IN ('m', 'f')),
    email          TEXT        NOT NULL,
    phone_number   TEXT        NOT NULL,
    duffel_user_id TEXT,
    match_key      TEXT        NOT NULL UNIQUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX users_email_idx ON users (lower(email));

-- Commande Duffel. `pricing` garde le détail du prix de vente :
-- {"currency": "EUR", "fee_rate": "0.05",
--  "flight": {"price": "184.47", "markup": "9.22"},
--  "services": [{"id": "ase_…", "type": "baggage", "quantity": 2, "price": "40.00", "markup": "8.00"}]}
-- `price` = prix Duffel de la ligne, `markup` = notre marge sur la ligne (frais BIP, +20 % sur les services).
CREATE TABLE flight_order
(
    id                INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    duffel_order_id   TEXT           NOT NULL UNIQUE,
    offer_id          TEXT           NOT NULL,
    booking_reference TEXT,
    status            VARCHAR(30)    NOT NULL CHECK (status IN ('confirmed', 'changed', 'cancellation_pending',
                                                                'cancelled')),
    currency          VARCHAR(3)     NOT NULL,
    duffel_amount     NUMERIC(12, 2) NOT NULL,
    selling_amount    NUMERIC(12, 2) NOT NULL,
    pricing           JSONB          NOT NULL,
    live_mode         BOOLEAN        NOT NULL,
    -- Commande passée chez la compagnie ; paiement encaissé (réservation définitive) ; annulation confirmée.
    booked_at         TIMESTAMPTZ    NOT NULL,
    confirmed_at      TIMESTAMPTZ,
    cancelled_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE TABLE flight_order_passenger
(
    order_id            INTEGER     NOT NULL REFERENCES flight_order (id) ON DELETE CASCADE,
    user_id             INTEGER     NOT NULL REFERENCES users (id),
    duffel_passenger_id TEXT        NOT NULL,
    passenger_type      VARCHAR(30) NOT NULL,
    PRIMARY KEY (order_id, user_id)
);

CREATE INDEX flight_order_passenger_user_idx ON flight_order_passenger (user_id);

-- Historique d'une commande : création, changement (compagnie ou client), annulation et remboursement.
-- `duffel_event_id` (webhook `wev_…`) dédoublonne les livraisons répétées de Duffel.
CREATE TABLE flight_order_event
(
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id        INTEGER        NOT NULL REFERENCES flight_order (id) ON DELETE CASCADE,
    type            VARCHAR(50)    NOT NULL,
    duffel_event_id TEXT UNIQUE,
    refund_amount   NUMERIC(12, 2),
    refund_currency VARCHAR(3),
    refund_to       TEXT,
    details         JSONB,
    occurred_at     TIMESTAMPTZ    NOT NULL,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX flight_order_event_order_idx ON flight_order_event (order_id);

-- Paiement Stripe d'une réservation, de l'autorisation à l'encaissement, la libération ou le remboursement.
-- Montants en plus petite unité de la devise (centimes ; francs pour le XOF). Ni numéro de carte ni CVC : seulement
-- la marque et les 4 derniers chiffres que Stripe expose.
CREATE TABLE stripe_payment
(
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id            INTEGER REFERENCES flight_order (id),
    checkout_session_id TEXT        NOT NULL UNIQUE,
    payment_intent_id   TEXT UNIQUE,
    status              VARCHAR(30) NOT NULL CHECK (status IN ('authorised', 'captured', 'capture_failed', 'released',
                                                                'failed', 'partially_refunded', 'refunded')),
    currency            VARCHAR(3)  NOT NULL,
    amount              BIGINT      NOT NULL,
    amount_captured     BIGINT      NOT NULL DEFAULT 0,
    amount_refunded     BIGINT      NOT NULL DEFAULT 0,
    payment_method_type TEXT,
    card_brand          TEXT,
    card_last4          VARCHAR(4),
    customer_email      TEXT,
    customer_name       TEXT,
    failure_reason      TEXT,
    live_mode           BOOLEAN     NOT NULL,
    authorised_at       TIMESTAMPTZ NOT NULL,
    captured_at         TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    refunded_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX stripe_payment_order_idx ON stripe_payment (order_id);

-- Historique d'un paiement : nos actions (capture, libération) et les webhooks Stripe (`evt_…`, dédoublonnés).
CREATE TABLE stripe_payment_event
(
    id              INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    payment_id      INTEGER     NOT NULL REFERENCES stripe_payment (id) ON DELETE CASCADE,
    type            VARCHAR(80) NOT NULL,
    stripe_event_id TEXT UNIQUE,
    amount          BIGINT,
    details         JSONB,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX stripe_payment_event_payment_idx ON stripe_payment_event (payment_id);
