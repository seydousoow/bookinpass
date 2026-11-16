-- Référence d'une activité : 8 caractères (majuscules et chiffres, sans 0/O ni 1/I, comme les codes de réservation),
-- montrée au back-office et employée dans les adresses de l'API à la place de la clé technique.
ALTER TABLE activity
    ADD COLUMN ref VARCHAR(8) UNIQUE CHECK (ref ~ '^[A-HJ-NP-Z2-9]{8}$');

DO
$$
    DECLARE
        alphabet CONSTANT TEXT := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
        row_id            INTEGER;
        code              TEXT;
    BEGIN
        FOR row_id IN SELECT id FROM activity WHERE ref IS NULL ORDER BY id
            LOOP
                LOOP
                    SELECT string_agg(substr(alphabet, 1 + floor(random() * length(alphabet))::INT, 1), '')
                    INTO code
                    FROM generate_series(1, 8);
                    EXIT WHEN NOT EXISTS (SELECT 1 FROM activity WHERE ref = code);
                END LOOP;
                UPDATE activity SET ref = code WHERE id = row_id;
            END LOOP;
    END
$$;

ALTER TABLE activity
    ALTER COLUMN ref SET NOT NULL;

-- Position obligatoire : la recherche par ville prend les activités à moins de 100 km. Une fiche sans position prend
-- celle de sa ville, à corriger au back-office.
UPDATE activity a
SET latitude  = c.latitude,
    longitude = c.longitude
FROM city c
WHERE c.id = a.city_id
  AND a.latitude IS NULL;

ALTER TABLE activity
    ALTER COLUMN latitude SET NOT NULL,
    ALTER COLUMN longitude SET NOT NULL;

CREATE INDEX activity_published_country_idx ON activity (status, city_id);
