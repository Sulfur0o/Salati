package com.sulfuro.salati.core.permissions

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat

/** Walks the context chain, because LocalContext is often a wrapper rather than the Activity. */
private fun Context.findActivity(): android.app.Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is android.app.Activity) return current
        current = current.baseContext
    }
    return null
}

/** The app's own notification page in system settings, where the switch actually is. */
internal fun openAppNotificationSettings(context: Context) {
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
        add(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        )
    }
    for (intent in intents) {
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

/**
 * An "allow notifications" action that still does something once Android has stopped
 * showing the permission dialog.
 *
 * On Android 13+ the second refusal is final: the system never shows the dialog again and
 * the request returns immediately. A button wired straight to the launcher therefore
 * becomes inert, with nothing on screen to say so - the user taps Allow, nothing happens,
 * and for a prayer app that is the whole product quietly switched off. It is worst during
 * onboarding, where the next tap is "Finish".
 *
 * Telling the two situations apart takes no stored state, only a reading either side of
 * the request. `shouldShowRequestPermissionRationale` is false both before the first ask
 * and after the last one, but it *flips to true* the moment a dialog is genuinely shown
 * and declined. So if it was false going in and is still false coming out, no dialog
 * appeared, and the only way left is system settings. If it flipped, the user was asked
 * properly and said no - which is an answer, and is left alone.
 *
 * @param onStateChanged re-read the permission state; the caller owns that.
 */
@Composable
internal fun rememberNotificationPermissionRequest(onStateChanged: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val couldShowDialog = remember { mutableStateOf(false) }

    fun rationaleIsOffered(): Boolean {
        // There is no runtime notification permission below 33, so there is no rationale
        // to offer and nothing the dialog could have been suppressed for.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val host = activity ?: return false
        return ActivityCompat.shouldShowRequestPermissionRationale(
            host,
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        onStateChanged()
        if (!granted && !couldShowDialog.value && !rationaleIsOffered()) {
            openAppNotificationSettings(context)
        }
    }

    return remember(launcher, activity, context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                couldShowDialog.value = rationaleIsOffered()
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                // No runtime permission to ask for below 33; the switch the user needs is
                // the one in system settings.
                openAppNotificationSettings(context)
            }
        }
    }
}
