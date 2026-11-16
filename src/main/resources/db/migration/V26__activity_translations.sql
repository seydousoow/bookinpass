-- Fiches en plusieurs langues. `activity.lang` : langue dans laquelle la fiche est saisie (source). Chaque autre langue
-- servie a une traduction : celle appliquée (`content`, montrée sur le site), et une proposition de Claude en attente de
-- relecture (`proposal`). Une modification de la source rend la traduction appliquée périmée (`outdated`) et demande
-- une nouvelle proposition (`ai_pending`).
ALTER TABLE activity
    ADD COLUMN lang VARCHAR(2) NOT NULL DEFAULT 'fr' CHECK (lang IN ('fr', 'en'));

-- `content`, `proposal` : {"title", "summary", "description", "highlights", "includes", "excludes", "not_allowed",
-- "to_bring", "know_before", "itinerary": [{"name", "description"}], "meeting_point", "image_alts", "options": [{"id",
-- "title", "description"}]} ; listes dans l'ordre de la source. `source_hash` : empreinte de la source traduite.
CREATE TABLE activity_translation
(
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    activity_id   INTEGER     NOT NULL REFERENCES activity (id) ON DELETE CASCADE,
    lang          VARCHAR(2)  NOT NULL CHECK (lang IN ('fr', 'en')),
    content       JSONB,
    content_hash  VARCHAR(64),
    applied_at    TIMESTAMPTZ,
    applied_by    INTEGER REFERENCES staff_user (id),
    proposal      JSONB,
    proposal_hash VARCHAR(64),
    proposed_at   TIMESTAMPTZ,
    ai_pending    BOOLEAN     NOT NULL DEFAULT FALSE,
    ai_attempts   SMALLINT    NOT NULL DEFAULT 0,
    ai_error      TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (activity_id, lang)
);

CREATE INDEX activity_translation_pending_idx ON activity_translation (updated_at) WHERE ai_pending;

-- Fiches existantes : une proposition en anglais sera demandée à Claude.
INSERT INTO activity_translation (activity_id, lang, ai_pending)
SELECT id, 'en', TRUE
FROM activity;
