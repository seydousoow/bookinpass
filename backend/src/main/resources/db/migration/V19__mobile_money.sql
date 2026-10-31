-- Paiement des billets de car par Wave / Orange Money / Free Money, via PayDunya. Une réservation peut avoir plusieurs
-- factures (paiement abandonné puis repris) ; une seule est payée.
-- `refund_needed` : payé alors que les places avaient été revendues entre-temps (paiement très tardif) — le mobile money
-- ne s'annule pas comme une autorisation de carte : remboursement à faire à la main.
CREATE TABLE transport_mobile_payment
(
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    booking_id    INTEGER        NOT NULL REFERENCES transport_booking (id),
    provider      VARCHAR(20)    NOT NULL DEFAULT 'paydunya',
    invoice_token TEXT           NOT NULL UNIQUE,
    status        VARCHAR(20)    NOT NULL CHECK (status IN ('pending', 'completed', 'cancelled', 'failed', 'refund_needed')),
    amount        NUMERIC(12, 2) NOT NULL,
    currency      CHAR(3)        NOT NULL,
    receipt_url   TEXT,
    created_at    TIMESTAMPTZ    NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ
);

CREATE INDEX transport_mobile_payment_booking_idx ON transport_mobile_payment (booking_id);
