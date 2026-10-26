# Protocolo de pruebas reales · v0.3.0

No marques una prueba como superada sin observarla en el dispositivo indicado. Conserva fecha, commit/APK, Android, Meta AI, modelo de gafas y firmware en un registro privado. Si compartes una incidencia, usa únicamente el informe filtrado de Diagnóstico y elimina información personal. Las observaciones de la aplicación tienen hora; un resultado antiguo no demuestra el estado actual.

## 1. Backend y OpenAI sin sensores

Prepara [el despliegue](../deploy/README.md). Desde una red externa verifica HTTPS válido, `/healthz` y `401` sin autenticación. La salud del proceso no demuestra conectividad con OpenAI.

Ejecuta `check-live.mjs` con el token personal en un archivo privado. Registra salida/fecha sin credenciales. Un éxito demuestra emisión efímera, conexión WebSocket y respuesta de texto reales, **no WebRTC ni audio**. Si faltan configuración o credenciales, la prueba termina como no realizada; no existe un modo de éxito ficticio.

En Android, introduce URL HTTPS y token personal en el asistente. En Diagnóstico:

1. **Comprobar credencial temporal**: debe observar HTTPS y emisión efímera; SDP, ICE y sesión no quedan acreditados por ello.
2. **Verificar Realtime sin micrófono**: debe observar SDP aceptado, transporte/canal y `SESSION_CREATED`. Cierra la sesión al confirmar. No crea pista de micrófono ni solicita respuesta.
3. Prueba un token personal incorrecto y, si administras el servidor, un error de cuota/configuración controlado. Comprueba el código y que no se muestre ningún secreto. No gastes cuota repitiendo solicitudes innecesarias.

## 2. Micrófono y altavoz del teléfono

Sin gafas ni auriculares conectados, desactiva **Preferir audio Bluetooth**. Concede micrófono bajo acción explícita. Detén la conversación antes de probar audio local.

1. Abre Diagnóstico y autoriza **Probar micrófono durante 3 segundos**. Habla durante la captura. Comprueba muestras leídas, nivel RMS y entrada del teléfono. No se guarda ni envía sonido; nivel bajo/silencio se informa como revisión pendiente.
2. Autoriza **Reproducir tono de prueba**, a volumen moderado. Comprueba dónde se oye. Pulsa **He oído el tono** o **No lo he oído** solo después de escucharlo. La aplicación conserva el resultado como confirmación manual.
3. Regresa al asistente e inicia conversación real. Di «Recuérdame el número 37». Después pregunta «¿Qué número te he dicho?». Comprueba que OpenAI oye y responde por el teléfono. No des por correcto el contexto hasta observar la respuesta.
4. Pausa: comprueba el indicador de micrófono de Android y ausencia de envío de voz. Reanuda, interrumpe una respuesta y finaliza. Prueba cancelar durante conexión. Sal de la aplicación y comprueba que se detiene todo sin reanudación automática.
5. Prueba permiso denegado, volumen nulo, otra aplicación que toma el foco y corte de red. El sistema puede cerrar el proceso al revocar permisos: registra ese comportamiento real.

## 3. Ray-Ban Meta, por etapas

Usa gafas emparejadas con Meta AI, firmware actualizado y modo de desarrollo o autorización de Meta. Anota modelo y versiones; no generalices un resultado a otros modelos.

1. Diagnóstico debe mostrar Bluetooth activado y permiso concedido. Registra la aplicación en Meta AI. Comprueba descubrimiento, metadatos y sesión `STARTED`. Ninguno de estos estados acredita micrófono, altavoz o cámara.
2. Activa **Preferir audio Bluetooth**. Sin conversación activa, ejecuta la captura local y consulta el tipo de entrada observado. Prueba hablar cerca de las gafas con el teléfono alejado; compara con el teléfono. Una ruta Bluetooth no demuestra por sí sola que sea el micrófono de las Ray-Ban.
3. Reproduce el tono y confirma físicamente que sale por los altavoces de las gafas. Registra por separado entrada y salida. Si se usa el teléfono, registra la alternativa como tal; no marques las gafas como correctas.
4. Inicia voz real y repite conversación/contexto/interrupción. Observa en Diagnóstico los eventos nativos y los errores, y confirma en qué dispositivo se oye.
5. Consulta el permiso de cámara DAT sin abrir sensores. Desde el asistente autoriza captura. Si Meta AI redirige para permisos, vuelve, conecta y pulsa capturar otra vez. Revisa la imagen y luego confirma el envío a OpenAI. Comprueba una respuesta hablada sobre lo que aparece en ella.
6. Repite denegación/revocación de cámara, cancelación de captura, quitar gafas, cerrar patillas, pérdida Bluetooth y paso a segundo plano. Comprueba cierre de cámara y micrófono. No habilites escucha permanente ni vídeo continuo para hacer estas pruebas.

## Clasificación de resultados

| Resultado | Qué acredita |
| --- | --- |
| Backend saludable | El proceso responde; no valida OpenAI |
| Credencial emitida | El proveedor emitió un token vigente; no acredita sesión |
| `session.created` | Se estableció una sesión Realtime por ese transporte |
| Capturador nativo iniciado o muestras locales | Se abrió captura software; requiere confirmar ruta e inteligibilidad |
| Reproductor iniciado o tono enviado | El software inició reproducción; requiere escucha humana |
| Tono oído | Confirmación manual de esa salida, no de OpenAI |
| Foto recibida de DAT | Se obtuvo esa imagen; no se ha enviado por ello a OpenAI |
| Conversación/visión verificadas físicamente | Solo acredita esa combinación concreta de APK, teléfono, gafas y firmware |
