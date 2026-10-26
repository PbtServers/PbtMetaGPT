# Codex instructions — PbtMetaGPT

Project: Android Kotlin app for Ray-Ban Meta (Gen 1 / Gen 2) using Meta Wearables Device Access Toolkit and OpenAI Realtime.

## Canonical upstream references
- Meta: https://github.com/facebook/meta-wearables-dat-android
- Official Meta Android Codex plugin: `codex plugin marketplace add facebook/meta-wearables-dat-android` then `codex plugin add mwdat-android@mwdat-android-marketplace`
- OpenAI Realtime: https://developers.openai.com/api/docs/guides/realtime

## Work priorities
1. Follow the CURRENT Meta sample app for registration, developer mode, permission flow, device/session lifecycle and `mwdat-speech`/camera integration; do not invent APIs.
2. Implement device discovery and user-visible connect/disconnect state with lifecycle cleanup and tests.
3. Implement safe server-issued OpenAI ephemeral credentials. Require server-side authentication and rate limiting; never ship `OPENAI_API_KEY` to Android.
4. Add WebRTC Realtime voice (fallback to phone mic when eyewear microphone route unavailable) and Android audio routing to glasses where supported. Verify on physical hardware.
5. Add explicit camera capture/preview and user-confirmed visual question; do not continually upload video or auto-enable camera.
6. Expose clear permission, connection, recording and privacy status; support interruption/cancellation and no unattended audio capture.
7. Add CI build + tests and publish debug APK as GitHub Actions artifact after passing compilation.

## Important limitations
- This initial repository is a minimal starter, NOT a functional ChatGPT-glasses integration.
- Feature availability depends on device and SDK; some camera/mic paths are experimental or restricted for publishing. Check current SDK docs.
- ChatGPT Plus subscription and OpenAI API billing are separate.
- Do not claim background listening/Hey Meta takeover without verifying actual toolkit capability.
- Never commit tokens, app secrets, or signing credentials.

## Validation
- Run `./gradlew testDebugUnitTest assembleDebug` when Gradle wrapper is available.
- Compare manifest setup with official SDK samples before claiming the DAT integration works.
- Document each tested path (emulator, Mock Device Kit, actual Ray-Ban Meta).
