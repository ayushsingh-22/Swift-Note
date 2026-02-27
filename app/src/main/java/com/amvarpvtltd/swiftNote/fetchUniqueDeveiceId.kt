package com.amvarpvtltd.swiftNote

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings.Secure.ANDROID_ID
import android.provider.Settings.Secure.getString

@SuppressLint("HardwareIds")
fun generateUniqueDeviceId(context: Context): kotlin.String {

    return try {

        val androidId = getString(
            context.contentResolver,
            ANDROID_ID
        )
        androidId.ifEmpty { java.util.UUID.randomUUID().toString() }
    } catch (e: Exception) {
        java.util.UUID.randomUUID().toString()
    }
}