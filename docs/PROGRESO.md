# Seguimiento de PbtMetaGPT

Actualización: 27 de octubre de 2026. Objetivo: ampliación de v0.4.0, compilación experimental **0.4.1-dev**, código 5. No es una Release estable.

- Progreso ponderado: **65 %**. BYOK 20/25; configuración/perfiles 13/15; activación múltiple 14/20; motores/voz 9/20; integración 5/10; pruebas/documentación/APK 4/10. Incluye implementación y pruebas con alcances distintos; descuenta la validación física pendiente.
- Fase actual: integración de Biblioteca y ruta Realtime textual; pruebas con modelo real preparadas, aún sin resultado.
- Incidencia de CI posterior: `0aee4c6`, ejecución 40489877319, pasó compilación/Lint, 41 JVM, Node y Python; el emulador pasó 5 de 6 pruebas y rechazó el modelo por metadatos. La revisión del runtime oficial confirma que Piper se identifica mediante `comment` y usa `n_speakers`, no los campos inicialmente asumidos. Se corrige el validador y el nombre de instancia Android; no se da la inferencia por superada hasta repetirla.
- Segundo intento: `1967217`, CI 40491497858, compilación/Lint y JVM correctos; 6 de 7 pruebas Android correctas. El modelo ya supera la importación, pero falla dentro del runtime aislado. Se añaden etapa/tipo de excepción filtrados y log nativo solo de la fixture pública en CI para resolverlo. No se ha eliminado ni omitido la prueba. Último commit publicado antes de este bloque: `1967217`.
- Tercer intento: `2dc94ff`, CI 40492795452, 42 JVM y 6/7 Android correctos; la fixture falla en inicialización nativa (etapa 4, RuntimeException). Se habilita diagnóstico ampliado exclusivamente en el test del modelo público; la interfaz y los modelos del usuario mantienen mensajes filtrados. Último commit publicado antes de la siguiente actualización: `2dc94ff`. La inferencia sigue sin acreditarse; no se publica APK de una ejecución fallida.
- Último commit publicado antes de este bloque: **4256249**. El commit que contiene esta actualización guarda la siguiente subfase; consultar `git log` y su propia CI.
- Compilación y pruebas del bloque anterior: [CI 40488236081](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40488236081), tres trabajos correctos. 36 pruebas JVM, 4 Android/emulador, 19 Node, 3 Python; Lint, APK y contenedores correctos.
- Tiempo transcurrido aproximado: 31 minutos. Estimación restante: 50–90 minutos para pruebas/correcciones/documentación. Hardware y OpenAI de pago no tienen una estimación fiable sin dispositivos ni credenciales.
- Actualización de tiempo: 55 minutos aproximadamente; estimación restante 35–70 minutos, condicionada al fallo nativo. Porcentaje mantenido en 65 % hasta acreditar inferencia. README actualizado, herramienta de empaquetado y cinco pruebas Python superadas localmente. La compilación del bloque nuevo incorpora 42 JVM y 7 Android declaradas; todavía no es su resultado final.
- Actualización de seguimiento: unos 69 minutos. Documentación de arquitectura/protocolo y paginación de frases completadas; espera local no se rearma tras una conexión fallida ni al navegar por la bienvenida. Próxima tarea inmediata: leer el error concreto de inicialización nativa de la fixture, corregirlo y repetir toda la CI antes de cerrar la entrega.

## Completado e implementado

- Base BYOK cifrada y backend opcional, DAT/cámara y diagnósticos. El uso físico y la conversación real siguen pendientes.
- Esquema de perfiles v2 migrable desde v1: múltiples frases, activar/desactivar, principal, nombre automático, tolerancia textual opcional, prueba escrita y acústica sin OpenAI. No hay cuota artificial de número: presupuesto de perfiles 256 KiB y límite técnico de sugerencias Binder 32 Ki caracteres. Todas las frases habilitadas participan en la comparación.
- Arbitraje entre asistentes del mismo idioma, rechazo de ambigüedad y protección contra duplicados/callbacks antiguos. Motor exclusivamente on-device y solo visible. No hay sensibilidad acústica configurable ni segundo plano.
- Avatar textual, confirmación, bienvenida y duración por asistente. Persistencia y exportación sin almacenes de claves.
- Runtime oficial Sherpa 2.0.1 con hash fijado; paquetes ZIP de datos con metadatos, hashes, modelo VITS/Piper, símbolos y diccionario. Proceso aislado por petición. Validación estructural probada; no equivale a inferencia real.
- Biblioteca de voces, selección de motor, proveedor HTTPS con credencial cifrada separada y muestras locales sin clave. Realtime textual para síntesis alternativa, audio nativo deshabilitado, micrófono apagado durante reproducción alternativa, cancelación coordinada y limpieza temporal.
- Pruebas de cola/HTTP y fixture LJSpeech de inferencia real preparadas para la nueva CI. La fixture solo se descarga para la APK de pruebas, no para la aplicación.

## Pendiente y límites

1. Ejecutar CI del bloque integrado; corregir cualquier fallo de Kotlin, Lint, emulador o modelo antes de afirmar su resultado.
2. Probar realmente inferencia VITS aislada, separación de secretos y selección de motores. Ampliar diagnóstico y pruebas de interfaz.
3. Actualizar README, arquitectura, validación y protocolo con los resultados concretos.
4. Probar voz Android instalada, endpoints TTS y conversación real con OpenAI; no hay clave de pago disponible en este entorno. Los tests HTTP usan un proveedor sustituido.
5. Reconocimiento acústico, micrófono/altavoces y Ray-Ban Meta pendientes de hardware. Nunca declarar compatibilidad completa por compilar o por una prueba PCM.

El modelo necesita pronunciaciones para todas las palabras; no se distribuye un fonemizador universal. Un ONNX arbitrario o estructuralmente válido puede fallar al ejecutar. OpenAI Speech es transitorio: retirada anunciada para 06/01/2027; el modo predeterminado sigue siendo Realtime. No se distribuyen voces clonadas de actores. Formato y requisitos: `BIBLIOTECA_DE_VOCES.md`.

## Próxima tarea concreta

Diagnóstico definitivo de `8d19640`, CI 40496859020: 43 JVM/19 Node/5 Python, Lint, debug y Docker correctos; 6/7 Android. El modelo alcanza la generación, pero JNI aborta buscando `invoke(float[]) -> Integer` en una lambda indy de Kotlin que solo expone el puente genérico. Se sustituye por un objeto de función explícito con método conservado mediante `@Keep`, según la firma que busca el código oficial JNI. No se ha omitido la prueba ni se acredita PCM todavía. Último commit publicado al redactar: `8d19640`. Tiempo transcurrido: unos 96 minutos; repetir CI tras esta corrección concreta.

Último intento: `7642735`, CI 40495714189, compilación/Lint/42 JVM/19 Node/5 Python/Docker correctos y 6/7 Android. El proveedor de assets con callback aborta en JNI. Se sustituye por un ZIP interno de dos assets en memfd, cargado por descriptor con el lector nativo de ResourcesProvider; sigue sin instalar ni ejecutar código del modelo. Se amplía el diagnóstico de crash de la fixture pública. También se comprueba cancelación antes de publicar paquetes y se rechazan identificadores de símbolos dañados/duplicados. Nueva suite prevista: 43 JVM; resultados pendientes de CI. Tiempo transcurrido: unos 89 minutos; estimación restante 20–50 minutos, condicionada a la carga nativa.

Actualización del bloqueo: `002eb06`, CI 40494561151, compila y supera Lint/42 JVM/19 Node/5 Python/Docker, pero mantiene 6/7 Android. La causa ya está identificada: error 13 al reabrir `/proc/self/fd` desde el UID aislado. Este bloque sustituye esa apertura por assets de solo lectura suministrados mediante API pública ResourcesProvider/AssetsProvider, sin ampliar permisos ni retirar aislamiento. Incluye validación de tokens Bearer en instrucciones exportables, documentación de empaquetado y registro de resultados. Progreso mantenido en 65 % hasta repetir la inferencia; último commit publicado anterior a este bloque: `002eb06`.

Publicar el bloque integrado, revisar CI de HEAD y la prueba nativa LJSpeech, corregir fallos, completar diagnóstico/documentación y publicar la APK de prueba del commit final. No crear Release estable. Al reanudar: leer este archivo y AGENTS, comprobar estado local/remoto y CI antes de repetir trabajo. Continuar guardando y haciendo push por cada bloque significativo.
