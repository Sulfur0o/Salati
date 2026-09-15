package com.sulfuro.salati.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanPlaybackService
import com.sulfuro.salati.theme.SalatiSpacing

/**
 * A stop button for a recitation that is playing, across every screen of the app.
 *
 * The playback notification has carried one from the start, but a notification is not
 * where people look. An adhan can start at full alarm volume in a waiting room or on a
 * bus, and the reflex then is to open the app - so the app has to be able to answer. This
 * sits above whatever tab is showing until the recitation ends, on its own or by this
 * button.
 *
 * A settings audition is left alone: the picker that started it has a stop of its own on
 * the row the user just tapped, and a second one overhead would only be in the way.
 */
@Composable
fun AdhanPlayingBanner(modifier: Modifier = Modifier) {
    val appContext = LocalContext.current.applicationContext
    val nowPlaying by AdhanPlaybackService.nowPlaying.collectAsState()

    AdhanPlayingBanner(
        nowPlaying = nowPlaying,
        onStop = { AdhanPlaybackService.stop(appContext) },
        modifier = modifier
    )
}

/**
 * The same banner, told what is playing rather than reading it off the service, so the
 * states it has to handle - a prayer, an audition, silence - can be put in front of it.
 */
@Composable
fun AdhanPlayingBanner(
    nowPlaying: AdhanPlaybackService.NowPlaying?,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (nowPlaying == null || nowPlaying.isPreview) return

    val playingLabel = stringResource(R.string.adhan_playing)
    val prayerLabel = remember(nowPlaying.prayerLabel) { nowPlaying.prayerLabel.trim() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // The banner is the Scaffold's top bar, which is laid out edge to edge
                // behind the status bar. The colour is meant to run up there; the text
                // and the button are not.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                    )
                )
                .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
        ) {
            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prayerLabel.ifBlank { playingLabel },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (prayerLabel.isNotBlank()) {
                    Text(
                        text = playingLabel,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(stringResource(R.string.adhan_stop))
            }
        }
    }
}
