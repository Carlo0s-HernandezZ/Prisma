-- Migración 002: dispositivos con acceso biométrico (huella en Android / Face ID o Touch ID en iOS).
--
-- Aplicar:
--   psql -h localhost -U postgres -d prisma_db -f server/src/main/resources/db/migrations/002_dispositivos_biometricos.sql
--
-- Cómo funciona:
--   * Tras un login completo (contraseña + código de Google Authenticator) el usuario puede registrar
--     su dispositivo. El servidor genera un token aleatorio que el teléfono guarda protegido por
--     biometría (Keystore / Keychain). Aquí solo se guarda su hash SHA-256, nunca el token.
--   * ultimo_2fa es la hora del último código 2FA válido. El acceso biométrico solo se acepta
--     durante las 24 horas siguientes; después hay que volver a hacer login con 2FA, lo que la renueva.
--
-- Idempotente: se puede ejecutar más de una vez.

BEGIN;

CREATE TABLE IF NOT EXISTS dispositivos_biometricos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id      UUID NOT NULL REFERENCES usuarios(id) ON DELETE CASCADE,
    token_hash      VARCHAR(64) NOT NULL UNIQUE,
    plataforma      VARCHAR(20) NOT NULL CHECK (plataforma IN ('android', 'ios')),
    ultimo_2fa      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_uso      TIMESTAMP,
    fecha_creacion  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dispositivos_biometricos_usuario ON dispositivos_biometricos (usuario_id);

COMMIT;
