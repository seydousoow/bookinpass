-- Activités touristiques (visites, croisières, excursions) : fiches gérées au back-office, options et créneaux datés,
-- réservations payées par carte (Stripe) ou mobile money (PayDunya), annulations et remboursements, avis modérés.
-- Heures : instants UTC ; le fuseau d'une activité est celui de sa ville (`city.timezone`).

-- Fiche d'une activité. Brouillon (invisible sur le site), publiée, ou archivée (plus vendue, réservations gardées).
-- Listes en JSONB (tableaux de textes) : points forts, inclus, non inclus, interdits, à savoir avant de partir.
-- `itinerary` : [{"name", "kind", "duration_minutes", "optional", "description"}] ;
-- `images` : [{"url", "alt"}], la première est l'image principale.
-- `free_cancellation_hours` : annulation gratuite par le client jusqu'à N heures avant le début ; nulle = non remboursable.
CREATE TABLE activity
(
    id                      INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    slug                    VARCHAR(120) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'published', 'archived')),
    title                   TEXT         NOT NULL,
    city_id                 INTEGER      NOT NULL REFERENCES city (id),
    category                VARCHAR(30)  NOT NULL CHECK (category IN ('tour', 'cruise', 'excursion', 'museum', 'outdoor',
                                                                       'food', 'culture', 'water', 'other')),
    provider_name           TEXT         NOT NULL,
    summary                 TEXT         NOT NULL,
    description             TEXT         NOT NULL,
    highlights              JSONB        NOT NULL DEFAULT '[]',
    includes                JSONB        NOT NULL DEFAULT '[]',
    excludes                JSONB        NOT NULL DEFAULT '[]',
    not_allowed             JSONB        NOT NULL DEFAULT '[]',
    know_before             JSONB        NOT NULL DEFAULT '[]',
    itinerary               JSONB        NOT NULL DEFAULT '[]',
    images                  JSONB        NOT NULL DEFAULT '[]',
    meeting_point           TEXT         NOT NULL,
    latitude                DOUBLE PRECISION CHECK (latitude BETWEEN -90 AND 90),
    longitude               DOUBLE PRECISION CHECK (longitude BETWEEN -180 AND 180),
    duration_min_minutes    SMALLINT     NOT NULL CHECK (duration_min_minutes > 0),
    duration_max_minutes    SMALLINT CHECK (duration_max_minutes >= duration_min_minutes),
    free_cancellation_hours SMALLINT CHECK (free_cancellation_hours >= 0),
    currency                CHAR(3)      NOT NULL DEFAULT 'XOF',
    -- « Ce qu'en disent les voyageurs » : résumé des avis publiés, écrit par Claude à la demande d'un responsable.
    review_summary          TEXT,
    review_summary_count    INTEGER,
    review_summary_at       TIMESTAMPTZ,
    published_at            TIMESTAMPTZ,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK ((latitude IS NULL) = (longitude IS NULL))
);

CREATE INDEX activity_city_idx ON activity (city_id) WHERE status = 'published';

-- Option vendue d'une activité (ex. « Croisière seule », « Croisière + dîner ») : prix par adulte, par enfant (nul =
-- pas de tarif enfant), prix barré affiché, et places proposées par défaut sur chaque nouveau créneau.
CREATE TABLE activity_option
(
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    activity_id    INTEGER        NOT NULL REFERENCES activity (id) ON DELETE CASCADE,
    title          TEXT           NOT NULL,
    description    TEXT,
    price          NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    original_price NUMERIC(12, 2) CHECK (original_price > price),
    child_price    NUMERIC(12, 2) CHECK (child_price >= 0),
    capacity       SMALLINT       NOT NULL CHECK (capacity > 0),
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    position       SMALLINT       NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX activity_option_activity_idx ON activity_option (activity_id);

-- Créneau daté d'une option. `held` : places bloquées le temps du paiement ; `sold` : vendues. Un créneau annulé
-- (météo, prestataire) ne se vend plus ; ses réservations sont annulées et remboursées.
CREATE TABLE activity_slot
(
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    option_id     INTEGER     NOT NULL REFERENCES activity_option (id) ON DELETE CASCADE,
    starts_at     TIMESTAMPTZ NOT NULL,
    capacity      SMALLINT    NOT NULL CHECK (capacity > 0),
    sold          SMALLINT    NOT NULL DEFAULT 0 CHECK (sold >= 0),
    held          SMALLINT    NOT NULL DEFAULT 0 CHECK (held >= 0),
    cancelled_at  TIMESTAMPTZ,
    cancel_reason TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (option_id, starts_at),
    CHECK (sold + held <= capacity)
);

CREATE INDEX activity_slot_starts_idx ON activity_slot (starts_at) WHERE cancelled_at IS NULL;

-- Réservation : places bloquées (`held`) jusqu'à `hold_expires_at`, confirmée une fois payée, libérée (paiement
-- abandonné) ou annulée (par le client dans le délai d'annulation gratuite, ou par un responsable).
CREATE TABLE activity_booking
(
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference           VARCHAR(8)     NOT NULL UNIQUE,
    request_id          UUID           NOT NULL UNIQUE,
    slot_id             INTEGER        NOT NULL REFERENCES activity_slot (id),
    status              VARCHAR(20)    NOT NULL CHECK (status IN ('held', 'confirmed', 'released', 'cancelled')),
    adults              SMALLINT       NOT NULL CHECK (adults BETWEEN 0 AND 20),
    children            SMALLINT       NOT NULL DEFAULT 0 CHECK (children BETWEEN 0 AND 20),
    adult_price         NUMERIC(12, 2) NOT NULL,
    child_price         NUMERIC(12, 2),
    total               NUMERIC(12, 2) NOT NULL,
    currency            CHAR(3)        NOT NULL,
    given_name          TEXT           NOT NULL,
    family_name         TEXT           NOT NULL,
    email               TEXT           NOT NULL,
    phone               TEXT           NOT NULL,
    lang                VARCHAR(2)     NOT NULL CHECK (lang IN ('fr', 'en')),
    checkout_session_id TEXT UNIQUE,
    hold_expires_at     TIMESTAMPTZ    NOT NULL,
    confirmed_at        TIMESTAMPTZ,
    released_at         TIMESTAMPTZ,
    cancelled_at        TIMESTAMPTZ,
    -- Nul pour une annulation par le client.
    cancelled_by        INTEGER REFERENCES staff_user (id),
    cancel_reason       TEXT,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CHECK (adults + children BETWEEN 1 AND 20)
);

CREATE INDEX activity_booking_slot_idx ON activity_booking (slot_id);
CREATE INDEX activity_booking_held_idx ON activity_booking (hold_expires_at) WHERE status = 'held';
CREATE INDEX activity_booking_created_idx ON activity_booking (created_at);
CREATE INDEX activity_booking_email_idx ON activity_booking (lower(email));
CREATE INDEX activity_booking_family_name_idx ON activity_booking (lower(family_name));

-- Paiement Stripe d'une réservation d'activité.
ALTER TABLE stripe_payment
    ADD COLUMN activity_booking_id INTEGER REFERENCES activity_booking (id),
    ADD CHECK (num_nonnulls(order_id, transport_booking_id, activity_booking_id) <= 1);

CREATE INDEX stripe_payment_activity_booking_idx ON stripe_payment (activity_booking_id);

-- Facture PayDunya (Wave, Orange Money, Free Money) d'une réservation d'activité ; mêmes statuts que pour le car.
CREATE TABLE activity_mobile_payment
(
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id    INTEGER        NOT NULL REFERENCES activity_booking (id),
    provider      VARCHAR(20)    NOT NULL DEFAULT 'paydunya',
    invoice_token TEXT           NOT NULL UNIQUE,
    status        VARCHAR(20)    NOT NULL CHECK (status IN ('pending', 'completed', 'cancelled', 'failed', 'refund_needed')),
    amount        NUMERIC(12, 2) NOT NULL,
    currency      CHAR(3)        NOT NULL,
    receipt_url   TEXT,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ
);

CREATE INDEX activity_mobile_payment_booking_idx ON activity_mobile_payment (booking_id);

-- Remboursement d'une réservation (total ou partiel). Carte : remboursé par Stripe tout de suite (`refunded`, ou
-- `failed`). Mobile money : à faire à la main (`pending`), puis noté au back-office (`refunded`).
CREATE TABLE activity_refund
(
    id               INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id       INTEGER        NOT NULL REFERENCES activity_booking (id),
    method           VARCHAR(20)    NOT NULL CHECK (method IN ('card', 'mobile_money')),
    amount           NUMERIC(12, 2) NOT NULL CHECK (amount > 0),
    currency         CHAR(3)        NOT NULL,
    status           VARCHAR(20)    NOT NULL CHECK (status IN ('pending', 'refunded', 'failed')),
    reason           TEXT           NOT NULL,
    stripe_refund_id TEXT UNIQUE,
    failure_reason   TEXT,
    -- Nul : demandé par le client (annulation gratuite).
    requested_by     INTEGER REFERENCES staff_user (id),
    refunded_by      INTEGER REFERENCES staff_user (id),
    -- Mobile money : opérateur et référence de la transaction de remboursement.
    note             TEXT,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT now(),
    refunded_at      TIMESTAMPTZ
);

CREATE INDEX activity_refund_booking_idx ON activity_refund (booking_id);
CREATE INDEX activity_refund_pending_idx ON activity_refund (created_at) WHERE status = 'pending';

-- Avis d'un voyageur, un par réservation confirmée, après le créneau. Publié une fois approuvé (par Claude en
-- modération automatique, ou par un responsable). `ai_verdict` : approve, reject ou review (à voir par un humain).
CREATE TABLE activity_review
(
    id                  INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    activity_id         INTEGER     NOT NULL REFERENCES activity (id) ON DELETE CASCADE,
    booking_id          INTEGER     NOT NULL UNIQUE REFERENCES activity_booking (id),
    rating              SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    value_rating        SMALLINT CHECK (value_rating BETWEEN 1 AND 5),
    service_rating      SMALLINT CHECK (service_rating BETWEEN 1 AND 5),
    organization_rating SMALLINT CHECK (organization_rating BETWEEN 1 AND 5),
    author_name         TEXT        NOT NULL,
    country             CHAR(2),
    lang                VARCHAR(2)  NOT NULL,
    text                TEXT        NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    ai_verdict          VARCHAR(20) CHECK (ai_verdict IN ('approve', 'reject', 'review')),
    ai_reason           TEXT,
    ai_categories       JSONB,
    ai_checked_at       TIMESTAMPTZ,
    ai_attempts         SMALLINT    NOT NULL DEFAULT 0,
    -- Nul : décision de la modération automatique.
    moderated_by        INTEGER REFERENCES staff_user (id),
    moderated_at        TIMESTAMPTZ,
    rejection_reason    TEXT,
    helpful_count       INTEGER     NOT NULL DEFAULT 0,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX activity_review_published_idx ON activity_review (activity_id, created_at) WHERE status = 'approved';
CREATE INDEX activity_review_queue_idx ON activity_review (created_at) WHERE status = 'pending';

-- Réglages du back-office, modifiables sans redéploiement.
CREATE TABLE app_setting
(
    key        VARCHAR(60) PRIMARY KEY,
    value      TEXT        NOT NULL,
    updated_by INTEGER REFERENCES staff_user (id),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Modération des avis : publication automatique des avis jugés sans problème par Claude (et rejet des abus évidents).
INSERT INTO app_setting (key, value) VALUES ('reviews.auto_moderation', 'true');
