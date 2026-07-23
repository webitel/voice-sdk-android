package com.webitel.voice.sdk


/**
 * Listener interface for receiving call events.
 *
 * A single-method (SAM) interface — implement it with a lambda:
 * ```
 * call.addEventListener { event ->
 *     when (event) {
 *         is ConnectionEvent.StateChanged -> { }
 *         is LocalMediaEvent -> { }
 *         is VideoEvent -> { }
 *         is RemoteMediaEvent -> { }
 *     }
 * }
 * ```
 *
 * [ConnectionEvent.StateChanged] is the most important event — make sure to handle it.
 *
 * For Flutter `EventChannel` bridging, [event] serializes directly to a Map — that's
 * the reason this stays one unified callback instead of a method per event type.
 */
fun interface CallEventListener {

    /**
     * Called for every call event.
     *
     * @param event a sealed [CallEvent] describing what changed
     */
    fun onEvent(event: CallEvent)
}
