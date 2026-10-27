# Seguimiento de PbtMetaGPT

Actualización: 27 de octubre de 2026. Objetivo: ampliación de v0.4.0, compilación experimental **0.4.1-dev**, código 5. No es una Release estable.

- Progreso ponderado: **65 %**. BYOK 20/25; configuración/perfiles 13/15; activación múltiple 14/20; motores/voz 9/20; integración 5/10; pruebas/documentación/APK 4/10. Incluye implementación y pruebas con alcances distintos; descuenta la validación física pendiente.
- Fase actual: integración de Biblioteca y ruta Realtime textual; pruebas con modelo real preparadas, aún sin resultado.
- Último commit publicado antes de este bloque: **4256249**. El commit que contiene esta actualización guarda la siguiente subfase; consultar `git log` y su propia CI.
- Compilación y pruebas del bloque anterior: [CI 40488236081](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40488236081), tres trabajos correctos. 36 pruebas JVM, 4 Android/emulador, 19 Node, 3 Python; Lint, APK y contenedores correctos.
- Tiempo transcurrido aproximado: 31 minutos. Estimación restante: 50–90 minutos para pruebas/correcciones/documentación. Hardware y OpenAI de pago no tienen una estimación fiable sin dispositivos ni credenciales.

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

Publicar el bloque integrado, revisar CI de HEAD y la prueba nativa LJSpeech, corregir fallos, completar diagnóstico/documentación y publicar la APK de prueba del commit final. No crear Release estable. Al reanudar: leer este archivo y AGENTS, comprobar estado local/remoto y CI antes de repetir trabajo. Continuar guardando y haciendo push por cada bloque significativo.
