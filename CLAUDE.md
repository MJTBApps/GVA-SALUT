# Mi Bolsa — contexto del proyecto

App (Android e iPhone, **Capacitor 8**, mismo sistema que La Meua Lliga) que enseña a cada persona su situación en las **Listas de Empleo Temporal de la Conselleria de Sanidad** (Comunitat Valenciana), incluido el **SES** (Locutor C.I.C.U., Técnico Emergencias Sanitarias, Enfermera/o y Médico SAMU; departamentos SES Alicante, Castellón y Valencia). App **no oficial**. Nombre provisional: «Mi Bolsa» (`appId` `es.mjtbapps.mibolsa`).

## De dónde salen los datos (importante)

- La única fuente pública es el buscador de la GVA: `https://svsiscv.san.gva.es/btt-consulta/pages/index.xhtml?idioma=es&version=0` → «Consulta de la situación de los candidatos…» (`request=getListadoSituacionCandidatoIntervalo`; el `param` cambia con cada publicación, por eso se lee el enlace desde la portada). No hay PDF, Excel ni datos abiertos de estas listas (comprobado el 18/11/2026).
- Formulario PrimeFaces 6: tres `selectOneMenu` (categoría, turno `O`/`P`, departamento) que se eligen con `widget.selectValue(código)`, y tres campos de texto (primer apellido, segundo apellido, nombre).
- Está protegido con **Cloudflare Turnstile** y cada consulta necesita su propio token.

**Reglas que no se cambian:**
1. La consulta la hace el **navegador real del móvil de cada usuario** (complemento `Navegador`), oculto detrás de la app, solo con **sus propios datos**. No se cambia el User-Agent ni se imita nada.
2. **Nunca** se intenta saltar, resolver o esquivar Turnstile, ni se consulta desde servidores, GitHub Actions o scripts. Si la verificación no se resuelve sola, se enseña al usuario (`Navegador.mostrar`) y la resuelve él.
3. De cada lista solo se guarda en el móvil **la fila del usuario y recuentos** (activos, desactivados, no disponibles delante). Nunca nombres de otras personas. No hay buscador de terceros.
4. La app dice siempre que es no oficial y que los datos vienen de la consulta pública de la Conselleria.

## Dónde está el código

- `www/index.html` → toda la app (HTML/JS/CSS). **Copia idéntica** en `android/app/src/main/assets/public/index.html` (el workflow de APK lo comprueba con `cmp`).
  - `paginaGVA()` se inyecta en la página de la GVA y define `window.__mb` (lista, listo, elegir, escribir, verificado, enviar, leer).
  - `actualizar()` recorre los departamentos del perfil; `resumir()` calcula los recuentos; `demo()` da datos de ejemplo en la vista previa del ordenador.
- `android/app/src/main/java/es/mjtbapps/mibolsa/NavegadorPlugin.java` → complemento Android (WebView detrás de la app). Métodos: `abrir`, `ejecutar` (devuelve el resultado como texto JSON), `mostrar`, `ocultar`, `cerrar`; evento `cerrado`. Solo abre URLs de `https://svsiscv.san.gva.es/`.
- `ios-native/BolsaNative/…/NavegadorPlugin.swift` → el mismo complemento en iPhone (WKWebView). `ios-native/prepare.py` lo añade al proyecto que genera Capacitor en GitHub.

## Cómo se compila

- **No se compila en local**: el iMac solo tiene Java 17 y Capacitor 8 pide Java 21; no hay Node ni Xcode.
- APK: `.github/workflows/apk.yml` (artefacto `mi-bolsa-apk`). Firma fija opcional con `MIBOLSA_KEYSTORE_B64` y `MIBOLSA_KEYSTORE_PASSWORD`.
- iPhone: `.github/workflows/ios.yml` (a mano) → TestFlight, con los secretos `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (los de OpoAlert). Antes hay que crear la app con el bundle `es.mjtbapps.mibolsa` en App Store Connect.
- Claude no hace push: commits en local y el usuario sube con GitHub Desktop.

## Pendiente

- Probar en un móvil real la consulta oculta (no se ha podido validar la tabla de resultados ni el comportamiento de Turnstile dentro del WebView).
- Icono propio (ahora usa el de La Meua Lliga como provisional).
- Si la persona no está en el primer tramo (1-200), los recuentos «delante» solo cubren su tramo.
