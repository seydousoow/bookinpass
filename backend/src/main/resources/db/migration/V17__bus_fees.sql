-- Frais de service Book In Pass sur les billets de car, par passager (même règle que les vols : 3 % si départ et
-- arrivée sont dans le même pays, 5 % sinon). `total` les comprend : (prix + frais) × passagers.
ALTER TABLE transport_booking
    ADD COLUMN unit_fee NUMERIC(12, 2) NOT NULL DEFAULT 0 CHECK (unit_fee >= 0);
