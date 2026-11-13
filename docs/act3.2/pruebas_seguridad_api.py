#!/usr/bin/env python3
"""
Pruebas de seguridad contra la API real de Prisma (Actividad 3.2 – Codificación Segura).

Uso (servidor de pruebas en modo development, sin SMTP, para poder leer los códigos 2FA de su log):
    python3 docs/act3.2/pruebas_seguridad_api.py https://localhost:9443 <ruta-del-log-del-servidor>

Crea dos usuarios de prueba (alice / bob @prisma.test), ejecuta ataques de IDOR, CSRF,
inyección SQL, XSS, manipulación de JWT y subida de archivos, y al final borra los usuarios.
"""
import base64
import http.cookies
import json
import re
import ssl
import sys
import time
import urllib.error
import urllib.request
import uuid

BASE = sys.argv[1] if len(sys.argv) > 1 else "https://localhost:9443"
LOG = sys.argv[2] if len(sys.argv) > 2 else "server.log"
CERT = "server/certs/prisma-cert.pem"

# Se confía ÚNICAMENTE en el certificado de Prisma (como hacen las apps): nada de verify=False.
CTX = ssl.create_default_context(cafile=CERT)
CTX.minimum_version = ssl.TLSVersion.TLSv1_2

RESULTS = []


def call(method, path, body=None, token=None, headers=None, raw=None, content_type="application/json"):
    data = raw if raw is not None else (json.dumps(body).encode() if body is not None else None)
    req = urllib.request.Request(BASE + path, data=data, method=method)
    if data is not None:
        req.add_header("Content-Type", content_type)
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, context=CTX) as r:
            return r.status, r.read().decode(errors="replace"), r.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(errors="replace"), e.headers


def check(title, ok, detail):
    RESULTS.append(ok)
    print(f"[{'OK ' if ok else 'FALLA'}] {title}\n        {detail}")


def short(body, n=110):
    body = body.replace("\n", " ")
    return body if len(body) <= n else body[:n] + "…"


def last_code_for(email):
    for _ in range(30):
        text = open(LOG, encoding="utf-8", errors="replace").read()
        found = re.findall(r"PARA: " + re.escape(email) + r"\nASUNTO: [^\n]*\nMENSAJE:\nTu código para ingresar a Prisma es: (\d{6})", text)
        if found:
            return found[-1]
        time.sleep(0.3)
    raise SystemExit("No se encontró el código 2FA en el log")


def login(email, password):
    st, body, _ = call("POST", "/api/v1/auth/login", {"email": email, "password": password})
    assert st == 200, (st, body)
    temp = json.loads(body)["tempToken"]
    st, body, headers = call("POST", "/api/v1/auth/verify-2fa", {"tempToken": temp, "code": last_code_for(email)})
    assert st == 200, (st, body)
    return json.loads(body), headers


REQUIRED = ["TerminosUso", "AvisoPrivacidad", "DatosFinancieros", "EnvioIa"]
run = uuid.uuid4().hex[:6]
alice_email, bob_email, pwd = f"alice.{run}@prisma.test", f"bob.{run}@prisma.test", "Prueba#Segura2026"

print(f"Servidor: {BASE}   (TLS ≥ 1.2, certificado de Prisma)\n")
print("=== Preparación: dos usuarios de prueba ===")
for e in (alice_email, bob_email):
    st, body, _ = call("POST", "/api/v1/auth/register", {"email": e, "password": pwd, "consentimientos": REQUIRED})
    print(f"registro {e}: {st}")
alice, alice_headers = login(alice_email, pwd)
bob, _ = login(bob_email, pwd)
A, B = alice["accessToken"], bob["accessToken"]
print("sesiones iniciadas (usuario + contraseña + código 2FA)\n")

print("=== 1. JWT (Parte 4.3) ===")
h, p, s = A.split(".")
pad = lambda x: x + "=" * (-len(x) % 4)
print("encabezado:", base64.urlsafe_b64decode(pad(h)).decode())
claims = json.loads(base64.urlsafe_b64decode(pad(p)))
print("carga útil:", json.dumps({k: claims[k] for k in ("iss", "aud", "sub", "iat", "exp") if k in claims}))
forged_claims = dict(claims, sub=str(uuid.uuid4()))
forged = h + "." + base64.urlsafe_b64encode(json.dumps(forged_claims).encode()).decode().rstrip("=") + "." + s
st, body, _ = call("GET", "/api/v1/cards", token=forged)
check("JWT con 'sub' alterado (sin conocer la llave) se rechaza", st == 401, f"{st} {short(body)}")
none_alg = base64.urlsafe_b64encode(b'{"alg":"none","typ":"JWT"}').decode().rstrip("=") + "." + p + "."
st, body, _ = call("GET", "/api/v1/cards", token=none_alg)
check("JWT con alg=none se rechaza", st == 401, f"{st} {short(body)}")

print("\n=== 2. Cookies de sesión (Parte 1.2) ===")
for line in alice_headers.get_all("Set-Cookie"):
    name = line.split("=", 1)[0]
    print("Set-Cookie:", line.replace(line.split(";")[0].split("=", 1)[1], "<valor>"))
session_cookie = [c for c in alice_headers.get_all("Set-Cookie") if c.startswith("__Host-prisma_session=")][0]
check("Cookie de sesión con HttpOnly, Secure y SameSite=Strict",
      all(f in session_cookie for f in ("HttpOnly", "Secure", "SameSite=Strict")), "flags presentes")

print("\n=== 3. CSRF (Parte 2.4) ===")
cookie = {"Cookie": "__Host-prisma_session=" + A}
st, body, _ = call("POST", "/api/v1/incomes", {"nombre": "Ataque CSRF", "montoNeto": "1"}, headers=cookie)
check("POST con la cookie pero SIN X-CSRF-Token (sitio atacante) → 403", st == 403, f"{st} {short(body)}")
st, body, _ = call("POST", "/api/v1/incomes", {"nombre": "Ataque CSRF", "montoNeto": "1"}, headers={**cookie, "X-CSRF-Token": "adivinado123"})
check("POST con X-CSRF-Token adivinado → 403", st == 403, f"{st} {short(body)}")
st, body, _ = call("POST", "/api/v1/incomes", {"nombre": "Sueldo", "montoNeto": "15000"}, headers={**cookie, "X-CSRF-Token": alice["csrfToken"]})
check("POST legítimo con la cookie + token CSRF de la sesión → 201", st == 201, f"{st} {short(body)}")

print("\n=== 4. IDOR – control de acceso por recurso (Parte 2.3) ===")
st, body, _ = call("POST", "/api/v1/cards", {"banco": "BBVA", "alias": "Tarjeta de Alice", "ultimosCuatro": "4321"}, token=A)
card = json.loads(body)["id"]
print(f"Alice crea la tarjeta {card}")
for method, path, body in [
    ("GET", f"/api/v1/cards/{card}/documents", None),
    ("PUT", f"/api/v1/cards/{card}", {"banco": "Hackeado", "alias": "Robada"}),
    ("DELETE", f"/api/v1/cards/{card}", None),
]:
    st, resp, _ = call(method, path, body, token=B)
    check(f"Bob ({method} {path.replace(card, '{id-de-Alice}')}) → 404", st == 404, f"{st} {short(resp)}")
st, body, _ = call("GET", "/api/v1/cards", token=A)
check("La tarjeta de Alice sigue intacta", "Tarjeta de Alice" in body and "Hackeado" not in body, short(body))
st, body, _ = call("GET", "/api/v1/cards", token=B)
check("Bob solo ve sus propias tarjetas (lista vacía)", st == 200 and body.strip() == "[]", f"{st} {body}")

print("\n=== 5. Inyección SQL (Parte 2.2) ===")
for payload in ["' OR '1'='1' --", "admin@prisma.test' --", "x@x.com'; DROP TABLE usuarios; --"]:
    st, body, _ = call("POST", "/api/v1/auth/login", {"email": payload, "password": "' OR '1'='1"})
    check(f"login con email={payload!r}", st in (400, 401), f"{st} {short(body)}")
st, body, _ = call("GET", "/api/v1/cards/1%20OR%201=1/documents", token=A)
check("id de ruta '1 OR 1=1' → 400 (no es UUID)", st == 400, f"{st} {short(body)}")
st, body, _ = call("POST", "/api/v1/incomes", {"nombre": "x'); DELETE FROM ingresos; --", "montoNeto": "10"}, token=A)
check("nombre de ingreso con SQL → 400 (lista blanca)", st == 400, f"{st} {short(body)}")
st, body, _ = call("POST", "/api/v1/incomes", {"nombre": "Bono", "montoNeto": "1 OR 1=1"}, token=A)
check("monto '1 OR 1=1' → 400 (solo números con 2 decimales)", st == 400, f"{st} {short(body)}")
st, body, _ = call("GET", "/api/v1/incomes", token=A)
check("Los ingresos de Alice siguen ahí (no se ejecutó DELETE)", st == 200 and "Sueldo" in body, short(body))

print("\n=== 6. XSS – validación de entradas y salida JSON (Parte 2.1) ===")
for field, payload in [("nombre", "<script>alert(document.cookie)</script>"), ("apellido", "\"><img src=x onerror=alert(1)>")]:
    data = {"nombre": "Alicia", "apellido": "Prueba"}
    data[field] = payload
    st, body, _ = call("POST", "/api/v1/user/profile", data, token=A)
    check(f"perfil {field}={payload!r} → 400", st == 400, f"{st} {short(body)}")
st, body, _ = call("POST", "/api/v1/cards", {"banco": "<svg onload=alert(1)>", "alias": "x"}, token=A)
check("banco con <svg onload> → 400", st == 400, f"{st} {short(body)}")
st, body, headers = call("GET", "/api/v1/incomes", token=A)
check("Las respuestas son JSON con nosniff y CSP default-src 'none'",
      headers["Content-Type"].startswith("application/json") and headers["X-Content-Type-Options"] == "nosniff"
      and "default-src 'none'" in headers["Content-Security-Policy"], headers["Content-Type"])

print("\n=== 7. Subida de archivos con lista blanca (Parte 2.1) ===")
boundary = "----prisma" + run
def multipart(name, ctype, content):
    return (f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{name}\"\r\n"
            f"Content-Type: {ctype}\r\n\r\n").encode() + content + f"\r\n--{boundary}--\r\n".encode()
mp = f"multipart/form-data; boundary={boundary}"
st, body, _ = call("POST", f"/api/v1/cards/{card}/documents", raw=multipart("estado.pdf", "application/pdf",
                   b"<html><script>alert(1)</script></html>"), token=A, content_type=mp)
check("HTML disfrazado de PDF (estado.pdf) → 415", st == 415, f"{st} {short(body)}")
st, body, _ = call("POST", f"/api/v1/cards/{card}/documents", raw=multipart("../../etc/<b>estado.pdf", "text/html",
                   b"%PDF-1.4\n%%EOF"), token=A, content_type=mp)
name = json.loads(body).get("nombreArchivo") if st == 201 else None
check("PDF real con nombre malicioso → se guarda con nombre limpio", st == 201 and name and "/" not in name and "<" not in name,
      f"{st} nombreArchivo={name!r}")

print("\n=== 8. Datos personales cifrados (AES-256-GCM) ===")
st, body, _ = call("POST", "/api/v1/user/profile", {"nombre": "Alicia", "apellido": "Prueba", "fechaNacimiento": "2000-01-15"}, token=A)
st, body, _ = call("GET", "/api/v1/user/profile", token=A)
check("La API devuelve el perfil descifrado al dueño", "Alicia" in body, short(body))
print(f"(usuario de prueba: {alice_email} — ver consulta SQL en 06-bd-cifrado.txt)")

print("\n=== 9. Cierre de sesión revoca el JWT ===")
st, _, _ = call("POST", "/api/v1/auth/logout", token=B)
st2, body, _ = call("GET", "/api/v1/cards", token=B)
check("Tras logout, el mismo JWT (firma aún válida) → 401", st == 204 and st2 == 401, f"logout {st}; reuso {st2} {short(body)}")

print(f"\nResultado: {sum(RESULTS)}/{len(RESULTS)} controles verificados")
print(f"USUARIOS_DE_PRUEBA={alice_email},{bob_email}")
