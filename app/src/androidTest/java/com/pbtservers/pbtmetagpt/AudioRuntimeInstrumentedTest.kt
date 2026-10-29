package com.pbtservers.pbtmetagpt

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.webrtc.PeerConnectionFactory
import ai.onnxruntime.OrtEnvironment

/** Carga nativa real sin permisos de captura, red, modelos o credenciales. */
class AudioRuntimeInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun runtimesWebrtcYVoskCarganSinAbrirMicrofono() {
        assertNotEquals(PackageManager.PERMISSION_GRANTED, context.checkSelfPermission(Manifest.permission.RECORD_AUDIO))
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        val factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        factory.dispose()
        assertNull((context.applicationContext as PbtApplication).microphone.owner())
    }
    @Test fun motorRvcOnnxYCapaSherpaCompartenBibliotecasNativasSinMicrófono() {
        // Atrapa ABI incompatibles aunque Gradle haya resuelto las bibliotecas
        // duplicadas al empaquetar un solo libonnxruntime.so.
        val ort = OrtEnvironment.getEnvironment()
        assertNotNull(ort)
        System.loadLibrary("sherpa-onnx-jni")
        assertNull((context.applicationContext as PbtApplication).microphone.owner())
    }
    @Test fun servicioPrivadoDeclaraTipoMicrofonoYPermisosSinArrancarPorDefecto() {
        val service = context.packageManager.getServiceInfo(ComponentName(context, ContinuousWakeService::class.java), 0)
        assertFalse(service.exported)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE or ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE, service.foregroundServiceType)
        val requested = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue(Manifest.permission.FOREGROUND_SERVICE in requested)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_MICROPHONE in requested)
        assertTrue(Manifest.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE in requested)
        assertFalse(ContinuousWakeService.running)
    }
}
