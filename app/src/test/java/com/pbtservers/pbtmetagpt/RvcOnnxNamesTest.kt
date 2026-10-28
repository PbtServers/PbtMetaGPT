package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class RvcOnnxNamesTest {
    @Test fun soloAdmiteLosTresComponentesRvcOnnx() {
        assertEquals("synth", RvcOnnxNames.kind("AuronPlay_RVC.onnx"))
        assertEquals("synth", RvcOnnxNames.kind("SYNTH.onnx"))
        assertEquals("hubert", RvcOnnxNames.kind("content_vec_500.onnx"))
        assertEquals("hubert", RvcOnnxNames.kind("HuBERT.onnx"))
        assertEquals("rmvpe", RvcOnnxNames.kind("rmvpe.onnx"))
        for (name in listOf("voice.pth", "speaker.index", "unknown.onnx", "malicious.sh", "x.onnx")) {
            assertTrue("Debe rechazar " + name, runCatching { RvcOnnxNames.kind(name) }.isFailure)
        }
    }
}
