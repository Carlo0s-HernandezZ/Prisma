CREATE TABLE IF NOT EXISTS documentos_tarjeta (
    id UUID PRIMARY KEY,
    tarjeta_id UUID NOT NULL REFERENCES tarjetas_credito(id) ON DELETE CASCADE,
    nombre_archivo VARCHAR(255) NOT NULL,
    tipo_mime VARCHAR(100) NOT NULL,
    url_o_datos TEXT NOT NULL,
    monto_pesos NUMERIC(12,2),
    periodo VARCHAR(50),
    fecha_corte VARCHAR(50),
    fecha_pago VARCHAR(50),
    fecha_registro TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
