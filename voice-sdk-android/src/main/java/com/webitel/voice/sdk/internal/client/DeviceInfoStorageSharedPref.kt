package com.webitel.voice.sdk.internal.client

import android.content.Context
import android.content.SharedPreferences


internal class DeviceInfoStorageSharedPref(context: Context): DeviceInfoStorage {

    private val preferences: SharedPreferences = context.getSharedPreferences(
        SHARED_PREFS,
        Context.MODE_PRIVATE
    )


    override fun getDeviceId(): String? {
        return preferences.getString(KEY_DEVICE_ID, "")
    }


    override fun saveDeviceId(id: String) {
        preferences.edit().putString(KEY_DEVICE_ID, id).apply()
    }


    override fun clear() {
        preferences.edit().remove(KEY_DEVICE_ID).apply()
    }


    private companion object {
        const val SHARED_PREFS = "webitel_voice_device"
        const val KEY_DEVICE_ID = "device_id"
    }
}
