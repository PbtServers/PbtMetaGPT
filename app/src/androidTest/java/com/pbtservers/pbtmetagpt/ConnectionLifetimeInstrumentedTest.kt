package com.pbtservers.pbtmetagpt

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

/** Comprueba propiedad y observación; no simula una sesión DAT ni certifica Bluetooth. */
class ConnectionLifetimeInstrumentedTest {
    @Test fun elControladorYElEstadoDatSobrevivenALaRecreacionDeLaActividad() {
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PbtApplication
        val controller = app.glasses
        val state = controller.state
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            assertSame(controller, app.glasses)
            assertSame(state, app.glasses.state)
            assertFalse(app.glasses.state.value.capturing)
        }
    }
}
