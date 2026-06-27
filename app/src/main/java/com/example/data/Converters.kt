package com.example.data

import androidx.room.TypeConverter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory

class Converters {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    
    private val stringListType = Types.newParameterizedType(List::class.java, String::class.java)
    private val stringListAdapter = moshi.adapter<List<String>>(stringListType)
    
    private val longListType = Types.newParameterizedType(List::class.java, Long::class.javaObjectType)
    private val longListAdapter = moshi.adapter<List<Long>>(longListType)

    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return stringListAdapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        return try {
            stringListAdapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String {
        return longListAdapter.toJson(value ?: emptyList())
    }

    @TypeConverter
    fun toLongList(value: String): List<Long> {
        return try {
            longListAdapter.fromJson(value) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
}
