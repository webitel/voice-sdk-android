package com.webitel.voice.sdk.internal.voice


internal class CallException(
    val code: Int,
    override val message: String
): Exception(message) {
    override fun toString(): String {
        return "CallException(code=$code, message=$message)"
    }
}