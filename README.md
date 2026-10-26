# PbtMetaGPT 🕶️

**PbtMetaGPT** es un proyecto de código abierto para Android cuyo objetivo es integrar **ChatGPT y las capacidades de voz y visión de OpenAI** con las gafas inteligentes **Ray-Ban Meta**, utilizando el kit oficial **Meta Wearables Device Access Toolkit**.

La finalidad es ofrecer una experiencia manos libres desde las gafas, con una aplicación Android que gestione las conexiones, los permisos, las conversaciones y la comunicación segura con OpenAI.

> [!IMPORTANT]
> **Estado actual: estructura inicial de desarrollo.** La aplicación Android muestra una interfaz provisional y **todavía no se conecta a las gafas ni a OpenAI**. El backend devuelve deliberadamente `HTTP 501` en el endpoint de credenciales hasta implementar autenticación y límites de uso. No hay una APK funcional lista para utilizar.

## Objetivos del proyecto

- **Conversación por voz:** hablar con ChatGPT y recibir respuestas a través de las gafas.
- **Visión mediante cámara:** consultar a ChatGPT sobre imágenes capturadas con permiso del usuario.
- **Interacción fluida:** explorar conversaciones en tiempo real con OpenAI Realtime API.
- **Aplicación Android propia:** administrar la conexión, los permisos y la configuración desde el teléfono.
- **Privacidad y seguridad:** permitir que el usuario controle cuándo se usa el micrófono o la cámara y evitar exponer claves privadas.

Estas funciones son **objetivos de desarrollo**, no características implementadas en la versión inicial. Su disponibilidad dependerá de las capacidades y restricciones del SDK oficial de Meta y de las pruebas con dispositivos reales.

## Tecnologías

| Componente | Tecnología |
| --- | --- |
| Aplicación móvil | Kotlin / Android |
| Entorno de compilación | Java 17 y Android SDK 36 |
| Integración con las gafas | Meta Wearables Device Access Toolkit `1.0.1` |
| Módulos previstos del SDK | `mwdat-core`, `mwdat-camera`, `mwdat-speech`, `mwdat-inputs` |
| Inteligencia artificial | OpenAI Realtime API (integración pendiente) |
| Backend | Node.js (estructura inicial, limitado a localhost por defecto) |
| Desarrollo asistido | Codex, con instrucciones en `AGENTS.md` |

## Puesta en marcha para desarrollo

1. Clona el repositorio:

   ```bash
   git clone https://github.com/PbtServers/PbtMetaGPT.git
   cd PbtMetaGPT
   ```

2. Abre el proyecto en **Android Studio** y prepara **JDK 17** y **Android SDK 36**.
3. Genera o añade el **Gradle Wrapper** con una versión compatible; aún no está incluido en esta estructura inicial.
4. Registra la aplicación Android siguiendo la [documentación oficial de Meta Wearables](https://developers.meta.com/wearables/).
5. Consulta `AGENTS.md` para las instrucciones y los hitos de implementación previstos para Codex.
6. Implementa y valida progresivamente la conexión con las gafas, el servicio de OpenAI y las pruebas en un dispositivo Android real.

**Nota:** esta estructura no ha sido validada como aplicación funcional para gafas. No presupongas que las funciones previstas ya están disponibles.

## Arquitectura prevista

```text
Ray-Ban Meta
    ↕
Aplicación PbtMetaGPT (Android)
    ↕
Backend de autenticación y credenciales temporales
    ↕
OpenAI Realtime API
```

La arquitectura definitiva se ajustará durante el desarrollo según las interfaces admitidas por Meta y los requisitos actuales de OpenAI.

## Seguridad y privacidad

- Nunca incluyas una **clave API permanente de OpenAI** en el código Android, el repositorio o una APK.
- El backend deberá autenticar a los clientes, limitar las solicitudes y emitir credenciales de corta duración cuando corresponda.
- El uso del micrófono y la cámara debe requerir permisos y controles claros para el usuario.
- No se debe iniciar una captura de audio o vídeo sin una acción o autorización explícita conforme a las capacidades de la plataforma.
- No subas secretos, tokens, archivos de configuración personales ni datos de conversaciones a GitHub.

## Estado y próximos pasos

- [x] Crear la estructura inicial del proyecto Android y del backend.
- [x] Documentar el desarrollo asistido por Codex en `AGENTS.md`.
- [ ] Configurar y verificar una compilación Android reproducible.
- [ ] Implementar el emparejamiento y la conexión con Ray-Ban Meta mediante el SDK oficial.
- [ ] Integrar voz y respuestas mediante OpenAI Realtime API.
- [ ] Incorporar análisis de imágenes desde la cámara, con consentimiento.
- [ ] Implementar autenticación y gestión segura de credenciales en el backend.
- [ ] Ejecutar pruebas reales, automatizadas y de compatibilidad.
- [ ] Publicar una primera APK de prueba.

## Documentación oficial

- [Meta Wearables Device Access Toolkit para Android](https://github.com/facebook/meta-wearables-dat-android)
- [Meta Wearables para desarrolladores](https://developers.meta.com/wearables/)
- [Documentación de OpenAI Realtime API](https://developers.openai.com/api/docs/guides/realtime)

---

**PbtMetaGPT** — Proyecto de **PbtServers**, desarrollado con el objetivo de conectar gafas inteligentes con asistentes de inteligencia artificial de forma abierta, práctica y segura.
