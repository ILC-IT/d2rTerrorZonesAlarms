package com.example.d2rtz_fgservice

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
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
import androidx.core.content.ContextCompat
import com.github.kittinunf.fuel.Fuel
import java.util.Calendar
import java.text.SimpleDateFormat
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
            val resultado = "App Version: $version\n${EndlessService.alarmaInfo}\n${EndlessService.notifinfo}"
            Toast.makeText(this, resultado, Toast.LENGTH_SHORT).show()
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


    /**
    Función que busca un valor en un mapa usando una clave dada.
    @param clave Clave que se usará para buscar en el mapa.
    @return El valor correspondiente a la clave dada, o un mensaje de error si la clave no existe.
     */
    @Suppress("SpellCheckingInspection")
    private fun buscarEnMapa(clave: String): String {

        val mapa = mapOf(
            "2" to "Blood Moor - Den of Evil",
            "3" to "Cold Plains - Cave",
            "4" to "Stony Field",
            "5" to "Darkwood - Underground Passage",
            "6" to "Black Marsh - The Hole",
            "12" to "Pit",
            "17" to "Burial Grounds - Crypt - Mausoleum",
            "20" to "Forgotten Tower",
            "28" to "Jail - Barracks",
            "33" to "Cathedral - Catacombs",
            "38" to "Tristram",
            "39" to "Moo Moo Farm",
            "41" to "Stony Tomb - Rocky Waste",
            "42" to "Dry Hills - Halls of the Dead",
            "43" to "Far Oasis",
            "44" to "Lost City - Valley of Snakes", // - Claw Viper Temple",
            "47" to "Lut Gholein Sewers",
            "65" to "Ancient Tunnels",
            "66" to "Tal Rasha's Tombs",
            "74" to "Arcane Sanctuary",
            "76" to "Spider Forest - Spider Cavern",
            "77" to "Great Marsh",
            "78" to "Flayer Jungle and Dungeon",
            "80" to "Kurast Bazaar - Temples",
            "83" to "Travincal",
            "100" to "Durance of Hate",
            "104" to "Outer Steppes - Plains of Despair",
            "106" to "City of the Damned - River of Flame",
            "108" to "Chaos Sanctuary",
            "110" to "Bloody Foothills - Frigid Highlands", // - Abbadon",
            "112" to "Arreat Plateau - Pit of Acheron",
            "113" to "Crystalline Passage - Frozen River",
            "115" to "Glacial Trail - Drifter Cavern",
            "118" to "Ancient's Way - Icy Cellar",
            "121" to "Nihlathak's Temple and Halls",
            "128" to "Throne of Destruction",
        )

        return mapa[clave] ?: "Zona no encontrada"
    }

}