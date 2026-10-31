-- Lien du PDF de la facture (invoice_pdf), gardé un mois à partir de la première lecture : les téléchargements
-- suivants ne rappellent pas Stripe. Passé ce délai, le lien est relu chez Stripe et gardé de nouveau.
ALTER TABLE stripe_payment
    ADD COLUMN invoice_pdf_url        TEXT,
    ADD COLUMN invoice_pdf_expires_at TIMESTAMPTZ;
