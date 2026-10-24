package com.bogdan.shifts

import android.content.Context
import java.time.LocalDate
import java.time.LocalTime

/** Настройки графика. overrides: epochDay -> 1 (смена) / 2 (выходной). */
data class Config(
    val startDate: LocalDate,
    val workDays: Int,
    val offDays: Int,
    val shiftStart: LocalTime,
    val shiftHours: Int,
    val alarmEnabled: Boolean,
    val alarmMinutesBefore: Int,
    val overrides: Map<Long, Int>
)

fun Config.withOverride(date: LocalDate, value: Int): Config {
    val m = overrides.toMutableMap()
    if (value == 0) m.remove(date.toEpochDay()) else m[date.toEpochDay()] = value
    return copy(overrides = m)
}

object Prefs {
    private const val NAME = "shifts"

    fun isConfigured(ctx: Context): Boolean =
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).contains("start")

    fun load(ctx: Context): Config {
        val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val overrides = HashMap<Long, Int>()
        (p.getString("overrides", "") ?: "").split(",").forEach {
            val parts = it.split(":")
            if (parts.size == 2) {
                val k = parts[0].toLongOrNull()
                val v = parts[1].toIntOrNull()
                if (k != null && v != null) overrides[k] = v
            }
        }
        return Config(
            startDate = LocalDate.ofEpochDay(p.getLong("start", LocalDate.now().toEpochDay())),
            workDays = p.getInt("work", 3),
            offDays = p.getInt("off", 3),
            shiftStart = LocalTime.ofSecondOfDay(p.getInt("startMin", 8 * 60) * 60L),
            shiftHours = p.getInt("hours", 12),
            alarmEnabled = p.getBoolean("alarm", true),
            alarmMinutesBefore = p.getInt("alarmBefore", 60),
            overrides = overrides
        )
    }

    fun save(ctx: Context, c: Config) {
        val s = c.overrides.entries.joinToString(",") { "${it.key}:${it.value}" }
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit()
            .putLong("start", c.startDate.toEpochDay())
            .putInt("work", c.workDays)
            .putInt("off", c.offDays)
            .putInt("startMin", c.shiftStart.hour * 60 + c.shiftStart.minute)
            .putInt("hours", c.shiftHours)
            .putBoolean("alarm", c.alarmEnabled)
            .putInt("alarmBefore", c.alarmMinutesBefore)
            .putString("overrides", s)
            .apply()
    }
}
