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
        const val URL: String = "https://www.d2emu.com/api/v1/tz"
        const val USERNAME: String = ""
        const val TOKEN: String = ""
        //////////////////////////// ALARMAS - NOTIF //////////////////
        const val MINUTOPARAALARMASDEFAULT: Int = 15 // Minuto predeterminado para alarma 1
        const val MINUTOPARAALARMAS2DEFAULT: Int = 45 // Minuto predeterminado para alarma 2
        const val MINUTOPARANOTIF: Int = 5 // Minuto predeterminado para notificación. NO debe ser menor que DELAYAPIDEFAULT
        const val DELAYAPIDEFAULT: Int = 5 // Minuto predeterminado para que la api retorne resultados. NO debe ser menor al valor delayApi retornado por la API
        //////////////////////////// MUTE APP /////////////////////////
        val BEST_ZONES: List<String> = ZONAS.items.toTypedArray().toList()
//        val BEST_ZONES = arrayOf(
//            "Throne of Destruction",
//            "Tal Rasha's Tombs",
//            "Chaos Sanctuary",
//            "Flayer Jungle and Dungeon",
//            "Stony Tomb - Rocky Waste"
//        ).toList()
        var selectedBestZones: List<String> = emptyList()
        var horaInicio: Int = -1
        var horaFin: Int = -1
        var rangoActivo: Boolean = false
        var mute: Boolean = false
        var ultimaDesactivacionAuto: String = ""
        //////////////////////////// WINTER EVENT /////////////////////
        const val WINTERMINZONES: Int = 13 // Mínimo número de zonas que vienen en la api cuando es el winter event
    }
}

object DBConfig {
        const val DBNAME: String = "terror_zones_db"
        const val ZONESBETWEEN: Long = (2L * 24 * 60 * 60 * 1000) // Ver zonas de los últimos 2 días
        const val OLDZONESTODELETE: Long = (3L * 24 * 60 * 60 * 1000) // Borrar zonas de hace más de 3 días
}



// Terror Zones, no cambiarlas
object ZONAS {
    val items = listOf(
        "Throne of Destruction", //Worldstone Keep, Throne of Destruction, and Worldstone Chamber
        "Tal Rasha's Tombs", //Canyon of the Magi and Tal Rasha's Tombs
        "Chaos Sanctuary", //"Chaos Sanctuary"
        "Flayer Jungle and Dungeon", //Flayer Jungle and Flayer Dungeon / Swampy Pit
        "Stony Tomb - Rocky Waste", //Rocky Waste and Stony Tomb
        "Dark Wood - Underground Passage", //Dark Wood / Underground Passage
        "Dry Hills - Halls of the Dead", //Dry Hills and Halls of the Dead
        "Black Marsh - The Hole - Tower", //Black Marsh / The Hole / The Forgotten Tower
        "Arcane Sanctuary", //Harem, Palace and Arcane Sanctuary
        "Cold Plains - Cave", //Cold Plains and The Cave
        "Lut Gholein Sewers", //Sewers
        "Lost City - Valley of Snakes", //Lost City, Valley of Snakes, and Claw Viper Temple, Ancient Tunnels
        "Ancient's Way - Icy Cellar", //Ancient's Way and Icy Cellar
        "Crystalline Passage - Frozen River", //Crystalline Passage and Frozen River
        "Glacial Trail - Drifter Cavern", //Glacial Trail / Drifter Cavern
        "Outer Steppes - Plains of Despair", //Outer Steppes and Plains of Despair
        "City of the Damned - River of Flame", //River of Flame / City of the Damned
        "Bloody Foothills - Frigid Highlands", //Bloody Foothills / Frigid Highlands / Abbadon
        "Frozen Tundra", //Frozen Tundra + Infernal Pit
        "Arreat Plateau - Pit of Acheron", //Arreat Plateau / Pit of Acheron
        "Nihlathak's Temple and Halls", //Nihlathak's Temple, Halls of Anguish, Halls of Pain, and Halls of Vaught (Temple Halls)
        "Kurast Bazaar - Temples", //Kurast Bazaar, Ruined Temple, Disused Fane, Lower Kurast, Upper Kurast, Forgotten Temple, Forgotten Reliquary, Disused Reliquary, Ruined Fane
        "Jail - Barracks", //Jail / Barracks
        "Cathedral - Catacombs", //Inner Cloister / Cathedral and Catacombs
        "Pit", //The Pit / Tamoe Highland / Outer Cloister
        "Spider Forest - Spider Cavern", //Spider Forest and Spider Cavern / Arachnid Lair
        "Durance of Hate", //Durance of Hate
        "Great Marsh", //Great Marsh
        "Far Oasis", //Far Oasis and The Maggot Lair
        "Travincal", //Travincal
        "Moo Moo Farm", //Moo Moo Farm
        "Stony Field - Tristam", //Stony Field / Tristram
        "Blood Moor - Den of Evil", //Blood Moor / Den of Evil
        "Burial Grounds - Crypt - Mausoleum" //Burial Grounds, The Crypt, and the Mausoleum
    )
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
@param clave: Clave que se usará para buscar en el mapa.
@return El valor correspondiente a la clave dada, o un mensaje de error si la clave no existe.
 */
fun buscarEnMapa(clave: String): String {

    val mapa = mapOf(
        "2" to "Blood Moor - Den of Evil", //2,8
        "3" to "Cold Plains - Cave", //3,9-13
        "4" to "Stony Field", //4,38
        "5" to "Dark Wood - Underground Passage", //5,10-14
        "6" to "Black Marsh - The Hole - Tower", //6-11-15-20-21-22-23-24-25
        "7" to "Pit", //7-27-12-16-26
        "12" to "Pit",
        "16" to "Pit",
        "17" to "Burial Grounds - Crypt - Mausoleum", //17-18-19
        "20" to "Forgotten Tower",
        "26" to "Pit",
        "27" to "Pit",
        "28" to "Jail - Barracks", //28-29-30-31
        "33" to "Cathedral - Catacombs", //33,34,35,36,37,32
        "38" to "Tristram",
        "39" to "Moo Moo Farm",
        "41" to "Stony Tomb - Rocky Waste", //41-55-59
        "42" to "Dry Hills - Halls of the Dead", //42-56-57-60
        "43" to "Far Oasis", //43,62-63-64
        "44" to "Lost City - Valley of Snakes", //44,45,58-61,65
        "45" to "Valley of snakes",
        "46" to "Canyon of the Magi",
        "47" to "Lut Gholein Sewers", //47-48-49
        "50" to "Harem Lvl 1", //NEW
        "51" to "Harem Lvl 2", //NEW
        "52" to "Palace Lvl 1", //NEW
        "53" to "Palace Lvl 1", //NEW
        "54" to "Palace Lvl 1", //NEW
        "58" to "Claw Viper Temple Lvl 1",
        "61" to "Claw Viper Temple Lvl 2",
        "62" to "The Maggot Lair Lvl 1", // NEW
        "63" to "The Maggot Lair Lvl 2", // NEW
        "64" to "The Maggot Lair Lvl 3", // NEW
        "65" to "Ancient Tunnels",
        "66" to "Tal Rasha's Tombs", //66-67-68-69-70-71-72-73,46
        "74" to "Arcane Sanctuary", //74,50-51,52-53-54
        "76" to "Spider Forest - Spider Cavern", //76,84-85 Arachnid Lair
        "77" to "Great Marsh",
        "78" to "Flayer Jungle and Dungeon", //78,86-87-88,89-90-91 swampy
        "80" to "Kurast Bazaar - Temples", //80-79-81-82-92-93-94-95-96-97-98-99
        "83" to "Travincal",
        "100" to "Durance of Hate", //100-101-102
        "104" to "Outer Steppes - Plains of Despair", //104-105
        "106" to "City of the Damned - River of Flame", //106-107
        "108" to "Chaos Sanctuary",
        "110" to "Bloody Foothills - Frigid Highlands", //110,111,125
        "112" to "Arreat Plateau - Pit of Acheron", //112-126
        "113" to "Crystalline Passage - Frozen River", //113-114
        "115" to "Glacial Trail - Drifter Cavern", //115-116
        "117" to "Frozen Tundra", //117-127
        "118" to "Ancient's Way - Icy Cellar",
        "121" to "Nihlathak's Temple and Halls", //121,122,123,124
        "127" to "Infernal Pit",
        "128" to "Throne of Destruction" //128-129-130-131-132
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

//en cmd:
//	adb shell dumpsys meminfo com.example.d2rtz_fgservice | findstr Uptime
//en powershell:
//	[TimeSpan]::FromMilliseconds(Uptime)