-- Remboursement à la main d'un paiement mobile tardif (places revendues) : noté au back-office par un responsable.
ALTER TABLE transport_mobile_payment
    DROP CONSTRAINT transport_mobile_payment_status_check,
    ADD CONSTRAINT transport_mobile_payment_status_check
        CHECK (status IN ('pending', 'completed', 'cancelled', 'failed', 'refund_needed', 'refunded')),
    ADD COLUMN refunded_at TIMESTAMPTZ,
    ADD COLUMN refunded_by INTEGER REFERENCES staff_user (id),
    -- Comment le client a été remboursé (opérateur, référence de transaction).
    ADD COLUMN refund_note TEXT;

CREATE INDEX transport_mobile_payment_refund_idx ON transport_mobile_payment (completed_at) WHERE status IN ('refund_needed', 'refunded');
-- Recherche des réservations au guichet, par téléphone ou nom de famille.
CREATE INDEX transport_booking_phone_idx ON transport_booking (phone);
CREATE INDEX transport_ticket_family_name_idx ON transport_ticket (lower(family_name));
