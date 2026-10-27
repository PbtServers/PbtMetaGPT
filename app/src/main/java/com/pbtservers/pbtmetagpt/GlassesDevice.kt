package com.pbtservers.pbtmetagpt

import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceType
import com.meta.wearable.dat.core.types.LinkState

/** El identificador procede de DAT; el nombre visible nunca se interpreta como modelo. */
data class GlassesDevice(
    val id: DeviceIdentifier,
    val name: String,
    val type: DeviceType,
    val compatibility: DeviceCompatibility,
    val linkState: LinkState,
    val displayCapable: Boolean,
) {
    val model: String get() = when (type) {
        DeviceType.META_GLASSES -> "Meta Glasses · familia DAT"
        else -> type.description
    }
    // Sin lista de marcas: DAT decide compatibilidad también para tipos futuros.
    val canConnect get() = compatibility == DeviceCompatibility.COMPATIBLE && linkState == LinkState.CONNECTED
    val status: String get() = when {
        compatibility == DeviceCompatibility.DEVICE_UPDATE_REQUIRED -> "Actualiza el firmware desde Meta AI"
        compatibility != DeviceCompatibility.COMPATIBLE -> "Compatibilidad no confirmada por DAT"
        linkState == LinkState.CONNECTED -> "Enlace DAT disponible; sesión aún no confirmada"
        else -> "Enlace DAT pendiente; comprueba Meta AI y las gafas"
    }
}
