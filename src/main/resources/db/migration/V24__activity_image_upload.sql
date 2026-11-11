-- Photos déposées dans S3 depuis le back-office. Une photo qu'aucune fiche n'emploie (jamais enregistrée, ou retirée
-- d'une fiche) est supprimée du bucket par une tâche planifiée, après un délai de grâce.
CREATE TABLE activity_image_upload
(
    id          INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    -- Clé de l'objet dans le bucket ; l'adresse publique est celle du CDN.
    object_key  TEXT        NOT NULL UNIQUE,
    url         TEXT        NOT NULL UNIQUE,
    uploaded_by VARCHAR(50) NOT NULL,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX activity_image_upload_uploaded_at_idx ON activity_image_upload (uploaded_at);
