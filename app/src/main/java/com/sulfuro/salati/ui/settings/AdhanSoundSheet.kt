package com.sulfuro.salati.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.sulfuro.salati.R
import com.sulfuro.salati.core.audio.AdhanAudioStore
import com.sulfuro.salati.core.audio.AdhanCatalogFetcher
import com.sulfuro.salati.core.audio.AdhanCatalogResult
import com.sulfuro.salati.core.audio.AdhanDownloadResult
import com.sulfuro.salati.core.audio.AdhanDownloads
import com.sulfuro.salati.core.audio.AdhanOption
import com.sulfuro.salati.core.audio.AdhanPlaybackService
import com.sulfuro.salati.theme.SalatiSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val BYTES_PER_MEGABYTE = 1024.0 * 1024.0

/**
 * Lets the user pick the adhan that plays at prayer time, downloading it on demand.
 *
 * Recordings are several megabytes each and most people settle on one, so nothing is
 * fetched until it is asked for, the size is shown before the tap that spends the data,
 * and anything downloaded can be deleted again from the same row.
 *
 * @param fajr picks which half of the catalogue is on offer. A Fajr recording adds
 *   "as-salatu khayrun min an-nawm", so it is right at dawn and wrong at the other four
 *   prayers - the two lists never overlap, which is what stops someone choosing one for
 *   the wrong slot. The Fajr sheet's first row clears the choice back to "same as the
 *   other prayers" rather than to the device tone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdhanSoundSheet(
    selectedId: String?,
    onSelect: (AdhanOption?) -> Unit,
    onDismiss: () -> Unit,
    fajr: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var catalog by remember { mutableStateOf<AdhanCatalogResult?>(null) }
    var downloadedIds by remember { mutableStateOf(emptySet<String>()) }
    var failureMessage by remember { mutableStateOf<String?>(null) }

    // Held outside this sheet, so closing the picker no longer throws away a transfer
    // that is already several megabytes in.
    val downloadState by AdhanDownloads.state.collectAsState()

    // What is actually playing, read from the service rather than tracked here. A
    // recording ends by itself when it finishes, and a local flag would go on claiming
    // that a finished adhan was still playing.
    val playingId by AdhanPlaybackService.nowPlayingId.collectAsState()
    val previewStartedId = remember { mutableStateOf<String?>(null) }

    // Bumped after a download or a delete, to re-read the directory off the main thread.
    var storeRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(storeRevision) {
        downloadedIds = withContext(Dispatchers.IO) { AdhanAudioStore.downloadedIds(context) }
    }

    // Closing the picker has to silence a preview, or a three minute recitation carries
    // on over whatever the user does next. Only a preview this sheet started is stopped,
    // so closing it can never cut off a real prayer call that began in the meantime.
    DisposableEffect(Unit) {
        onDispose {
            val started = previewStartedId.value
            if (started != null && AdhanPlaybackService.nowPlayingId.value == started) {
                AdhanPlaybackService.stop(context)
            }
        }
    }

    // Resolved here rather than inside the download coroutine: reading resources off
    // LocalContext skips recomposition, so a language change would leave the message
    // behind in the previous language.
    val offlineMessage = stringResource(R.string.settings_adhan_download_offline)
    val failedMessage = stringResource(R.string.settings_adhan_download_failed)
    val tooLargeMessage = stringResource(R.string.settings_adhan_download_too_large)

    LaunchedEffect(Unit) {
        catalog = AdhanCatalogFetcher.fetch()
    }

    // A download that finished while the sheet was closed is picked up here on reopening,
    // so its outcome is never lost with the screen that started it.
    LaunchedEffect(downloadState) {
        when (val current = downloadState) {
            is AdhanDownloads.State.Done -> {
                storeRevision++
                failureMessage = null
                onSelect(current.option)
                AdhanDownloads.acknowledge()
            }
            is AdhanDownloads.State.Failed -> {
                storeRevision++
                failureMessage = when (current.cause) {
                    AdhanDownloadResult.Unreachable -> offlineMessage
                    is AdhanDownloadResult.TooLarge -> tooLargeMessage
                    else -> failedMessage
                }
                AdhanDownloads.acknowledge()
            }
            else -> Unit
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val offered = (catalog as? AdhanCatalogResult.Available)
            ?.options
            ?.filter { it.isFajr == fajr }
            .orEmpty()

        // A recording withdrawn from the catalogue leaves a stored id that matches no row.
        // Without this the sheet would show every option unselected - including the row
        // that clears the choice - while the alarm went on playing the vanished file.
        val selectionIsOffered = selectedId.isNullOrBlank() || offered.any { it.id == selectedId }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm)
        ) {
            item {
                Text(
                    text = stringResource(
                        if (fajr) R.string.settings_adhan_fajr_title
                        else R.string.settings_adhan_sound_title
                    ),
                    style = MaterialTheme.typography.titleMedium
                )

                if (fajr) {
                    // Says why this list is separate, where the user is actually choosing.
                    Text(
                        text = stringResource(R.string.settings_adhan_fajr_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Kept at the top, next to the title. Below the list it would render under
                // thirty-odd rows, where the user who tapped row three would never find it.
                failureMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(vertical = SalatiSpacing.xs)
                    )
                }

                // The row that clears the choice always leads, because it needs no
                // download. For Fajr that means falling back to the general adhan;
                // elsewhere it means the short device notification tone.
                AdhanRow(
                    label = stringResource(
                        if (fajr) R.string.settings_adhan_fajr_same
                        else R.string.settings_adhan_device_tone
                    ),
                    supporting = stringResource(
                        if (fajr) R.string.settings_adhan_fajr_same_description
                        else R.string.settings_adhan_device_tone_description
                    ),
                    selected = !selectionIsOffered || selectedId.isNullOrBlank(),
                    onSelect = {
                        AdhanPlaybackService.stop(context)
                        previewStartedId.value = null
                        onSelect(null)
                    }
                )
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }

            when (catalog) {
                null -> item { StatusText(stringResource(R.string.settings_adhan_loading)) }

                AdhanCatalogResult.Unreachable ->
                    item { StatusText(stringResource(R.string.settings_adhan_unreachable)) }

                AdhanCatalogResult.Malformed ->
                    item { StatusText(stringResource(R.string.settings_adhan_unavailable)) }

                is AdhanCatalogResult.Available -> {
                    if (offered.isEmpty()) {
                        item { StatusText(stringResource(R.string.settings_adhan_empty)) }
                    } else {
                        items(offered, key = AdhanOption::id) { option ->
                            val isDownloaded = option.id in downloadedIds
                            val running = downloadState as? AdhanDownloads.State.Running
                            AdhanRow(
                                label = option.name,
                                supporting = supportingLine(option),
                                selected = selectionIsOffered && selectedId == option.id,
                                enabled = isDownloaded,
                                onSelect = {
                                    AdhanPlaybackService.stop(context)
                                    previewStartedId.value = null
                                    onSelect(option)
                                },
                                trailing = {
                                    when {
                                        running?.id == option.id -> DownloadProgress(
                                            received = running.bytesReceived,
                                            total = running.totalBytes,
                                            onCancel = { AdhanDownloads.cancel() }
                                        )

                                        isDownloaded -> Row {
                                            PreviewButton(
                                                isPreviewing = playingId == option.id,
                                                onToggle = {
                                                    if (playingId == option.id) {
                                                        AdhanPlaybackService.stop(context)
                                                        previewStartedId.value = null
                                                    } else {
                                                        AdhanPlaybackService.stop(context)
                                                        val started = AdhanPlaybackService.start(
                                                            context = context,
                                                            adhanId = option.id,
                                                            prayerLabel = option.name,
                                                            preview = true
                                                        )
                                                        previewStartedId.value =
                                                            if (started) option.id else null
                                                    }
                                                }
                                            )
                                            IconButton(onClick = {
                                                AdhanPlaybackService.stop(context)
                                                previewStartedId.value = null
                                                // Never leave the app pointing at audio
                                                // that is no longer on disk.
                                                if (selectedId == option.id) onSelect(null)
                                                scope.launch {
                                                    withContext(Dispatchers.IO) {
                                                        AdhanAudioStore.delete(context, option.id)
                                                    }
                                                    storeRevision++
                                                }
                                            }) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(
                                                        R.string.settings_adhan_delete,
                                                        option.name
                                                    )
                                                )
                                            }
                                        }

                                        else -> IconButton(onClick = {
                                            failureMessage = null
                                            AdhanDownloads.start(context, option)
                                        }) {
                                            Icon(
                                                Icons.Default.Download,
                                                contentDescription = stringResource(
                                                    R.string.settings_adhan_download,
                                                    option.name
                                                )
                                            )
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * How far the transfer has got, and the way out of it.
 *
 * The ring doubles as the cancel button: there is only ever one download in flight, so a
 * separate control beside it would be one more thing on a crowded row.
 */
@Composable
private fun DownloadProgress(received: Long, total: Long, onCancel: () -> Unit) {
    IconButton(onClick = onCancel) {
        if (total > 0) {
            CircularProgressIndicator(
                progress = { (received.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.settings_adhan_download_cancel),
            modifier = Modifier.size(12.dp)
        )
    }
}

@Composable
private fun PreviewButton(isPreviewing: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (isPreviewing) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = stringResource(
                if (isPreviewing) R.string.adhan_stop else R.string.settings_adhan_preview
            )
        )
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = SalatiSpacing.sm)
    )
}

@Composable
private fun AdhanRow(
    label: String,
    supporting: String,
    selected: Boolean,
    onSelect: () -> Unit,
    enabled: Boolean = true,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onSelect)
            .padding(vertical = SalatiSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SalatiSpacing.sm)
    ) {
        RadioButton(
            selected = selected,
            onClick = onSelect,
            enabled = enabled,
            // The whole row is the control; a second announcement would just repeat it.
            modifier = Modifier.clearAndSetSemantics { }
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

/**
 * The line under the name: who and where, how long, how much data.
 *
 * Length earns its place because the choice is between recitations that differ by minutes
 * and is being made for something that will play unattended at dawn.
 */
@Composable
private fun supportingLine(option: AdhanOption): String {
    return listOfNotNull(
        option.reciter?.takeIf { it.isNotBlank() },
        option.license?.takeIf { it.isNotBlank() },
        durationLabel(option),
        sizeLabel(option)
    ).joinToString(" · ")
}

/** Minutes and seconds, in the display locale's own digits. */
@Composable
private fun durationLabel(option: AdhanOption): String? {
    if (option.seconds <= 0) return null
    val locale = LocalConfiguration.current.locales[0]
    val total = option.seconds.toInt()
    return java.lang.String.format(locale, "%d:%02d", total / 60, total % 60)
}

/** Renders a download size the way a person would say it, or nothing when unknown. */
@Composable
private fun sizeLabel(option: AdhanOption): String? {
    if (option.sizeBytes <= 0) return null
    val megabytes = option.sizeBytes / BYTES_PER_MEGABYTE
    val locale = LocalConfiguration.current.locales[0]
    return stringResource(
        R.string.settings_adhan_size,
        java.lang.String.format(locale, "%.1f", megabytes)
    )
}
