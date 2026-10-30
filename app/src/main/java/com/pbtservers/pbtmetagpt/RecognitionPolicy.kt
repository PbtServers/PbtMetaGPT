package com.pbtservers.pbtmetagpt

enum class RecognitionEngine { VOSK, ANDROID_LOCAL, GROQ_WHISPER }
enum class RecognitionMode { PRECISION, SPEED }
enum class DictationSource { AUTO, VOSK, ANDROID_LOCAL, GROQ_WHISPER }

/** La autorización de red es independiente de la elección de calidad. */
object RecognitionPolicy {
    fun engine(source: DictationSource, cloudAuthorized: Boolean, androidAvailable: Boolean, continuous: Boolean): RecognitionEngine =
        when {
            source == DictationSource.VOSK -> RecognitionEngine.VOSK
            source == DictationSource.ANDROID_LOCAL -> RecognitionEngine.ANDROID_LOCAL
            source == DictationSource.GROQ_WHISPER || cloudAuthorized -> RecognitionEngine.GROQ_WHISPER
            androidAvailable && !continuous -> RecognitionEngine.ANDROID_LOCAL
            else -> RecognitionEngine.VOSK
        }
    fun whisperModel(mode: RecognitionMode) = if (mode == RecognitionMode.PRECISION) "whisper-large-v3" else "whisper-large-v3-turbo"
    fun silenceMs(mode: RecognitionMode) = if (mode == RecognitionMode.PRECISION) 1700 else 1000
}
