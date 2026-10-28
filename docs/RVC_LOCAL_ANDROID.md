# RVC completamente local en Android: viabilidad e integración segura

Estado: **viable con modelos ONNX compatibles; no implementado ni validado en PbtMetaGPT**. Este archivo es una evaluación técnica, no una declaración de funcionalidad.

## Implementaciones de referencia

- https://github.com/ouor/RVC-Android : Android 12+/arm64 y ONNX Runtime Android. Usa tres modelos ONNX separados (sintetizador RVC exportado por voice-changer, ContentVec/HuBERT y RMVPE). Pipeline de voz grabada/audio en memoria -> extracción de características y F0 -> sintetizador -> audio. El sintetizador debe contener metadatos específicos de voice-changer.
- https://github.com/Sharl210/ultimate-rvc-mobile : inferencia Android local RVC v2 con ONNX HuBERT/RMVPE y modelo de voz ONNX; también requiere convertir .pth fuera del teléfono.
- https://github.com/SUC-DriverOld/rvc.onnx : herramientas de exportación a ONNX para redes RVC y auxiliares; no es una biblioteca Android.

**Compatibilidad:** no basta importar un .pth/.index y ejecutar: .pth es un checkpoint PyTorch, no un grafo ONNX. Los formatos no son intercambiables. **No deserializar PyTorch/pickle en Android** ni ejecutar comandos del ZIP importado. La exportación debe usar una herramienta confiable y verificable desde un ordenador o solución controlada antes de importar el ONNX. La indexación FAISS es opcional en pipelines que permiten trabajar sin índice, pero no debe representarse como compatible hasta implementarse y probarse.

## Requisitos de implementación para PbtMetaGPT

1. Auditar licencias y orígenes de las dependencias/código antes de reutilizar. No copiar clases de otros repositorios sin verificar permisos y atribución.
2. Integrar **ONNX Runtime Android** arm64 y adaptar HuBERT/ContentVec y RMVPE; seleccionar versión y modelos descargables con hashes y licencias comprobados. Evitar modelos gigantes empaquetados en APK.
3. Importar un sintetizador ONNX RVC con firma y metadatos compatibles, más modelos auxiliares ONNX. Limitar tamaño, memoria, duración de audio y consumo térmico. Validar entradas/tipos/salidas antes de ejecutarlos; ejecución en proceso separado protegido.
4. Ruta de audio: Groq texto -> TTS base local español -> PCM -> RVC ONNX local -> PCM con la voz convertida -> Bluetooth/gafas. La conversión no puede omitir la TTS base, dado que RVC no transforma texto en audio.
5. Aislar inferencia del hilo UI, permitir cancelar, reportar latencia real y evitar agotar batería/RAM del Nothing Phone 3a Lite. Asegurar que nunca se envían pesos ni audio a un servicio externo por esta función.
6. Prueba instrumental con modelo RVC ONNX de muestra realmente autorizado y resultado acústico, CPU, RAM, batería. No marcar como operativo por pasar una prueba de formato ONNX.
7. Reutilizar importador actual .pth/.index como **almacenamiento** y ofrecer indicación clara: «Convierte a RVC ONNX compatible para utilizarlo localmente». No simular una conversión que aún no existe.

## Criterio de aceptación

Seleccionar voz RVC -> sintetizar frase local -> convertir su PCM en Android sin red -> reproducir voz convertida en móvil/gafas; medir primera muestra, latencia total, memoria y continuidad. Probar con red desactivada y no marcar estable hasta hacerlo. Las implementaciones externas prueban viabilidad técnica general; **no prueban rendimiento ni compatibilidad de modelos concretos en PbtMetaGPT**.
