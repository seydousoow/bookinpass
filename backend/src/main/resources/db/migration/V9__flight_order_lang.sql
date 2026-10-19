-- Langue du site au moment de la réservation : celle des e-mails envoyés pour cette commande (confirmation,
-- modification, annulation).
ALTER TABLE flight_order
    ADD COLUMN lang VARCHAR(2) NOT NULL DEFAULT 'fr' CHECK (lang IN ('fr', 'en'));
