# Biblioteca modular de voces · desarrollo experimental

Esta ampliación usa `0.4.1-dev` como número de compilación, dentro del trabajo solicitado para v0.4.0. No es una Release estable. Consulta `PROGRESO.md` y la CI del commit; no se han probado voces en altavoces físicos ni gafas.

## Motores y separación de responsabilidades

La entrada de voz y el contexto siguen en OpenAI Realtime mediante WebRTC. La salida nativa usa las voces oficiales. Para otra síntesis se solicita **solo texto** a Realtime y se envía la respuesta terminada al motor elegido. El controlador alternativo mantiene un solo trabajo y reproductor; cancelar invalida resultados tardíos, cierra HTTP, detiene el motor y vacía PCM. Se deshabilitan la reproducción y la pista remota de audio nativo en esta ruta.

Ejecutores integrados: Android TTS con voces marcadas como locales e instaladas, API HTTPS compatible con el contrato PCM de Speech, OpenAI Speech y Sherpa ONNX 2.0.1. El estado de pruebas de cada uno figura en `VALIDACION.md` y `PROGRESO.md`: integrar el código no acredita calidad ni compatibilidad física. Durante síntesis/reproducción alternativa se apaga el micrófono para evitar realimentación; la interrupción manual vuelve a permitir escuchar si no está pausado. Se conserva el contexto en la misma sesión Realtime.

En **Biblioteca de voces** o **Voz** se eligen motor, voz, velocidad e idioma y se guarda la selección por asistente. Android añade tono; Realtime/Speech permiten instrucciones de expresión. No se presenta sensibilidad acústica o expresividad como un parámetro universal. Los proveedores externos deben implementar exactamente el contrato JSON/PCM descrito en la pantalla; frecuencia y credenciales se configuran en el teléfono, y voz/modelo pueden variar por perfil. La clave OpenAI nunca se reenvía automáticamente al proveedor.

OpenAI anuncia la retirada de `gpt-4o-mini-tts`/Speech el 6 de enero de 2027. Este adaptador es transitorio y se desactiva en esa fecha; la opción predeterminada sigue siendo Realtime. Consulta la [documentación oficial](https://developers.openai.com/api/docs/guides/text-to-speech). Las respuestas de texto y una segunda síntesis aumentan latencia, consumo, memoria y, en proveedores de pago, coste. Android TTS necesita confiar también en el motor instalado y en sus condiciones.

## Paquete local admitido

No se admiten archivos ONNX aislados ni modelos arbitrarios de RVC, PyTorch, GGUF, APK o bibliotecas. Un ZIP debe contener exactamente estos cuatro archivos en su raíz, sin rutas ni directorios:

| Archivo | Contenido y límite técnico |
| --- | --- |
| `voz.json` | Manifiesto UTF-8, hasta 64 KiB |
| `model.onnx` | Modelo Piper/VITS convertido para Sherpa; hasta 256 MiB |
| `tokens.txt` | Símbolos y sus identificadores del modelo, hasta 2 MiB |
| `lexicon.tsv` | Palabra en minúsculas, tabulador y pronunciación Unicode compatible; hasta 16 MiB |

El formato inicial tiene vocabulario explícito: **no incluye un fonemizador universal**. Cada palabra/número de una respuesta debe figurar en el diccionario. Si falta, se rechaza la síntesis con un error propio, sin inventar pronunciación ni cambiar automáticamente de voz. El propietario del modelo debe preparar el diccionario según el sistema de símbolos de su entrenamiento; añadir una voz o renombrar archivos no convierte otro formato.

Ejemplo de manifiesto (los tres hashes deben sustituirse por SHA-256 reales):

```json
{
  "version": 1,
  "name": "Personaje original autorizado",
  "format": "sherpa-piper-phonemes-v1",
  "engine": "sherpa-onnx",
  "engineVersion": "2.0.1",
  "language": "es-ES",
  "license": "Licencia y condiciones del modelo",
  "source": "Procedencia documentada por su propietario",
  "sampleText": "hola",
  "minRamMb": 2048,
  "rightsConfirmed": true,
  "sha256": {
    "model.onnx": "SHA256_REAL_DEL_MODELO",
    "tokens.txt": "SHA256_REAL_DE_SIMBOLOS",
    "lexicon.tsv": "SHA256_REAL_DE_PRONUNCIACIONES"
  }
}
```

Ejemplo de entrada TSV: `hola` seguido de tabulador y sus fonemas exactos. No se proporciona una pronunciación universal: debe coincidir con los tokens del modelo. No se distribuyen modelos de actores ni se considera una casilla de autorización prueba legal de derechos. Utiliza modelos propios o autorizados, revisando datos de entrenamiento, licencia y procedencia.

## Seguridad y requisitos

Se verifican entradas ZIP, CRC, tamaño descomprimido, hashes, metadatos ONNX VITS/Piper, símbolos y cobertura del texto de muestra. Se instala primero en una carpeta temporal y solo se publica atómicamente si termina la revisión. Las pruebas estructurales no cargan pesos y **no acreditan inferencia**. Un modelo estructuralmente válido puede fallar al ejecutar: su muestra es una comprobación separada.

Los límites protegen almacenamiento, memoria y transacciones Binder: al menos 320 MiB libres para preparar una instalación; proceso de 64 bits (`arm64-v8a`/`x86_64`), RAM declarada y ausencia de presión de memoria; pronunciaciones hasta 32 Ki puntos Unicode por petición; PCM hasta 16 MiB y síntesis hasta 60 segundos. Estos mínimos no garantizan rendimiento ni calidad. No se permite descargar runtime desde el paquete.

El AAR oficial Sherpa 2.0.1 se fija por SHA-256 en `tools/preparar_sherpa.py`. La inferencia corre en un servicio con UID aislado, sin credenciales o acceso a los archivos privados de la aplicación. Recibe descriptores de solo lectura para modelo/símbolos y un pipe de salida; no instala ejecutables. El proceso se cierra por petición y al cancelar. Un grafo dañado o costoso todavía puede agotar recursos de su proceso; el aislamiento no es una garantía contra cualquier vulnerabilidad del runtime.

Los modelos no se incluyen en las exportaciones de asistentes: se conserva una referencia local que debe reasociarse en otro teléfono. Las API keys y tokens se guardan en almacenes cifrados separados y nunca forman parte de perfiles/paquetes exportados. La síntesis Android puede crear un WAV temporal privado, eliminado al finalizar o cancelar; se limpiarán restos al iniciar la aplicación.

## Referencias verificadas

El emulador descarga una fixture LJSpeech/Piper con SHA-256 fijado, solo para la APK de pruebas. Su [MODEL_CARD](https://huggingface.co/rhasspy/piper-voices/blob/main/en/en_US/ljspeech/medium/MODEL_CARD) declara datos de dominio público; el repositorio del modelo usa MIT. No se distribuye en la APK principal ni se ofrece como personaje. La prueba genera PCM para una palabra cubierta por el diccionario y comprueba muestras/frecuencia; no acredita pronunciación ni altavoces. Se descartó una fixture Lessac tras revisar las restricciones de sus datos de origen.

- [Sherpa ONNX: runtime oficial 2.0.1](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1), [API Kotlin](https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/sherpa-onnx/kotlin-api/Tts.kt) y [modelos/licencias](https://k2-fsa.github.io/sherpa/onnx/tts/apk.html).
- [Android TextToSpeech](https://developer.android.com/reference/android/speech/tts/TextToSpeech) y [bindIsolatedService](https://developer.android.com/reference/android/content/Context#bindIsolatedService(android.content.Intent,%20int,%20java.lang.String,%20java.util.concurrent.Executor,%20android.content.ServiceConnection)).
