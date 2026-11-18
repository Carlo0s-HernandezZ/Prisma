-- Migración 005: análisis de estados de cuenta con IA, movimientos para dashboards y consejo del día.
-- DatabaseFactory crea estas tablas automáticamente (SchemaUtils.create) si no existen;
-- este archivo documenta el esquema y sirve para crearlo a mano.

BEGIN;

CREATE TABLE IF NOT EXISTS analisis_estados_cuenta (
    id              UUID PRIMARY KEY,
    usuario_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tarjeta_id      UUID NOT NULL REFERENCES tarjetas_credito(id) ON DELETE CASCADE,
    periodo         VARCHAR(7) NOT NULL,
    huella          VARCHAR(64) NOT NULL,
    fuente          VARCHAR(10) NOT NULL,
    resultado       TEXT NOT NULL,          -- JSON cifrado con AES-256-GCM
    deuda_total     NUMERIC(12, 2) NOT NULL,
    total_gastos    NUMERIC(12, 2) NOT NULL,
    total_hormiga   NUMERIC(12, 2) NOT NULL,
    fecha_analisis  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT analisis_estados_cuenta_tarjeta_periodo_unique UNIQUE (tarjeta_id, periodo)
);

CREATE TABLE IF NOT EXISTS movimientos_analizados (
    id           UUID PRIMARY KEY,
    analisis_id  UUID NOT NULL REFERENCES analisis_estados_cuenta(id) ON DELETE CASCADE,
    usuario_id   UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    tarjeta_id   UUID NOT NULL REFERENCES tarjetas_credito(id) ON DELETE CASCADE,
    periodo      VARCHAR(7) NOT NULL,
    fecha        DATE,
    descripcion  TEXT NOT NULL,             -- cifrada con AES-256-GCM
    categoria    VARCHAR(40) NOT NULL,
    tipo         VARCHAR(20) NOT NULL,
    monto        NUMERIC(12, 2) NOT NULL,
    es_hormiga   BOOLEAN NOT NULL DEFAULT FALSE,
    sospechoso   BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS movimientos_analizados_usuario_periodo
    ON movimientos_analizados (usuario_id, periodo);

CREATE TABLE IF NOT EXISTS consejos_diarios (
    usuario_id  UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    fecha       DATE NOT NULL,
    titulo      VARCHAR(120) NOT NULL,
    texto       TEXT NOT NULL,
    fuente_id   VARCHAR(30) NOT NULL,
    PRIMARY KEY (usuario_id, fecha)
);

COMMIT;
