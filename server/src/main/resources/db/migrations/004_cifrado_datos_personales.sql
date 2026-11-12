-- Migración 004: columnas de datos personales con espacio para el texto cifrado (AES-256-GCM).
--
-- El servidor guarda nombre y apellido como "enc:v1:" + Base64(IV + cifrado + etiqueta GCM),
-- que ocupa más que el texto original. Los registros antiguos sin cifrar se siguen leyendo y se
-- cifran en la siguiente actualización del perfil.
--
-- Aplicar:
--   psql -h localhost -U postgres -d prisma_db -f server/src/main/resources/db/migrations/004_cifrado_datos_personales.sql
--
-- Idempotente: se puede ejecutar más de una vez.

BEGIN;

ALTER TABLE datos_personales ALTER COLUMN nombre   TYPE VARCHAR(512);
ALTER TABLE datos_personales ALTER COLUMN apellido TYPE VARCHAR(512);

COMMIT;
