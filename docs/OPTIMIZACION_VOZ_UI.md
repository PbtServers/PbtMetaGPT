# Optimización de voz, interfaz e icono · 28/10/2026

Esta intervención empieza en `1fe967e`, separada del 70 % histórico. Orden: voz → interfaz → icono → validación/publicación. Sin clave Groq en el entorno ni teléfono/gafas conectados al inicio; no se inventará una medición acústica.

## Recorrido anterior y cambios de voz

Antes: Vosk crea su reconocedor local → petición Groq completa (`stream:false`) → inicializar/verificar TTS → sintetizar toda la respuesta → crear AudioTrack y reproducir. Qwen creaba cliente HTTP y consultaba `/info` cada vez; Android TTS abría motor para verificar y lo volvía a abrir para sintetizar. Estos pasos secuenciales están demostrados por el código anterior; no acreditan por sí mismos una reducción numérica.

Ahora: SSE Groq incremental → frases completas → un sintetizador y un reproductor con un fragmento anticipado. La respuesta visible no espera al TTS. Un fallo de voz conserva el texto y no reintenta ni cambia de proveedor. Qwen recibe como máximo dos peticiones por respuesta, no una por token/frase. No ofrece streaming PCM: cada fragmento debe estar completo antes de reproducir. La cancelación cierra llamadas y canales, invalida callbacks y libera audio. El AudioTrack se conserva entre fragmentos del mismo formato para evitar reconstrucciones; el pool HTTP compartido permite reutilizar conexiones/HTTP2. Solo se cachean metadatos de contrato durante cinco minutos, nunca conversaciones ni grabaciones.

Modos: **Rápida**, predeterminado en perfiles nuevos, usa una voz Android instalada compatible (sin red ni cola remota); **Natural** usa Qwen; **Personalizada** conserva motores actuales. Las preferencias antiguas se migran por motor, manteniendo voz y modelo; no se impone Rápida a perfiles anteriores. OpenAI sigue siendo una elección separada y facturable, nunca un fallback.

## Métricas y límites de interpretación

`VoiceLatency`/`LatencyStore` conservan solo veinte muestras numéricas privadas: transcripción (incluye carga Vosk y habla del usuario), primer token y completado de Groq desde su petición, conexión TTS cuando se abre una nueva, envío, espera remota combinada, descarga, síntesis acumulada, primera trama renderizada y total. No hay texto, audio, URLs ni identificadores de cuenta. Las duraciones de etapas concurrentes no deben sumarse.

La primera trama se mide con `AudioTrack.playbackHeadPosition`; no prueba que el altavoz físico ni Bluetooth hayan sonado. Para dictado, su referencia empieza al terminar Vosk; el total incluye captura y reproducción. Cola y generación Qwen no se separan cuando Gradio no expone el inicio GPU: se registran juntas, sin inventar tiempos o posiciones.

La comparación remota optativa ejecuta el cliente Kotlin real contra Qwen, sin mocks ni token: respuesta sintética completa frente al primer fragmento. Mide disponibilidad de PCM, no sonido. Es exploratoria: orden, calentamiento, longitud y carga pueden sesgar una pareja; no demuestra que un proveedor sea el más rápido. Un rechazo de cuota hace fallar la prueba, sin reintentos. Groq TTFT y primer sonido en el Nothing requieren la cuenta/dispositivo reales.

## Alternativas investigadas

| Servicio | Resultado para esta app |
| --- | --- |
| Android TTS instalado | Alternativa ligera sin colas de Internet; calidad depende del motor/voz. No hay comparación física que permita declararlo universalmente más rápido |
| Qwen3-TTS público | Español y voces documentadas, sin token; opción Natural, cuotas y disponibilidad variables, sin SLA |
| Groq TTS | La [documentación](https://console.groq.com/docs/text-to-speech) enumera inglés y árabe; no se incorpora como voz española |
| Kokoro oficial hexgrad | Su [Space](https://huggingface.co/spaces/hexgrad/Kokoro-TTS/blob/main/app.py) carga pipelines `a`/`b` y voces inglesas; no basta que el modelo general sea multilingüe |
| Kokoro Spanish de leonelhs | [API pública documentada](https://huggingface.co/spaces/leonelhs/kokoro-tts-spanish/blob/main/app.py), voces Dora/Alex/Santa y español. Candidato a comparar, sin latencia/retención/licencia de pesos contrastadas para producción; no se presenta como servicio garantizado |
| Azure Speech | Voces neuronales y API, pero el [alta general](https://azure.microsoft.com/en-us/pricing/purchase-options/azure-account) requiere tarjeta. No cumple el modo gratuito sin tarjeta; las excepciones para estudiantes no son generales |

No se usa edge-tts ni automatización de servicios privados. No se incorpora una opción como «más rápida» sin medirla. RVC continúa experimental no disponible mientras no exista proveedor operativo apto y probado.

## Evidencia en curso

Primera subfase: seis JVM de segmentación/orden/cancelación/fallo/telemetría correctas localmente; 31 Python y 19 Node correctas; sintaxis Kotlin comprobada, sin sustituir compilación Android. CI y benchmark remoto deben corresponder a su SHA. README.md y seis archivos locales anteriores protegidos por SHA256 en `diagnosticos-locales/optimizacion-preservados.json` y excluidos de commits.

## Interfaz y persistencia

Cuatro destinos: Inicio, Asistentes, Voces y Ajustes. Inicio contiene Hablar, perfil, escucha y gafas. El chat Groq usa mensajes seleccionables y texto incremental; permite editar mientras TTS trabaja, detener, repetir audio, reintentar y elegir explícitamente una voz local ante fallo. Un indicador de trabajo único impide que se pierda un envío mientras llega el audio.

La biblioteca ofrece Rápida (Android instalado), Natural (Qwen con consentimiento y disponibilidad no garantizada) y Personalizada. Endpoints, importaciones, voces oficiales y motores anteriores permanecen en opciones avanzadas. Seleccionar una tarjeta no reproduce muestras. Qwen admite un máximo de dos solicitudes por respuesta; no tiene streaming de PCM en este adaptador Gradio.

Primer inicio: Servicio, Voz y Prueba opcional. Los borradores de cinco etapas migran conservando sus campos y las instalaciones que ya finalizaron no repiten el proceso. Se conservan regresiones de suspensión antes de recomposición, todas las voces oficiales, biblioteca/frases independientes y permiso denegado; adaptadas a tres etapas. Las pruebas de emulador distinguen navegación de inferencia real.

Gafas muestra detección y sesión DAT por separado del Bluetooth, selección de salida y prueba explícita de sonido. Cámara, retirada de registro y detalles de capacidad permanecen en opciones avanzadas. No se acredita 00R6 por nombre ni se declara validación acústica.

CI de la canalización: `1b964a0` no acredita compilación porque el diagnóstico capturado ocultaba su código de salida; se corrige la propagación del fallo sin omitir verificaciones. El benchmark Kotlin terminó con salida numérica vacía: no se acepta como evidencia hasta comprobar su informe real en un paso independiente.

## Icono: propuestas y revisión visual

Se conserva #388BFF del foreground anterior como azul existente del proyecto; no se ha encontrado una especificación corporativa externa que permita atribuirle otra oficialidad. Fondo #000000 y trazos #FFFFFF. La antialiasing rasteriza mezclas de estos colores, sin añadir colores de diseño.

- A, fusión geométrica: contorno continuo y puente azul suave; a 24 dp el centro pierde identidad.
- B, inteligencia integrada: lentes simétricas y nodo geométrico azul en el puente. Elegida por contorno reconocible, apertura de lentes y equilibrio a tamaño de launcher.
- C, diálogo abstracto: pares de formas angulares que convergen en el centro; su parte inferior resulta más compleja a 24 dp.

[Comparativa con el icono anterior](assets/iconos/comparativa.png) y [máscaras a 24/36/48/72 dp](assets/iconos/mascaras.png), renderizadas e inspeccionadas visualmente. B incluye foreground vectorial, fondo negro, adaptativo/round API26 y monocromo API33 existentes, y respaldo en cinco densidades. Sin letras, burbujas en lentes, verde, dorado, degradados ni símbolos de Meta.

Reproducción: `python tools/disenar_icono.py` y `python tools/renderizar_icono.py`. La prueba de recursos verifica paleta, zona segura convexa de radio 33, referencias adaptativas/monocromáticas y dimensiones PNG. Esto no acredita un launcher físico ni la audición en Nothing/00R6.

Corrección de evidencia de CI: las ejecuciones `1b964a0` y `5567726` capturaron la salida Gradle con `tee` sin shell explícita. Su estado de paso no acredita compilación, JVM, Lint ni inferencia real. `f3aa48d` restaura la propagación estricta mediante `shell: bash`; el control de seguridad nunca se ha omitido. Se requiere una ejecución completa posterior correcta.

## Validación estricta y resultado remoto

- `b844a56`, [Android 40603751768](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40603751768): compilación, JVM, Lint, ensamblado y escaneo de APK correctos; aún no acredita ambos emuladores ni APK final. Se requiere comprobar el SHA final tras los ajustes de revisión.
- [Benchmark real 40603751805](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40603751805): **FALLO_REAL**, etapa COMPLETO, HostedFailure «No se pudo conectar por HTTPS». No hay PCM ni tiempos comparables. No se infiere que el proveedor haya agotado cuota o esté caído globalmente; la causa exacta no está acreditada. No se hacen más intentos automáticos. La ejecución anterior bd2cbdd también falló sin PCM y su artefacto se inspeccionó sin nuevas síntesis.
- Local: 32 Python, 19 Node, seis JVM de canalización y escaneo de fuentes correctos; siete hashes preservados. `preparar_sherpa.py` y `preparar_modelo_prueba.py` bloqueados por WinError10013. Gradle local, tras configurar ANDROID_USER_HOME escribible, confirma SDK ausente. ADB no detecta dispositivos. Estos fallos no se sustituyen por mocks ni por resultados de otra versión.
- Groq real y medición acústica con Nothing/00R6 siguen pendientes de clave Free en la app y hardware. No se afirma que un motor o proveedor sea más rápido sin mediciones.

OpenAI opcional: resolución de Rápida a una voz Android antes de abrir audio y solicitar credenciales; el perfil efectivo se utiliza también al emitir la credencial efímera. Se borra la selección de sesión al detener para evitar reutilizar un perfil anterior. Se conserva la modalidad textual para TTS alternativo, según [OpenAI Docs, client secrets](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create). La integración sigue requiriendo elección expresa; no consume una clave OpenAI desde Groq.
