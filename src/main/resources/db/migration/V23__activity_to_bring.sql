-- « À savoir avant de partir » en deux listes : « À emporter » (to_bring) et « Bon à savoir » (know_before).
ALTER TABLE activity
    ADD COLUMN to_bring JSONB NOT NULL DEFAULT '[]';

-- Fiches saisies avec les intertitres « À emporter » et « Bon à savoir » comme lignes de know_before : les lignes qui
-- suivent « À emporter » (jusqu'à « Bon à savoir ») passent dans to_bring ; les intertitres disparaissent.
WITH item AS (SELECT a.id,
                     e.value                                                       AS value,
                     e.ord,
                     trim(e.value #>> '{}') ~* '^[àa] emporter\s*:?$'               AS bring_title,
                     trim(e.value #>> '{}') ~* '^bon [àa] savoir\s*:?$'             AS know_title
              FROM activity a,
                   jsonb_array_elements(a.know_before) WITH ORDINALITY AS e(value, ord)),
     mark AS (SELECT id,
                     min(ord) FILTER (WHERE bring_title) AS bring_at,
                     min(ord) FILTER (WHERE know_title)  AS know_at
              FROM item
              GROUP BY id),
     sorted AS (SELECT i.id,
                       i.value,
                       i.ord,
                       i.bring_title OR i.know_title                                              AS title,
                       i.ord > m.bring_at AND (m.know_at IS NULL OR m.know_at < m.bring_at OR i.ord < m.know_at) AS to_bring
                FROM item i
                         JOIN mark m ON m.id = i.id
                WHERE m.bring_at IS NOT NULL)
UPDATE activity a
SET to_bring    = coalesce((SELECT jsonb_agg(s.value ORDER BY s.ord) FROM sorted s WHERE s.id = a.id AND s.to_bring AND NOT s.title), '[]'),
    know_before = coalesce((SELECT jsonb_agg(s.value ORDER BY s.ord) FROM sorted s WHERE s.id = a.id AND NOT s.to_bring AND NOT s.title), '[]')
WHERE a.id IN (SELECT id FROM sorted);
