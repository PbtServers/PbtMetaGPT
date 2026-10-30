package com.pbtservers.pbtmetagpt

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

enum class ConfirmationResult { DISABLED, PLAYED, OMITTED }

/** El aviso es accesorio. Solo una cancelación de la sesión cancela la activación. */
object OptionalConfirmation {
    suspend fun run(enabled: Boolean, budgetMs: Long = 500, play: suspend () -> Unit): ConfirmationResult {
        if (!enabled) return ConfirmationResult.DISABLED
        require(budgetMs > 0)
        return try {
            if (withTimeoutOrNull(budgetMs) { play(); true } == true) ConfirmationResult.PLAYED
            else ConfirmationResult.OMITTED
        } catch (cancel: CancellationException) {
            // Un timeout interno del reproductor no es cancelar la sesión padre.
            currentCoroutineContext().ensureActive()
            ConfirmationResult.OMITTED
        }
        catch (_: Exception) { ConfirmationResult.OMITTED }
    }
}
