package com.webitel.voice.sdk.internal.client


internal interface DeviceInfoStorage {
    fun getDeviceId(): String?
    fun saveDeviceId(id: String)
    fun clear()
}