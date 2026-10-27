# Contribuir a PbtMetaGPT

Gracias por colaborar. Este proyecto se desarrolla en español de España y mantiene el estado experimental hasta acreditar las pruebas manuales.

1. Antes de comenzar, lee [AGENTS.md](AGENTS.md), [el progreso](docs/PROGRESO.md) y [la validación](docs/VALIDACION.md). Abre un Issue para describir errores o propuestas y sus límites.
2. Trabaja en una rama propia y prepara cambios pequeños. No añadas credenciales, tokens, firmas privadas, capturas personales, grabaciones, modelos o voces de terceros sin permiso.
3. Para Android usa Java 17 y SDK 36. Ejecuta `python tools/preparar_sherpa.py`, `./gradlew testDebugUnitTest lintDebug assembleDebug`, `node --test backend/*.test.mjs` y `python -m unittest discover -s tools -p 'test_*.py'`. Las pruebas instrumentadas precisan emulador y `python tools/preparar_modelo_prueba.py`. En Windows utiliza `gradlew.bat`.
4. Comprueba `git diff --check`, enlaces y ausencia de secretos. Explica en el Pull Request qué has probado y qué **no** has verificado en dispositivos reales.
5. No cambies permisos, almacenes de claves, rutas de cámara/audio ni APIs de DAT sin contrastarlas con sus contratos y pruebas. No declares una Release estable basándote solo en CI.

Usa [Issues](https://github.com/PbtServers/PbtMetaGPT/issues) para defectos y mejoras; para cambios de código abre [Pull Requests](https://github.com/PbtServers/PbtMetaGPT/pulls). Nunca publiques datos de una cuenta OpenAI o Meta.
