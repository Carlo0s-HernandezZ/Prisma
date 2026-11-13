// Servidor web de la app (webpack-dev-server).
// - host 0.0.0.0: permite abrir la versión web desde otros dispositivos de la misma red.
// - Cabeceras de seguridad (Actividad 3.2), equivalentes a Helmet:
//   CSP: solo scripts/estilos/fuentes propios; WebAssembly permitido ('wasm-unsafe-eval');
//        la API solo se contacta por HTTPS (connect-src https:); sin <object>, sin iframes externos.
//   X-Frame-Options DENY (clickjacking), nosniff (MIME sniffing), Referrer-Policy y Permissions-Policy.
// Los mapas de código se generan sin eval() (devtool = "source-map" en app/webApp/build.gradle.kts):
// así la CSP puede prohibir 'unsafe-eval' también en desarrollo.
if (config.devServer) {
    config.devServer.host = "0.0.0.0";
    config.devServer.allowedHosts = "all";
    config.devServer.headers = {
        "Content-Security-Policy": [
            "default-src 'self'",
            "script-src 'self' 'wasm-unsafe-eval'",
            "connect-src 'self' https: ws:", // https: API de Prisma · ws: recarga en vivo del servidor de desarrollo
            "img-src 'self' data: blob:",
            "style-src 'self' 'unsafe-inline'",
            "font-src 'self' data:",
            "object-src 'none'",
            "base-uri 'self'",
            "form-action 'self'",
            "frame-ancestors 'none'",
        ].join("; "),
        "X-Frame-Options": "DENY",
        "X-Content-Type-Options": "nosniff",
        "Referrer-Policy": "no-referrer",
        "Permissions-Policy": "camera=(), microphone=(), geolocation=(), payment=()",
    };
}
