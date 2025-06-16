package com.webitel.voice.sdk


/**
 * Listener interface for receiving updates about a call's state and properties.
 */
interface CallListener {

    /**
     * Called when the call state changes (e.g., from ringing to active or disconnected).
     *
     * @param call the call whose state has changed
     * @param state the new state of the call
     */
    fun onCallStateChanged(call: Call, state: CallState)


    /**
     * Called when the hold status of the call changes.
     *
     * @param call the call whose hold state changed
     * @param isOnHold true if the call is now on hold, false otherwise
     */
    fun onHoldChanged(call: Call, isOnHold: Boolean) {}
}