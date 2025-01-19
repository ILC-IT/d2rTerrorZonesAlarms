package com.example.d2rtz_fgservice

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.sqlite.SQLiteException
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.kittinunf.fuel.Fuel
import com.google.android.material.snackbar.Snackbar
import database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class MainActivity : ComponentActivity() {

    private lateinit var msgTzCurrent: TextView
    private lateinit var msgTzNext: TextView
    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "Notification permission granted")
        } else {
            Log.d("MainActivity", "Notification permission denied")
        }
    }
    private lateinit var listView: ListView
    private lateinit var btnShowList: Button
    private lateinit var btnHideList: Button
    private lateinit var btnClearList: Button
    private lateinit var btnSelectAllList: Button
    private lateinit var btnSetAlarm: Button
    private lateinit var btnSetNotif: Button
    private lateinit var btnInfo: ImageButton
    private lateinit var btnListLayout: LinearLayout
    private lateinit var minuteInput: EditText
    private lateinit var minuteNotifInput: EditText
    private lateinit var btnShowHistory: Button
    private val items = listOf(
        "Throne of Destruction",
        "Tal Rasha's Tombs",
        "Chaos Sanctuary",
        "Flayer Jungle and Dungeon",
        "Stony Tomb - Rocky Waste",
        "Darkwood - Underground Passage",
        "Dry Hills - Halls of the Dead",
        "Black Marsh - The Hole",
        "Arcane Sanctuary",
        "Cold Plains - Cave",
        "Lut Gholein Sewers",
        "Lost City - Valley of Snakes", // - Claw Viper Temple",
        "Ancient's Way - Icy Cellar",
        "Crystalline Passage - Frozen River",
        "Glacial Trail - Drifter Cavern",
        "Outer Steppes - Plains of Despair",
        "City of the Damned - River of Flame",
        "Bloody Foothills - Frigid Highlands", // - Abbadon",
        "Arreat Plateau - Pit of Acheron",
        "Nihlathak's Temple and Halls",
        "Kurast Bazaar - Temples",
        "Jail - Barracks",
        "Cathedral - Catacombs",
        "Forgotten Tower",
        "Pit",
        "Spider Forest - Spider Cavern",
        "Durance of Hate",
        "Great Marsh",
        "Far Oasis",
        "Travincal",
        "Moo Moo Farm",
        "Ancient Tunnels",
        "Stony Field",
        "Tristram",
        "Blood Moor - Den of Evil",
        "Burial Grounds - Crypt - Mausoleum"
    )
    private val selectedItems = mutableSetOf<String>()
    private lateinit var adapter: ItemsAdapter
    private var delayApi: Int = Login.DELAYAPIDEFAULT

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        msgTzCurrent = findViewById(R.id.idTZCurrent)
        msgTzNext = findViewById(R.id.idTZNext)
        listView = findViewById(R.id.listView)
        btnShowList = findViewById(R.id.showListButton)
        btnHideList = findViewById(R.id.closeListButton)
        btnClearList = findViewById(R.id.clearListButton)
        btnSelectAllList = findViewById(R.id.selectAllListButton)
        btnListLayout = findViewById(R.id.buttonsListLayout)
        btnSetAlarm = findViewById(R.id.setAlarmButton)
        btnSetNotif = findViewById(R.id.setNotifButton)
        minuteInput = findViewById(R.id.minuteInput)
        minuteNotifInput = findViewById(R.id.minuteNotifInput)
        btnInfo = findViewById(R.id.infoButton)
        btnShowHistory = findViewById(R.id.btnShowHistory)
        title = "Endless Service"

        loadSelectedItems() // Recuperar selectedItems de SharedPreferences
        adapter = ItemsAdapter(items)
        listView.adapter = adapter
        adapter.notifyDataSetChanged()

        btnShowList.setOnClickListener {
            listView.visibility = View.VISIBLE
            btnListLayout.visibility = View.VISIBLE  // Mostrar los botones de la lista
            btnShowList.visibility = View.GONE
        }
        btnHideList.setOnClickListener {
            listView.visibility = View.GONE
            btnListLayout.visibility = View.GONE  // Ocultar los botones de la lista
            btnShowList.visibility = View.VISIBLE
            sortSelectedItems()
        }
        btnClearList.setOnClickListener {
            clearListSelections()
        }
        btnSelectAllList.setOnClickListener {
            selectAllItemsList()
        }
        // Boton Actualizar
        findViewById<ImageButton>(R.id.idActualizar).let {
            it.setOnClickListener {
                checkApiImmediately()
                Log.d("MainActivity", "Clicked on update button")
            }
        }
        // Boton Start Service
        findViewById<Button>(R.id.btnStartService).let {
            forceRestart = true
            it.setOnClickListener {
                log("START THE FOREGROUND SERVICE ON DEMAND")
                actionOnService(Actions.START)
            }
        }
        // Boton Stop Service
        findViewById<Button>(R.id.btnStopService).let {
            forceRestart = false
            it.setOnClickListener {
                log("STOP THE FOREGROUND SERVICE ON DEMAND")
                actionOnService(Actions.STOP)
            }
        }
        // Boton setAlarm Button Service
        btnSetAlarm.setOnClickListener {
            // Verificar si el servicio está activo
            if (getServiceState(this) == ServiceState.STOPPED) {
                Toast.makeText(this, "El servicio no está activo. Inícialo antes de establecer la alarma.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val inputText = minuteInput.text.toString()
            minuteInput.hint = "$delayApi-59"
            try {
                val selectedMinute = if (inputText.isEmpty()) {
                    minuteInput.setText(String.format(Locale.getDefault(), "%d", Login.MINUTOPARAALARMASDEFAULT))
                    Login.MINUTOPARAALARMASDEFAULT // Valor predeterminado si el EditText está vacío
                } else {
                    inputText.toInt()
                }
                if (selectedMinute in delayApi..59) {
                    // Crear un Intent para enviar al servicio en ejecución y actualizar la alarma
                    val updateIntent = Intent().apply {
                        action = "UPDATE_ALARM_ACTION"
                        putExtra("NEW_MINUTE", selectedMinute)
                    }
                    sendBroadcast(updateIntent)
                    log("Broadcast sent with minuto alarma seleccionado = $selectedMinute")
                } else {
                    Toast.makeText(this, "Input number $delayApi - 59", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Wrong input!", Toast.LENGTH_SHORT).show()
            }
        }
        // Boton setNotif Button Service
        btnSetNotif.setOnClickListener {
            // Verificar si el servicio está activo
            if (getServiceState(this) == ServiceState.STOPPED) {
                Toast.makeText(this, "El servicio no está activo. Inícialo antes de establecer la notificación.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val inputText = minuteNotifInput.text.toString()
            minuteNotifInput.hint = "$delayApi-59"
            try {
                val selectedMinute = if (inputText.isEmpty()) {
                    minuteNotifInput.setText(String.format(Locale.getDefault(), "%d", delayApi))
                    Login.DELAYAPIDEFAULT // Valor predeterminado si el EditText está vacío
                } else {
                    inputText.toInt()
                }
                if (selectedMinute in delayApi..59) {
                    // Crear un Intent para enviar al servicio en ejecución y actualizar la notificacion
                    val updateIntent = Intent().apply {
                        action = "UPDATE_NOTIF_ACTION"
                        putExtra("NEW_NOTIF_MINUTE", selectedMinute)
                    }
                    sendBroadcast(updateIntent)
                    log("Broadcast sent with minuto notificación seleccionado = $selectedMinute")
                } else {
                    Toast.makeText(this, "Input number $delayApi - 59", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Wrong input!", Toast.LENGTH_SHORT).show()
            }
        }

        // Configurar el click listener
        btnInfo.setOnClickListener {
            // Obtener la versión de la app y mostrarla en un Toast
            val version = getAppVersion(this)
            // Actualizo tamaño de DB
            EndlessService.sizeDBBytes = getDBTotalSize(this@MainActivity)
            // Muestro resultado
            val resultado = "App Version: $version\n${EndlessService.alarmaInfo}\n${EndlessService.notifinfo}\nDB: ${EndlessService.sizeDBBytes/1024}KB"
            showSnackbar(findViewById(android.R.id.content), resultado)
        }

        // Ver el historial de zonas guardadas en la database
        btnShowHistory.setOnClickListener {
            showHistoryDialog()
        }

        // Solicitar permiso para mostrar notificaciones en Android 13 y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        // Realiza la llamada a la API al iniciar la aplicación
        checkApiImmediately()

    }

    override fun onResume() {
        super.onResume()
        // Recargar selectedItems de SharedPreferences cuando la actividad se reanuda
        loadSelectedItems()
        adapter.notifyDataSetChanged()
        checkApiImmediately()
        // Recuperar el valor del minuto para alarmas de SharedPreferences
        val preferences = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
        val savedMinute = preferences.getInt("minutoParaAlarmas", Login.MINUTOPARAALARMASDEFAULT) // Valor predeterminado: 30
        val savedNotifMinute = preferences.getInt("minutoParaNotif", Login.DELAYAPIDEFAULT) // Valor predeterminado: 20
        minuteInput.setText(String.format(Locale.getDefault(), "%d", savedMinute))
        minuteNotifInput.setText(String.format(Locale.getDefault(), "%d", savedNotifMinute))
    }

    // Clase para la lista de zonas en el desplegable y su vista
    inner class ItemsAdapter(private val items: List<String>) : BaseAdapter() {

        override fun getCount(): Int {
            return items.size
        }

        override fun getItem(position: Int): String {
            return items[position]
        }

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view: View
            val viewHolder: ViewHolder

            if (convertView == null) {
                view = layoutInflater.inflate(R.layout.item_name, parent, false)
                viewHolder = ViewHolder(view)
                view.tag = viewHolder
            } else {
                view = convertView
                viewHolder = convertView.tag as ViewHolder
            }

            val item = getItem(position)
            viewHolder.textView.text = item

            // Remove any existing listener before setting a new one
            viewHolder.checkBox.setOnCheckedChangeListener(null)
            viewHolder.checkBox.isChecked = selectedItems.contains(item)

            viewHolder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    selectedItems.add(item)
                } else {
                    selectedItems.remove(item)
                }
                sortSelectedItems()
                saveSelectedItems()
            }

            return view
        }

        inner class ViewHolder(view: View) {
            val textView: TextView = view.findViewById(R.id.nameTextView)
            val checkBox: CheckBox = view.findViewById(R.id.checkBox)
        }
    }

    private fun checkApiImmediately() {
        val url = Login.URL
        var tzCurrent: String
        var tzNext: String
        var tzNextHour: Long
        var tzNextAvailableHour: Long

        try {
            Fuel.get(url)
                .appendHeader("x-emu-username", Login.USERNAME)
                .appendHeader("x-emu-token", Login.TOKEN)
                .responseObject(TerrorZone.Deserializer())
                { _, _, result ->
                    val (terrorZone, error) = result

                    if (error != null) {
                        log("[response error] ${error.message}")
                        msgTzCurrent.text = String.format(Locale.getDefault(), "%s", "Error fetching data: ${error.message}")
                        msgTzNext.text =  String.format(Locale.getDefault(), "%s", "Error fetching data: ${error.message}")
                        return@responseObject
                    }

                    if (terrorZone != null) {
                        // Manejar el caso de error desde la respuesta deserializada
                        if (terrorZone.error != null) {
                            log("checkApiImmediately API Error: ${terrorZone.error}")
                            msgTzCurrent.text = String.format(Locale.getDefault(), "%s", "Error: ${terrorZone.error}")
                            msgTzNext.text = ""
                            return@responseObject
                        }

                        // Procesar datos válidos
                        // Validar listas vacías antes de acceder
                        tzCurrent = if (terrorZone.current.isNotEmpty()) {
                            buscarEnMapa(terrorZone.current[0])
                        } else {
                            log("Warning: current zones list is empty")
                            "Empty current zone"
                        }

                        tzNext = if (terrorZone.next.isNotEmpty()) {
                            buscarEnMapa(terrorZone.next[0])
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
//                        log("current: $tzCurrent $tzNext $tzNextHour $tzNextAvailableHour $delayApi")

                        val currentTimeMillis = System.currentTimeMillis() // Hora actual en milisegundos
                        val minuteSplit = nextTerrorTimeUtcAvailable.split(":")[1].toInt() // Cojo los minutos
                        val editTextValue = minuteNotifInput.text.toString() // Obtener el EditText como String
                        val calendar = Calendar.getInstance()
                        calendar.timeInMillis = currentTimeMillis
                        val currentMinutes = calendar.get(Calendar.MINUTE) // Obtener los minutos actuales
//                        val salida = if (currentTimeMillis < tzNextAvailableHour) {
                        val salida = if (currentMinutes < minuteSplit) {
                            if (editTextValue.toInt() < minuteSplit) {
//                                log("currentTimeMillis < tzNextAvailableHour, editTextValue < minuteSplit")
                                "Wait until $nextTerrorTimeUtcAvailable $tzNext"
                            }else{
                                // Actualizar el minuto de la hora actual con el nuevo minuto
                                calendar.set(Calendar.MINUTE, editTextValue.toInt())
                                calendar.set(Calendar.SECOND, 0)
                                // Convertir de vuelta a epoch time
                                val updatedEpoch = calendar.timeInMillis
                                val dateStringUpdateEpoch = simpleDateFormat.format(updatedEpoch)
                                val nextupdatedEpochUtc = String.format("%s", dateStringUpdateEpoch)
//                                log("currentTimeMillis < tzNextAvailableHour, editTextValue > minuteSplit")
                                "Wait until $nextupdatedEpochUtc $tzNext"
                            }
                        } else {
                            if (currentMinutes < editTextValue.toInt()) {
                                // Actualizar el minuto de la hora actual con el nuevo minuto
                                calendar.set(Calendar.MINUTE, editTextValue.toInt())
                                calendar.set(Calendar.SECOND, 0)
                                // Convertir de vuelta a epoch time
                                val updatedEpoch = calendar.timeInMillis
                                val dateStringUpdateEpoch = simpleDateFormat.format(updatedEpoch)
                                val nextupdatedEpochUtc = String.format("%s", dateStringUpdateEpoch)
//                                log("currentTimeMillis >= tzNextAvailableHour, currentMinutes < editTextValue")
                                "Wait until $nextupdatedEpochUtc $tzNext"
                            }else{
//                                log("currentTimeMillis >= tzNextAvailableHour, currentMinutes >= editTextValue")
                                "$nextTerrorTimeUtc $tzNext"
                            }
                        }
                        msgTzCurrent.text = tzCurrent
                        msgTzNext.text = salida
                    }
                }
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
        }
    }

    private fun sortSelectedItems() {
        val sortedSelectedItems = selectedItems.sortedWith(compareBy { items.indexOf(it) })
        selectedItems.clear()
        selectedItems.addAll(sortedSelectedItems)
    }

    private fun clearListSelections() {
        selectedItems.clear()
        adapter.notifyDataSetChanged()
        saveSelectedItems()
    }

    private fun selectAllItemsList() {
        selectedItems.clear()
        selectedItems.addAll(items)
        adapter.notifyDataSetChanged()
        saveSelectedItems()
    }

    private fun saveSelectedItems() {
        val sharedPreferences = getSharedPreferences("EndlessService", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("selectedItems", selectedItems.toSet())
        editor.apply()
    }

    private fun loadSelectedItems() {
        selectedItems.clear()
        val sharedPreferences = getSharedPreferences("EndlessService", Context.MODE_PRIVATE)
        val savedItems = sharedPreferences.getStringSet("selectedItems", emptySet())?.toMutableList()
        if (savedItems != null) {
            selectedItems.addAll(savedItems)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestNotificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun actionOnService(action: Actions) {
        if (getServiceState(this) == ServiceState.STOPPED && action == Actions.STOP) return
        Intent(this, EndlessService::class.java).also {
            it.action = action.name
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                log("Starting the service in >=26 Mode")
                startForegroundService(it)
                return
            }
            log("Starting the service in < 26 Mode")
            startService(it)
        }
    }

    private fun getAppVersion(context: Context): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            // Usar longVersionCode para API >= 28, y versionCode para versiones más antiguas
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toString()
            }
            "Version ${packageInfo.versionName} ($versionCode)"
        } catch (e: Exception) {
            "Unknown Version"
        }
    }

    private fun showHistoryDialog() {
        lifecycleScope.launch {
            val database = AppDatabase.getInstance(this@MainActivity)

            // Obtener el número total de zonas
            val count = database.terrorZoneDao().countZones()

            // Obtener las zonas en un intervalo de tiempo
            val zones = database.terrorZoneDao().getZonesBetween(
                startTime = System.currentTimeMillis() - DBConfig.ZONESBETWEEN,
                endTime = System.currentTimeMillis()
            )

            if (zones.isEmpty()) {
                Toast.makeText(this@MainActivity, "No hay datos en el historial", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // Crear el diálogo con un tema personalizado
            val dialog = Dialog(this@MainActivity, R.style.CustomDialogTheme)
            dialog.setContentView(R.layout.dialog_history)

            val listView = dialog.findViewById<ListView>(R.id.listViewHistory)
            val btnClose = dialog.findViewById<Button>(R.id.btnCloseHistory)
            val btnDelete = dialog.findViewById<Button>(R.id.btnDeleteDB)
            val titleView = dialog.findViewById<TextView>(R.id.dialogTitle)

            // Configurar el título del diálogo
            val titulo = "History: $count TZ in DB"
            titleView.text = titulo

            // Crear el adaptador para la lista
            val adapter = ArrayAdapter(
                this@MainActivity,
                R.layout.list_item_custom, // Aquí usamos nuestro diseño personalizado
                zones.map { "${formatTimestamp(it.timestamp)} - ${it.zoneName}" }
            )

            // Configurar el adaptador en el ListView
            listView.adapter = adapter

            btnClose.setOnClickListener {
                dialog.dismiss()
            }
//            btnClose.setBackgroundColor(Color.DKGRAY) // Fondo gris oscuro del botón
//            btnClose.setTextColor(Color.WHITE) // Texto blanco del botón

            // Botón para borrar la base de datos
            btnDelete.setOnClickListener {
                showDeleteConfirmationDialog(dialog) // Llamar a la función que muestra el diálogo de confirmación
            }

            dialog.show()
        }
    }

    // Mostrar el cuadro de diálogo de confirmación para borrar la base de datos.
    private fun showDeleteConfirmationDialog(parentDialog: Dialog) {
        val confirmationDialog = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Confirmación")
            .setMessage("¿Desea borrar la base de datos?")
            .setPositiveButton("Sí") { dialog, _ ->
                // Borrar la base de datos en un hilo secundario
                lifecycleScope.launch(Dispatchers.IO) {
                    val database = AppDatabase.getInstance(this@MainActivity)

                    try {
                        // Borra todas las tablas de la base de datos
                        database.clearAllTables()
                        log("DB borrada")

                        // Ejecutar el checkpoint para liberar espacio en el archivo WAL
                        val supportSQLiteDb = database.openHelper.writableDatabase
                        val cursor = supportSQLiteDb.query("PRAGMA wal_checkpoint(FULL)", emptyArray())
                        // Procesar resultados del checkpoint
                        if (cursor.moveToFirst()) {
                            val a = cursor.getInt(0) // Páginas en el WAL antes del checkpoint
                            val b = cursor.getInt(1) // Páginas transferidas al archivo principal
                            val c = cursor.getInt(2) // Páginas restantes en el WAL

                            log("Checkpoint ejecutado: a=$a, b=$b, c=$c")
                        }
                        cursor.close()

                        // Actualizo la variable del tamaño de la DB
                        EndlessService.sizeDBBytes = getDBTotalSize(this@MainActivity)
                        if (EndlessService.sizeDBBytes >= 0) {
                            log("Tamaño total de la DB actualizado: ${EndlessService.sizeDBBytes} bytes")
                        } else {
                            log("Error al calcular el tamaño de la DB")
                        }

                        withContext(Dispatchers.Main) {
                            // Mostrar el Toast en el hilo principal
                            Toast.makeText(this@MainActivity, "DB borrada", Toast.LENGTH_SHORT)
                                .show()
                            // Cerrar ambos diálogos en el hilo principal
                            dialog.dismiss()
                            parentDialog.dismiss()
                        }
                    } catch(e: SQLiteException){
                        log("Error al borrar la DB o ejecutar checkpoint: ${e.message}")
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@MainActivity, "Error al ejecutar checkpoint de DB", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("No") { dialog, _ ->
                // Cerrar solo el diálogo de confirmación
                dialog.dismiss()
            }
            .create()

        confirmationDialog.show()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val dateFormat = SimpleDateFormat("dd/MM/yy H':00'", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private fun showSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setTextMaxLines(4) // Permite hasta 4 líneas
            .show()
    }

    /**
    Función que busca un valor en un mapa usando una clave dada.
    @param clave Clave que se usará para buscar en el mapa.
    @return El valor correspondiente a la clave dada, o un mensaje de error si la clave no existe.
     */


}