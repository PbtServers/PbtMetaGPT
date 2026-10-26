package com.pbtservers.pbtmetagpt

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView

/** Starter UI only. Do not claim device connection until DAT lifecycle is wired. */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 32)
        }
        layout.addView(TextView(this).apply { text = "PbtMetaGPT"; textSize = 28f })
        layout.addView(TextView(this).apply {
            text = "Proyecto inicial: conexión con las gafas y conversación por voz pendientes de implementar."
            textSize = 16f
        })
        setContentView(layout)
    }
}
