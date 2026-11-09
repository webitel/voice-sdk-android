package com.webitel.voice.sdk.internal.call

import com.webitel.voice.sdk.CallError
import com.webitel.voice.sdk.CallState
import com.webitel.voice.sdk.internal.support.SdkLogger


// State preconditions for `Call` operations. Each throws CallError.InvalidState.

internal fun CallImpl.ensureCallOngoing() {
    if (state != CallState.Ongoing) {
        fail("Call is not in an ongoing state. Current state: $state")
    }
}


/**
 * Looser than [ensureCallOngoing]: mute and local-video-pause may be requested as soon as
 * the call exists (connecting/ringing). The desired state is recorded even without active
 * media and applied once media activates.
 */
internal fun CallImpl.ensureMediaControlAllowed() {
    val current = state
    if (current == CallState.Idle || current is CallState.Disconnected) {
        fail("Call is not active. Current state: $current")
    }
}


internal fun CallImpl.ensureHoldAllowed() {
    ensureCallOngoing()
    ensureSipIdValid()
    if (isHoldInProgress) fail("Hold/unhold operation already in progress.")
}


internal fun CallImpl.ensureHasVideo() {
    if (!hasVideo) fail("The call has no video. Enable video first via enableVideo().")
}


internal fun CallImpl.ensureSipIdValid() {
    if (sipId < 0) fail("SIP call is not created yet.")
}


private fun fail(message: String): Nothing {
    SdkLogger.warn("CallImpl", message)
    throw CallError.InvalidState(message)
}
