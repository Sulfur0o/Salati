package io.github.sulfuro25.salati.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import io.github.sulfuro25.salati.R
import io.github.sulfuro25.salati.core.audio.AdhanAudioStore
import io.github.sulfuro25.salati.core.audio.AdhanCatalogFetcher
import io.github.sulfuro25.salati.core.audio.AdhanCatalogResult
import io.github.sulfuro25.salati.core.audio.AdhanDownloadResult
import io.github.sulfuro25.salati.core.audio.AdhanOption
import io.github.sulfuro25.salati.core.audio.AdhanPlaybackService
import io.github.sulfuro25.salati.theme.SalatiSpacing
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
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var failureMessage by remember { mutableStateOf<String?>(null) }

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

    LaunchedEffect(Unit) {
        catalog = AdhanCatalogFetcher.fetch()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SalatiSpacing.md, vertical = SalatiSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(SalatiSpacing.xs)
        ) {
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

            // The row that clears the choice always leads, because it needs no download.
            // For Fajr that means falling back to the general adhan; elsewhere it means
            // the short device notification tone.
            AdhanRow(
                label = stringResource(
                    if (fajr) R.string.settings_adhan_fajr_same
                    else R.string.settings_adhan_device_tone
                ),
                supporting = stringResource(
                    if (fajr) R.string.settings_adhan_fajr_same_description
                    else R.string.settings_adhan_device_tone_description
                ),
                selected = selectedId.isNullOrBlank(),
                onSelect = {
                    AdhanPlaybackService.stop(context)
                    previewStartedId.value = null
                    onSelect(null)
                }
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            when (val current = catalog) {
                null -> StatusText(stringResource(R.string.settings_adhan_loading))

                AdhanCatalogResult.Unreachable ->
                    StatusText(stringResource(R.string.settings_adhan_unreachable))

                AdhanCatalogResult.Malformed ->
                    StatusText(stringResource(R.string.settings_adhan_unavailable))

                is AdhanCatalogResult.Available -> {
                    val offered = current.options.filter { it.isFajr == fajr }
                    if (offered.isEmpty()) {
                        StatusText(stringResource(R.string.settings_adhan_empty))
                    } else {
                        offered.forEach { option ->
                            val isDownloaded = option.id in downloadedIds
                            val sizeText = sizeLabel(option)
                            val supportingText = if (option.reciter.isNullOrBlank()) {
                                sizeText
                            } else {
                                "${option.reciter} · $sizeText"
                            }
                            AdhanRow(
                                label = option.name,
                                supporting = supportingText,
                                selected = selectedId == option.id,
                                enabled = isDownloaded,
                                onSelect = {
                                    AdhanPlaybackService.stop(context)
                                    previewStartedId.value = null
                                    onSelect(option)
                                },
                                trailing = {
                                    when {
                                        downloadingId == option.id -> CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp
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
                                                            context, option.id, option.name
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
                                            downloadingId = option.id
                                            scope.launch {
                                                val result = AdhanAudioStore.download(context, option)
                                                downloadingId = null
                                                storeRevision++
                                                failureMessage = when (result) {
                                                    is AdhanDownloadResult.Success -> {
                                                        onSelect(option)
                                                        null
                                                    }
                                                    AdhanDownloadResult.Unreachable -> offlineMessage
                                                    is AdhanDownloadResult.Rejected,
                                                    is AdhanDownloadResult.NotStored -> failedMessage
                                                }
                                            }
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

            failureMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = SalatiSpacing.xs)
                )
            }
        }
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

/** Renders a download size the way a person would say it, or nothing when unknown. */
@Composable
private fun sizeLabel(option: AdhanOption): String {
    if (option.sizeBytes <= 0) return stringResource(R.string.settings_adhan_not_downloaded)
    val megabytes = option.sizeBytes / BYTES_PER_MEGABYTE
    return stringResource(R.string.settings_adhan_size, "%.1f".format(megabytes))
}
