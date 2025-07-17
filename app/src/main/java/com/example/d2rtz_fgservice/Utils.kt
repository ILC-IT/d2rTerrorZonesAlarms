package com.example.d2rtz_fgservice

import android.content.Context
import android.util.Log
import com.example.d2rtz_fgservice.DBConfig.DBNAME
import database.AppDatabase
import java.io.File
import java.util.Calendar

class Login {
    companion object {
        //////////////////////////// LOGIN ////////////////////////////
        const val URL = "https://www.d2emu.com/api/v1/tz"
        const val USERNAME = ""
        const val TOKEN = ""
        //////////////////////////// ALARM - NOTIF ////////////////////
        const val MINUTOPARAALARMASDEFAULT = 40 // Minuto predeterminado para alarmas
        const val MINUTOPARANOTIF = 11 // Minuto predeterminado para notificación. NO debe ser menor que DELAYAPIDEFAULT
        const val DELAYAPIDEFAULT = 11 // Minuto predeterminado para que la api retorne resultados. NO debe ser menor al valor delayApi retornado por la API
        //////////////////////////// MUTE APP /////////////////////////
        val BEST_ZONES = arrayOf("Throne of Destruction", "Tal Rasha's Tombs", "Chaos Sanctuary").toList()
        var horaInicio: Int = -1
        var horaFin: Int = -1
        var rangoActivo: Boolean = false
        var mute: Boolean = false
    }
}

object DBConfig {
        const val DBNAME = "terror_zones_db"
        const val ZONESBETWEEN = (2L * 24 * 60 * 60 * 1000) // Ver zonas de los últimos 2 días
        const val OLDZONESTODELETE = (3L * 24 * 60 * 60 * 1000) // Borrar zonas de hace más de 3 días
}



//////////////////////////// FUNCTIONS ////////////////////////////

fun log(msg: String) {
    Log.d("ENDLESS-SERVICE", msg)
}

fun getDBTotalSize(context: Context): Long {
    // Calcular el tamaño de la base de datos en total en bytes
    val databasePath = context.getDatabasePath(DBNAME)
    var totalSize = 0L

    // Archivos relacionados con la base de datos
    val dbFiles = listOf(
        databasePath, // Archivo principal de la base de datos
        File("${databasePath.path}-shm"), // Archivo de memoria compartida
        File("${databasePath.path}-wal") // Archivo de registro WAL
    )

    for (file in dbFiles) {
        if (file.exists()) {
            totalSize += file.length()
        }
    }

    return totalSize
}

fun getDBSize(context: Context): Long {
    // Calcular el tamaño de la base de datos en bytes
    val db = AppDatabase.getInstance(context).openHelper.readableDatabase
    val pageSizeCursor = db.query("PRAGMA page_size")
    val pageCountCursor = db.query("PRAGMA page_count")
    var pageSize = 0
    var pageCount = 0

    if (pageSizeCursor.moveToFirst()) {
        pageSize = pageSizeCursor.getInt(0)
    }
    if (pageCountCursor.moveToFirst()) {
        pageCount = pageCountCursor.getInt(0)
    }

    pageSizeCursor.close()
    pageCountCursor.close()

    return (pageSize * pageCount).toLong()
}

/**
Función que busca un valor en un mapa usando una clave dada.
@param clave Clave que se usará para buscar en el mapa.
@return El valor correspondiente a la clave dada, o un mensaje de error si la clave no existe.
 */
fun buscarEnMapa(clave: String): String {

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

/**
Función que busca si existe una string dentro de una lista
@param selectedItems Lista que se usará para buscar dentro de ella
@param zonaBuscada Zona a buscar
@return True o false segun la zona buscada exista o no
 */
fun isNameInSelectedItems(selectedItems: List<String>, zonaBuscada: String): Boolean {
    return selectedItems.contains(zonaBuscada)
}

fun milisegundosHastaMinuto(targetMinute: Int): Long {
    // Obtener la instancia actual del calendario
    val calendar = Calendar.getInstance()

    // Obtener el minuto actual
    val currentMinute = calendar.get(Calendar.MINUTE)

    // Ajustar la hora y los minutos del calendario al minuto objetivo
    calendar.set(Calendar.SECOND, 0)
    calendar.set(Calendar.MILLISECOND, 0)

    // Si el minuto objetivo ya ha pasado en esta hora, ajusta a la siguiente hora
    if (targetMinute <= currentMinute) {
        calendar.add(Calendar.HOUR_OF_DAY, 1)
    }

    calendar.set(Calendar.MINUTE, targetMinute)

    // Calcular la diferencia en milisegundos entre el tiempo actual y el tiempo objetivo
    val milisegundosHastaMinuto = calendar.timeInMillis - System.currentTimeMillis()

    return milisegundosHastaMinuto
}
