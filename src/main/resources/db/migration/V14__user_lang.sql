-- Langue par défaut du voyageur : celle du site lors de sa première réservation. Elle ne change plus ensuite ; il
-- pourra la modifier depuis son espace client.
ALTER TABLE users
    ADD COLUMN lang VARCHAR(2) NOT NULL DEFAULT 'fr' CHECK (lang IN ('fr', 'en'));
