package io.github.sulfuro25.salati.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStateRefreshControllerTest {
    @Test
    fun activityResumeDetectsExactAccessGrantAndRevocation() {
        var refreshes = 0
        val controller = PermissionStateRefreshController(
            AppPermissionState(exactAlarmAccess = false, notificationPermission = true)
        ) { refreshes++ }

        assertTrue(controller.onActivityResume(AppPermissionState(true, true)))
        assertTrue(controller.onActivityResume(AppPermissionState(false, true)))
        assertEquals(2, refreshes)
    }

    @Test
    fun activityResumeDetectsNotificationGrantAndRevocation() {
        var refreshes = 0
        val controller = PermissionStateRefreshController(
            AppPermissionState(exactAlarmAccess = true, notificationPermission = false)
        ) { refreshes++ }

        assertTrue(controller.onActivityResume(AppPermissionState(true, true)))
        assertTrue(controller.onActivityResume(AppPermissionState(true, false)))
        assertEquals(2, refreshes)
    }

    @Test
    fun repeatedResumesWithoutStateChangeDoNotEnqueueWork() {
        var refreshes = 0
        val state = AppPermissionState(exactAlarmAccess = false, notificationPermission = false)
        val controller = PermissionStateRefreshController(state) { refreshes++ }

        repeat(5) {
            assertFalse(controller.onActivityResume(state))
        }

        assertEquals(0, refreshes)
    }

    @Test
    fun simultaneousPermissionChangesEnqueueOnlyOneRefresh() {
        var refreshes = 0
        val controller = PermissionStateRefreshController(
            AppPermissionState(exactAlarmAccess = false, notificationPermission = false)
        ) { refreshes++ }

        assertTrue(controller.onActivityResume(AppPermissionState(true, true)))
        assertEquals(1, refreshes)
    }

    @Test
    fun notificationPolicyAccessChangeTriggersAlarmMetadataRefresh() {
        var refreshes = 0
        val controller = PermissionStateRefreshController(
            AppPermissionState(
                exactAlarmAccess = true,
                notificationPermission = true,
                notificationPolicyAccess = false
            )
        ) { refreshes++ }

        assertTrue(
            controller.onActivityResume(
                AppPermissionState(
                    exactAlarmAccess = true,
                    notificationPermission = true,
                    notificationPolicyAccess = true
                )
            )
        )
        assertEquals(1, refreshes)
    }
}
