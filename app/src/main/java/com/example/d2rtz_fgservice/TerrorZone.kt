package com.example.d2rtz_fgservice

import com.github.kittinunf.fuel.core.ResponseDeserializable
import com.google.gson.Gson

import com.google.gson.annotations.SerializedName

data class TerrorZone(
    val current: List<String>,
    val next: List<String>,
    @SerializedName("next_terror_time_utc") // Esto mapea el campo JSON
    val nextTerrorTimeUtc: Long
){
    class Deserializer: ResponseDeserializable<TerrorZone> {
        override fun deserialize(content: String): TerrorZone? = Gson().fromJson(content, TerrorZone::class.java)
    }
}