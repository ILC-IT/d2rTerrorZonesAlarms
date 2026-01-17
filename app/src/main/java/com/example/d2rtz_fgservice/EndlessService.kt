package com.example.d2rtz_fgservice

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.github.kittinunf.fuel.Fuel
import database.AppDatabase
import database.TerrorZoneEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EndlessService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted: Boolean = false
    private var tzCurrent: String = ""
    private var tzNext: String = ""
    private var tzNextHour: Long = 0L
    private var tzNextAvailableHour: Long = 0L
    private var delayApi: Int = Login.DELAYAPIDEFAULT
    private var minutoParaAlarmas: Int = Login.MINUTOPARAALARMASDEFAULT
    private var minutoParaNotif: Int = Login.MINUTOPARANOTIF

    companion object {
        // Para que sean accedidas desde MainActivity
        var alarmaInfo: String = "Alarma"
        var notifinfo: String = "Notif"
        var sizeDBBytes: Long = 0
    }

    override fun onBind(intent: Intent): IBinder? {
        log("Some component want to bind with the service")
        // We don't provide binding, so return null
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand executed with startId: $startId")
        if (intent != null) {
            val action = intent.action
            log("using an intent with action $action")
            when (action) {
                Actions.START.name -> startService()
                Actions.STOP.name -> stopService()
                "CHECK_API" -> {
                    startApiCheckLoop() // Ejecuta el bucle de verificación de la API y programa la siguiente alarma
                }
                "CHECK_API_NOTIFICATION" -> {
                    startApiNotificationLoop() // Ejecuta la API y programa la siguiente alarma para actualizar la notificacion de fgService
                }
//                "COLOR_MUTE_UPDATE" -> {
//                    startCheckMuteLoop() // Comprueba el estado del mute al inicio de cada hora
//                }
                else -> log("This should never happen. No action in the received intent")
            }
        } else {
            log(
                "with a null intent. It has been probably restarted by the system."
            )
        }
        // by returning this we make sure the service is restarted if the system kills the service
        return START_STICKY
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onCreate() {
        super.onCreate()
        log("The service has been created".uppercase())
        // Creamos los canales de notificaciones
        createNotificationChannel()
        createNotificationAlarmChannel()
        // Creamos el fgService
        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // Android 10+
            startForeground(
                1,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(1, notification)
        }
        // Registrar el BroadcastReceiver para actualizar las alarmas
        val filter = IntentFilter().apply {
            addAction("UPDATE_ALARM_ACTION")
            addAction("UPDATE_NOTIF_ACTION")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            registerReceiver(updateAlarmReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(updateAlarmReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Desregistrar el BroadcastReceiver
        unregisterReceiver(updateAlarmReceiver)
//        // Borro sharedpreferences
        val pref = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
        val editor = pref.edit()
        editor.clear()
        editor.apply()

        // Enviar un broadcast cuando el servicio se detiene
        val intent = Intent("EndlessService_DETENIDO")
        sendBroadcast(intent)

        log("The service has been destroyed".uppercase())
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_SHORT).show()
    }

    override fun onTaskRemoved(rootIntent: Intent) {
        if (forceRestart) {
            log("onTaskRemoved entra")
            val restartServiceIntent = Intent(applicationContext, EndlessService::class.java).also {
                it.setPackage(packageName)
            }
            val restartServicePendingIntent: PendingIntent = PendingIntent.getService(
                this, 1, restartServiceIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
            )
            applicationContext.getSystemService(Context.ALARM_SERVICE)
            val alarmService: AlarmManager =
                applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmService.set(
                AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + 1000,
                restartServicePendingIntent
            )
        }
    }

    private var serviceJob: Job? = null
    private var notificationJob: Job? = null
//    private var muteJob: Job?= null

    private fun startService() {
        if (isServiceStarted) return
        log("Starting the foreground service task")
        Toast.makeText(this, "Service starting", Toast.LENGTH_SHORT).show()
        isServiceStarted = true
        setServiceState(this, ServiceState.STARTED)

        // we need this lock so our service gets not affected by Doze Mode
        wakeLock =
            (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "EndlessService::lock").apply {
                    acquire()
                }
            }

        // Iniciar la primera llamada a la API y la actualización de la notificación del fgService
        checkApiForNotification()

        // Configurar la primera alarma para despertar al telefono
        // e iniciar el chequeo de la API y las alarmas repetitivas
        setInitialAlarm(minutoParaAlarmas)

        // Consultar el check del boton mute
//        setHourlyUpdateAtExactHourMute(0)
    }

    private fun startApiCheckLoop() {
        if (!isServiceStarted) return

        serviceJob = CoroutineScope(Dispatchers.IO).launch {
            // Checkear la API
            checkApi()
            // Configurar la próxima alarma horaria
            setHourlyAlarm(minutoParaAlarmas)
        }
    }

    private fun startApiNotificationLoop() {
        if (!isServiceStarted) return

        notificationJob = CoroutineScope(Dispatchers.IO).launch {
            // Checkear la API y actualizar la notificación
            checkApiForNotification()
        }
    }

//    private fun startCheckMuteLoop() {
//        if (!isServiceStarted) return
//
//        muteJob = CoroutineScope(Dispatchers.IO).launch {
//            // Checkear el mute y actualizar las variables y el color
//            desactivarMuteAuto()
//            // Consultar el check del boton mute
//            setHourlyUpdateAtExactHourMute(0)
//        }
//    }

    private fun stopService() {
        log("Stopping the foreground service")
        Toast.makeText(this, "Service stopping", Toast.LENGTH_SHORT).show()
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }

            // Cancelar la corutina en ejecución
            serviceJob?.cancel()
            serviceJob = null
            notificationJob?.cancel()
            notificationJob = null
//            muteJob?.cancel()
//            muteJob = null

            // Cancelar todas las alarmas
            cancelInitialAlarm()
            cancelHourlyAlarm()
            cancelHourlyUpdateAtExactHour()
//            cancelHourlyUpdateAtExactHourMute()

            // Eliminar ModoMuteConfig
            val prefs = getSharedPreferences("ModoMuteConfig", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
            // Valores por defecto de los botones del mute
            Login.horaInicio = -1
            Login.horaFin = -1
            Login.rangoActivo = false
            Login.mute = false

            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        } catch (e: Exception) {
            log("Service stopped without being started: ${e.message}")
        }
        isServiceStarted = false
        setServiceState(this, ServiceState.STOPPED)
    }

    private fun setInitialAlarm(minutoParaAlarmas: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "START_API_CHECK_ACTION"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        cancelInitialAlarm()

        // Obtener el tiempo actual
        val calendar = Calendar.getInstance()

        // Establecer los minutos de la alarma
        calendar.set(Calendar.MINUTE, minutoParaAlarmas)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Si el tiempo ya ha pasado, ajustar la alarma para la siguiente hora
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        }

        // Establecer la alarma para el tiempo calculado
        val triggerAtMillis = calendar.timeInMillis
//        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
        val ac = AlarmClockInfo(triggerAtMillis, null)
        alarmManager.setAlarmClock(ac, pendingIntent)

        // Formateo de la hora para el toast
        val simpleDateFormat = SimpleDateFormat("H:mm:ss", Locale.getDefault())
        val formattedTime = simpleDateFormat.format(calendar.time)
        alarmaInfo = "Alarma: $formattedTime"
        Toast.makeText(this, "Alarma: $formattedTime", Toast.LENGTH_SHORT).show()
        log("Initial alarm set for: ${calendar.time}")
    }

    private fun setHourlyAlarm(minutoParaAlarmas: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "HOURLY_ALARM_ACTION"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        cancelHourlyAlarm()

        // Obtener el tiempo actual
        val calendar = Calendar.getInstance()

        // Establecer el minuto y ajustar la hora para la siguiente
        calendar.set(Calendar.MINUTE, minutoParaAlarmas)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.add(Calendar.HOUR_OF_DAY, 1) // Añadir una hora para la próxima alarma

        // Establecer la alarma para el tiempo calculado
        val triggerAtMillis = calendar.timeInMillis

//        val triggerAtMillis = System.currentTimeMillis() + 10 * 60 * 1000 // 10 min
//        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)

        val ac = AlarmClockInfo(triggerAtMillis, null)
        alarmManager.setAlarmClock(ac, pendingIntent)

        // Formateo de la hora para el log
        val simpleDateFormat = SimpleDateFormat("H:mm:ss", Locale.getDefault())
        val formattedTime = simpleDateFormat.format(calendar.time)
        alarmaInfo = "Alarma: $formattedTime"
        log("Hourly alarm set for: ${calendar.time}")
    }

    private fun setHourlyUpdateAtExactHour(minutoParaNotif: Int) {
        // Alarma para actualizar la notificación del fg 1 vez cada hora cada minuto minutoParaNotif

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "HOURLY_EXACT_UPDATE_ACTION"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        cancelHourlyUpdateAtExactHour()

        // Obtener la hora actual y programar la alarma para el próximo "XX:minutoParaNotif"
        val calendar = Calendar.getInstance()

        // Establecer el minuto a minutoParaNotif para la próxima hora en punto
        calendar.set(Calendar.MINUTE, minutoParaNotif)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // Si ya pasamos la hora en punto, ajustar a la siguiente hora
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.HOUR_OF_DAY, 1)
        }

        // Establecer la alarma
        val triggerAtMillis = calendar.timeInMillis
        val ac = AlarmClockInfo(triggerAtMillis, null)
        alarmManager.setAlarmClock(ac, pendingIntent)

        // Formateo de la hora para el toast
        val simpleDateFormat = SimpleDateFormat("H:mm:ss", Locale.getDefault())
        val formattedTime = simpleDateFormat.format(calendar.time)
        notifinfo = "Checkeo notificación: $formattedTime"
        Toast.makeText(this, "Notif: $formattedTime", Toast.LENGTH_SHORT).show()
        log("Exact hourly alarm set for: ${calendar.time}")
    }

//    private fun setHourlyUpdateAtExactHourMute(minutoParaNotif: Int) {
//        // Alarma para actualizar el color del boton mute una vez cada hora, cada minuto 'minutoParaNotif' y a los 30 segundos
//
//        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        val intent = Intent(this, AlarmReceiver::class.java).apply {
//            action = "HOURLY_EXACT_UPDATE_COLOR_MUTE_ACTION"
//        }
//        val pendingIntent = PendingIntent.getBroadcast(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
//
//        cancelHourlyUpdateAtExactHourMute()
//
//        // Obtener la hora actual y programar la alarma para el próximo "XX:minutoParaNotif"
//        val calendar = Calendar.getInstance()
//
//        // Establecer el minuto a minutoParaNotif para la próxima hora en punto
//        calendar.set(Calendar.MINUTE, minutoParaNotif)
//        calendar.set(Calendar.SECOND, 30)
//        calendar.set(Calendar.MILLISECOND, 0)
//        calendar.add(Calendar.HOUR_OF_DAY, 1) // Añadir una hora para la próxima alarma
//
//        // Establecer la alarma
//        val triggerAtMillis = calendar.timeInMillis
//        val ac = AlarmClockInfo(triggerAtMillis, null)
//        alarmManager.setAlarmClock(ac, pendingIntent)
//
//        // Formateo de la hora para el toast
//        val simpleDateFormat = SimpleDateFormat("H:mm:ss", Locale.getDefault())
//        val formattedTime = simpleDateFormat.format(calendar.time)
////        Toast.makeText(this, notifinfo, Toast.LENGTH_SHORT).show()
//        log("Exact hourly alarm set for cheking mute: ${calendar.time}")
//    }

    private fun cancelInitialAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "START_API_CHECK_ACTION"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(pendingIntent)
        log("cancelInitialAlarm")
    }

    private fun cancelHourlyAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "HOURLY_ALARM_ACTION"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(pendingIntent)
        log("cancelHourlyAlarm")
    }

    private fun cancelHourlyUpdateAtExactHour() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java).apply {
            action = "HOURLY_EXACT_UPDATE_ACTION"
        }
        val pendingIntent = PendingIntent.getBroadcast(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(pendingIntent)
        log("cancelHourlyUpdateAtExactHour")
    }

//    private fun cancelHourlyUpdateAtExactHourMute() {
//        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
//        val intent = Intent(this, AlarmReceiver::class.java).apply {
//            action = "HOURLY_EXACT_UPDATE_COLOR_MUTE_ACTION"
//        }
//        val pendingIntent = PendingIntent.getBroadcast(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
//
//        alarmManager.cancel(pendingIntent)
//        log("cancelHourlyUpdateAtExactHourMute")
//    }

    private val updateAlarmReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            log("Broadcast received in updateAlarmReceiver")
            if (intent?.action == "UPDATE_ALARM_ACTION") {
                val newMinute = intent.getIntExtra("NEW_MINUTE", -1)
                if (newMinute in delayApi..59) {
                    updateAlarm(newMinute)
                    log("Minuto para alarma es $newMinute")
                } else {
                    Toast.makeText(context, "Input number $delayApi - 59", Toast.LENGTH_SHORT).show()
                    log("Minuto para alarma no válido.")
                }
            }
            else if (intent?.action == "UPDATE_NOTIF_ACTION") {
                val newMinute = intent.getIntExtra("NEW_NOTIF_MINUTE", -1)
                if (newMinute in delayApi..59) {
                    updateAlarmNotif(newMinute)
                    log("Minuto para notificación es $newMinute")
                } else {
                    Toast.makeText(context, "Input number $delayApi - 59", Toast.LENGTH_SHORT).show()
                    log("Minuto para notificación no válido.")
                }
            } else {
                log("Action not recognized: ${intent?.action}")
            }
        }
    }

    private fun updateAlarm(minutosParaAlarmas: Int) {
        // Actualizar la variable global
        this.minutoParaAlarmas = minutosParaAlarmas

        // Cancelar cualquier alarma horaria existente cuando el usuario cambia minutoParaAlarmas
        cancelInitialAlarm()
        cancelHourlyAlarm()

        // Guardar el nuevo valor en SharedPreferences
        val preferences = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putInt("minutoParaAlarmas", minutoParaAlarmas)
        editor.apply()

        // Configurar la nueva alarma inicial
        setInitialAlarm(minutoParaAlarmas)
    }

    private fun updateAlarmNotif(minutosParaNotif: Int) {
        // Actualizar la variable global
        this.minutoParaNotif = minutosParaNotif

        // Cancelar cualquier alarma horaria existente cuando el usuario cambia minutoParaNotif
        cancelHourlyUpdateAtExactHour()

        // Guardar el nuevo valor en SharedPreferences
        val preferences = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
        val editor = preferences.edit()
        editor.putInt("minutoParaNotif", minutoParaNotif)
        editor.apply()

        // Configurar la nueva alarma inicial
        setHourlyUpdateAtExactHour(minutoParaNotif)
    }

    private fun checkApi() {
        val url = Login.URL

        try {
            Fuel.get(url)
                .appendHeader("x-emu-username", Login.USERNAME)
                .appendHeader("x-emu-token", Login.TOKEN)
                .responseObject(TerrorZone.Deserializer())
                { _, _, result ->
                    val (terrorZone, error) = result

                    if (error != null) {
                        log("[response error] ${error.message}")
                        return@responseObject
                    }

                    if (terrorZone != null) {
                        // Manejo de errores desde la respuesta deserializada
                        if (terrorZone.error != null) {
                            log("checkApi API Error: ${terrorZone.error}")
                            return@responseObject
                        }

                        // Procesar datos válidos
                        // Validar listas vacías antes de acceder
                        tzCurrent = if (terrorZone.current.isNotEmpty()) {
                            if (terrorZone.current.size > 10) {
                                "Winter event is ON."
                            } else {
                                buscarEnMapa(terrorZone.current[0])
                            }
                        } else {
                            log("Warning: current zones list is empty")
                            "Empty current zone"
                        }

                        tzNext = if (terrorZone.next.isNotEmpty()) {
                            if (terrorZone.next.size > 10) {
                                "Terror zones activated"
                            } else {
                                buscarEnMapa(terrorZone.next[0])
                            }
                        } else {
                            log("Warning: next zones list is empty")
                            "Empty next zone"
                        }
                        tzNextHour = terrorZone.nextTerrorTimeUtc.times(1000) // ms
                        // Le sumo 1,05 minutos para dar un margen por si se retrasa alguna alarma
                        tzNextAvailableHour = terrorZone.nextAvailableTimeUtc.times(1000) + 65000 // ms
                        val simpleDateFormat = SimpleDateFormat("H:mm", Locale.getDefault())
                        val dateString = simpleDateFormat.format(tzNextHour)
                        val dateStringAvailable = simpleDateFormat.format(tzNextAvailableHour)
                        val nextTerrorTimeUtc = String.format("%s", dateString)
                        val nextTerrorTimeUtcAvailable = String.format("%s", dateStringAvailable)
                        delayApi = (terrorZone.delay / 60) + 1 // paso a minutos y le sumo 1

                        // Obtener las zonas seleccionadas en MainActivty desde SharedPreferences
                        val sharedPreferences = applicationContext.getSharedPreferences("EndlessService", Context.MODE_PRIVATE)
                        val selectedItems = sharedPreferences.getStringSet("selectedItems", emptySet()) ?.mapNotNull { it } ?: emptyList()

                        // Insertar zona actual y hora en la base de datos
                        // Esto va a meter la primera zona cuando se cumpla el minuto de "ALARMA". Si ya ha pasado meterá la de la siguiente hora
                        CoroutineScope(Dispatchers.IO).launch {
                            val database = AppDatabase.getInstance(this@EndlessService)

                            // Si queremos que se borren las zonas de hace más de 5 días de antigüedad
                            val oldZonesToDelete = System.currentTimeMillis() - DBConfig.OLDZONESTODELETE
                            deleteOldZones(database, oldZonesToDelete)

                            // Insertar nueva zona si no contiene "Empty" y no está vacía
                            insertNewZone(database, tzCurrent)

                            // Calculamos tamaño de la DB
                            sizeDBBytes = getDBTotalSize(this@EndlessService)
                            log("tamaño de la DB en bytes: $sizeDBBytes")
                        }


//                        if (isNameInSelectedItems(selectedItems ?: listOf(), tzNext)) {
//                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
//                                ContextCompat.checkSelfPermission(
//                                    applicationContext,
//                                    android.Manifest.permission.POST_NOTIFICATIONS
//                                ) == PackageManager.PERMISSION_GRANTED
//                            ) {
//                                val currentTimeMillis = System.currentTimeMillis()
//                                if (currentTimeMillis < tzNextAvailableHour) {
//                                    createNotificationAlarm(tzNext, "Wait until $nextTerrorTimeUtcAvailable")
//                                } else {
//                                    createNotificationAlarm(tzNext, nextTerrorTimeUtc)
//                                }
//                                log("Notification with alarm 32 done")
//                            } else {
//                                log("Notification with alarm 32 permission not granted")
//                            }
//                        }


                        // Notification Alarm en funcion de Login.mute
                        if (shouldNotifyZone(tzNext, selectedItems)){
                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                                ContextCompat.checkSelfPermission(
                                    applicationContext,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                val currentTimeMillis = System.currentTimeMillis()
                                if (currentTimeMillis < tzNextAvailableHour) {
                                    createNotificationAlarm(tzNext, "Wait until $nextTerrorTimeUtcAvailable")
                                } else {
                                    createNotificationAlarm(tzNext, nextTerrorTimeUtc)
                                }
                                log("Notification with alarm done")
                            } else {
                                log("Notification with alarm permission not granted")
                            }
                        } else {
                            log("NO Notification with alarm sent due to selectedItems empty / mute settings")
                        }

                    }
                }
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
        }
    }

    private fun checkApiForNotification() {
        val url = Login.URL

        // Compruebo estado del mute
        desactivarMuteAuto()

        try {
            Fuel.get(url)
                .appendHeader("x-emu-username", Login.USERNAME)
                .appendHeader("x-emu-token", Login.TOKEN)
                .responseObject(TerrorZone.Deserializer()) { _, _, result ->
                    val (terrorZone, error) = result

                    if (error != null) {
                        log("[response error] ${error.message}")
                        tzCurrent = "ERROR API / BLOQUEO"
                        tzNext = ""
                        updateForegroundNotification("Wait until XX:$minutoParaNotif")
                        val calendar = Calendar.getInstance()
                        val currentMinutes = calendar.get(Calendar.MINUTE)
                        if (currentMinutes >= minutoParaNotif) {
                            setHourlyUpdateAtExactHour(1)
                            return@responseObject
                        } else {
                            setHourlyUpdateAtExactHour(minutoParaNotif)
                            return@responseObject
                        }
                    }

                    if (terrorZone != null) {
                        // Manejo de errores desde la respuesta deserializada
                        if (terrorZone.error != null) {
                            log("checkApiForNotification API Error: ${terrorZone.error}")
                            tzCurrent = terrorZone.error
                            tzNext = terrorZone.error
                            delayApi = (terrorZone.delay / 60) + 1 // paso a minutos y le sumo 1
                            tzNextHour = obtenerHoraUnixMasUnaHora(minutoParaNotif)
                            val simpleDateFormat = SimpleDateFormat("H:mm", Locale.getDefault())
                            val dateString = simpleDateFormat.format(tzNextHour)
                            val nextTerrorTimeUtc = String.format("%s", dateString)
                            updateForegroundNotification(nextTerrorTimeUtc)
                            val calendar = Calendar.getInstance()
                            val currentMinutes = calendar.get(Calendar.MINUTE)
                            if (currentMinutes >= minutoParaNotif) {
                                setHourlyUpdateAtExactHour(1)
                                return@responseObject
                            } else {
                                setHourlyUpdateAtExactHour(minutoParaNotif)
                                return@responseObject
                            }
                        }

                        // Procesar datos válidos
                        // Validar listas vacías antes de acceder
                        tzCurrent = if (terrorZone.current.isNotEmpty()) {
                            if (terrorZone.current.size > 10) {
                                "Winter event is ON."
                            } else {
                                buscarEnMapa(terrorZone.current[0])
                            }
                        } else {
                            log("Warning: current zones list is empty")
                            "Empty current zone"
                        }

                        tzNext = if (terrorZone.next.isNotEmpty()) {
                            if (terrorZone.next.size > 10) {
                                "Terror zones activated"
                            } else {
                                buscarEnMapa(terrorZone.next[0])
                            }
                        } else {
                            log("Warning: next zones list is empty")
                            "Empty next zone"
                        }
                        tzNextHour = terrorZone.nextTerrorTimeUtc.times(1000) // ms
                        // Le sumo 1,05 minutos para dar un margen por si se retrasa alguna alarma
                        tzNextAvailableHour = terrorZone.nextAvailableTimeUtc.times(1000) + 65000 // ms
                        val simpleDateFormat = SimpleDateFormat("H:mm", Locale.getDefault())
                        val dateString = simpleDateFormat.format(tzNextHour)
                        val dateStringAvailable = simpleDateFormat.format(tzNextAvailableHour)
                        val nextTerrorTimeUtc = String.format("%s", dateString)
                        val nextTerrorTimeUtcAvailable = String.format("%s", dateStringAvailable)
                        delayApi = (terrorZone.delay / 60) + 1 // paso a minutos y le sumo 1

                        // Actualizar la notificación del fgService
                        val currentTimeMillis = System.currentTimeMillis() // Hora actual en milisegundos
                        var minuteSplit = nextTerrorTimeUtcAvailable.split(":")[1].toInt() // Cojo los minutos
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = currentTimeMillis
                        val currentMinutes = calendar.get(Calendar.MINUTE) // Obtener los minutos actuales
                        if (minuteSplit != delayApi){
                            minuteSplit = delayApi
                            // Esto es para igualar el minuto de nextTerrorTimeUtcAvailable con el delay
                            // ya que puede devolver delay = 11 y el minuteSplit = 10
                        }
                        val salida = if (currentMinutes < minuteSplit) {
//                            log("a $currentTimeMillis $tzNextAvailableHour")
                            if (minutoParaNotif < minuteSplit) {
//                                log("currentTimeMillis < tzNextAvailableHour, minutoParaNotif < minuteSplit")
                                "Wait until $nextTerrorTimeUtcAvailable"
                            }else{
                                // Actualizar el minuto de la hora actual con el nuevo minuto
                                calendar.set(Calendar.MINUTE, minutoParaNotif)
                                calendar.set(Calendar.SECOND, 0)
                                // Convertir de vuelta a epoch time
                                val updatedEpoch = calendar.timeInMillis
                                val dateStringUpdateEpoch = simpleDateFormat.format(updatedEpoch)
                                val nextupdatedEpochUtc = String.format("%s", dateStringUpdateEpoch)
//                                log("currentTimeMillis < tzNextAvailableHour, minutoParaNotif > minuteSplit")
                                "Wait until $nextupdatedEpochUtc"
                            }
                        } else {
//                            log("b $currentTimeMillis $tzNextAvailableHour")
                            if (currentMinutes < minutoParaNotif) {
                                // Actualizar el minuto de la hora actual con el nuevo minuto
                                calendar.set(Calendar.MINUTE, minutoParaNotif)
                                calendar.set(Calendar.SECOND, 0)
                                // Convertir de vuelta a epoch time
                                val updatedEpoch = calendar.timeInMillis
                                val dateStringUpdateEpoch = simpleDateFormat.format(updatedEpoch)
                                val nextupdatedEpochUtc = String.format("%s", dateStringUpdateEpoch)
//                                log("currentTimeMillis >= tzNextAvailableHour, currentMinutes < editTextValue")
                                "Wait until $nextupdatedEpochUtc"
                            }else{
//                                log("currentTimeMillis >= tzNextAvailableHour, currentMinutes >= editTextValue")
                                nextTerrorTimeUtc
                            }
                        }

                        // Actualizar foreground notification
                        updateForegroundNotification(salida)
//                        log("salida = $salida")

                        // Actualiza vista de mainActivity para cuando la pantalla esta activa
                        val intent = Intent("UPDATE_TZNEXT")
                        intent.putExtra("new_tzcurrent", tzCurrent)
                        intent.putExtra("new_tznext", "$salida $tzNext")
                        sendBroadcast(intent)

                        // Verificar si debe desactivarse el modo mute automáticamente
//                        setHourlyUpdateAtExactHourMute(0) // A los 30s de cada hora
//                        desactivarMuteAuto()

                        // Configurar la próxima actualización exacta en la siguiente hora en punto + 1 min
//                        log("$currentMinutes, $minutoParaNotif")
                        if (currentMinutes >= minutoParaNotif) {
                            // Esto es para que haga una notif a xx:01
                            setHourlyUpdateAtExactHour(1)
                            return@responseObject
                        }
                        // Configurar la próxima actualización exacta en la siguiente hora en punto + delayApi/minutoParaNotif
                        if(minutoParaNotif > delayApi) {
                            setHourlyUpdateAtExactHour(minutoParaNotif)
                        }else{
                            setHourlyUpdateAtExactHour(delayApi)
                        }
                    } else {
                        log("Error: terrorZone es nulo")
                        updateForegroundNotification("ERROR terrorZone es nulo")
                        setHourlyUpdateAtExactHour(minutoParaNotif)
                        return@responseObject
                    }
                }
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
        }
    }

    private fun obtenerHoraUnixMasUnaHora(delayApi: Int): Long {
        // Obtengo la hora actual y le sumo 1 a la hora y retorno el tiempo en formato Unix

        // Obtener la instancia del calendario con la zona horaria local
        val calendario = Calendar.getInstance()

        // Sumar 1 hora
        calendario.add(Calendar.HOUR_OF_DAY, 1)

        // Establecer los minutos a delayApi, segundos y milisegundos a cero
        calendario.set(Calendar.MINUTE, delayApi)
        calendario.set(Calendar.SECOND, 0)
        calendario.set(Calendar.MILLISECOND, 0)

        // Convertir a tiempo Unix (Epoch time)
        return calendario.timeInMillis // / 1000 // Dividir entre 1000 para obtener segundos
    }

    private fun createNotificationChannel(){
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // Crear el NotificationChannel para API 26+ (Oreo y superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8+
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service notifications channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Endless Service channel"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotificationAlarmChannel(){
        val notificationChannelId = "ZONES CHANNEL"

        // Crear el NotificationChannel para API 26+ (Oreo y superior)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8+
            val channel = NotificationChannel(
                notificationChannelId,
                "Endless Service zones channel",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Zone channel"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
                vibrationPattern = longArrayOf(100, 200, 300, 400, 500, 400, 300, 200, 400)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(
                    android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // Crear el PendingIntent
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        // Usar NotificationCompat.Builder para construir la notificación
        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Foreground service of d2rTZ")
            .setContentText("d2rTZ-fgservices")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Foreground service of d2rTZ")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // Crear la notificación
        return builder.build()
    }

    private fun createNotificationAlarm(next: String, nextTerrorTimeUtc: String) {
        val notificationChannelId = "ZONES CHANNEL"

        // Crear el PendingIntent
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        // Usar NotificationCompat.Builder para construir la notificación
        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle("Zona OP encontrada")
            .setContentText("$nextTerrorTimeUtc $next")
            .setContentIntent(pendingIntent)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setTicker("Zona OP encontrada")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSound(android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setTimeoutAfter(900000) // big time in milliseconds

        // Crear la notificación
        with(NotificationManagerCompat.from(applicationContext)) {
            // notificationId es un ID único para la notificación.
            if (ActivityCompat.checkSelfPermission(
                    applicationContext,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                Log.d("sendNotificationAlarm", "sendNotificationAlarm NO enviada / no permisos")
                return
            }
            val notification = builder.build()
            notification.flags = notification.flags or Notification.FLAG_INSISTENT
            Log.d("sendNotificationAlarm", "sendNotificationAlarm enviada")
            notify(2, notification)
        }
    }

    private fun updateForegroundNotification(nextTerrorTimeUtc: String) {
        val notificationChannelId = "ENDLESS SERVICE CHANNEL"

        // Crear un PendingIntent para abrir la MainActivity al tocar la notificación
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        // Crear la nueva notificación con los valores actuales de tzCurrent y tzNext
        val notification = NotificationCompat.Builder(this, notificationChannelId)
            .setContentTitle(tzCurrent)
            .setContentText("$nextTerrorTimeUtc $tzNext")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOnlyAlertOnce(true)
            .build()

        // Actualizar la notificación del servicio en primer plano
        val mNotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(1, notification)
        Log.d("updateForegroundNotification", "titulo=${tzCurrent}, texto=$nextTerrorTimeUtc $tzNext")
    }

    private suspend fun deleteOldZones(database: AppDatabase, tiempo: Long) {
        database.terrorZoneDao().deleteOldZones(tiempo)
        log("Zonas antiguas eliminadas.")
    }

    private suspend fun insertNewZone(database: AppDatabase, tzCurrent: String) {
        if (!tzCurrent.contains("Empty", ignoreCase = true) && tzCurrent.isNotBlank()) {
            val currentTimeMillis = System.currentTimeMillis()
            val timestampHour = truncateToHour(currentTimeMillis)
            database.terrorZoneDao().insertZone(
                TerrorZoneEntity(
                    timestamp = currentTimeMillis,
                    zoneName = tzCurrent,
                    timestampHour = timestampHour
                )
            )
            log("Zona insertada: $tzCurrent")
        }
    }

    private fun truncateToHour(timestamp: Long): Long {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    private fun shouldNotifyZone(tzNext: String, selectedItems: List<String>): Boolean {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val start = Login.horaInicio
        val end = Login.horaFin

        // Si mute está desactivado, notificar siempre que la zona esté en la lista seleccionada
        if (!Login.mute) {
            return isNameInSelectedItems(selectedItems, tzNext)
        }

        // Verificar si estamos dentro del intervalo silenciado
        val isWithinMutedInterval = if (start < end) {
            // Ej: 5-7, mute entre 5:00 y 6:59
            currentHour in start..<end // currentHour >= start && currentHour < end
        } else {
            // Ej: 22-5, mute entre 22:00 y 4:59
            currentHour >= start || currentHour < end
        }

        return if (isWithinMutedInterval) {
            if (Login.rangoActivo) {
                // Solo notificar si es una zona "buena"
//                isNameInSelectedItems(Login.BEST_ZONES, tzNext)
                isNameInSelectedItems(Login.selectedBestZones, tzNext)
            } else {
                // En rango mute, no notificar
                false
            }
        } else {
            // Fuera del rango de mute, notificar normalmente
            isNameInSelectedItems(selectedItems, tzNext)
        }
    }

    private fun desactivarMuteAuto() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        Log.d("EndlessService", "desactivarMuteAuto - Mute=${Login.mute}, horaInicio=${Login.horaInicio}, horaFin=${Login.horaFin}, bestzones=${Login.rangoActivo}")
        // Si mute activo, comprueba si hay que desactivar el mute cuando el rango de horas se ha cumplido
        if (Login.mute) {
            val start = Login.horaInicio
            val end = Login.horaFin

            val shouldBeMuted = if (start < end) {
                // Rango normal, ejemplo: 11-15, mute de 11:00 hasta 14:59
                currentHour in start until end
            } else {
                // Rango cruzando medianoche, ejemplo: 22-5, mute de 22:00 hasta 4:59
                currentHour >= start || currentHour < end
            }

            if (!shouldBeMuted) {
                Login.mute = false
                log("Modo Mute desactivado automáticamente.")

                // Guardar en SharedPreferences
                val prefs = getSharedPreferences("ModoMuteConfig", Context.MODE_PRIVATE)
                prefs.edit().putBoolean("mute", false).apply()

                // Enviar broadcast para notificar a MainActivity
                val intent = Intent("MUTE_STATE_CHANGED")
                intent.putExtra("muteState", false)
                sendBroadcast(intent)
            }
        }
    }

    private fun isConnectedToInternet(context: Context): Boolean {
//        Funcion que verifica si el movil tiene red wifi y está operativa
//        ConnectivityManager + NetworkCapabilities → Verifica si la red está validada.
//        Verificación real con HttpURLConnection → Se asegura de que efectivamente haya acceso a Internet.
//        USO:
//            val isOnline = isConnectedToInternet(this)
//            if (isOnline) {
//                Log.d("InternetCheck", "¡Sí hay conexión real!")
//                Ejecutar alarmas
//            } else {
//                Log.d("InternetCheck", "Conectado pero sin datos.")
//                Lanzar alarma para indicar que no hay conexion
//            }


        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        val hasInternetCapability = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        // Si tiene capacidad de Internet y Android lo ha validado, probablemente tiene acceso real
        if (hasInternetCapability && isValidated) {
            return true
        }

        // Si no está validado, hacemos una verificación real con HTTP
        return try {
            val url = URL("https://clients3.google.com/generate_204")
            val connection = url.openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", "Android")
            connection.setRequestProperty("Connection", "close")
            connection.connectTimeout = 1500
            connection.connect()
            connection.responseCode == 204
        } catch (e: IOException) {
            false
        }
    }


}