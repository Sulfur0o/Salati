package io.github.sulfuro25.salati.release

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ReleaseManifestPolicyTest {
    private val androidNamespace = "http://schemas.android.com/apk/res/android"

    @Test
    fun manifestUsesApprovedActionsAndNonExportedReceivers() {
        val document = parseXml(sourceFile("src/main/AndroidManifest.xml"))
        val permissions = document.getElementsByTagName("uses-permission").asElements()
            .map { it.getAttributeNS(androidNamespace, "name") }
        val actions = document.getElementsByTagName("action").asElements()
            .map { it.getAttributeNS(androidNamespace, "name") }

        assertTrue(permissions.contains("android.permission.ACCESS_NOTIFICATION_POLICY"))
        assertTrue(actions.contains("android.intent.action.TIME_SET"))
        assertTrue(actions.contains("android.intent.action.TIMEZONE_CHANGED"))
        assertFalse(actions.contains("android.intent.action.TIME_CHANGED"))
        assertTrue(actions.contains("android.intent.action.BOOT_COMPLETED"))
        assertTrue(actions.contains("android.intent.action.MY_PACKAGE_REPLACED"))
        assertTrue(actions.contains("android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"))

        val receivers = document.getElementsByTagName("receiver").asElements()
        val alarmReceiver = receivers.single {
            it.getAttributeNS(androidNamespace, "name").endsWith(".AlarmReceiver")
        }
        val restorationReceiver = receivers.single {
            it.getAttributeNS(androidNamespace, "name").endsWith(".AlarmRestorationReceiver")
        }
        val mosqueModeReceiver = receivers.single {
            it.getAttributeNS(androidNamespace, "name").endsWith(".MosqueModeReceiver")
        }
        assertEquals("false", alarmReceiver.getAttributeNS(androidNamespace, "exported"))
        assertEquals("false", restorationReceiver.getAttributeNS(androidNamespace, "exported"))
        assertEquals("false", mosqueModeReceiver.getAttributeNS(androidNamespace, "exported"))

        val restorationActions = restorationReceiver.getElementsByTagName("action").asElements()
            .map { it.getAttributeNS(androidNamespace, "name") }
            .toSet()
        assertEquals(
            setOf(
                "android.intent.action.BOOT_COMPLETED",
                "android.intent.action.MY_PACKAGE_REPLACED",
                "android.intent.action.TIME_SET",
                "android.intent.action.TIMEZONE_CHANGED",
                "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
            ),
            restorationActions
        )
    }

    @Test
    fun manifestWiresExplicitNoBackupPolicy() {
        val document = parseXml(sourceFile("src/main/AndroidManifest.xml"))
        val application = document.getElementsByTagName("application").item(0) as Element

        assertEquals("false", application.getAttributeNS(androidNamespace, "allowBackup"))
        assertEquals("false", application.getAttributeNS(androidNamespace, "usesCleartextTraffic"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(androidNamespace, "fullBackupContent"))
        assertEquals(
            "@xml/data_extraction_rules",
            application.getAttributeNS(androidNamespace, "dataExtractionRules")
        )
    }

    @Test
    fun legacyAndModernRulesExcludeEveryAppDataDomain() {
        val expectedDomains = setOf("root", "file", "database", "sharedpref", "external")

        val legacy = parseXml(sourceFile("src/main/res/xml/backup_rules.xml"))
        assertEquals(expectedDomains, excludedDomains(legacy.documentElement))

        val modern = parseXml(sourceFile("src/main/res/xml/data_extraction_rules.xml"))
        val cloud = modern.getElementsByTagName("cloud-backup").item(0) as Element
        val transfer = modern.getElementsByTagName("device-transfer").item(0) as Element
        assertEquals(expectedDomains, excludedDomains(cloud))
        assertEquals(expectedDomains, excludedDomains(transfer))
    }

    @Test
    fun dedicatedNotificationIconIsACompiledVectorResource() {
        val icon = parseXml(sourceFile("src/main/res/drawable/ic_notification.xml"))
        assertEquals("vector", icon.documentElement.tagName)
        assertNotNull(icon.getElementsByTagName("path").item(0))
    }
    @Test
    fun mainActivityCreatesChannelsBeforeSchedulingWithoutAutomaticPermissionPrompts() {
        val source = sourceFile("src/main/java/io/github/sulfuro25/salati/MainActivity.kt").readText()
        val channelCreation = source.indexOf("PrayerNotificationChannels.create")
        val initialScheduling = source.indexOf("triggerInitialScheduling()")

        assertTrue(channelCreation >= 0)
        assertTrue(initialScheduling > channelCreation)
        assertFalse(source.contains("ACTION_REQUEST_SCHEDULE_EXACT_ALARM"))
        assertFalse(source.contains("RequestPermission"))
    }

    @Test
    fun releaseBuildUsesPermanentIdentityNamespaceVersionAndShrinking() {
        val buildScript = sourceFile("build.gradle.kts").readText()

        assertTrue(buildScript.contains("namespace = \"io.github.sulfuro25.salati\""))
        assertTrue(buildScript.contains("applicationId = \"com.sulfuro.salati\""))
        assertTrue(buildScript.contains("versionCode = 3"))
        assertTrue(buildScript.contains("versionName = \"1.1.0\""))
        assertTrue(buildScript.contains("isMinifyEnabled = true"))
        assertTrue(buildScript.contains("isShrinkResources = true"))
    }

    @Test
    fun mergedReleaseManifestUsesPermanentNonDebuggablePolicyAndApprovedExports() {
        val document = parseXml(mergedReleaseManifest())
        val manifest = document.documentElement
        val application = document.getElementsByTagName("application").item(0) as Element

        assertEquals("com.sulfuro.salati", manifest.getAttribute("package"))
        assertEquals("3", manifest.getAttributeNS(androidNamespace, "versionCode"))
        assertEquals("1.1.0", manifest.getAttributeNS(androidNamespace, "versionName"))
        assertFalse(application.getAttributeNS(androidNamespace, "debuggable").toBoolean())
        assertEquals("false", application.getAttributeNS(androidNamespace, "allowBackup"))
        assertEquals("false", application.getAttributeNS(androidNamespace, "usesCleartextTraffic"))
        assertEquals("@xml/backup_rules", application.getAttributeNS(androidNamespace, "fullBackupContent"))
        assertEquals(
            "@xml/data_extraction_rules",
            application.getAttributeNS(androidNamespace, "dataExtractionRules")
        )

        val exported = listOf("activity", "activity-alias", "service", "receiver", "provider")
            .flatMap { tag ->
                document.getElementsByTagName(tag).asElements()
                    .filter { it.getAttributeNS(androidNamespace, "exported") == "true" }
                    .map { "$tag:${it.getAttributeNS(androidNamespace, "name")}" }
            }
            .toSet()
        assertEquals(
            setOf(
                "activity:io.github.sulfuro25.salati.MainActivity",
                "service:androidx.work.impl.background.systemjob.SystemJobService",
                "receiver:androidx.work.impl.diagnostics.DiagnosticsReceiver",
                "receiver:androidx.profileinstaller.ProfileInstallReceiver",
                "receiver:io.github.sulfuro25.salati.widget.SalatiAppWidgetProvider"
            ),
            exported
        )
    }

    private fun excludedDomains(parent: Element): Set<String> {
        val excludes = parent.getElementsByTagName("exclude").asElements()
        assertTrue(excludes.all { it.getAttribute("path") == "." })
        return excludes.map { it.getAttribute("domain") }.toSet()
    }

    private fun parseXml(file: File) = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
    }.newDocumentBuilder().parse(file)

    private fun sourceFile(pathWithinApp: String): File {
        return listOf(File("app", pathWithinApp), File(pathWithinApp))
            .firstOrNull(File::isFile)
            ?: error("Missing source file: $pathWithinApp")
    }

    private fun mergedReleaseManifest(): File {
        val relative = "build/intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml"
        return listOf(File("app", relative), File(relative))
            .firstOrNull(File::isFile)
            ?: error("Missing generated release manifest; processReleaseMainManifest must run before this test")
    }

    private fun org.w3c.dom.NodeList.asElements(): List<Element> {
        return (0 until length).map { item(it) as Element }
    }
}
