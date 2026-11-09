package com.webitel.voice.sdk


/**
 * Incoming call data carried by a push notification. Pass it to
 * [VoiceClient.handleIncomingPush].
 *
 * The push is a data-only FCM message with this payload:
 * ```json
 * { "call_id": "<uuid>", "from_number": "...", "from_name": "..." }
 * ```
 *
 * @property callId Server-side call identifier. Becomes the [Call.id] of the incoming call
 *           and is matched against the `X-Webitel-Uuid` header of the SIP INVITE.
 * @property fromNumber Caller phone number.
 * @property fromName Caller display name. Falls back to [fromNumber] when absent.
 */
data class IncomingCallPush(
    val callId: String,
    val fromNumber: String,
    val fromName: String
) {

    companion object {
        private const val KEY_CALL_ID = "call_id"
        private const val KEY_FROM_NUMBER = "from_number"
        private const val KEY_FROM_NAME = "from_name"

        /**
         * Parses the data payload of an FCM message (`RemoteMessage.data`).
         *
         * @return `null` if the payload has no `call_id` — i.e. it isn't an incoming call push.
         */
        @JvmStatic
        fun fromData(data: Map<String, String>): IncomingCallPush? {
            val callId = data[KEY_CALL_ID]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val number = data[KEY_FROM_NUMBER]?.takeIf { it.isNotBlank() } ?: "unknown"
            val name = data[KEY_FROM_NAME]?.takeIf { it.isNotBlank() } ?: number
            return IncomingCallPush(callId, number, name)
        }
    }
}
