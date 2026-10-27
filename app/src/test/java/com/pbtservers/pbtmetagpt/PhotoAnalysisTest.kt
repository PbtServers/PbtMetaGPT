package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class PhotoAnalysisTest {
    @Test fun imagenYCredencialesHabilitanSinSesionRealtime() {
        val status = PhotoAnalysisPolicy.status(true, true, VoiceState())
        assertEquals(PhotoAnalysisStatus.READY, status)
        assertTrue(PhotoAnalysisPolicy.canAnalyze(status))
    }
    @Test fun requisitosYProcesamientoSeDistinguen() {
        assertEquals(PhotoAnalysisStatus.NO_IMAGE, PhotoAnalysisPolicy.status(false, true, VoiceState()))
        assertEquals(PhotoAnalysisStatus.NEEDS_CONFIGURATION, PhotoAnalysisPolicy.status(true, false, VoiceState()))
        for (voice in listOf(VoiceState(connecting = true), VoiceState(active = true, responding = true), VoiceState(active = true, sendingImage = true))) {
            assertEquals(PhotoAnalysisStatus.PROCESSING, PhotoAnalysisPolicy.status(true, true, voice))
        }
        assertEquals(PhotoAnalysisStatus.PAUSED, PhotoAnalysisPolicy.status(true, true, VoiceState(active = true, paused = true)))
    }
    @Test fun unErrorPermiteReintentarSinRepetirFoto() {
        assertTrue(PhotoAnalysisPolicy.canAnalyze(PhotoAnalysisPolicy.status(true, true, VoiceState(), failed = true)))
        assertFalse(PhotoAnalysisPolicy.canAnalyze(PhotoAnalysisPolicy.status(true, false, VoiceState(), failed = true)))
    }
}
