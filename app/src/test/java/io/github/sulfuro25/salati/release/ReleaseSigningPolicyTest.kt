package io.github.sulfuro25.salati.release

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseSigningPolicyTest {
    private val buildScript = sourceFile("build.gradle.kts").readText()

    @Test
    fun releaseSigningUsesOnlyExternalProperties() {
        assertTrue(buildScript.contains("SALATI_SIGNING_PROPERTIES"))
        assertTrue(buildScript.contains("""signingConfigs.create("externalRelease")"""))
        assertTrue(buildScript.contains("""signingConfig = signingConfigs.findByName("externalRelease")"""))
        assertFalse(buildScript.contains("""signingConfig = signingConfigs.getByName("debug")"""))
        listOf("storePassword", "keyPassword").forEach { propertyName ->
            assertFalse(buildScript.contains(propertyName + " = " + '"'))
        }
    }

    @Test
    fun requiredSigningHasAnExplicitFailClosedGate() {
        assertTrue(buildScript.contains("salati.requireSigning"))
        assertTrue(buildScript.contains("Release signing is required"))
        assertTrue(buildScript.contains("The external release keystore does not exist"))
    }

    private fun sourceFile(pathWithinApp: String): File {
        return listOf(File("app", pathWithinApp), File(pathWithinApp))
            .firstOrNull(File::isFile)
            ?: error("Missing source file: $pathWithinApp")
    }
}