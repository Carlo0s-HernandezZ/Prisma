# Actividad 3.2 – Codificación Segura (Prisma)

- `Act.3.2 – NombreEquipo.docx` / `.pdf`: documento de entrega (cambia `NombreEquipo` en el nombre del archivo y llena la portada).
- `evidencias/`: salidas reales de terminal, capturas, reportes de Semgrep (`semgrep-resultado.json`) y OWASP ZAP (`zap-dast-reporte.html`).
- `pruebas_seguridad_api.py`: 26 ataques (IDOR, CSRF, SQLi, XSS, JWT, subida de archivos) contra la API.

## Cómo levantar Prisma ahora que todo es HTTPS

1. **Secretos**: copia `server/.env.example` a `server/.env` y llena los valores (cada línea dice cómo generarlos).
   `server/.env` y la llave privada TLS están en `.gitignore`: cada integrante genera los suyos.
2. **Certificado TLS** (incluye la IP de tu computadora):
   ```bash
   ./server/certs/generate-cert.sh
   ```
   **Si cambias de red (otra IP), vuelve a ejecutarlo y recompila las apps**: el certificado y el pin que
   llevan Android/iOS dependen de él.
3. **Base de datos**: aplica las migraciones nuevas (son idempotentes):
   ```bash
   psql -h localhost -U postgres -d prisma_db -f server/src/main/resources/db/migrations/004_cifrado_datos_personales.sql
   ```
4. **Servidor**: `./gradlew :server:run` → HTTPS en `https://<ip>:8443` (el puerto 8080 solo redirige).
5. **Web**: abre una vez `https://localhost:8443` en el navegador y acepta el certificado autofirmado
   (o confía en `server/certs/prisma-cert.pem` en Acceso a Llaveros). Luego `./gradlew :app:webApp:wasmJsBrowserDevelopmentRun`.
6. **Android/iOS**: compila normalmente; Gradle incrusta el certificado y su pin (`generateServerConfig`).

## Repetir las pruebas

```bash
./gradlew :server:test                                   # 33 pruebas (12 de seguridad)
semgrep scan --no-git-ignore --config p/kotlin --config p/java --config p/secrets \
  --config p/owasp-top-ten --config p/jwt server/src app/shared/src app/androidApp/src core/src
```
El script de ataques necesita un servidor con `PRISMA_ENV=development` y sin SMTP (lee los códigos 2FA de su log):
`python3 docs/act3.2/pruebas_seguridad_api.py https://localhost:9443 <ruta-del-log>`. Crea usuarios `@prisma.test`; bórralos al terminar.
