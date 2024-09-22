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
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.github.kittinunf.fuel.Fuel
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
    private lateinit var btnListLayout: LinearLayout
    private lateinit var minuteInput: EditText
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
        "Lost City - Valley of Snakes - Claw Viper Temple",
        "Ancient's Way - Icy Cellar",
        "Crystalline Passage - Frozen River",
        "Glacial Trail - Drifter Cavern",
        "Outer Steppes - Plains of Despair",
        "City of the Damned - River of Flame",
        "Bloody Foothills - Frigid Highlands - Abbadon",
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
        minuteInput = findViewById(R.id.minuteInput)
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
        findViewById<Button>(R.id.idActualizar).let {
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
            try {
                val selectedMinute = if (inputText.isEmpty()) {
                    minuteInput.setText("30")
                    30 // Valor predeterminado si el EditText está vacío
                } else {
                    inputText.toInt()
                }
                if (selectedMinute in 0..59) {
                    // Crear un Intent para enviar al servicio en ejecución y actualizar la alarma
                    val updateIntent = Intent().apply {
                        action = "UPDATE_ALARM_ACTION"
                        putExtra("NEW_MINUTE", selectedMinute)
                    }
                    sendBroadcast(updateIntent)
                    log("Broadcast sent with minuto alarma seleccionado = $selectedMinute")
                } else {
                    Toast.makeText(this, "Input number 0 - 59", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Wrong input!", Toast.LENGTH_SHORT).show()
            }
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
        val savedMinute = preferences.getInt("minutoParaAlarmas", 30) // Valor predeterminado: 30
        minuteInput.setText(savedMinute.toString())
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
        val url = "https://www.d2emu.com/api/v1/tz"
        var tzCurrent: String
        var tzNext: String
        var tzNextHour: Long

        try {
            Fuel.get(url)
                .appendHeader("x-emu-username", Login.USERNAME)
                .appendHeader("x-emu-token", Login.TOKEN)
                .responseObject(TerrorZone.Deserializer())
                { _, _, result ->
                    val (terrorZone , error) = result
                    if (terrorZone  != null) {
                        tzCurrent = buscarEnMapa(terrorZone .current[0])
                        tzNext = buscarEnMapa(terrorZone .next[0])
                        tzNextHour = terrorZone .nextTerrorTimeUtc.times(1000)
                        val simpleDateFormat = SimpleDateFormat("H:mm", Locale.FRANCE)
                        val dateString = simpleDateFormat.format(tzNextHour)
//                        log("current: $tzCurrent $tzNext $tzNextHour")
                        val nextTerrorTimeUtc = String.format("%s", dateString)
                        val salida = "$nextTerrorTimeUtc $tzNext"
                        msgTzCurrent.text = tzCurrent
                        msgTzNext.text = salida
                    } else {
                        msgTzCurrent.text = "${error?.message}"
                        msgTzNext.text = "${error?.message}"
                        log("[response error] ${error?.message}")
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
            "44" to "Lost City - Valley of Snakes - Claw Viper Temple",
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
            "110" to "Bloody Foothills - Frigid Highlands - Abbadon",
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