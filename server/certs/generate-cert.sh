#!/usr/bin/env bash
# Genera el certificado TLS autofirmado del servidor Prisma (pruebas internas / desarrollo).
#
#   ./server/certs/generate-cert.sh            # usa la IP actual de la computadora
#   ./server/certs/generate-cert.sh 192.168.1.50
#   ./server/certs/generate-cert.sh --nueva-llave   # rota la llave: cambia el pin, hay que reinstalar las apps
#
# Crea:
#   server/certs/prisma-cert.pem      certificado público (lo usan las apps para el Certificate Pinning)
#   server/certs/prisma-keystore.p12  llave privada + certificado para Ktor (NO se sube a git)
#
# El certificado incluye como nombres válidos (SAN): localhost, el nombre Bonjour de esta computadora
# (<equipo>.local), 127.0.0.1, 10.0.2.2 (emulador Android) y la IP actual. Las apps usan <equipo>.local,
# así que al cambiar de red NO hay que regenerarlo: solo hace falta estar en la misma red que el servidor.
set -euo pipefail

NEW_KEY=0
if [[ "${1:-}" == "--nueva-llave" ]]; then NEW_KEY=1; shift; fi

DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="$DIR/../.env"
LAN_IP="${1:-$(ipconfig getifaddr en0 2>/dev/null || ipconfig getifaddr en1 2>/dev/null || hostname -I 2>/dev/null | awk '{print $1}' || echo 127.0.0.1)}"
# Nombre Bonjour/mDNS de esta computadora (no cambia al cambiar de red): las apps se conectan a él.
MDNS_NAME="$(scutil --get LocalHostName 2>/dev/null || hostname -s 2>/dev/null || echo prisma).local"

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
subjectAltName = DNS:localhost, DNS:${MDNS_NAME}, IP:127.0.0.1, IP:10.0.2.2, IP:${LAN_IP}
CNF

# Llave EC P-256 (equivalente a RSA-3072) y certificado válido por 825 días (máximo aceptado por Apple).
# Se REUTILIZA la llave del keystore existente: el pin (SHA-256 de la llave pública) no cambia y las apps
# ya instaladas siguen conectándose. Solo con --nueva-llave se genera otra (hay que reinstalar todas las apps).
if [[ "${NEW_KEY:-0}" != 1 && -f "$DIR/prisma-keystore.p12" ]] &&
   openssl pkcs12 -in "$DIR/prisma-keystore.p12" -nocerts -nodes -passin "pass:$PASSWORD" 2>/dev/null |
     openssl pkey -out "$TMP/key.pem" 2>/dev/null; then
  echo "Reutilizando la llave existente (el pin de las apps no cambia)."
else
  echo "Generando llave NUEVA: recompila y reinstala Android, iOS y vuelve a aceptar el certificado en el navegador."
  openssl ecparam -name prime256v1 -genkey -noout -out "$TMP/key.pem"
fi
openssl req -new -x509 -sha256 -days 825 -key "$TMP/key.pem" -config "$TMP/openssl.cnf" -out "$DIR/prisma-cert.pem"
openssl pkcs12 -export -name prisma -inkey "$TMP/key.pem" -in "$DIR/prisma-cert.pem" \
  -out "$DIR/prisma-keystore.p12" -passout "pass:$PASSWORD"
chmod 600 "$DIR/prisma-keystore.p12"

PIN="$(openssl x509 -in "$DIR/prisma-cert.pem" -pubkey -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 -binary | openssl base64)"
echo "Certificado generado para: localhost, ${MDNS_NAME}, 127.0.0.1, 10.0.2.2, ${LAN_IP}"
echo "Pin SHA-256 de la llave pública (Certificate Pinning): sha256/${PIN}"
