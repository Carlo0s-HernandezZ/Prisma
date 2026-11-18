/*
 * «Volver a Prisma»: regresa a la app desde la que se abrió esta página.
 *  - Web (sin ?origen): la app web está en este mismo servidor ("./").
 *  - ?origen=android: reabre la app instalada; si no está, abre la versión web.
 *  - ?origen=ios: reabre la app instalada (prisma://abrir).
 * El enlace no lleva datos: solo abre la app.
 */
(function () {
  var origen = new URLSearchParams(location.search).get("origen");
  var web = new URL("./", location.href).href;
  var destino = web, texto = "← Volver a Prisma";
  if (origen === "android") {
    destino = "intent://abrir#Intent;scheme=prisma;package=com.example.prisma;S.browser_fallback_url=" + encodeURIComponent(web) + ";end";
    texto = "← Volver a la app Prisma";
  } else if (origen === "ios") {
    destino = "prisma://abrir";
    texto = "← Volver a la app Prisma";
  }
  document.querySelectorAll("[data-volver]").forEach(function (a) {
    a.href = destino;
    a.textContent = texto;
    if (!origen) {
      // Web: si la página se abrió en otra pestaña desde la app, vuelve a esa pestaña.
      a.addEventListener("click", function (e) {
        try {
          if (window.opener && !window.opener.closed && window.opener.location.origin === location.origin) {
            e.preventDefault(); window.opener.focus(); window.close();
          }
        } catch (_) { /* otra pestaña u origen: se sigue el enlace normal */ }
      });
    }
  });
})();
