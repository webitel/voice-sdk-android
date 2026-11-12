package com.webitel.voice.sdk.internal.sip

import com.webitel.voice.sdk.CallOptions


internal object CallMetadataEncoder {

    const val HEADER = "X-Webitel-Metadata"


    sealed class Result {
        /** [value] is `null` when there is nothing to send (no non-empty keys). */
        data class Encoded(val value: String?) : Result()

        /** Encoded JSON is larger than [CallOptions.MAX_METADATA_BYTES]. */
        data class TooLarge(val bytes: Int) : Result()
    }


    fun encode(metadata: Map<String, String>): Result {
        val entries = metadata.filterKeys { it.isNotEmpty() }.toSortedMap()
        if (entries.isEmpty()) return Result.Encoded(null)

        val json = entries.entries.joinToString(separator = ",", prefix = "{", postfix = "}") { (k, v) ->
            quote(k) + ":" + quote(v)
        }

        // ASCII only, so length == UTF-8 byte count.
        val bytes = json.length
        return if (bytes <= CallOptions.MAX_METADATA_BYTES) Result.Encoded(json) else Result.TooLarge(bytes)
    }


    /** JSON string literal with only printable ASCII; iterates UTF-16 units, so emoji become a `\uXXXX` pair. */
    private fun quote(s: String): String = buildString(s.length + 2) {
        append('"')
        for (c in s) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                in ' '..'~' -> append(c)
                else -> {
                    val hex = c.code.toString(16)
                    append("\\u")
                    repeat(4 - hex.length) { append('0') }
                    append(hex)
                }
            }
        }
        append('"')
    }
}
