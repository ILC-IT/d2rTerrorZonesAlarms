package com.example.d2rtz_fgservice

import android.util.Log

fun log(msg: String) {
    Log.d("ENDLESS-SERVICE", msg)
}

class Login {
    companion object {
        const val URL = "https://www.d2emu.com/api/v1/tz"
        const val USERNAME = ""
        const val TOKEN = ""
        const val MINUTOPARAALARMASDEFAULT = 30 // Minuto predeterminado para alarmas
        const val MINUTOPARANOTIF = 21 // Minuto predeterminado para notificación. NO debe ser menor que DELAYAPIDEFAULT
        const val DELAYAPIDEFAULT = 21 // Minuto predeterminado para que la api retorne resultados
    }
}
