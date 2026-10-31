-- Reçu envoyé par Stripe lui-même (Dashboard → E-mails aux clients → Paiements réussis) : plus de reçu côté
-- Book In Pass, ses colonnes sont retirées.
ALTER TABLE stripe_payment
    DROP COLUMN charge_id,
    DROP COLUMN receipt_url,
    DROP COLUMN receipt_url_expires_at;
