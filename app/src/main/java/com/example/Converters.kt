package com.example

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromEnergyLevel(value: EnergyLevel): String {
        return value.name
    }

    @TypeConverter
    fun toEnergyLevel(value: String): EnergyLevel {
        return try {
            EnergyLevel.valueOf(value)
        } catch (e: Exception) {
            EnergyLevel.MEDIUM
        }
    }
}
