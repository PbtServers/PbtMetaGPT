# PbtMetaGPT v0.5.0 — candidata RC1

Versión Android: `0.5.0-rc1` (`versionCode 6`). No crear la etiqueta final `v0.5.0` ni una Release estable antes de validar en teléfono y gafas reales y revisar la CI de su SHA.

## Mejoras de la candidata

- Salida Bluetooth: precarga silenciosa antes de la primera sílaba para evitar solapamientos con sonidos de conexión/activación de Meta. Requiere escucha real en Meta Glasses 00R6.
- Frases de activación: señal de confirmación audible en flujo de música antes de iniciar el dictado. Se ofrece interruptor directo en Activación por voz y se activa por defecto para nuevos perfiles.
- Dictado: intento prioritario del motor de reconocimiento Android estrictamente on-device para texto libre, con Vosk como alternativa cuando no esté instalado. No se envía audio a Groq para transcribirlo.
- Voz de España: preferencia para variantes `es-ES` y motores neuronales offline Android compatibles con la API `TextToSpeech`, como HayaiTTS o Kokoly, instalados voluntariamente. No se distribuyen modelos externos en la APK ni se afirma que todos sean rápidos en este teléfono.
- Visión: enviar una fotografía con confirmación explícita a Groq Vision con `qwen/qwen3.8-27b` mediante chat completions multimodales, sin recurrir a la API de pago de OpenAI. Puede haber límites de cuota Free y acceso condicionado por el proveedor.
- RVC: importar `.pth`, `.index` o un ZIP con esos archivos. Los modelos se guardan como datos en almacenamiento privado; se validan rutas y tamaños y se impide instalar código ejecutable. **No hay inferencia ni previsualización RVC**: falta un proveedor gratuito de conversión de modelos personalizados verificado que no requiera self-hosting.
- Icono de marca: reutiliza el icono actual negro, blanco y azul; visible al principio del README.

## Pruebas automáticas

- Ejecutar `python tools/preparar_sherpa.py`.
- `./gradlew testDebugUnitTest lintDebug assembleDebug` con JDK 17, Android SDK 36.
- `node --test backend/*.test.mjs`.
- `python -m unittest discover -s tools -p 'test_*.py'`.
- Escáner de secretos tanto de fuentes como de APK.
- Instrumentación de Android 15 / API 35 y Android 16 / API 36.
- Pruebas nuevas de modelos RVC, ZIP con rutas peligrosas, duplicados, cancelación y archivo de índice.

## Validación física requerida para cerrar v0.5.0

- [ ] Se escucha completa la primera palabra de cada respuesta en Meta Glasses 00R6.
- [ ] No se solapa con el sonido de activación de Meta, con diez activaciones seguidas.
- [ ] Se escucha la confirmación de frase en gafas y móvil; no corta el dictado posterior.
- [ ] Se comprenden frases completas en español de España con ruido normal.
- [ ] Se prueba una voz neuronal offline es-ES y se compara latencia y batería con Qwen remoto.
- [ ] Se consigue un análisis de fotografía con Groq Vision usando una clave Free real sin introducirla en logs/CI.
- [ ] Importa un ZIP RVC real y también `.pth` e `.index` por separado; los muestra como importados sin afirmar reproducción.
- [ ] Los cambios de voz, los permisos, el historial y la selección de asistente sobreviven a cerrar y reabrir.
- [ ] No hay regresiones Android 15 y Android 16 ni incidencias graves de seguridad.
- [ ] Firma de Release, archivo de distribución y GitHub Release asociados al commit validado.

## Notas de seguridad y privacidad

No enviar fotografías sin confirmación; no registrar credenciales, imágenes o conversaciones. No ejecutar archivos `.pth` como Python/Pickle ni cargar modelos RVC desde fuentes no confiables. Una biblioteca de modelos no implica autorización para reproducir voces de personas reales.

Esta nota describe una **candidata**, no sustituye las pruebas físicas ni declara la Release final.
