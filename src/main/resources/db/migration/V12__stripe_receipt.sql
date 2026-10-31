-- Reçu Stripe à la place de la facture : la réservation est payée par Checkout, un reçu suffit.
-- `receipt_url` : page du reçu de la charge (téléchargeable en PDF depuis la page), envoyée au voyageur et
-- re-téléchargeable depuis « Gérer mon vol ». Stripe fait expirer ces liens après 30 jours : passé
-- `receipt_url_expires_at`, le lien est relu sur la charge.
ALTER TABLE stripe_payment
    DROP COLUMN invoice_id,
    DROP COLUMN invoice_pdf_url,
    DROP COLUMN invoice_pdf_expires_at,
    ADD COLUMN charge_id              TEXT,
    ADD COLUMN receipt_url            TEXT,
    ADD COLUMN receipt_url_expires_at TIMESTAMPTZ;
