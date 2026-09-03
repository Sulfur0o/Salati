package io.github.sulfuro25.salati.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceManufacturerDetectorTest {

    @Test
    fun detectsXiaomiFamily() {
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturerDetector.detect("Xiaomi", "Xiaomi"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturerDetector.detect("Xiaomi", "POCO"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturerDetector.detect("Redmi", "Redmi"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturerDetector.detect("POCO", "POCO"))
        assertEquals(DeviceManufacturer.XIAOMI, DeviceManufacturerDetector.detect("xiaomi", "2107113SG"))
    }

    @Test
    fun detectsSamsung() {
        assertEquals(DeviceManufacturer.SAMSUNG, DeviceManufacturerDetector.detect("samsung", "samsung"))
        assertEquals(DeviceManufacturer.SAMSUNG, DeviceManufacturerDetector.detect("SAMSUNG", "Galaxy"))
    }

    @Test
    fun detectsOppoRealmeOnePlus() {
        assertEquals(DeviceManufacturer.OPPO_REALME_ONEPLUS, DeviceManufacturerDetector.detect("OPPO", "OPPO"))
        assertEquals(DeviceManufacturer.OPPO_REALME_ONEPLUS, DeviceManufacturerDetector.detect("OnePlus", "OnePlus"))
        assertEquals(DeviceManufacturer.OPPO_REALME_ONEPLUS, DeviceManufacturerDetector.detect("realme", "realme"))
    }

    @Test
    fun detectsHuaweiHonor() {
        assertEquals(DeviceManufacturer.HUAWEI_HONOR, DeviceManufacturerDetector.detect("HUAWEI", "HUAWEI"))
        assertEquals(DeviceManufacturer.HUAWEI_HONOR, DeviceManufacturerDetector.detect("HONOR", "HONOR"))
    }

    @Test
    fun detectsGenericAndPixel() {
        assertEquals(DeviceManufacturer.GENERIC, DeviceManufacturerDetector.detect("Google", "Pixel"))
        assertEquals(DeviceManufacturer.GENERIC, DeviceManufacturerDetector.detect("Sony", "Xperia"))
        assertEquals(DeviceManufacturer.GENERIC, DeviceManufacturerDetector.detect("Motorola", "moto"))
        assertEquals(DeviceManufacturer.GENERIC, DeviceManufacturerDetector.detect("Unknown", "Generic"))
    }

    @Test
    fun capitalizesDisplayName() {
        assertEquals("Xiaomi", DeviceManufacturerDetector.getDeviceDisplayName("xiaomi"))
        assertEquals("Samsung", DeviceManufacturerDetector.getDeviceDisplayName("samsung"))
        assertEquals("Google", DeviceManufacturerDetector.getDeviceDisplayName("google"))
    }
}
