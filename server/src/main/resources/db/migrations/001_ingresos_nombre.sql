-- Migración 001: nombre (concepto) para cada ingreso, p. ej. "Salario", "Freelance", "Renta".
--
-- Aplicar en cada base de datos de desarrollo/producción:
--   psql -h localhost -U postgres -d prisma_db -f server/src/main/resources/db/migrations/001_ingresos_nombre.sql
--
-- Es idempotente: se puede ejecutar más de una vez sin error.
-- Los ingresos que ya existieran reciben el nombre 'Ingreso' antes de volver la columna obligatoria.

BEGIN;

ALTER TABLE ingresos ADD COLUMN IF NOT EXISTS nombre VARCHAR(100);

UPDATE ingresos SET nombre = 'Ingreso' WHERE nombre IS NULL OR btrim(nombre) = '';

ALTER TABLE ingresos ALTER COLUMN nombre SET NOT NULL;

ALTER TABLE ingresos DROP CONSTRAINT IF EXISTS ingresos_nombre_check;
ALTER TABLE ingresos ADD CONSTRAINT ingresos_nombre_check CHECK (btrim(nombre) <> '');

COMMIT;
