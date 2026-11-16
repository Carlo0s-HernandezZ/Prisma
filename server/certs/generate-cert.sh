#!/usr/bin/env bash
# Genera el certificado TLS autofirmado del servidor Prisma (pruebas internas / desarrollo).
#
#   ./server/certs/generate-cert.sh            # usa la IP actual de la computadora
#   ./server/certs/generate-cert.sh 192.168.1.50
#
# Crea:
#   server/certs/prisma-cert.pem      certificado público (lo usan las apps para el Certificate Pinning)
#   server/certs/prisma-keystore.p12  llave privada + certificado para Ktor (NO se sube a git)
#
# El certificado incluye como nombres válidos (SAN): localhost, 127.0.0.1, 10.0.2.2 (emulador Android)
# y la IP de red de esta computadora. Si cambias de red, vuelve a ejecutarlo y recompila las apps.
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$DIR/../.env"
LAN_IP="${1:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || echo 127.0.0.1)}"

PASSWORD="$(grep -E '^KEYSTORE_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2- || true)"
if [[ -z "$PASSWORD" ]]; then
  echo "Falta KEYSTORE_PASSWORD en server/.env (copia server/.env.example a server/.env)." >&2
  exit 1
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT

cat > "$TMP/openssl.cnf" <<CNF
[req]
distinguished_name = dn
x509_extensions = v3
prompt = no
[dn]
C = MX
ST = Jalisco
O = Prisma
CN = prisma-api
[v3]
basicConstraints = critical, CA:FALSE
keyUsage = critical, digitalSignature, keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = DNS:localhost, IP:127.0.0.1, IP:10.0.2.2, IP:${LAN_IP}
CNF

# Llave EC P-256 (equivalente a RSA-3072) y certificado válido por 825 días (máximo aceptado por Apple).
openssl ecparam -name prime256v1 -genkey -noout -out "$TMP/key.pem"
openssl req -new -x509 -sha256 -days 825 -key "$TMP/key.pem" -config "$TMP/openssl.cnf" -out "$DIR/prisma-cert.pem"
openssl pkcs12 -export -name prisma -inkey "$TMP/key.pem" -in "$DIR/prisma-cert.pem" \
  -out "$DIR/prisma-keystore.p12" -passout "pass:$PASSWORD"
chmod 600 "$DIR/prisma-keystore.p12"

PIN="$(openssl x509 -in "$DIR/prisma-cert.pem" -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64)"
echo "Certificado generado para: localhost, 127.0.0.1, 10.0.2.2, ${LAN_IP}"
echo "Pin SHA-256 de la llave pública (Certificate Pinning): sha256/${PIN}"
