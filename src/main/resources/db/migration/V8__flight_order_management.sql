-- Gestion d'une réservation par le voyageur : retrouvée par PNR + nom de famille, jusqu'à deux mois après le dernier vol.

-- Date du dernier vol : départ de l'aller (aller simple) ou du retour (aller-retour), date locale de l'aéroport.
-- Nulle pour une commande enregistrée avant cette migration : elle reste consultable.
ALTER TABLE flight_order
    ADD COLUMN last_travel_date DATE;

-- Recherche par PNR, sans tenir compte de la casse saisie.
CREATE INDEX flight_order_booking_reference_idx ON flight_order (upper(booking_reference));
