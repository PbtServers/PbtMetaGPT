// SPDX-License-Identifier: GPL-3.0-or-later
// Adaptador propio de la API pública eSpeak NG. Sin síntesis, red ni callbacks.
#include <jni.h>
#include <string>
#include <mutex>
#include <espeak-ng/speak_lib.h>

static std::mutex guard;
static bool initialized = false;

extern "C" JNIEXPORT void JNICALL
Java_com_pbtservers_pbtmetagpt_SpanishPhonemizer_release(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(guard);
    if (initialized) espeak_Terminate();
    initialized = false;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_pbtservers_pbtmetagpt_SpanishPhonemizer_convert(JNIEnv *env, jobject,
                                                      jstring directory, jstring text) {
    std::lock_guard<std::mutex> lock(guard);
    const char *path = env->GetStringUTFChars(directory, nullptr);
    if (!path) return nullptr;
    if (!initialized) {
        initialized = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, path,
                                        espeakINITIALIZE_DONT_EXIT) > 0;
        if (initialized && espeak_SetVoiceByName("es") != EE_OK) {
            espeak_Terminate();
            initialized = false;
        }
    }
    env->ReleaseStringUTFChars(directory, path);
    if (!initialized) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Fonemizador español no disponible");
        return nullptr;
    }
    const char *input = env->GetStringUTFChars(text, nullptr);
    if (!input) return nullptr;
    const void *cursor = input;
    std::string result;
    while (cursor) {
        const void *before = cursor;
        const char *part = espeak_TextToPhonemes(&cursor, espeakCHARS_UTF8, 2);
        if (part) { result += part; result += " "; }
        if (cursor == before || result.size() > 32768) break;
    }
    env->ReleaseStringUTFChars(text, input);
    return env->NewStringUTF(result.c_str());
}
