package com.example.d2rtz_fgservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            "START_API_CHECK_ACTION" -> {
                // Iniciar el servicio cuando la alarma se active con la acción "CHECK_API"
                val serviceIntent = Intent(context, EndlessService::class.java).apply {
                    action = "CHECK_API"
                }
                context.startService(serviceIntent)
            }
            "HOURLY_ALARM_ACTION" -> {
                // Iniciar el chequeo de la API y configurar la próxima alarma horaria
                val serviceIntent = Intent(context, EndlessService::class.java).apply {
                    action = "CHECK_API"
                }
                context.startService(serviceIntent)
            }
        }
    }
}