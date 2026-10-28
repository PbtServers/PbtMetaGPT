# IA y voces alojadas por terceros

Investigación del **27/10/2026**. Implementación experimental, sin conversación real de extremo a extremo acreditada. Este documento amplía la documentación existente; README.md y los seis archivos locales anteriores se conservan íntegros.

[Descargar APK experimental optimizada de `cc489d5`](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40606942378/artifacts/12781607132), publicada después de superar Android15 y Android16. Puede requerir iniciar sesión en GitHub para descargar el artifact. Es una APK de prueba; faltan Groq con cuenta Free real y validación física de audio/gafas.

Actualización **28/10/2026**: [optimización y metodología](OPTIMIZACION_VOZ_UI.md). Inicio, Asistentes, Voces y Ajustes; configuración breve y texto progresivo. CI de cc489d5 correcta, con 28/28 instrumentadas en cada API35/API36. La nueva prueba real Kotlin de Qwen falló por HTTPS; no hay una mejora de latencia remota o acústica acreditada.

## Estado de esta entrega

| Función | Implementación | Validación real |
| --- | --- | --- |
| GroqCloud Free: cuenta propia, clave cifrada, consulta de modelos, respuesta textual y contexto por sesión | Adaptador y pantallas compilados; configuración/Keystore probados | Generación pendiente de clave Free real |
| Dictado español con Vosk, tras «Atento» o botón | Captura local por turnos de hasta 20 s; reutiliza el modelo pequeño ya instalado | Pendiente de teléfono y reconocimiento hablado |
| Voz por asistente, Android TTS instalado | Se reutiliza el motor existente; permitido en la ruta gratuita | Nueva ruta de audio sin validar físicamente |
| Qwen3-TTS CustomVoice en Space oficial | Adaptador Gradio experimental, consentimiento, contrato y muestras explícitas | Prueba Python histórica correcta; nuevo benchmark Kotlin falla HTTPS sin PCM. Uso actual y audición no acreditados |
| RVC con modelos personales | No disponible; sin importación ni subida de modelos | No hay proveedor apto y probado en esta investigación |
| Otros Spaces y segundo proveedor LLM gratuito | No hay selector genérico ni segundo adaptador integrado | Pendientes; no se presentan como utilizables |
| OpenAI API | Integración anterior conservada y seleccionable de forma separada | No se usa como fallback del modo gratuito |

No se requiere Proxmox, VPS, Docker, Space propio ni servidor doméstico. Las opciones antiguas de backend y Sherpa se conservan por compatibilidad; no forman parte del recorrido gratuito propuesto. No se ejecutan Qwen, LLM ni modelos PyTorch en Android.

## Proveedores evaluados

### GroqCloud: primera integración

Cuenta y clave personales en [Groq Console](https://console.groq.com/keys). El plan Free se distingue del Developer: este último exige un medio de pago. La FAQ oficial confirma que agotar Free produce rechazo por cuota, sin cargo ni upgrade automático. La app pide confirmar que la cuenta sigue siendo Free; no puede deducir el plan de una clave. Una cuenta que su titular haya convertido a pago puede facturar sus llamadas. [Facturación](https://console.groq.com/docs/billing-faqs), [aclaración oficial sobre Free](https://community.groq.com/t/do-i-get-charged-for-anything-on-the-groq-free-plan/832).

API HTTPS documentada: `GET /openai/v1/models` y `POST /openai/v1/chat/completions` en `api.groq.com`. Modelo inicial configurable: `openai/gpt-oss-20b`, ejecutado por **GroqCloud**, sin conexión con la cuenta ChatGPT ni con OpenAI API. El catálogo también publica GPT OSS 120B y Qwen; la consulta real de modelos en la app manda sobre una lista estática. No todos los IDs devueltos son modelos conversacionales. [Modelos](https://console.groq.com/docs/models), [contrato de chat](https://console.groq.com/docs/text-chat).

Se limitan las respuestas a 1024 tokens de completado, las peticiones a 2000 caracteres y el historial a unos pocos turnos en memoria. La página de cuotas consultada muestra para GPT OSS 20B **30 RPM, 1000 RPD, 8000 TPM y 200 000 TPD**, pero también advierte de variaciones por organización y presenta información de distintos planes: **no se garantiza que esos números sean la cuota Free de una cuenta concreta**. Comprobar sus límites en Console. HTTP 429 detiene el turno; se muestra `Retry-After` numérico acotado, sin reintentos ni rotación de cuentas. [Límites oficiales](https://console.groq.com/docs/rate-limits).

Español y catalán pueden solicitarse mediante el idioma del perfil y texto Unicode; la calidad de cada modelo y sus variantes queda pendiente de evaluación real. Vosk instalado en esta app es español; para catalán se ofrece entrada escrita. No se promete que una voz o un reconocedor soporte catalán por admitirlo el LLM.

Desde la optimización del 28/10 la ruta Groq utiliza **streaming SSE**, actualiza mensajes progresivamente y prepara voz por frases. Registra TTFT, completado, síntesis y primera trama de AudioTrack sin texto/audio en las métricas. No hay medición acústica desde el Nothing Phone ni generación con clave Free real acreditada. No se extrapolan velocidades publicitarias a la conversación completa.

El acuerdo contempla integración de APIs en aplicaciones y acceso de usuarios finales (§3.1). Es un servicio para desarrolladores/empresas, no una suscripción de consumo; el titular BYOK debe poder aceptar ese acuerdo, la política de uso y la licencia del modelo. No se redistribuye una clave compartida. [Acuerdo vigente](https://console.groq.com/docs/legal/services-agreement), [privacidad de los datos](https://console.groq.com/docs/your-data).

### Gemini API: descartado para el modo gratuito destinado a España

Tiene nivel gratuito para modelos/cuentas elegibles, registro Google y clave API. Ofrece generación multilingüe y streaming. Las cuotas RPM/TPM/RPD se consultan por proyecto y modelo; su elegibilidad no es universal. No se ha medido latencia ni probado generación. [Facturación](https://ai.google.dev/gemini-api/docs/billing), [límites](https://ai.google.dev/gemini-api/docs/rate-limits), [elegibilidad](https://ai.google.dev/gemini-api/docs/troubleshooting).

El motivo decisivo es contractual: las condiciones exigen **Paid Services al ofrecer clientes API a usuarios del EEE, Suiza o Reino Unido**. Por ello no se incorpora como alternativa gratuita para este caso de uso en España. La existencia de una tabla de precios Free no basta. [Condiciones, Use Restrictions](https://ai.google.dev/gemini-api/terms).

### Cerebras: descartado por tarjeta obligatoria en la oferta actual

Existe documentación histórica de Free, pero la página comercial vigente ofrece Developer con **5 USD promocionales tras añadir un medio de pago**, con vencimiento a 30 días. No cumple el requisito de acceso gratuito sin tarjeta. Se publican GPT OSS 120B y Qwen 3.8 27B; ninguna generación, latencia, cuota ni disponibilidad lingüística se ha probado aquí. No se configura ni se incluye su SDK. [Precios vigentes](https://www.cerebras.ai/pricing), [documentación antigua](https://inference-docs.cerebras.ai/support/pricing), [condiciones de API](https://cloud.cerebras.ai/terms).

### OpenRouter: candidato para un segundo adaptador, no integrado

El router `openrouter/free` selecciona modelos gratuitos; requiere cuenta y clave. También existen variantes `:free`. La oferta tiene límites diarios y por minuto y depende de modelos/proveedores cambiantes; español/catalán y latencia dependen del modelo elegido. Hay streaming. No se han verificado aquí una cuenta nueva sin tarjeta, las condiciones de todos los proveedores elegibles ni una generación real. Por ello no se anuncia como alternativa ya operativa ni se cambia a él automáticamente. [Router gratuito](https://openrouter.ai/docs/guides/routing/routers/free-router), [límites](https://openrouter.ai/docs/api-reference/limits).

## Qwen3-TTS online

Se ha revisado el código público del [Space oficial Qwen/Qwen3-TTS](https://huggingface.co/spaces/Qwen/Qwen3-TTS/tree/main). La página de archivos indicaba Running on Zero. Las consultas locales quedaron bloqueadas, pero la [prueba remota CI 40593117803](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40593117803), commit `a673343`, terminó correctamente: contrato CustomVoice y generación de un WAV PCM16 mono con muestras no silenciosas, usando una frase sintética en español, Ryan y 0.6B. Informe en artifact `evidencia-qwen-real` (ID 12773909868). Esto acredita una llamada real desde el runner Python, **no el adaptador Android, calidad auditiva ni gafas**. No hay medición extraída del artifact ni se inventa latencia.

El código declara CustomVoice 0.6B/1.7B y las voces Aiden, Dylan, Eric, Ono_anna, Ryan, Serena, Sohee, Uncle_fu y Vivian. Idiomas: Auto, chino, inglés, japonés, coreano, francés, alemán, español, portugués y ruso. **Catalán no documentado**. El servicio también contiene funciones de diseño y clonación; este adaptador implementa exclusivamente CustomVoice y no promete las demás. [Código fuente del Space](https://huggingface.co/spaces/Qwen/Qwen3-TTS/blob/main/app.py).

Hugging Face documenta llamadas HTTP desde cualquier cliente a Spaces Gradio públicos. El adaptador primero consulta `/gradio_api/info` y exige la función y los cinco parámetros esperados, incluyendo las opciones de idioma, voz y tamaño; si no coinciden, no envía texto. Después usa la cola `/gradio_api/call/…`, recibe eventos SSE y acepta únicamente un archivo HTTPS del mismo Space. No inventa posiciones de cola ni estimaciones: muestra «en cola / generando», permite cancelar y limita tiempo, tamaño y formato PCM16 mono. No sigue redirecciones ni interpreta el cuerpo de errores como un mensaje público. [API oficial de Spaces](https://huggingface.co/docs/hub/en/spaces-api-endpoints).

Solo se usa acceso anónimo: sin token HF, sin acceso a créditos de pago. ZeroGPU documenta 2 minutos diarios sin autenticar y 5 para cuentas gratuitas, con prioridad y disponibilidad variables; la cuota anónima puede ser compartida y más restrictiva. No se garantiza una cantidad concreta de frases. No se automatizan sus interfaces web ni se eluden colas. [ZeroGPU](https://huggingface.co/docs/hub/spaces-zerogpu).

El texto solo sale tras consentimiento independiente. No se envía audio de referencia, `.pth` ni `.index`. Se admiten hasta 1000 caracteres por síntesis y la petición a Groq pide una respuesta breve. Si el LLM supera el límite, se conserva el texto visible y se rechaza la síntesis completa; no se recorta silenciosamente. La velocidad y el tono acústico no se muestran como controles Qwen implementados.

La biblioteca ofrece prueba de contrato sin texto y previsualización explícita de síntesis. Un contrato compatible sigue sin acreditar generación ni reproducción. Android TTS instalado permanece como alternativa ligera y gratuita elegible por asistente; no se sustituye una voz remota automáticamente.

No se incluye un configurador genérico para URLs de cualquier Space: cada contrato necesita revisión y una prueba real. Esa ampliación permanece pendiente. No se pide crear un Space propio.

## RVC: razón concreta para mantenerlo no disponible

Se revisó [r3gm/rvc_zero](https://huggingface.co/spaces/r3gm/rvc_zero/tree/main), que aparecía Running on Zero. Su código contempla modelos de enlaces HF, `.pth` y `.index`, pero describe la demo como **educativa y de investigación** e incluye `edge_tts`. No se ha validado autorización del recorrido completo para un asistente de uso general ni API de inferencia operativa desde Android. Una licencia MIT de código no acredita los derechos de modelos o de la síntesis base. [Código revisado](https://huggingface.co/spaces/r3gm/rvc_zero/blob/main/app.py), [metadatos](https://huggingface.co/spaces/r3gm/rvc_zero/blob/main/README.md).

No se afirma que ningún servicio pueda existir: **no se ha encontrado y probado uno que satisfaga conjuntamente estos requisitos**. Importación, almacenamiento remoto de modelos personales, conversión, muestras y asignación RVC están desactivados. No se envía ningún modelo. Para reabrir esta función hacen falta contrato público autorizado, condiciones aptas, inferencia real y consentimiento de cada subida.

## Recorrido de prueba desde el teléfono

1. Primer inicio → Servicio → GroqCloud Free. Registrarse y generar la clave en la web oficial; guardarla exclusivamente en el campo cifrado de la app.
2. Confirmar plan Free y autorizar envío de texto/contexto. Guardar. Comprobar acceso/modelos y después «Probar respuesta real». Una prueba de modelos no acredita generación.
3. Elegir voz en el segundo paso; realizar la prueba opcional del tercero o finalizar. Después, en Voces elegir Rápida con una voz Android instalada, Natural con Qwen experimental y consentimiento, o Personalizada para ajustes avanzados. Guardar por asistente.
4. Iniciar la sesión gratuita y escribir una petición. Confirmar respuesta identificada como GroqCloud y audio por la voz del perfil.
5. Instalar Vosk español desde Activación por voz, conceder micrófono y notificación. Probar «Atento + nombre» y dictado local. La ruta gratuita no envía grabaciones a Groq.
6. Probar por separado el altavoz del teléfono y la salida Bluetooth de las gafas. La captura nueva Vosk usa el teléfono; no se acredita el micrófono de las gafas. Perder foco de audio detiene reproducción/sesión.
7. Detener desde la app y desde la notificación; comprobar liberación de micrófono, interrupción y ausencia de reinicio automático. Probar cuota real solo si el proveedor la devuelve: no agotar cuentas para fabricar un 429.

## Verificación y limitaciones pendientes

Ejecutar en un entorno con Java17/SDK36:

```powershell
python tools/preparar_sherpa.py
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
python -m unittest discover -s tools -p 'test_*.py'
node --test backend/server.test.mjs backend/check-live.test.mjs
python tools/comprobar_secretos.py app/build/outputs/apk/debug/app-debug.apk
```

La [CI 40593718535](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40593718535), commit `e5cadba`, ha superado compilación, suite JVM, Lint, escaneo de APK y emuladores **API35 y API36**, incluidas las nuevas pruebas de Keystore real. Se conserva la barrera de publicación de APK tras ambas versiones. Estas pruebas no acreditan audio físico ni generación Groq.

Prueba remota independiente, sin mocks: `python tools/probar_servicios_online.py --qwen --informe diagnosticos-locales/online-proveedores.json`. Para Groq, proporcionar `GROQ_API_KEY` de forma privada en el entorno y añadir `--confirmar-plan-free --generar-groq`. No incluir una clave en argumentos, capturas, commits ni esta documentación. Los informes contienen solo resultados, tamaños y tiempos; nunca respuestas, audio o claves. Los pasos no ejecutados producen resultado no satisfactorio, sin convertirlos en éxitos.

En este entorno local: sin SDK Android, ADB sin dispositivos, descarga de Sherpa bloqueada y conexiones Python rechazadas con WinError10013. Groq no ejecutado por ausencia de clave; contrato Qwen inaccesible y síntesis no ejecutada localmente. La prueba Qwen independiente y la compilación/Lint/pruebas Android15/16 sí pasaron en CI como se detalla arriba. Las pruebas unitarias de cuota/formato/seguridad no sustituyen estas comprobaciones. Quedan generación Groq real, prueba del recorrido completo, cuotas reales cuando se produzcan, audición, batería y Meta00R6. **No hay Release estable ni conversación gratuita de extremo a extremo validada.**

Icono: recursos adaptativos blanco/azul sobre negro, variante monocroma y PNG de respaldo por densidad. Previsualización de los vectores reales revisada a 48/64/96 px en [icono-launcher.png](assets/icono-launcher.png). Falta revisión en launcher Android físico.
