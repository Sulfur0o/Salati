package io.github.sulfuro25.salati.ui.settings

import android.os.Build
import java.util.Locale

enum class DeviceManufacturer {
    XIAOMI,
    SAMSUNG,
    OPPO_REALME_ONEPLUS,
    HUAWEI_HONOR,
    GENERIC
}

object DeviceManufacturerDetector {
    fun detect(
        manufacturer: String = Build.MANUFACTURER,
        brand: String = Build.BRAND
    ): DeviceManufacturer {
        val m = manufacturer.lowercase(Locale.ROOT)
        val b = brand.lowercase(Locale.ROOT)
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") ||
            b.contains("xiaomi") || b.contains("redmi") || b.contains("poco") ->
                DeviceManufacturer.XIAOMI

            m.contains("samsung") || b.contains("samsung") ->
                DeviceManufacturer.SAMSUNG

            m.contains("oppo") || m.contains("realme") || m.contains("oneplus") ||
            b.contains("oppo") || b.contains("realme") || b.contains("oneplus") ->
                DeviceManufacturer.OPPO_REALME_ONEPLUS

            m.contains("huawei") || m.contains("honor") ||
            b.contains("huawei") || b.contains("honor") ->
                DeviceManufacturer.HUAWEI_HONOR

            else ->
                DeviceManufacturer.GENERIC
        }
    }

    fun getDeviceDisplayName(manufacturer: String = Build.MANUFACTURER): String {
        return manufacturer.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
        }
    }
}
