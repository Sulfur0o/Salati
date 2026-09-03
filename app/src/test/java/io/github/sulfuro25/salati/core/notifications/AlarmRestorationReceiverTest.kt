package io.github.sulfuro25.salati.core.notifications

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkManager
import androidx.work.WorkInfo
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class AlarmRestorationReceiverTest {

    private lateinit var context: Context
    private lateinit var receiver: AlarmRestorationReceiver

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        WorkManager.getInstance(context).apply {
            cancelAllWork().result.get()
            pruneWork().result.get()
        }
        receiver = AlarmRestorationReceiver()
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }

    private fun getEnqueuedWorkNames(): List<String> {
        val workManager = WorkManager.getInstance(context)
        val infoList = workManager.getWorkInfosForUniqueWork("salati_alarm_refresh").get()
        val activeWorks = infoList.filter { it.state != WorkInfo.State.CANCELLED }
        return if (activeWorks.isNotEmpty()) listOf("salati_alarm_refresh") else emptyList()
    }

    @Test
    fun testBootCompletedEnqueuesWorker() {
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)
        receiver.onReceive(context, intent)
        val works = getEnqueuedWorkNames()
        assertTrue(works.contains("salati_alarm_refresh"))
    }

    @Test
    fun testPackageReplacedEnqueuesWorker() {
        // Clear any previous works by re-initializing or just running directly
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)
        receiver.onReceive(context, intent)
        val works = getEnqueuedWorkNames()
        assertTrue(works.contains("salati_alarm_refresh"))
    }

    @Test
    fun timeSetEnqueuesExactlyOneUniqueCacheFirstRefresh() {
        val intent = Intent(Intent.ACTION_TIME_CHANGED)
        repeat(3) { receiver.onReceive(context, intent) }

        val enqueued = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AlarmWorkScheduler.REFRESH_WORK_NAME).get()
        assertEquals(1, enqueued.distinctBy { it.id }.size)
        assertEquals(AlarmWorkScheduler.REFRESH_WORK_NAME, "salati_alarm_refresh")
    }

    @Test

    fun exactAccessGrantBroadcastEnqueuesOneRefresh() {
        val intent = Intent(AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED)
        repeat(3) { receiver.onReceive(context, intent) }

        val enqueued = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(AlarmWorkScheduler.REFRESH_WORK_NAME).get()
        assertEquals(1, enqueued.distinctBy { it.id }.size)
    }

    @Test
    fun testUnsupportedActionIsIgnored() {
        val workManager = WorkManager.getInstance(context)
        workManager.cancelAllWork().result.get() // Ensure clean state

        val intent = Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED)
        receiver.onReceive(context, intent)

        val works = getEnqueuedWorkNames()
        assertTrue("Unsupported action should not enqueue work", works.isEmpty())
    }
}
