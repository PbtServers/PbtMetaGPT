package com.pbtservers.pbtmetagpt

import org.junit.Assert.*
import org.junit.Test

class RvcLocalBudgetTest {
    @Test fun rechazaModelosEIndicesExcesivos() {
        assertTrue(RvcLocalBudget.validModelSize(20_000_000, 0))
        assertFalse(RvcLocalBudget.validModelSize(193L * 1024 * 1024, 0))
        assertFalse(RvcLocalBudget.validModelSize(20_000_000, 65L * 1024 * 1024))
        assertFalse(RvcLocalBudget.validModelSize(-1, 0))
    }

    @Test fun rechazaFramesLargosMulticanalYMalformados() {
        assertTrue(RvcLocalBudget.validFrame(3200, 16000, 1, 100))
        assertFalse(RvcLocalBudget.validFrame(3201, 16000, 1, 100))
        assertFalse(RvcLocalBudget.validFrame(6400, 16000, 2, 100))
        assertFalse(RvcLocalBudget.validFrame(10000, 16000, 1, 100))
        assertFalse(RvcLocalBudget.validFrame(3200, 16000, 1, 200))
    }

    @Test fun indiceOpcionalQuedaInactivoConMemoriaBaja() {
        assertFalse(RvcLocalBudget.useIndex(4096, false, false))
        assertFalse(RvcLocalBudget.useIndex(1024, false, true))
        assertFalse(RvcLocalBudget.useIndex(4096, true, true))
        assertTrue(RvcLocalBudget.useIndex(4096, false, true))
    }
}
