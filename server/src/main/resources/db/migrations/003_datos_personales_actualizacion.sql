-- Migración 003: fecha_actualizacion para datos_personales.
--
-- Aplicar:
--   psql -h localhost -U postgres -d prisma_db -f server/src/main/resources/db/migrations/003_datos_personales_actualizacion.sql
--
-- Idempotente: se puede ejecutar más de una vez.

BEGIN;

ALTER TABLE datos_personales ADD COLUMN IF NOT EXISTS fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP;

COMMIT;
