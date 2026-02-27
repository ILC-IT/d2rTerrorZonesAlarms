package com.example.d2rtz_fgservice

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.database.sqlite.SQLiteException
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.CheckedTextView
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
    private lateinit var btnVentana: Button
    private val items = ZONAS.items
    private val selectedItems = mutableSetOf<String>()
    private lateinit var adapter: ItemsAdapter
    private var delayApi: Int = Login.DELAYAPIDEFAULT
    private var errorDelayApi: String = ""
    private lateinit var receiver: BroadcastReceiver

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
        btnVentana = findViewById(R.id.btnMostrarVentana)
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
                minuteInput.setText(Login.MINUTOPARAALARMASDEFAULT.toString())
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

                    val preferences = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
                    val editor = preferences.edit()
                    editor.putInt("minutoParaAlarmas", selectedMinute)
                    editor.apply()

                    sendBroadcast(updateIntent)
                    log("Broadcast sent with minuto alarma seleccionado = $selectedMinute")
                } else {
                    Toast.makeText(this, "Input number $delayApi - 59", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Wrong input!", Toast.LENGTH_SHORT).show()
            }
            // Ocultar teclado si está abierto
            ocultarTeclado(it, this)
        }

        // Boton setNotif Button Service
        btnSetNotif.setOnClickListener {
            // Verificar si el servicio está activo
            if (getServiceState(this) == ServiceState.STOPPED) {
                minuteNotifInput.setText(Login.MINUTOPARANOTIF.toString())
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

                    val preferences = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
                    val editor = preferences.edit()
                    editor.putInt("minutoParaNotif", selectedMinute)
                    editor.apply()

                    sendBroadcast(updateIntent)
                    log("Broadcast sent with minuto notificación seleccionado = $selectedMinute")
                } else {
                    Toast.makeText(this, "Input number $delayApi - 59", Toast.LENGTH_SHORT).show()
                }
            } catch (e: NumberFormatException) {
                Toast.makeText(this, "Wrong input!", Toast.LENGTH_SHORT).show()
            }
            // Ocultar teclado si está abierto
            ocultarTeclado(it, this)
        }

        btnInfo.setOnClickListener {
            // Obtener la versión de la app y mostrarla en un Toast
            val version = getAppVersion(this)
            // Actualizo tamaño de DB
            EndlessService.sizeDBBytes = getDBTotalSize(this@MainActivity)
            // Miro tiempo de ejecución
            val uptime = EndlessService.getServiceUptime()
            // Fuerzo actualizacion de delayApi por si está en primer plano sin cambiar
            updateDelayApi()
            // Muestro resultado
            val resultado =
                "App: $version\n" +
                "$uptime\n" +
                "API Delay: XX:%02d $errorDelayApi\n".format(delayApi) +
                "${EndlessService.alarmaInfo}\n" +
                "${EndlessService.notifinfo}\n" +
                "DB: ${EndlessService.sizeDBBytes/1024}KB\n" +
                "Mute: ${Login.mute}" +
                    if (Login.mute) ", [${Login.horaInicio} - ${Login.horaFin}), Use Best Zones: ${Login.rangoActivo}" else ""
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

        // Dialog para el mute
        cargarPreferenciasModoMute()

        // Boton mute
        actualizarColorMute()
        btnVentana.setOnClickListener {
            // Verificar si el servicio está activo
            if (getServiceState(this) == ServiceState.STOPPED) {
                Toast.makeText(this, "El servicio no está activo. Inícialo antes de establecer el mute.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showModoMuteDialog()
        }

        // Inicializa el receiver para cambiar el color del mute
        receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "EndlessService_DETENIDO") {
                    val red = ColorStateList.valueOf(Color.parseColor("#B00020"))
                    btnVentana.backgroundTintList = red

                    val preferences = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
                    val savedMinute: Int = if (preferences.contains("minutoParaAlarmas")) {
                        preferences.getInt("minutoParaAlarmas", Login.MINUTOPARAALARMASDEFAULT)
                    } else {
                        Login.MINUTOPARAALARMASDEFAULT
                    }
                    minuteInput.setText(String.format(Locale.getDefault(), "%d", savedMinute))

//                    val savedMinuteNotif: Int = if (preferences.contains("minutoParaNotif")) {
//                        preferences.getInt("minutoParaNotif", Login.MINUTOPARANOTIF)
//                    } else {
//                        Login.MINUTOPARANOTIF
//                    }
                    val editor = preferences.edit()
                    editor.putInt("minutoParaNotif", Login.MINUTOPARANOTIF)
                    editor.apply()
                    minuteNotifInput.setText(String.format(Locale.getDefault(), "%d", Login.MINUTOPARANOTIF))
                }
                if (intent?.action == "MUTE_STATE_CHANGED") {
//                    val newState = intent.getBooleanExtra("muteState", false)
                    val red = ColorStateList.valueOf(Color.parseColor("#B00020"))
                    btnVentana.backgroundTintList = red
                }
                if (intent?.action == "UPDATE_TZNEXT") {
                    val newValueCurrent = intent.getStringExtra("new_tzcurrent") ?: return
                    val newValueNext = intent.getStringExtra("new_tznext") ?: return
                    runOnUiThread {
                        msgTzCurrent.text = newValueCurrent
                        msgTzNext.text = newValueNext
                    }
                }
            }
        }

    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        // Recargar selectedItems de SharedPreferences cuando la actividad se reanuda
        loadSelectedItems()
        adapter.notifyDataSetChanged()
        checkApiImmediately()

        // Recuperar el valor del minuto para alarmas de SharedPreferences
        val preferences = getSharedPreferences("AlarmPreferences", Context.MODE_PRIVATE)
        val savedMinute = preferences.getInt("minutoParaAlarmas", Login.MINUTOPARAALARMASDEFAULT) // Valor predeterminado: 40
        val savedNotifMinute = preferences.getInt("minutoParaNotif", Login.DELAYAPIDEFAULT) // Valor predeterminado: 11
        minuteInput.setText(String.format(Locale.getDefault(), "%d", savedMinute))
        minuteNotifInput.setText(String.format(Locale.getDefault(), "%d", savedNotifMinute))

        // Registrar broadcast para cambiar el color del boton mute y actualizar vista con pantalla activa
        actualizarColorMute()
        val filter = IntentFilter().apply {
            addAction("EndlessService_DETENIDO")
            addAction("MUTE_STATE_CHANGED")
            addAction("UPDATE_TZNEXT")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(receiver)
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

    private fun showModoMuteDialog() {
        val dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_modo_mute)

        val editHoraInicio = dialog.findViewById<EditText>(R.id.editHoraInicio)
        val editHoraFin = dialog.findViewById<EditText>(R.id.editHoraFin)
        val checkBoxRango = dialog.findViewById<CheckBox>(R.id.checkBoxRango)
        val checkBoxMute = dialog.findViewById<CheckBox>(R.id.checkBoxMute)
        val btnAccept = dialog.findViewById<Button>(R.id.btnAccept)
        val btnCancel = dialog.findViewById<Button>(R.id.btnCancel)
        val btnSelectBestZones = dialog.findViewById<Button>(R.id.btnSelectBestZones)

        // Mostrar valores actuales o por defecto
        if (Login.horaInicio == -1 && Login.horaFin == -1) {
            editHoraInicio.setText("0")
            editHoraFin.setText("23")
        } else {
            editHoraInicio.setText(Login.horaInicio.toString())
            editHoraFin.setText(Login.horaFin.toString())
        }

        checkBoxRango.isChecked = Login.rangoActivo
        checkBoxMute.isChecked = Login.mute

        // Copia temporal de las selecciones actuales
        val tempSelectedZones: MutableList<String> =
            if (Login.rangoActivo)
                Login.selectedBestZones.toMutableList()
            else
                mutableListOf()

        // Mostrar u ocultar según el estado inicial del checkbox
        btnSelectBestZones.visibility = if (checkBoxRango.isChecked) View.VISIBLE else View.GONE
        checkBoxRango.setOnCheckedChangeListener { _, isChecked ->
            btnSelectBestZones.visibility = if (isChecked) View.VISIBLE else View.GONE

//            if (!isChecked) {
//                // Reiniciar seleccion best zones si usar best zones está desmarcado
//                Login.selectedBestZones = emptyList()
//                Log.d("BestZones", "Best Zones reiniciadas (checkbox desmarcado)")
//            }
        }

        // Abrir selector best zones
        btnSelectBestZones.setOnClickListener {

            // Si se vuelve a abrir el diálogo de seleccionar zonas, las selecciones previas apareceran marcadas
            val selectedFlags = BooleanArray(Login.BEST_ZONES.size) { index ->
                tempSelectedZones.contains(Login.BEST_ZONES[index])
            }

            val adapter = object : ArrayAdapter<String>(
                this,
                R.layout.item_best_zone,
                android.R.id.text1,
                Login.BEST_ZONES
            ) {
                override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                    val view = super.getView(position, convertView, parent)
                    val checkedTextView = view.findViewById<CheckedTextView>(android.R.id.text1)
                    checkedTextView.isChecked = selectedFlags[position]
                    return view
                }
            }

            val builder = AlertDialog.Builder(this, R.style.MyAlertDialogTheme)
                .setTitle(getString(R.string.select_best_zones))
//                .setPositiveButton(getString(R.string.aceptar)) { _, _ ->
//                    Login.selectedBestZones =
//                        Login.BEST_ZONES.filterIndexed { index, _ ->
//                            selectedFlags[index]
//                        }
//                    Log.d("BestZones", "Zonas seleccionadas (Aceptar): ${Login.selectedBestZones}")
//                }
                .setPositiveButton(getString(R.string.aceptar)) { _, _ ->
                    tempSelectedZones.clear()
                    Login.BEST_ZONES.forEachIndexed { index, zone ->
                        if (selectedFlags[index]) {
                            tempSelectedZones.add(zone)
                        }
                    }
                }
                .setNegativeButton(getString(R.string.cancelar), null)
                .setNeutralButton(getString(R.string.seleccionar_todas), null)
                .setAdapter(adapter, null)

            val dialogZones = builder.create()
            dialogZones.show()

            // Boton seleccionar/deseleccionar todas las best zones
            val btnSelectAll = dialogZones.getButton(AlertDialog.BUTTON_NEUTRAL)

            fun updateNeutralText() {
                btnSelectAll.text =
                    if (selectedFlags.all { it })
                        getString(R.string.deseleccionar_todas)
                    else
                        getString(R.string.seleccionar_todas)
            }

            updateNeutralText()

            btnSelectAll.setOnClickListener {
                val selectAll = selectedFlags.any { !it }

                for (i in selectedFlags.indices) {
                    selectedFlags[i] = selectAll
                }

                // Refrescar lista
                for (i in 0 until dialogZones.listView.childCount) {
                    val item = dialogZones.listView.getChildAt(i)
                        ?.findViewById<CheckedTextView>(android.R.id.text1)
                    item?.isChecked = selectAll
                }

                updateNeutralText()
            }

            // Hacer más alto el diálogo de best zones
            dialogZones.window?.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            val displayMetrics = resources.displayMetrics
            val maxHeight = (displayMetrics.heightPixels * 0.8).toInt() // Cambiar 0.8 para variar altura
            dialogZones.listView.layoutParams.height = maxHeight

//            Log.d("BestZones", selectedFlags.contentToString())

            // Manejar clics en cada item (que no cierre el diálogo)
            dialogZones.listView.setOnItemClickListener { _, view, position, _ ->
                selectedFlags[position] = !selectedFlags[position]
                val checkedTextView = view.findViewById<CheckedTextView>(android.R.id.text1)
                checkedTextView.isChecked = selectedFlags[position]
                updateNeutralText()
//                Log.d("BestZones","Zona '${Login.BEST_ZONES[position]}' -> ${if (selectedFlags[position]) "SELECCIONADA" else "DESELECCIONADA"}")
            }

            dialogZones.getButton(AlertDialog.BUTTON_POSITIVE)
                .setTextColor(Color.WHITE)

            dialogZones.getButton(AlertDialog.BUTTON_NEGATIVE)
                .setTextColor(Color.WHITE)

            dialogZones.getButton(AlertDialog.BUTTON_NEUTRAL)
                .setTextColor(Color.WHITE)
        }

        btnAccept.setOnClickListener {
            val inicio = editHoraInicio.text.toString().toIntOrNull()
            val fin = editHoraFin.text.toString().toIntOrNull()
            val rangoActivo = checkBoxRango.isChecked
            val mute = checkBoxMute.isChecked

            if ((inicio == null || fin == null) && (mute)){
                Toast.makeText(this, "Debes introducir ambas horas", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if ((inicio !in 0..23 || fin !in 0..23)){
                Toast.makeText(this, "Las horas deben estar entre 0 y 23", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

//            if (fin <= inicio) {
//                Toast.makeText(this, "Hora final debe ser mayor que hora inicio", Toast.LENGTH_SHORT).show()
//                return@setOnClickListener
//            }

            if (rangoActivo && tempSelectedZones.isEmpty()) {
                Toast.makeText(this, "Selecciona al menos una Best Zone", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Guardar los valores
            Login.horaInicio = inicio ?: 0
            Login.horaFin = fin ?: 0
            Login.rangoActivo = rangoActivo
            Login.mute = mute
            Login.selectedBestZones =
                if (rangoActivo)
                    tempSelectedZones.toList()
                else
                    emptyList()

            if (!rangoActivo) {
                Log.d("BestZones", "Best Zones reiniciadas (Aceptar Mute)")
            }

            Log.d("BestZonesssss", "Zonas activas: ${Login.selectedBestZones}")

            // Cambiar de color el boton M según mute
            actualizarColorMute()

            guardarPreferenciasModoMute(
                Login.horaInicio,
                Login.horaFin,
                Login.rangoActivo,
                Login.mute,
                Login.selectedBestZones
            )

            Toast.makeText(this, "Configuración mute guardada", Toast.LENGTH_SHORT).show()
            dialog.dismiss()
        }

        // Botón cancelar, cerrar sin guardar
        btnCancel.setOnClickListener {
//            Login.selectedBestZones = tempSelectedZones.toList()
            dialog.dismiss()
        }

        dialog.show()

        // Forzar el ancho del diálogo al ancho completo de pantalla
        val window = dialog.window
        window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    }

    private fun guardarPreferenciasModoMute(
        horaInicio: Int,
        horaFin: Int,
        rangoActivo: Boolean,
        mute: Boolean,
        selectedBestZones: List<String>
    ) {
        val prefs = getSharedPreferences("ModoMuteConfig", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putInt("horaInicio", horaInicio)
            putInt("horaFin", horaFin)
            putBoolean("rangoActivo", rangoActivo)
            putBoolean("mute", mute)
            putStringSet("selectedBestZones", selectedBestZones.toSet())
            apply()
        }
    }

    private fun cargarPreferenciasModoMute() {
        val prefs = getSharedPreferences("ModoMuteConfig", Context.MODE_PRIVATE)
        Login.horaInicio = prefs.getInt("horaInicio", -1)
        Login.horaFin = prefs.getInt("horaFin", -1)
        Login.rangoActivo = prefs.getBoolean("rangoActivo", false)
        Login.mute = prefs.getBoolean("mute", false)
        Login.selectedBestZones = prefs.getStringSet("selectedBestZones", emptySet())!!.toList()
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
                            if (terrorZone.current.size > Login.WINTERMINZONES) {
                                "Winter event is ON."
                            } else {
                                buscarEnMapa(terrorZone.current[0])
                            }
                        } else {
                            log("Warning: current zones list is empty")
                            "Empty current zone"
                        }

                        tzNext = if (terrorZone.next.isNotEmpty()) {
                            if (terrorZone.next.size > Login.WINTERMINZONES) {
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
                        val hint = getString(R.string.minuto_0_59, delayApi)
                        findViewById<EditText>(R.id.minuteInput).hint = hint
                        findViewById<EditText>(R.id.minuteNotifInput).hint = hint
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8+
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
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) { // Android 9+
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
                zones.map { "${formatTimestamp(it.timestampHour)} - ${it.zoneName}" }
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
        val dateFormat = SimpleDateFormat("dd/MM/yy H:mm", Locale.getDefault())
        return dateFormat.format(Date(timestamp))
    }

    private fun showSnackbar(view: View, message: String) {
        Snackbar.make(view, message, Snackbar.LENGTH_LONG)
            .setTextMaxLines(7) // Permite hasta 7 líneas
            .show()
    }

    private fun ocultarTeclado(view: View, context: Context) {
        val inputMethodManager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun actualizarColorMute() {
        // Cambia el color del boton mute según el estado de la variable
        log("actualizarColorMute")
        if (Login.mute){
            val green = ColorStateList.valueOf(Color.parseColor("#4CAF50"))
            btnVentana.backgroundTintList = green
        }
        else{
            val red = ColorStateList.valueOf(Color.parseColor("#B00020"))
            btnVentana.backgroundTintList = red
        }
    }

    private fun updateDelayApi(){
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
                        errorDelayApi = "Error API"
                        return@responseObject
                    }

                    if (terrorZone != null) {
                        // Manejar el caso de error desde la respuesta deserializada
                        if (terrorZone.error != null) {
                            log("checkApiImmediately API Error: ${terrorZone.error}")
                            errorDelayApi = "Error API"
                            return@responseObject
                        }

                        // Procesar datos válidos
                        delayApi = (terrorZone.delay / 60) + 1 // paso a minutos y le sumo 1
                        errorDelayApi = ""
                        val hint = getString(R.string.minuto_0_59, delayApi)
                        findViewById<EditText>(R.id.minuteInput).hint = hint
                        findViewById<EditText>(R.id.minuteNotifInput).hint = hint
                    }
                }
        } catch (e: Exception) {
            log("Error making the request: ${e.message}")
            errorDelayApi = "Error making the request"
        }
    }


}