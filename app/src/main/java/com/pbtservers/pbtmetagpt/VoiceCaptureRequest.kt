package com.pbtservers.pbtmetagpt
internal data class VoiceCaptureRequest(val language: String, val continuous: Boolean, val ready: () -> Unit,
    val started: () -> Unit, val ended: () -> Unit)
