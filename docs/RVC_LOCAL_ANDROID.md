# RVC completamente local en Android: viabilidad e integración segura

Estado: **primera implementación nativa ONNX incorporada en PbtMetaGPT; pendiente de validación con modelos RVC reales, latencia y audio físico**. No se considera estable hasta superar las pruebas de aceptación.

## Implementaciones de referencia

- https://github.com/ouor/RVC-Android : Android 12+/arm64 y ONNX Runtime Android. Usa tres modelos ONNX separados (sintetizador RVC exportado por voice-changer, ContentVec/HuBERT y RMVPE). Pipeline de voz grabada/audio en memoria -> extracción de características y F0 -> sintetizador -> audio. El sintetizador debe contener metadatos específicos de voice-changer.
- https://github.com/Sharl210/ultimate-rvc-mobile : inferencia Android local RVC v2 con ONNX HuBERT/RMVPE y modelo de voz ONNX; también requiere convertir .pth fuera del teléfono.
- https://github.com/SUC-DriverOld/rvc.onnx : herramientas de exportación a ONNX para redes RVC y auxiliares; no es una biblioteca Android.

**Compatibilidad:** no basta importar un .pth/.index y ejecutar: .pth es un checkpoint PyTorch, no un grafo ONNX. Los formatos no son intercambiables. **No deserializar PyTorch/pickle en Android** ni ejecutar comandos del ZIP importado. La exportación debe usar una herramienta confiable y verificable desde un ordenador o solución controlada antes de importar el ONNX. La indexación FAISS es opcional en pipelines que permiten trabajar sin índice, pero no debe representarse como compatible hasta implementarse y probarse.

## Requisitos de implementación para PbtMetaGPT

### Implementación inicial de la app

- Biblioteca → RVC: importar el conjunto **Synth ONNX + HuBERT/ContentVec ONNX + RMVPE ONNX** desde el selector de Android. Es necesario que los nombres contengan «synth» o «rvc», «hubert» o «content», y «rmvpe» para clasificarlos.
- Los archivos se guardan en almacenamiento privado de Android. No hay descarga automática de modelos ni envío de pesos a una API.
- Activar manualmente «Convertir localmente la voz sintetizada» después de importar los tres ONNX. El ajuste viene desactivado por defecto.
- El modo Natural con voz Android offline genera primero PCM con el sintetizador local y lo convierte mediante HuBERT/ContentVec, RMVPE y RVC ONNX en una corrutina de cálculo, antes de reproducirlo.
- El sintetizador debe haberse exportado con metadatos JSON voice-changer: `samplingRate`, `f0`, `embChannels`, `embOutputLayer` y `useFinalProj`. Los nombres de entradas y salidas requeridos se verifican al cargar. Las variantes de modelo no compatibles se rechazan sin simular voz.
- El conversor limita los fragmentos a ocho segundos, usa hasta dos hilos de CPU y libera sesiones ONNX al terminar. La conversión en dispositivos ARM64 puede tener una latencia considerable.
- **Limitación actual:** no convierte `.pth` en ONNX dentro del móvil; el `.index` de FAISS no interviene; no hay prueba aún con un conjunto de pesos real ni ejecución aislada en proceso separado. La API está preparada para realizar la inferencia, pero no hay evidencia de audio inteligible con un modelo concreto.

1. Auditar licencias y orígenes de las dependencias/código antes de reutilizar. No copiar clases de otros repositorios sin verificar permisos y atribución.
2. Integrar **ONNX Runtime Android** arm64 y adaptar HuBERT/ContentVec y RMVPE; seleccionar versión y modelos descargables con hashes y licencias comprobados. Evitar modelos gigantes empaquetados en APK.
3. Importar un sintetizador ONNX RVC con firma y metadatos compatibles, más modelos auxiliares ONNX. Limitar tamaño, memoria, duración de audio y consumo térmico. Validar entradas/tipos/salidas antes de ejecutarlos; ejecución en proceso separado protegido.
4. Ruta de audio: Groq texto -> TTS base local español -> PCM -> RVC ONNX local -> PCM con la voz convertida -> Bluetooth/gafas. La conversión no puede omitir la TTS base, dado que RVC no transforma texto en audio.
5. Aislar inferencia del hilo UI, permitir cancelar, reportar latencia real y evitar agotar batería/RAM del Nothing Phone 3a Lite. Asegurar que nunca se envían pesos ni audio a un servicio externo por esta función.
6. Prueba instrumental con modelo RVC ONNX de muestra realmente autorizado y resultado acústico, CPU, RAM, batería. No marcar como operativo por pasar una prueba de formato ONNX.
7. Reutilizar importador actual .pth/.index como **almacenamiento** y ofrecer indicación clara: «Convierte a RVC ONNX compatible para utilizarlo localmente». No simular una conversión que aún no existe.

## Criterio de aceptación

Seleccionar voz RVC -> sintetizar frase local -> convertir su PCM en Android sin red -> reproducir voz convertida en móvil/gafas; medir primera muestra, latencia total, memoria y continuidad. Probar con red desactivada y no marcar estable hasta hacerlo. Las implementaciones externas prueban viabilidad técnica general; **no prueban rendimiento ni compatibilidad de modelos concretos en PbtMetaGPT**.
