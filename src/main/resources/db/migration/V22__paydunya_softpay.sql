-- Paiements PayDunya sur notre propre page (SOFTPAY) : moyen choisi et numéro qui paie, conservé chiffré
-- (AES-256-GCM, `<id de clé>:<base64>`, clés PAYMENT_DATA_KEYS) pour un remboursement ; version masquée pour
-- l'affichage. Lignes antérieures (page PayDunya) : moyen et numéro inconnus.
ALTER TABLE transport_mobile_payment
    ADD COLUMN method          VARCHAR(30),
    ADD COLUMN phone_encrypted TEXT,
    ADD COLUMN phone_masked    VARCHAR(30);

ALTER TABLE activity_mobile_payment
    ADD COLUMN method          VARCHAR(30),
    ADD COLUMN phone_encrypted TEXT,
    ADD COLUMN phone_masked    VARCHAR(30);

-- Tentatives récentes d'une réservation (limite de fréquence).
CREATE INDEX transport_mobile_payment_attempts_idx ON transport_mobile_payment (booking_id, created_at);
CREATE INDEX activity_mobile_payment_attempts_idx ON activity_mobile_payment (booking_id, created_at);
DROP INDEX transport_mobile_payment_booking_idx;
DROP INDEX activity_mobile_payment_booking_idx;
