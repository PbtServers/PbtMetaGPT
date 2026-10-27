package com.pbtservers.pbtmetagpt

import com.meta.wearable.dat.core.types.RegistrationState

object DatRegistrationPolicy {
    fun confirmed(state: RegistrationState) = state == RegistrationState.REGISTERED
    fun callback(action: String?, scheme: String?) = action == "android.intent.action.VIEW" && scheme == "pbtmetagpt"
}
