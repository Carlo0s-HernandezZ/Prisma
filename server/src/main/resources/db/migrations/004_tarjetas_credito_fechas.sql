-- Migración 004: agregar dia_corte, hora_notificacion_corte, dia_pago, hora_notificacion_pago a la tabla tarjetas_credito.

BEGIN;

ALTER TABLE tarjetas_credito ADD COLUMN IF NOT EXISTS dia_corte INTEGER;
ALTER TABLE tarjetas_credito ADD COLUMN IF NOT EXISTS hora_notificacion_corte VARCHAR(5);
ALTER TABLE tarjetas_credito ADD COLUMN IF NOT EXISTS dia_pago INTEGER;
ALTER TABLE tarjetas_credito ADD COLUMN IF NOT EXISTS hora_notificacion_pago VARCHAR(5);

COMMIT;
