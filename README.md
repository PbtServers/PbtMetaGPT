# PbtMetaGPT

<p align="center">
  <strong>Tu asistente de voz, tus reglas.</strong><br>
  Una aplicación Android de código abierto para diseñar asistentes personales con OpenAI y explorar su integración con Ray-Ban Meta.
</p>

<p align="center">
  <a href="https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml"><img src="https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml/badge.svg" alt="Compilación Android"></a>
  <a href="https://github.com/PbtServers/PbtMetaGPT/blob/master/LICENSE"><img src="https://img.shields.io/github/license/PbtServers/PbtMetaGPT" alt="Licencia"></a>
  <img src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white" alt="Android 12 o posterior">
  <img src="https://img.shields.io/badge/versi%C3%B3n-0.4.1--dev-orange" alt="Versión experimental 0.4.1-dev">
</p>

<p align="center">
  <a href="https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml">Descargar APK experimental</a> ·
  <a href="#instalación">Instalación</a> ·
  <a href="#primeros-pasos">Primeros pasos</a> ·
  <a href="docs/PROGRESO.md">Progreso</a> ·
  <a href="https://github.com/PbtServers/PbtMetaGPT/issues">Reportar un problema</a>
</p>

> [!IMPORTANT]
> **Proyecto independiente y experimental.** PbtMetaGPT no está afiliado, patrocinado ni respaldado por Meta, OpenAI ni Marvel. La integración con Ray-Ban Meta, sus micrófonos, altavoces y cámara **no se ha validado todavía con gafas reales**. Esta versión no es una Release estable.

## Índice

- [Qué es PbtMetaGPT](#qué-es-pbtmetagpt)
- [Funciones y estado de validación](#funciones-y-estado-de-validación)
- [Capturas de pantalla](#capturas-de-pantalla)
- [Instalación](#instalación)
- [Primeros pasos](#primeros-pasos)
- [Asistentes y frases de activación](#asistentes-y-frases-de-activación)
- [Voces y modelos locales](#voces-y-modelos-locales)
- [Ray-Ban Meta](#ray-ban-meta)
- [Arquitectura](#arquitectura)
- [Seguridad y privacidad](#seguridad-y-privacidad)
- [Requisitos y compilación](#requisitos-y-compilación)
- [Pruebas y desarrollo](#pruebas-y-desarrollo)
- [Preguntas frecuentes](#preguntas-frecuentes)
- [Contribuir y créditos](#contribuir-y-créditos)

## Qué es PbtMetaGPT

**PbtMetaGPT** permite crear perfiles de asistentes con nombre, instrucciones, personalidad, idioma, estilo de conversación y voz. Utiliza OpenAI Realtime mediante WebRTC para conversar y ofrece motores alternativos de síntesis.

En **modo BYOK (Bring Your Own Key)** introduces tu propia clave de OpenAI API en el teléfono. **No necesitas montar un servidor, VPS ni dominio**. Existe un backend HTTPS opcional para quien prefiera custodiar la clave en un servidor.

La aplicación es de código abierto y se puede probar sin gafas, aunque la conversación con OpenAI real, la calidad acústica y las rutas de audio físicas siguen requiriendo comprobaciones. **ChatGPT Plus no incluye consumo de OpenAI API:** sus facturaciones son independientes.

## Funciones y estado de validación

**Leyenda:** Implementado = código presente y pruebas indicadas; Experimental = integrado, pendiente de prueba con servicios/dispositivos reales; Validación física pendiente = ningún resultado de laboratorio acredita funcionamiento en gafas. Consulta [la matriz de evidencias](docs/VALIDACION.md).

| Área | Estado | Alcance comprobado o limitación |
| --- | --- | --- |
| BYOK y conexión directa a OpenAI | **Experimental** | Claves protegidas y flujo de credencial temporal; conversación real con clave personal pendiente |
| Android Keystore y AES-256-GCM | **Implementado** | Pruebas JVM e instrumentadas; no garantiza seguridad frente a un dispositivo comprometido |
| OpenAI Realtime por WebRTC | **Experimental** | Protocolo implementado; voz bidireccional real pendiente |
| Asistentes y personalidades | **Implementado** | Edición, persistencia, migración e instrucciones por perfil |
| Importación y exportación de perfiles | **Implementado** | Sin claves; las referencias de modelos se reasocian |
| Varias frases por asistente | **Implementado** | Comparación, activación individual y rechazo de ambigüedad |
| Reconocimiento de activación local | **Experimental** | Usa SpeechRecognizer on-device; precisión acústica pendiente |
| Voces oficiales OpenAI | **Experimental** | Selección y contrato integrados; escucha real pendiente |
| Android TTS / API TTS externa | **Experimental** | Adaptadores y pruebas controladas; proveedores y salida real pendientes |
| Biblioteca de voces y paquetes Piper/VITS | **Experimental** | Importación, validación y eliminación; compatibilidad limitada |
| Sherpa ONNX y aislamiento | **Experimental** | Inferencia real de fixture en emulador, sin validación auditiva |
| Cancelación, interrupción y reproducción | **Implementado** | Contratos y flujo probados automáticamente; audición física pendiente |
| Meta Wearables DAT | **Validación física pendiente** | Registro y sesión implementados; gafas reales sin verificar |
| Cámara con vista previa y confirmación | **Validación física pendiente** | Flujo de consentimiento implementado; captura real sin acreditar |
| Diagnóstico de conexión y audio | **Implementado** | Indicadores y pruebas guiadas; no sustituyen confirmación humana |
| Backend HTTPS opcional | **Experimental** | Pruebas y contenedores CI; despliegue público no verificado |
| Escucha permanente, pantalla bloqueada, control de «Hey Meta» | **No disponible** | No se ofrece ni se promete |

## Capturas de pantalla

Todavía no se han incorporado capturas verificadas de la aplicación. **No se muestran maquetas como si fueran pantallas reales.**

Las nueve vistas previstas son: inicio, bienvenida, OpenAI, asistentes, personalidad, activación, biblioteca de voces, dispositivos y diagnóstico. Consulta [cómo preparar capturas reales](docs/assets/screenshots/README.md). Antes de subir una imagen, oculta claves, tokens, direcciones, identificadores y conversaciones personales.

## Instalación

**Requisitos:** Android 12 o posterior (API 31). Para voz local se requiere un reconocedor on-device compatible y, según el modelo, memoria y arquitectura de 64 bits.

1. Abre las [ejecuciones de Android en GitHub Actions](https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml) e inicia sesión en GitHub.
2. Abre una ejecución **correcta** correspondiente al commit que quieres probar; verifica que los trabajos hayan terminado en verde.
3. En **Artifacts**, descarga el ZIP de la APK debug de esa ejecución, si aún está disponible. Los artifacts caducan; no confundas una ejecución anterior con la versión de `master`.
4. Extrae el ZIP, copia `app-debug.apk` al teléfono y ábrelo para instalarlo.
5. Si Android lo solicita, autoriza temporalmente **Instalar aplicaciones desconocidas** para la app desde la que abras el APK. Desactiva ese permiso después si no lo necesitas.

> [!WARNING]
> **Es una APK de depuración**, no una publicación estable firmada para distribución. Los certificados debug pueden variar entre ejecutores de CI: Android podría impedir actualizar sobre una APK instalada con otra firma. Antes de desinstalar, exporta los perfiles que quieras conservar; **las claves API no se exportan** y tendrás que configurarlas de nuevo.

No existe una Release estable garantizada por esta documentación. El enlace a Actions muestra las ejecuciones existentes, no promete que haya un artifact disponible permanentemente.

## Primeros pasos

1. Instala PbtMetaGPT y sigue la pantalla de bienvenida.
2. Lee los avisos de privacidad, concede solo los permisos que necesites (micrófono; Bluetooth/cámara para funciones opcionales).
3. En [OpenAI Platform](https://platform.openai.com/api-keys), genera una **API key** de un proyecto con presupuesto y límites adecuados. No introduzcas tu contraseña de ChatGPT.
4. En la sección **OpenAI**, guarda la clave en el dispositivo y ejecuta **Comprobar credenciales**. Esa comprobación no valida la conversación de voz.
5. Crea un asistente o elige una plantilla, revisa sus instrucciones y selecciona una voz compatible.
6. Configura, si quieres, las frases de activación y prueba el reconocimiento local con la app visible.
7. Autoriza el micrófono e inicia una conversación manualmente. Espera a **Escuchando** y utiliza **Pausar**, **Interrumpir** o **Finalizar** cuando corresponda.

El consumo de OpenAI API corre por tu cuenta: configura límites de gasto en OpenAI Platform.

## Asistentes y frases de activación

Cada perfil admite nombre, avatar textual, idioma, personalidad, instrucciones, estilo, humor, vocabulario, extensión, voz y duración. Puedes importar y exportar configuraciones, pero nunca credenciales ni pesos de modelos.

**«Jarvis»** es solo un ejemplo de personalidad inspirada en la ficción: no es oficial de Marvel ni incluye una voz licenciada. Puedes crear las frases **«Atento Jarvis»**, **«Escucha Jarvis»** y **«Atención Jarvis»**, u otras propias. Se pueden activar varias por perfil y designar una principal.

La activación emplea `SpeechRecognizer.createOnDeviceSpeechRecognizer`. Requiere autorización, aplicación visible y motor local compatible. Tiene ventanas limitadas, no aprende nombres inventados y puede reconocer incorrectamente algunas frases. **No escucha permanentemente**, no funciona con la pantalla bloqueada ni sustituye «Hey Meta». Consulta los [detalles y límites técnicos](docs/GUIA_TECNICA.md).

## Voces y modelos locales

| Motor | Función | Limitación |
| --- | --- | --- |
| OpenAI Realtime | Voces nativas en sesión WebRTC | Requiere API de pago y conectividad |
| Android TTS | Voces instaladas en Android | Idiomas y tratamiento dependen del proveedor |
| OpenAI Speech | Síntesis de texto separada | Adaptador transitorio; verificar disponibilidad |
| API TTS externa | Servicio HTTPS y token independiente | Envía el texto de respuesta solo con autorización |
| Sherpa ONNX | Piper/VITS local en proceso aislado | Solo formato compatible y diccionario completo |

Se admiten paquetes ZIP `sherpa-piper-phonemes-v1` con `voz.json`, `model.onnx`, `tokens.txt` y `lexicon.tsv`. No acepta ONNX arbitrarios; una importación válida **no demuestra** síntesis inteligible. Consulta [Biblioteca de voces](docs/BIBLIOTECA_DE_VOCES.md).

## Ray-Ban Meta

La app integra componentes de **Meta Wearables Device Access Toolkit (DAT)**. Para probarlos necesitas gafas compatibles emparejadas, Meta AI, permisos y autorización del registro de desarrollador de Meta.

Comprueba por separado: sesión DAT, micrófono real, altavoces reales, captura puntual, vista previa y segunda confirmación antes de enviar una imagen a OpenAI. **Conectado no equivale a audio o cámara certificados.** La ruta Bluetooth puede depender de Android o recaer en el móvil.

No se garantiza funcionamiento con ninguna combinación de modelo, firmware y teléfono; tampoco captura continua, escucha en segundo plano o control de «Hey Meta». Consulta [pruebas reales](docs/PRUEBAS_REALES.md).

## Arquitectura

```mermaid
flowchart TD
    U[Consentimiento] --> A[App Android]
    A --> P[Perfiles y asistentes]
    A --> W[Reconocimiento local]
    A --> D[Meta DAT]
    G[Gafas Ray-Ban Meta] -.-> D
    P --> K[Keystore BYOK]
    K --> R[OpenAI Realtime WebRTC]
    P --> B[Backend HTTPS opcional]
    B --> R
    R --> V[Voz OpenAI]
    R --> Q[Cola TTS cancelable]
    Q --> T[Android TTS]
    Q --> S[Sherpa ONNX aislado]
    Q --> X[OpenAI Speech / TTS externo]
    V --> O[Audio Android]
    T --> O
    S --> O
    X --> O
```

El diagrama describe el software, **no rutas físicas comprobadas de las gafas**. Ver [arquitectura detallada](docs/ARQUITECTURA.md).

## Seguridad y privacidad

- BYOK cifra la clave en reposo con AES-256-GCM y Android Keystore; proveedores externos usan otro almacén. Una clave puede quedar expuesta en memoria durante su uso si el dispositivo está comprometido.
- No se incluyen claves en el repositorio, APK ni perfiles exportados. Los textos personales exportados **no se cifran**: revísalos antes de compartirlos.
- El reconocimiento local no conserva el audio de activación. Conversaciones autorizadas y fotos confirmadas pueden viajar a OpenAI; proveedores TTS externos autorizados reciben texto de respuesta.
- La cámara precisa acción explícita, vista previa y segunda confirmación. Los modelos de usuario se verifican y se ejecutan aislados, sin garantizar seguridad absoluta.
- No se almacena un historial permanente de conversación según la implementación documentada. Borrar una clave local **no** la revoca en el proveedor.

Utiliza una clave de proyecto con presupuesto limitado y revócala ante pérdida del teléfono. Revisa los términos del motor TTS Android. Puedes usar el [backend opcional](backend/README.md) en lugar de BYOK.


## Requisitos y compilación

Versiones verificadas en Gradle y el wrapper:

| Componente | Versión |
| --- | --- |
| Android mínimo / objetivo / compile SDK | API **31 / 36 / 36** |
| App | **0.4.1-dev**, código **5** |
| JDK / Java | **17** |
| Gradle / Android Gradle Plugin | **8.14.1 / 8.11.1** |
| Kotlin | **2.2.21** |
| Jetpack Compose BOM | **2026.05.01** |
| Meta DAT core/camera | **1.0.1** |
| WebRTC comunitario | **150.7871.01** |
| Sherpa ONNX | **2.0.1** |
| SDK y herramientas Android | SDK 36 y paquetes exigidos por AGP |

Instala Android Studio, JDK 17 y Android SDK 36. Configura el SDK en `local.properties` mediante `sdk.dir` o la variable `ANDROID_HOME`. Los parámetros públicos de registro Meta DAT (`mwdat_application_id` y `mwdat_client_token`) no sustituyen la clave personal de OpenAI. **No introduzcas claves privadas en el código.**

```bash
git clone https://github.com/PbtServers/PbtMetaGPT.git
cd PbtMetaGPT
python tools/preparar_sherpa.py
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

En Windows utiliza `gradlew.bat`. La APK debug se genera en `app/build/outputs/apk/debug/app-debug.apk`. Para pruebas adicionales:

```bash
node --test backend/*.test.mjs
python -m unittest discover -s tools -p 'test_*.py'
python tools/comprobar_secretos.py app/build/outputs/apk/debug/app-debug.apk
# Solo con emulador configurado y fixture preparada:
python tools/preparar_modelo_prueba.py
./gradlew connectedDebugAndroidTest
```

**Los comandos están contrastados con los scripts y la configuración del repositorio**, pero necesitan un entorno Android correctamente instalado; esta documentación no afirma una nueva compilación local.

## Pruebas y desarrollo

La evidencia CI de referencia documenta **74 pruebas superadas**: **43 JVM, 7 Android/emulador, 19 Node y 5 Python**, en [esta ejecución](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40497932982). Consulta siempre el SHA de la ejecución correspondiente al APK que vas a instalar. Las pruebas no acreditan audio ni sensores con gafas reales.

| Documentación | Contenido |
| --- | --- |
| [Progreso](docs/PROGRESO.md) | Fases, porcentajes y pendientes |
| [Validación](docs/VALIDACION.md) | Matriz de evidencias |
| [Pruebas reales](docs/PRUEBAS_REALES.md) | Teléfono, OpenAI y gafas |
| [Arquitectura](docs/ARQUITECTURA.md) | Componentes y contratos |
| [Biblioteca de voces](docs/BIBLIOTECA_DE_VOCES.md) | Paquetes, licencias y limitaciones |
| [Guía técnica](docs/GUIA_TECNICA.md) | Detalles del README anterior |
| [Desarrollo](docs/DESARROLLO.md) | Estructura, pruebas y contribuciones |

No confundas tests JVM, emulador, integración simulada y **aceptación con hardware real**. La aplicación mantiene el estado experimental.

## Preguntas frecuentes

<details>
<summary><strong>¿Necesito ChatGPT Plus o un servidor propio?</strong></summary>

No. BYOK permite utilizar la API sin servidor propio. **La API de OpenAI se factura independientemente de ChatGPT Plus.** El backend es opcional.
</details>

<details>
<summary><strong>¿La aplicación es gratuita? ¿La API tiene coste?</strong></summary>

El código se publica con la licencia del repositorio. Las APIs externas pueden generar costes: consulta tarifas y presupuestos del proveedor antes de iniciar conversaciones.
</details>

<details>
<summary><strong>¿Puedo usarla sin gafas y crear mi propio Jarvis?</strong></summary>

La interfaz Android permite configurar asistentes sin gafas. Jarvis es solo una inspiración de estilo, no un producto Marvel. La conversación con proveedor real todavía requiere validación.
</details>

<details>
<summary><strong>¿Puedo importar cualquier voz o modelo?</strong></summary>

No. Únicamente paquetes Piper/VITS compatibles con el formato y diccionario documentados, y sobre los que poseas los derechos necesarios.
</details>

<details>
<summary><strong>¿Funciona con la pantalla bloqueada o sustituye «Hey Meta»?</strong></summary>

No. La detección local requiere aplicación visible y consentimiento; no controla el activador reservado de Meta.
</details>

<details>
<summary><strong>¿Por qué no reconoce mi frase de activación?</strong></summary>

Comprueba permisos, motor de reconocimiento instalado, idioma, batería y coincidencia de la frase. Usa la prueba local; una comparación textual no mide reconocimiento acústico.
</details>

<details>
<summary><strong>¿Dónde está la API key y qué hago si falla la conexión?</strong></summary>

Se guarda cifrada localmente con Android Keystore. Comprueba credenciales, límites de API, red y diagnóstico. Si pierdes el teléfono, revoca la clave desde OpenAI Platform.
</details>

## Contribuir y créditos

Consulta [CONTRIBUTING.md](CONTRIBUTING.md) antes de abrir un [Issue](https://github.com/PbtServers/PbtMetaGPT/issues) o [Pull Request](https://github.com/PbtServers/PbtMetaGPT/pulls). Ejecuta pruebas, documenta cambios y no subas secretos, capturas privadas ni material protegido de terceros.

Proyecto de **PbtServers**. Tecnologías principales: Kotlin, Android, Jetpack Compose, Meta DAT, OpenAI Realtime, WebRTC y Sherpa ONNX. Respeta las licencias de cada dependencia y modelo. **Licencia del repositorio:** [LICENSE](LICENSE); no concede derechos sobre marcas o voces ajenas.

---

<sub>PbtMetaGPT es independiente de Meta, OpenAI y Marvel. Versión experimental 0.4.1-dev, sin certificación de compatibilidad física.</sub>
