package com.bogdan.shifts

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Status(val title: String, val subtitle: String, val onShift: Boolean)

data class MonthStats(val shifts: Int, val paidMinutes: Int, val offDays: Int)

object ShiftLogic {
    val ru: Locale = Locale.forLanguageTag("ru")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", ru)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    fun isWork(cfg: Config, date: LocalDate): Boolean {
        when (cfg.overrides[date.toEpochDay()]) {
            1 -> return true
            2 -> return false
        }
        val cycle = (cfg.workDays + cfg.offDays).toLong()
        if (cycle <= 0L) return false
        val d = Math.floorMod(date.toEpochDay() - cfg.startDate.toEpochDay(), cycle)
        return d < cfg.workDays.toLong()
    }

    fun shiftStart(cfg: Config, d: LocalDate): LocalDateTime = d.atTime(cfg.shiftStart)

    fun shiftEnd(cfg: Config, d: LocalDate): LocalDateTime =
        shiftStart(cfg, d).plusHours(cfg.shiftHours.toLong())

    /** Дата начала смены, которая идёт прямо сейчас (учитывает ночные смены). */
    fun currentShiftDate(cfg: Config, now: LocalDateTime): LocalDate? {
        val today = now.toLocalDate()
        for (d in listOf(today.minusDays(1), today)) {
            if (isWork(cfg, d) &&
                !now.isBefore(shiftStart(cfg, d)) &&
                now.isBefore(shiftEnd(cfg, d))
            ) return d
        }
        return null
    }

    fun nextShiftDate(cfg: Config, now: LocalDateTime): LocalDate? {
        val today = now.toLocalDate()
        for (i in 0L..400L) {
            val d = today.plusDays(i)
            if (isWork(cfg, d) && shiftStart(cfg, d).isAfter(now)) return d
        }
        return null
    }

    fun status(cfg: Config, now: LocalDateTime): Status {
        val cur = currentShiftDate(cfg, now)
        if (cur != null) {
            return Status("На смене", "до ${shiftEnd(cfg, cur).format(timeFmt)}", true)
        }
        val next = nextShiftDate(cfg, now) ?: return Status("Нет смен", "Проверьте настройки", false)
        val today = now.toLocalDate()
        val days = ChronoUnit.DAYS.between(today, next).toInt()
        val startTxt = shiftStart(cfg, next).format(timeFmt)
        if (days == 0) return Status("Смена сегодня", "начало в $startTxt", false)
        val title = if (isWork(cfg, today)) "Смена окончена" else "Выходной"
        val sub = if (days == 1) "Завтра в $startTxt"
        else "Через $days дн. · ${next.format(dayFmt)}, $startTxt"
        return Status(title, sub, false)
    }

    fun formatHours(minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return if (m == 0) "$h" else "$h,${m * 10 / 60}"
    }

    fun monthStats(cfg: Config, ym: YearMonth): MonthStats {
        var shifts = 0
        for (d in 1..ym.lengthOfMonth()) {
            if (isWork(cfg, ym.atDay(d))) shifts++
        }
        val paidPerShift = maxOf(0, cfg.shiftHours * 60 - cfg.breakMinutes)
        return MonthStats(shifts, shifts * paidPerShift, ym.lengthOfMonth() - shifts)
    }
}
