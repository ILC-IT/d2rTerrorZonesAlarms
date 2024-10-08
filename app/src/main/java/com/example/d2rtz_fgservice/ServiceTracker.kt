package com.example.d2rtz_fgservice

import android.content.Context
import android.content.SharedPreferences

enum class ServiceState {
    STARTED,
    STOPPED,
}

private const val name = "SERVICE_KEY"
private const val key = "SERVICE_STATE"
var forceRestart: Boolean = true

fun setServiceState(context: Context, state: ServiceState) {
    val sharedPrefs = getPreferences(context)
    sharedPrefs.edit().let {
        it.putString(key, state.name)
        it.apply()
    }
}

fun getServiceState(context: Context): ServiceState? {
    val sharedPrefs = getPreferences(context)
    val value = sharedPrefs.getString(key, ServiceState.STOPPED.name)
    if (value == ServiceState.STOPPED.name) {
        forceRestart = false
    }
    return value?.let { ServiceState.valueOf(it) }
}

private fun getPreferences(context: Context): SharedPreferences {
    return context.getSharedPreferences(name, 0)
}