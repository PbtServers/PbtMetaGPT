package com.pbtservers.pbtmetagpt

/** Los puertos de entrada/salida tienen ids/nombres distintos; dirección solo en RAM si Android la expone. */
object CommunicationSourcePolicy {
    fun bluetoothMatches(communicationConfirmed: Boolean, selectedAddress: String, actualAddress: String): Boolean =
        communicationConfirmed && (selectedAddress.isBlank() || actualAddress.isBlank() || selectedAddress.equals(actualAddress, ignoreCase = true))
}
