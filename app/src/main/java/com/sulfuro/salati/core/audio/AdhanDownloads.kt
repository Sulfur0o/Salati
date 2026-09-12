package com.sulfuro.salati.core.audio

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one adhan download that can be in flight, held outside the picker that starts it.
 *
 * A recording is several megabytes and the sheet it is started from is a bottom sheet: it
 * closes if the user swipes down, taps away, or goes to look at something else. Running
 * the transfer in the sheet's own scope meant all of those silently threw the download
 * away, part-downloaded, with nothing said. So the work lives here instead, in a scope
 * that outlives any screen, and the sheet only observes it - close the picker and come
 * back and the download is still going, with the same progress.
 *
 * Only one at a time. The picker offers one download button per row and the user is
 * choosing a single recording, so a queue would be machinery for a situation that does
 * not arise; asking for a second download replaces the first.
 */
object AdhanDownloads {

    sealed interface State {
        data object Idle : State

        /**
         * @param bytesReceived what has arrived so far.
         * @param totalBytes the manifest's expected size, or 0 when it did not give one -
         *   in which case the UI has bytes to show but no percentage.
         */
        data class Running(
            val id: String,
            val bytesReceived: Long,
            val totalBytes: Long
        ) : State

        /** Finished; the recording is on disk. */
        data class Done(val id: String, val option: AdhanOption) : State

        data class Failed(val id: String, val cause: AdhanDownloadResult) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var job: Job? = null

    /** The id currently transferring, or null. Convenience for the row that draws it. */
    val runningId: String?
        get() = (_state.value as? State.Running)?.id

    fun start(context: Context, option: AdhanOption) {
        val appContext = context.applicationContext
        job?.cancel()
        _state.value = State.Running(option.id, 0L, option.sizeBytes)
        job = scope.launch {
            val result = AdhanAudioStore.download(appContext, option) { received ->
                // Guarded: a cancelled transfer can emit one last chunk on its way out,
                // and it must not reinstate progress for a download that is over.
                val current = _state.value
                if (current is State.Running && current.id == option.id) {
                    _state.value = current.copy(bytesReceived = received)
                }
            }
            if (_state.value.let { it is State.Running && it.id == option.id }) {
                _state.value = when (result) {
                    is AdhanDownloadResult.Success -> State.Done(option.id, option)
                    else -> State.Failed(option.id, result)
                }
            }
        }
    }

    /** Stops the transfer. The part-file is cleaned up by the store's own `finally`. */
    fun cancel() {
        job?.cancel()
        job = null
        _state.value = State.Idle
    }

    /**
     * Clears a terminal state once the screen has acted on it, so reopening the picker
     * does not replay a success or an error the user has already seen.
     */
    fun acknowledge() {
        if (_state.value !is State.Running) _state.value = State.Idle
    }
}
