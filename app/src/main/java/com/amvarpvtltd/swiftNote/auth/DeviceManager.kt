package com.amvarpvtltd.swiftNote.auth

import android.content.Context

object DeviceManager {

    fun getOrCreateDeviceId(context: Context): String {
        return DeviceIdManager.getOrCreateDeviceId(context)
    }


    fun markFirstLaunchComplete(context: Context) {
        DeviceIdManager.markFirstLaunchComplete(context)
    }
}
