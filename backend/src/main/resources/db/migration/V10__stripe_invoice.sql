-- Facture Stripe d'un paiement encaissé (in_…) : émise via l'API Invoicing, payée hors Stripe (le paiement est celui de
-- Checkout). Téléchargeable depuis « Gérer mon vol ».
ALTER TABLE stripe_payment
    ADD COLUMN invoice_id TEXT UNIQUE;
