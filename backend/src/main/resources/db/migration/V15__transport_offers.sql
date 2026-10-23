-- Transport en car : exploitants, gares, lignes (un sens chacune) et horaires hebdomadaires.
-- Données relevées sur le site de Dakar Dem Dikk le 24/10/2026 (réseaux interurbain, international, Express AIBD).
-- Prix en XOF ; `transport_schedule` porte des horaires-types par jour de semaine, pas encore des départs datés
-- avec places (ils viendront avec la vente).

CREATE TABLE transport_operator
(
    code  VARCHAR(10) PRIMARY KEY,
    name  TEXT NOT NULL,
    -- Centre d'appel, E.164.
    phone TEXT
);

-- Gare : une ville GeoNames (recherche par ville, fuseau horaire) ou un aéroport.
CREATE TABLE transport_station
(
    id            INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code          VARCHAR(30) NOT NULL UNIQUE,
    operator_code VARCHAR(10) NOT NULL REFERENCES transport_operator (code),
    name          TEXT        NOT NULL,
    city_id       INTEGER REFERENCES city (id),
    airport_iata  CHAR(3) REFERENCES airport (iata),
    address       TEXT,
    -- Téléphone de la gare, E.164.
    phone         TEXT,
    CHECK (city_id IS NOT NULL OR airport_iata IS NOT NULL)
);

-- Ligne dans un sens : l'aller et le retour sont deux lignes, chacune avec ses horaires.
CREATE TABLE transport_route
(
    id                     INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code                   VARCHAR(70)    NOT NULL UNIQUE,
    operator_code          VARCHAR(10)    NOT NULL REFERENCES transport_operator (code),
    network                VARCHAR(20)    NOT NULL CHECK (network IN ('interurban', 'international', 'airport')),
    origin_station_id      INTEGER        NOT NULL REFERENCES transport_station (id),
    destination_station_id INTEGER        NOT NULL REFERENCES transport_station (id),
    price                  NUMERIC(12, 2) NOT NULL CHECK (price > 0),
    currency               CHAR(3)        NOT NULL DEFAULT 'XOF',
    active                 BOOLEAN        NOT NULL DEFAULT TRUE,
    CHECK (origin_station_id <> destination_station_id),
    UNIQUE (origin_station_id, destination_station_id)
);

CREATE INDEX transport_route_destination_idx ON transport_route (destination_station_id);

-- Horaire-type : jour ISO (1 = lundi … 7 = dimanche) et heure locale de la gare de départ.
-- `last_departure` renseigné = service continu de `departs_at` à `last_departure` (Thiès, Mbour → Dakar, AIBD) :
-- pas d'heure fixe, le billet vaut pour la journée.
CREATE TABLE transport_schedule
(
    id             INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    route_id       INTEGER  NOT NULL REFERENCES transport_route (id) ON DELETE CASCADE,
    day_of_week    SMALLINT NOT NULL CHECK (day_of_week BETWEEN 1 AND 7),
    departs_at     TIME     NOT NULL,
    last_departure TIME CHECK (last_departure > departs_at),
    UNIQUE (route_id, day_of_week, departs_at)
);

INSERT INTO transport_operator (code, name, phone)
VALUES ('DDD', 'Dakar Dem Dikk', '+221338241010');

-- Identifiants GeoNames de la table `city` (V6).
INSERT INTO transport_station (code, operator_code, name, city_id, airport_iata, address, phone)
VALUES ('DKR-LIB5', 'DDD', 'Terminus Liberté 5', 2253354, NULL, 'Liberté 5, Dakar', NULL),
       ('DKR-HLMGY', 'DDD', 'Terminus HLM Grand Yoff', 2253354, NULL, 'HLM Grand Yoff, en face du stade Léopold Sédar Senghor', NULL),
       ('DKR-COLOBANE', 'DDD', 'Gare de Colobane', 2253354, NULL, 'Colobane, Dakar', NULL),
       ('DKR-PA', 'DDD', 'Terminus des Parcelles Assainies', 2253354, NULL, 'Parcelles Assainies, Dakar', '+221775893627'),
       ('AIBD', 'DDD', 'Aéroport International Blaise Diagne', NULL, 'DSS', 'Diass', NULL),
       ('BANJUL', 'DDD', 'Dépôt GTSC Kanifing', 2413876, NULL, 'Dépôt de GTSC, Kanifing', '+2203048127'),
       ('FATICK', 'DDD', 'Fatick', 2251911, NULL, 'Boulevard, en face de Keur Macky Sall', '+221786200407'),
       ('PODOR', 'DDD', 'Podor', 6650652, NULL, 'Lao Demba, ancien ENO, près du stade municipal Alassane Wade', '+221786200402'),
       ('NDIOUM', 'DDD', 'Ndioum', 2247684, NULL, 'Quartier Nianga, immeuble Harouna Dia Mélou', '+221786200403'),
       ('KEDOUGOU', 'DDD', 'Kédougou', 2250645, NULL, 'Quartier Gomba, villa 172', '+221773853261'),
       ('LOUGA', 'DDD', 'Louga', 2249222, NULL,
        'Quartier Santhiaba Sud, route de la Gouvernance et du palais de justice, à côté de l''Inspection académique', '+221778480240'),
       ('KEBEMER', 'DDD', 'Kébémer', 2250659, NULL, NULL, NULL),
       ('DIOURBEL', 'DDD', 'Diourbel', 2252309, NULL, 'Quartier Cheikh Anta', '+221778480236'),
       ('THIES', 'DDD', 'Thiès', 2244802, NULL, 'Grand Standing, derrière la station EDK Oil', NULL),
       ('MATAM', 'DDD', 'Matam', 2248752, NULL, 'Quartier Moderne 1, derrière l''hôtel Oasis', '+221786200404'),
       ('MBOUR', 'DDD', 'Mbour', 2248477, NULL, 'Route nationale, en face du terrain Akhmadou Barro', '+221786200412'),
       ('TAMBACOUNDA', 'DDD', 'Tambacounda', 2244991, NULL, 'Médina Coura, en face de l''église Jean XXIII', '+221786200411'),
       ('SAINT-LOUIS', 'DDD', 'Saint-Louis', 2246452, NULL, 'Pikine Guinaw Rail, derrière la station Oil Libya', '+221786200401'),
       ('KAOLACK', 'DDD', 'Kaolack', 2250805, NULL, 'Quartier Casa Ville, en face de Gneti Gouy', '+221786200408'),
       ('ZIGUINCHOR', 'DDD', 'Ziguinchor', 2243940, NULL,
        'Quartier Néma, avenue Émile Badiane, en face de la DRDR (ex-DERBAC)', '+221786200409'),
       ('VELINGARA', 'DDD', 'Vélingara', 2244177, NULL, 'Quartier HLM, en face de la station Elton', '+221784604337'),
       ('TOUBA', 'DDD', 'Touba', 2244322, NULL, '28, pharmacie Serigne Fallou Mbacké', '+221786200406'),
       ('KOLDA', 'DDD', 'Kolda', 2249782, NULL, 'Derrière le stade régional, près de la pharmacie Tamarassy', '+221786200410'),
       ('TIVAOUANE', 'DDD', 'Tivaouane', 2244386, NULL,
        'Quartier El Malick Sy 2, route de Ndiassane, à côté du lycée Ababacar Sy', '+221786200405'),
       ('BIGNONA', 'DDD', 'Bignona', 2253901, NULL, 'Près du camp sapeur, sur la piste du stade', '+221778460293'),
       ('SEDHIOU', 'DDD', 'Sédhiou', 2245704, NULL, 'Quartier Santassou', '+221784604335'),
       ('BAKEL', 'DDD', 'Bakel', 2254441, NULL, 'HLM Bakel, à côté de la CNCA', '+221781845888'),
       ('KAFFRINE', 'DDD', 'Kaffrine', 2251007, NULL, 'Cité Millionnaire (Ndjigui 2)', '+221775893628'),
       ('KIDIRA', 'DDD', 'Kidira', 2249892, NULL, 'Quartier Plateau, en face de l''ancienne gare ferroviaire', NULL);

-- Code d'une ligne : gare de départ et gare d'arrivée.
WITH r (network, origin, destination, price) AS (
    VALUES ('interurban', 'DKR-LIB5', 'FATICK', 3000),
           ('interurban', 'FATICK', 'DKR-LIB5', 3000),
           ('interurban', 'DKR-LIB5', 'PODOR', 8000),
           ('interurban', 'PODOR', 'DKR-LIB5', 8000),
           ('interurban', 'DKR-LIB5', 'NDIOUM', 8000),
           ('interurban', 'DKR-LIB5', 'KEDOUGOU', 12000),
           ('interurban', 'KEDOUGOU', 'DKR-LIB5', 12000),
           ('interurban', 'DKR-LIB5', 'LOUGA', 4000),
           ('interurban', 'LOUGA', 'DKR-LIB5', 4000),
           ('interurban', 'DKR-LIB5', 'KEBEMER', 3000),
           ('interurban', 'DKR-LIB5', 'DIOURBEL', 3000),
           ('interurban', 'DIOURBEL', 'DKR-LIB5', 3000),
           ('interurban', 'DKR-COLOBANE', 'THIES', 2000),
           ('interurban', 'THIES', 'DKR-COLOBANE', 2000),
           ('interurban', 'DKR-LIB5', 'MATAM', 10000),
           ('interurban', 'MATAM', 'DKR-LIB5', 10000),
           ('interurban', 'DKR-LIB5', 'MBOUR', 2500),
           ('interurban', 'MBOUR', 'DKR-LIB5', 2500),
           ('interurban', 'DKR-LIB5', 'TAMBACOUNDA', 9000),
           ('interurban', 'TAMBACOUNDA', 'DKR-LIB5', 9000),
           ('interurban', 'DKR-LIB5', 'SAINT-LOUIS', 5000),
           ('interurban', 'SAINT-LOUIS', 'DKR-LIB5', 5000),
           ('interurban', 'DKR-LIB5', 'KAOLACK', 4000),
           ('interurban', 'KAOLACK', 'DKR-LIB5', 4000),
           ('interurban', 'DKR-LIB5', 'ZIGUINCHOR', 9000),
           ('interurban', 'ZIGUINCHOR', 'DKR-LIB5', 9000),
           ('interurban', 'DKR-LIB5', 'VELINGARA', 11000),
           ('interurban', 'VELINGARA', 'DKR-LIB5', 11000),
           ('interurban', 'DKR-LIB5', 'TOUBA', 4000),
           ('interurban', 'TOUBA', 'DKR-LIB5', 4000),
           ('interurban', 'DKR-LIB5', 'KOLDA', 9000),
           ('interurban', 'KOLDA', 'DKR-LIB5', 9000),
           ('interurban', 'DKR-LIB5', 'TIVAOUANE', 2500),
           ('interurban', 'TIVAOUANE', 'DKR-LIB5', 2500),
           ('interurban', 'DKR-LIB5', 'BIGNONA', 8000),
           ('interurban', 'BIGNONA', 'DKR-LIB5', 8000),
           ('interurban', 'DKR-LIB5', 'SEDHIOU', 9000),
           ('interurban', 'SEDHIOU', 'DKR-LIB5', 9000),
           ('interurban', 'DKR-HLMGY', 'BAKEL', 12000),
           ('interurban', 'BAKEL', 'DKR-HLMGY', 12000),
           ('interurban', 'DKR-HLMGY', 'KAFFRINE', 5000),
           ('interurban', 'KAFFRINE', 'DKR-HLMGY', 5000),
           ('interurban', 'DKR-HLMGY', 'KIDIRA', 13000),
           ('interurban', 'KIDIRA', 'DKR-HLMGY', 13000),
           ('international', 'DKR-PA', 'BANJUL', 12000),
           ('international', 'BANJUL', 'DKR-PA', 12000),
           ('airport', 'DKR-HLMGY', 'AIBD', 6000),
           ('airport', 'AIBD', 'DKR-HLMGY', 6000))
INSERT
INTO transport_route (code, operator_code, network, origin_station_id, destination_station_id, price)
SELECT o.code || '_' || d.code, 'DDD', r.network, o.id, d.id, r.price
FROM r
         JOIN transport_station o ON o.code = r.origin
         JOIN transport_station d ON d.code = r.destination;

-- Jours ISO × heures. Hypothèses, le site ne donnant parfois qu'un horaire ou qu'une liste de jours pour les deux sens :
-- - Fatick, Louga, Diourbel, Kaolack, Touba : mêmes heures dans les deux sens ;
-- - Matam, Sédhiou, Kidira : retour les mêmes jours que l'aller ;
-- - Tivaouane : le vendredi, départ de Dakar à 10 h au lieu de 17 h, et second départ de Tivaouane à 17 h 30.
-- Sans horaire publié, pas de retour Ndioum → Dakar ni Kébémer → Dakar.
WITH s (origin, destination, days, times, last_departure) AS (
    VALUES ('DKR-LIB5', 'FATICK', '{1,2,3,4,5,6,7}'::SMALLINT[], '{07:00,15:00}'::TIME[], NULL::TIME),
           ('FATICK', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('DKR-LIB5', 'PODOR', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('PODOR', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{06:00}', NULL),
           ('DKR-LIB5', 'NDIOUM', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('DKR-LIB5', 'KEDOUGOU', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('KEDOUGOU', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('DKR-LIB5', 'LOUGA', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('LOUGA', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('DKR-LIB5', 'KEBEMER', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('DKR-LIB5', 'DIOURBEL', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('DIOURBEL', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('DKR-COLOBANE', 'THIES', '{1,2,3,4,5,6,7}', '{07:00}', '19:00'),
           ('THIES', 'DKR-COLOBANE', '{1,2,3,4,5,6,7}', '{06:00}', '19:00'),
           ('DKR-LIB5', 'MATAM', '{1,4,6}', '{08:00}', NULL),
           ('MATAM', 'DKR-LIB5', '{1,4,6}', '{06:00}', NULL),
           ('DKR-LIB5', 'MBOUR', '{1,2,3,4,5,6,7}', '{08:00,16:00}', NULL),
           ('MBOUR', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00}', '16:00'),
           ('DKR-LIB5', 'TAMBACOUNDA', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('TAMBACOUNDA', 'DKR-LIB5', '{1,2,3,5,6,7}', '{08:30}', NULL),
           ('DKR-LIB5', 'SAINT-LOUIS', '{1,2,3,4,5,6,7}', '{07:00,14:00}', NULL),
           ('SAINT-LOUIS', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00,14:00}', NULL),
           ('DKR-LIB5', 'KAOLACK', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('KAOLACK', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00,15:00}', NULL),
           ('DKR-LIB5', 'ZIGUINCHOR', '{1,2,3,4,5,6,7}', '{08:00}', NULL),
           ('ZIGUINCHOR', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('DKR-LIB5', 'VELINGARA', '{1,2,3,4,5,6,7}', '{08:00}', NULL),
           ('VELINGARA', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('DKR-LIB5', 'TOUBA', '{1,2,4,5,6,7}', '{07:00,15:00}', NULL),
           ('TOUBA', 'DKR-LIB5', '{1,2,4,5,6,7}', '{07:00,15:00}', NULL),
           ('DKR-LIB5', 'KOLDA', '{1,2,3,4,5,6,7}', '{08:00}', NULL),
           ('KOLDA', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('DKR-LIB5', 'TIVAOUANE', '{1,2,3,4,6,7}', '{17:00}', NULL),
           ('DKR-LIB5', 'TIVAOUANE', '{5}', '{10:00}', NULL),
           ('TIVAOUANE', 'DKR-LIB5', '{1,2,3,4,6,7}', '{07:00}', NULL),
           ('TIVAOUANE', 'DKR-LIB5', '{5}', '{07:00,17:30}', NULL),
           ('DKR-LIB5', 'BIGNONA', '{1,2,3,4,5,6,7}', '{08:00}', NULL),
           ('BIGNONA', 'DKR-LIB5', '{1,2,3,4,5,6,7}', '{08:00}', NULL),
           ('DKR-LIB5', 'SEDHIOU', '{1,3,6}', '{08:00}', NULL),
           ('SEDHIOU', 'DKR-LIB5', '{1,3,6}', '{07:00}', NULL),
           ('DKR-HLMGY', 'BAKEL', '{2,5,7}', '{07:00}', NULL),
           ('BAKEL', 'DKR-HLMGY', '{2,5,7}', '{07:00}', NULL),
           ('DKR-HLMGY', 'KAFFRINE', '{1,3,5}', '{07:00}', NULL),
           ('KAFFRINE', 'DKR-HLMGY', '{2,4,6}', '{07:00}', NULL),
           ('DKR-HLMGY', 'KIDIRA', '{1,3,6}', '{07:00}', NULL),
           ('KIDIRA', 'DKR-HLMGY', '{1,3,6}', '{07:00}', NULL),
           ('DKR-PA', 'BANJUL', '{1,2,3,4,5,6,7}', '{07:00}', NULL),
           ('BANJUL', 'DKR-PA', '{1,2,3,4,5,6,7}', '{07:30}', NULL),
           -- Express AIBD : 24 h/24, vers l'aéroport une heure avant la convocation, retour une heure après l'atterrissage.
           ('DKR-HLMGY', 'AIBD', '{1,2,3,4,5,6,7}', '{00:00}', '23:59'),
           ('AIBD', 'DKR-HLMGY', '{1,2,3,4,5,6,7}', '{00:00}', '23:59'))
INSERT
INTO transport_schedule (route_id, day_of_week, departs_at, last_departure)
SELECT r.id, day, time, s.last_departure
FROM s
         JOIN transport_route r ON r.code = s.origin || '_' || s.destination
         CROSS JOIN unnest(s.days) AS day
         CROSS JOIN unnest(s.times) AS time;
