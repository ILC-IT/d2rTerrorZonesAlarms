package com.example.d2rtz_fgservice

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.google.gson.Gson
import com.google.gson.JsonObject

import com.google.gson.annotations.SerializedName

data class TerrorZone(
    val current: List<String> = emptyList(),
    val next: List<String> = emptyList(),
    @SerializedName("next_terror_time_utc") // Esto mapea el campo JSON
    val nextTerrorTimeUtc: Long = 0L,
    @SerializedName("next_available_time_utc")
    val nextAvailableTimeUtc: Long = 0L,
    val delay: Int = 0,
    val error: String? = null // Campo opcional para almacenar mensajes de error
){
    class Deserializer: ResponseDeserializable<TerrorZone> {
        override fun deserialize(content: String): TerrorZone? {
            val gson = Gson()
            val jsonObject = gson.fromJson(content, JsonObject::class.java)

            return if (jsonObject.has("ERROR")) {
                // Si la respuesta tiene un campo "ERROR", devuelve un objeto con errorMessage
                TerrorZone(error = jsonObject["ERROR"].asString)
            } else {
                // Si la respuesta es válida, deserializa normalmente
                gson.fromJson(content, TerrorZone::class.java)
            }
        }
    }
}