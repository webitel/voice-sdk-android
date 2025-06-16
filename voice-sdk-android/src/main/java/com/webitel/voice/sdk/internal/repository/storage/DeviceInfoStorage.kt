package com.webitel.voice.sdk.internal.repository.storage


internal interface DeviceInfoStorage {
    fun getDeviceId(): String?
    fun saveDeviceId(id: String)
    fun clear()
}