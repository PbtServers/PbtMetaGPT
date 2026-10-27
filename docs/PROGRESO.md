# Seguimiento de PbtMetaGPT

Actualización: 27 de octubre de 2026. Objetivo: ampliación de v0.4.0 con activación múltiple y biblioteca modular de voces. La siguiente compilación llevará una versión de desarrollo; no se publicará una Release estable sin completar la aceptación.

## Estado inicial de la ampliación

- Progreso ponderado: **55 %**. Fases: BYOK 20/25, configuración/perfiles 12/15, activación 10/20, motores/voz 4/20, integración 5/10, pruebas/documentación 4/10. Incluye implementación nueva aún pendiente de CI; la validación física sigue pendiente.
- Fase actual: esquema y controles de activación múltiple implementados; comienza la síntesis modular. Compilación de prueba `0.4.1-dev` (código 5), ampliación experimental de v0.4.0.
- Último commit publicado antes de este bloque: `d85185c`. El commit que contiene esta actualización guarda el bloque de frases/perfiles; consultar su SHA con `git log` y su propia CI.
- Compilación de la base: correcta. [CI 40484660913](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40484660913).
- Pruebas de la base: 22 JVM, 4 Android/emulador, 19 Node y 3 Python superadas. Lint, debug, contenedores y escáner correctos. No son pruebas acústicas ni de OpenAI de pago.
- Tiempo inicial previsto: 90–150 minutos para implementación/CI, sujeto a dependencias y fallos. Validación física sin estimación fiable.

## Completado en la base

BYOK con Keystore/AES-GCM, WebRTC, backend opcional, configuración inicial, perfiles, voces oficiales, escucha on-device con una frase, DAT/cámara con consentimiento y diagnóstico. El código está implementado; el uso real de OpenAI, el reconocimiento hablado y los sensores físicos siguen pendientes.

## Pendiente de esta ampliación

1. Esquema migrable de múltiples frases y ajustes independientes por perfil; arbitraje entre perfiles y protección contra duplicados.
2. Interfaz para editar/probar frases, avatar, bienvenida, confirmación y duración.
3. Separación de conversación textual y síntesis; cancelación coordinada y exclusión de audio nativo/alternativo.
4. Motores Android, API y modelos locales con un formato y motor concretos. No habilitar formatos sin ejecutor implementado.
5. Biblioteca segura: metadatos, licencia/procedencia, requisitos, importación limitada, validación, muestras y eliminación.
6. Tests, documentación y APK del commit final. Cada bloque significativo se guardará mediante commit y push a master.

## Problemas conocidos y límites

No hay SDK Android local, clave de pago ni hardware validado en el entorno. La compilación completa y el emulador se ejecutan en GitHub Actions. No se ofrecerá escucha en segundo plano sin una implementación específica y validación. Un archivo ONNX u otro modelo importado no acredita capacidad de ejecución. No se distribuirán modelos de actores clonados ni se instalará código de paquetes de voz.

## Continuación concreta

Bloque de frases: esquema JSON v2 migrable desde v1; lista sin cuota de número (presupuesto total 256 KiB); activar/desactivar, principal, nombre automático, tolerancia textual opcional, prueba escrita y prueba de micrófono sin OpenAI. `ActivationGate` consume una sola coincidencia y rechaza callbacks antiguos y ambigüedad entre perfiles. El motor acústico continúa siendo Android on-device, solo visible y por idioma. Avatar textual, bienvenida, sonido y duración quedan en cada perfil. Los motores alternativos permanecen bloqueados hasta completar su ejecutor y su integración.

Próxima tarea: runtime Sherpa ONNX 2.0.1 verificado por SHA-256, biblioteca de paquetes VITS con pronunciaciones explícitas, ejecutores Android/API y ruta Realtime de salida textual. Ejecutar CI del bloque de frases y corregir cualquier fallo antes de ampliar declaraciones de soporte.

Leer este documento, comprobar `git status`, `git log` y CI del HEAD; continuar por el esquema de frases/perfiles y seleccionar un runtime de modelo local verificable. Mantener actualizada esta sección en cada bloque, sin dar por probada la síntesis real por pruebas de JSON.
