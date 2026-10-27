# Runtime local

Ejecuta `python tools/preparar_sherpa.py` antes de Gradle. Descarga exclusivamente el AAR oficial de Sherpa ONNX 2.0.1, comprueba el SHA-256 publicado y lo guarda aquí sin versionarlo. CI ejecuta el mismo paso. La aplicación no descarga ni instala runtimes desde los paquetes de voces.

Fuente: https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1. Proyecto Apache-2.0, con dependencias y avisos propios: https://github.com/k2-fsa/sherpa-onnx/blob/v2.0.1/LICENSE. No se incluye ningún modelo de voz en la APK; las licencias de modelos se validan por separado. Esta integración utiliza VITS con pronunciaciones aportadas por el paquete, sin distribuir eSpeak ni un fonemizador de terceros.
