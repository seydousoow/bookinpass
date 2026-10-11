-- Copie de backend/src/main/resources/db/migration/V3__seed_exchange_rates.sql, au format goose.
-- +goose Up
-- +goose StatementBegin
-- Généré depuis frontend/public/rates.json. Taux statiques en attendant une source de marché.
INSERT INTO exchange_rate (base, currency, rate, updated_at)
VALUES ('XOF', 'XOF', 1, '2026-09-03T00:00:00.000Z'),
       ('XOF', 'EUR', 0.00152449, '2026-09-03T00:00:00.000Z'),
       ('XOF', 'USD', 0.00165, '2026-09-03T00:00:00.000Z'),
       ('XOF', 'GBP', 0.00127, '2026-09-03T00:00:00.000Z'),
       ('XOF', 'MAD', 0.01535, '2026-09-03T00:00:00.000Z');
-- +goose StatementEnd

-- +goose Down
DELETE FROM exchange_rate;
