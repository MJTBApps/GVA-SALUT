# Mi Bolsa — contexto del proyecto

App (Android e iPhone, **Capacitor 8**, mismo sistema que La Meua Lliga) que enseña a cada persona su situación en las **Listas de Empleo Temporal de la Conselleria de Sanidad** (Comunitat Valenciana), incluido el **SES** (Locutor C.I.C.U., Técnico Emergencias Sanitarias, Enfermera/o y Médico SAMU; departamentos SES Alicante, Castellón y Valencia). App **no oficial**. Nombre: **«Mi Bolsa Salut»** (19/11/2026; debajo del icono «Mi Bolsa», que es lo que cabe en iPhone). `appId` `es.mjtbapps.mibolsa`. No usar «GVA» ni parecerse a la otra app, «BolsaSanidad».

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
  - `actualizar()` recorre los departamentos del perfil; `resumir()` calcula los recuentos (por nombre, por número de puesto o, sin identificar, resumen de la lista); `demo()` da datos de ejemplo en la vista previa del ordenador.
  - Cada búsqueda guarda un registro de pasos con segundos (`paso()`, «Detalles de la última búsqueda» en la app). La opción «Ver la web de la GVA mientras busca» deja el navegador visible para pruebas.
- `android/app/src/main/java/es/mjtbapps/mibolsa/NavegadorPlugin.java` → complemento Android (WebView detrás de la app). Métodos: `abrir`, `ejecutar` (devuelve el resultado como texto JSON), `mostrar`, `ocultar`, `cerrar`; evento `cerrado`. Solo abre URLs de `https://svsiscv.san.gva.es/`.
- `ios-native/BolsaNative/…/NavegadorPlugin.swift` → el mismo complemento en iPhone (WKWebView). `ios-native/prepare.py` lo añade al proyecto que genera Capacitor en GitHub.

## Cómo se compila

- **No se compila en local**: el iMac solo tiene Java 17 y Capacitor 8 pide Java 21; no hay Node ni Xcode.
- APK: `.github/workflows/apk.yml` (artefacto `mi-bolsa-apk`). Firma fija opcional con `MIBOLSA_KEYSTORE_B64` y `MIBOLSA_KEYSTORE_PASSWORD`.
- iPhone: `.github/workflows/ios.yml` (a mano) → TestFlight, con los secretos `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8` (los de OpoAlert). Antes hay que crear la app con el bundle `es.mjtbapps.mibolsa` en App Store Connect.
- Alternativa si los Mac de GitHub están saturados: **Codemagic** (`codemagic.yaml`, app GVA-SALUT en codemagic.io, workflow «Mi Bolsa → TestFlight», grupo de variables `opoalert_ios` con las 3 claves ASC). Número de compilación = 100 + número de Codemagic.
- **Google Play**: `.github/workflows/aab.yml` (a mano) → artefacto `mi-bolsa-aab`, firmado con la clave de subida. Clave y contraseña en `~/MiBolsa-claves/` del iMac (fuera del repo; guardar copia): `mibolsa-play-subida.jks`, `.b64` y `play.properties`. Secretos `MIBOLSA_PLAY_KEYSTORE_B64` y `MIBOLSA_PLAY_PASSWORD`. versionCode = 1000 + número de ejecución.
- Claude no hace push: commits en local y el usuario sube con GitHub Desktop.

## Estado (19/11/2026)

- Funciona en iPhone (TestFlight, grupo interno «Pruebas»): encuentra a la persona por nombre, lee su puesto y cuenta quién tiene delante (consulta con nombre → puesto; luego lista completa en su tramo; el puesto se recuerda).
- Las celdas de la GVA en móvil llevan el título de columna dentro («Puesto: 123»): `leer()` lo quita.
- Historial por publicación (`sumarHist`), gráfica de disponibles delante (`grafica`), estimación (`estimacion`) y aviso de novedades dentro de la app (`cambios`/`avisos`). No hay notificaciones con la app cerrada.
- iOS: `runs-on: macos-26`. Los Mac de GitHub se saturan a veces. Los minutos de Actions de repos privados se acaban (presupuesto 0 $): se renuevan el día 1 de cada mes.

## Versión 2 (19/11/2026)

- Perfil con varias bolsas: `perfil = {ap1, ap2, nombre, bolsas:[{id, cat, turno, deps[], puesto}]}`; `datos` por clave `cat|turno|dep` (`clave()`); `migrar()` convierte el perfil antiguo.
- Recuento de todos los de delante: `consultarDep` salta al tramo del puesto (`irATramo`) y cuenta los tramos anteriores (`__mb.rangos()`); `alcance` = 'todo' o 'tramo'.
- Al abrir: si hay datos de más de 6 h se actualiza; si no, `comprobarPublicacion()` (como mucho cada hora) y actualiza si hay publicación nueva.
- Idiomas: `T.es` / `T.va` y `t(clave, …)`. Modo oscuro con `prefers-color-scheme`.
- Buscador de categoría/departamento: hoja inferior `hoja()`.
- Complemento Navegador: métodos nuevos `widget` (Android: `BolsaWidget`; iPhone: sin widget todavía) y `compartir` (imagen PNG de «Mi situación», FileProvider en Android).

## Versión 3 (19/11/2026)

- Actividad de la bolsa: consulta de nombramientos (`consultarNombramientos`, último mes, por categoría, como mucho cada 7 días). `__mb.tablaSinNombres()` quita las columnas de nombres en la propia página: solo se guardan recuentos por departamento y tipo (`store 'actividad'`).
- Predicción por tipo (largos = vacantes/interinidades, cortos = resto) en cada tarjeta; comparación de departamentos (`comparar`).
- Penalizaciones (`consultarPenalizaciones`, por dirección territorial V/A/C/Y, una vez por publicación): `__mb.buscarYo()` devuelve solo la fila propia.
- Tablón oficial, próxima publicación (días 1 y 15), calculadora de baremo (`calcBaremo`: Orden 4/2019 y Decreto 76/2024, cifras del DOGV; sin difícil cobertura).
- Widget de iPhone: `ios-native/BolsaWidget` + `add_widget.rb` (gema xcodeproj) en `ios.yml` (entrada `widget`, por defecto sí). Grupo de apps `group.es.mjtbapps.mibolsa` y App ID `es.mjtbapps.mibolsa.BolsaWidget` creados en developer.apple.com. El archivado ya no fuerza `PRODUCT_BUNDLE_IDENTIFIER` (lo pone Capacitor) para no pisar el del widget.
- Pendiente de comprobar en móvil: columnas reales de la tabla de nombramientos y de penalizaciones (salen en «Detalles de la última búsqueda»).

## Versión 4 (19/11/2026)

- Avisos con la app cerrada, sin servidor: método `avisos` del complemento Navegador (iPhone: `UNUserNotificationCenter`; Android: `AvisoReceiver` con AlarmManager, reprograma al reiniciar). `programarAvisos()` manda días 1 y 15 a las 10:00 (6 meses) y cada plazo el día antes y el mismo día. Interruptor en Ajustes (`avisosOn`).
- Explicación de cada situación: tocar la etiqueta → `explicarSit()` (hoja `#info`).
- Plazos que apunta la persona (`store 'plazos'`) en la pestaña Bolsa (`plazosCard`).
- Anuncios **AdMob** activados (`@capacitor-community/admob` 8.3.0). Apps «Mi Bolsa Sanidad» en AdMob: iPhone `ca-app-pub-1535697166232113~5672043661`, Android `~4767202281`; bloques Banner, Intersticial y Apertura (ids en `ADS` de index.html). Banner encima de las pestañas, apertura siempre al abrir, intersticial como mucho cada `cadaMin` (2) minutos al acabar una búsqueda o al cambiar de pestaña; `store 'premium'` los quita. Consentimiento UE (UMP) y permiso de seguimiento en iPhone antes de cargar anuncios.
  - Android: el complemento va copiado en `node_modules/@capacitor-community/admob` (en git, como `@capacitor/android`), incluido a mano en `capacitor.settings.gradle`, `app/capacitor.build.gradle` y `MainActivity`; id de app en el manifiesto.
  - iPhone: lo añade `npx cap add ios` desde package.json; `prepare.py` pone `GADApplicationIdentifier`, SKAdNetwork y `NSUserTrackingUsageDescription`.
  - Falta: mensaje de consentimiento (RGPD) creado en AdMob → Privacidad y mensajes; añadir las tiendas cuando se publique.
- Búsqueda: 1 minuto por departamento (+2 si sale la casilla de Cloudflare), 4 minutos en total, botón Cancelar; si falla se guarda `fallo` y no se repite sola.

## Versión 5 (20/11/2026)

- Inicio: «Tu quincena» (`quincenaCard`, compara los dos últimos puntos de `hist`). Evolución: simulador (`simuladorCard`/`enlazarSimulador`).
- Bolsa, en este orden: penalizaciones + NO LOCALIZABLE (`lista.noloc`, store `noloc`, reutiliza `consultarPenalizaciones`), actividad con duración/tipos/jornada de los contratos (`dur`, `tipos`, `jor`), ranking de departamentos (`rankingCard`, media de `actividad.hist`), «Mis puntos» (store `puntosOf`, los apunta la persona: la consulta pública no da puntos), plazos, información oficial.
- «¿No está tu categoría? Pídelo»: correo a mjtbapps@gmail.com (`pedirUrl`) en la hoja de búsqueda y en Ajustes.
- Pendiente de comprobar en móvil: columnas de inicio/fin/jornada de nombramientos y el filtro de la consulta NO LOCALIZABLE (salen en «Detalles de la última búsqueda»).

## Vacantes (20/11/2026)

- Tabla de nombramientos de la GVA con letras: Turno O/P, TNom A/C/I/S (I = interinidad, vacante), B (breve), Jor A/N/P (`TNOM`, `JORN`). La columna TNom ya no se quita como si fuera un nombre.
- `consultarNombramientos` devuelve `vac {O, P, otro, lista:[{dep, turno, pos, ini}]}` y `porDep[d].vac`; `vacBloque()` en la tarjeta de actividad: vacantes por turno ordinario / promoción interna y posición más alta con vacante en tus departamentos.

## Menú (20/11/2026)

- Pestañas: Inicio · Bolsa · Evolución · Mi zona · Ajustes (`PESTANAS`). Mi zona: Mis bolsas, Baremo, Mis puntos, Plazos. Ajustes: idioma, apariencia (`store 'tema'` auto/light/dark → `data-theme`, `ponerTema()`), avisos, información oficial, registro.

## Google Play (20/11/2026)

- App creada en Play Console (cuenta MJTB Apps, id de app 4974091835764455131), «Mi Bolsa Salut», categoría Economía, 18+, PEGI 3. Declaraciones hechas (anuncios, ID de publicidad, seguridad de datos: datos de AdMob + nombre, que se envía solo en la consulta y es opcional).
- Prueba cerrada «Alpha»: España, lista «Testers OpoAlert», versión 1001 (1.0.1) enviada a revisión el 20/11/2026. Después: 12 testers durante 14 días y pedir producción.
- Gráficos de la ficha: icono 512, gráfico 1024×500 y capturas 1080×1920 hechas con Chrome sin ventana sobre `demo()` (ancho mínimo de Chrome sin ventana: 500 px CSS).

## Pendiente

- Contar también a la gente de tramos anteriores (puestos > 200): una consulta más por tramo.
- Varias bolsas (categorías/turnos), widget, valenciano.
- Icono y nombre definitivos; página de privacidad y fichas para publicar.
