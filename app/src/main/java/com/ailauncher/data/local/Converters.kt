package com.ailauncher.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** Room type converters: ordered string lists are stored as JSON arrays. */
class Converters {
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(String.serializer())

    @TypeConverter
    fun stringListToJson(value: List<String>): String = json.encodeToString(listSerializer, value)

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        value?.let { runCatching { json.decodeFromString(listSerializer, it) }.getOrNull() } ?: emptyList()
}
