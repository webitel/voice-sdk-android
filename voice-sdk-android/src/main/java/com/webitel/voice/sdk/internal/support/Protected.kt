package com.webitel.voice.sdk.internal.support


/** A value guarded by a lock: readable and writable from any thread. */
internal class Protected<T>(private var value: T) {
    private val lock = Any()

    fun <R> read(block: (T) -> R): R = synchronized(lock) { block(value) }

    fun <R> write(block: (T) -> R): R = synchronized(lock) { block(value) }

    fun set(newValue: T) = synchronized(lock) { value = newValue }
}
