-- Migración 006: auditorías de nómina y recordatorios para subir nómina o estados de cuenta.
-- DatabaseFactory crea estas tablas automáticamente (SchemaUtils.create) si no existen.

BEGIN;

CREATE TABLE IF NOT EXISTS auditorias_nomina (
    id                UUID PRIMARY KEY,
    usuario_id        UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    periodo           VARCHAR(7) NOT NULL,
    fecha_referencia  VARCHAR(10) NOT NULL,
    fuente            VARCHAR(5) NOT NULL,
    huella            VARCHAR(64) NOT NULL,
    resultado         TEXT NOT NULL,            -- JSON cifrado con AES-256-GCM
    neto              NUMERIC(12, 2) NOT NULL,
    alertas           INTEGER NOT NULL,
    fecha_analisis    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT auditorias_nomina_usuario_huella_unique UNIQUE (usuario_id, huella)
);

CREATE TABLE IF NOT EXISTS recordatorios (
    id              UUID PRIMARY KEY,
    usuario_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tipo            VARCHAR(20) NOT NULL,       -- NOMINA o ESTADO_CUENTA
    tarjeta_id      UUID REFERENCES tarjetas_credito(id) ON DELETE CASCADE,
    titulo          VARCHAR(80) NOT NULL,
    frecuencia      VARCHAR(12) NOT NULL,       -- MENSUAL, QUINCENAL, SEMANAL, CATORCENAL
    dia             INTEGER,
    dia2            INTEGER,
    fecha_inicio    VARCHAR(10),
    hora            VARCHAR(5) NOT NULL,
    activo          BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMIT;
