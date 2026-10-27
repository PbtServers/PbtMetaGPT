# Diagnóstico seguro del bloqueo en classes4.dex

## Evidencias disponibles

La [ejecución 40558091837](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40558091837) del commit `46eae50` compila Android y supera JVM, Lint, Python, Node y backend/contenedores. El escáner posterior rechaza `app-debug.apk:classes4.dex`. Los emuladores API35/API36 y la publicación final quedan omitidos por ese rechazo; **no hay APK nueva publicada**.

El único artifact conservado es `informes-validacion`, no la APK ni los DEX. La usuaria confirma que tampoco dispone de una copia local. El mensaje antiguo no identifica qué patrón coincidió, su posición, clase o biblioteca. **No puede clasificarse ese hallazgo como secreto real ni falso positivo con el registro disponible. No se ha añadido una excepción.**

El escáner original tiene dos patrones: prefijo de clave OpenAI (`sk-`, también proyecto/cuenta de servicio) y bloque PEM privado completo. El segundo exige carga entre cabeceras para evitar confundir etiquetas de parsers con claves. Se mantienen ambos patrones sin cambios, sin fronteras de palabra nuevas ni exclusiones de archivos/dependencias. Tampoco se elimina Vosk, Meta DAT, WebRTC ni otra función.

## Cambio implementado

`comprobar_secretos.py` conserva el rechazo y añade un informe JSON opcional. Cada hallazgo incluye solo:

- Nombre del patrón, desplazamiento, longitud y SHA-256 del candidato.
- Archivo o entrada del APK, con nombres sospechosos ocultos por huella.
- Índice de string DEX y clases de valores estáticos cuando las tablas puedan interpretarse.
- Clases que referencian el string mediante `const-string`/`const-string/jumbo`, si está disponible `dexdump` oficial.
- Coincidencias exactas de huella en clases de JAR/AAR de la caché Gradle que el DEX declara. Se informa módulo/versión/archivo/clase, sin copiar constantes.

No contiene candidato, contexto de bytes, strings del DEX, claves ni volcado del desensamblador. El DEX temporal para `dexdump` se guarda únicamente durante el proceso en un directorio temporal y se elimina al terminar. La salida del desensamblador se procesa por tubería; no se imprime ni se incorpora al artifact.

La atribución es auxiliar: si falla, el formato no se admite o no se encuentra una referencia, el hallazgo original continúa rechazando la APK. Una coincidencia en una biblioteca **no demuestra inocuidad**. La caché puede contener varias versiones; no se presenta una de ellas como dependencia resuelta sin contrastar el grafo de Gradle y las referencias. Los valores inicializados en código o anotaciones pueden requerir inspección adicional cuando no aparecen en las referencias recogidas.

## Próxima ejecución y decisión

CI ejecuta el mismo escáner con `--informe app/build/reports/seguridad/hallazgos.json`. Ese JSON se incorpora a `informes-validacion`, que ya se publica con `if: always()`. Los metadatos se muestran también en el log. La APK y los DEX con posibles secretos no se publican como artifacts para facilitar el diagnóstico. La barrera de seguridad sigue siendo requisito de los emuladores y de la APK final.

La siguiente ejecución permitirá determinar cuál de los dos patrones se activó, correlacionar la huella con una constante concreta y revisar la fuente/bytecode y uso real de la clase responsable. No basta con que un candidato empiece dentro de un nombre de tarea/modelo o aparezca en una dependencia.

Si es una credencial real: retirar su origen, volver a compilar/escanear y comunicar la necesidad de revocarla al titular sin copiar su valor. Si es una constante pública no secreta: documentar su procedencia verificable, uso y huella, y añadir una regresión que conserve la detección de claves reales antes de considerar una excepción estrictamente acotada. **Esta intervención no cambia las reglas para aceptar ninguna coincidencia.**

## Validación y límites

20 pruebas Python correctas, incluidas las tres originales del escáner y once regresiones de metadatos, redacción, DEX corrupto, referencias, JAR/AAR, rechazo de `classes4.dex` y fixture no distribuible. Las tablas DEX sintéticas verifican el parser, no son una APK ejecutable ni evidencia del origen del caso real. El contrato de salida `dexdump` se contrastó con la muestra oficial AOSP.

README sin modificar y seis archivos locales conservados byte a byte. Progreso global de estabilización: **35 %**, sin incremento por este diagnóstico. Android15/16, identificación del hallazgo y APK siguen pendientes. Este cambio permite investigar el rechazo; **no se declara corregido el bloqueo del artefacto original**.

El intento local con Java17 de JVM/Lint/APK/instrumentadas termina en `SDK location not found`; la preparación de Sherpa no puede descargar por restricciones de red. No se ha reconstruido aquí el DEX que falló en GitHub.

## Seguimiento de c043114: atribución de metadatos pendiente

La ejecución [40561936117](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40561936117) detecta cinco coincidencias del patrón OpenAI, de 30 bytes y con la misma huella, en los índices 58649, 58651, 58653, 58655 y 58657. No encuentra referencias estáticas, `const-string` ni coincidencias en las clases examinadas de dependencias. Esa ausencia **no demuestra que sean falsos positivos**. No se dispone del DEX original ni se ha identificado su clase responsable.

La atribución ahora consulta también `type_ids`, `field_ids`, `method_ids`, prototipos, archivos fuente, anotaciones de clases/campos/métodos/parámetros y nombres/firmas del programa `debug_info_item`. Añade longitud y huella de la cadena contenedora y posición relativa del candidato; no exporta su contenido. Los nombres sospechosos siguen sustituyéndose por huellas. Esto permite investigar referencias que el análisis anterior no cubría, sin modificar la aceptación de fuentes o APK.

**25 pruebas Python superadas**: se conservan las 20 anteriores y se añaden cinco regresiones de metadatos, anotaciones, depuración, cinco subcadenas iguales y metadatos corruptos. Son fixtures estructurales, no la reproducción del APK rechazado. Patrones de seguridad intactos, ninguna excepción por huella o por `classes4.dex`. Origen exacto, clasificación, corrección definitiva, Android 15/16 y publicación de APK siguen pendientes de obtener y analizar la atribución del DEX real. Progreso de estabilización: **35 %**.

## Referencias primarias

- [Formato DEX oficial: tablas, strings y valores codificados](https://source.android.com/docs/core/runtime/dex-format).
- [Instrucciones Dalvik oficiales](https://source.android.com/docs/core/runtime/dalvik-bytecode).
- [Salida de prueba dexdump en AOSP, const-string y jumbo](https://android.googlesource.com/platform/art/+/6ebd0862f7f91b8ff7c6ba4d3082474940893434/test/dexdump/all-dex-files.txt).
