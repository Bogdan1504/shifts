package com.bogdan.shifts

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Status(val title: String, val subtitle: String, val onShift: Boolean)

data class MonthStats(
    val shifts: Int,
    val paidMinutes: Int,
    val offDays: Int,
    val vacationDays: Int
)

object ShiftLogic {
    val ru: Locale = Locale.forLanguageTag("ru")
    private val dayFmt = DateTimeFormatter.ofPattern("EEE, d MMM", ru)
    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm")

    /** Рабочий ли день по циклу, без учёта ручных отметок. */
    fun scheduled(cfg: Config, date: LocalDate): Boolean {
        val cycle = (cfg.workDays + cfg.offDays).toLong()
        if (cycle <= 0L) return false
        val d = Math.floorMod(date.toEpochDay() - cfg.startDate.toEpochDay(), cycle)
        return d < cfg.workDays.toLong()
    }

    /** 0 — нет отпуска, 3 — оплачиваемый, 4 — неоплачиваемый. */
    fun vacationType(cfg: Config, date: LocalDate): Int {
        val o = cfg.overrides[date.toEpochDay()]
        return if (o == 3) 3 else if (o == 4) 4 else 0
    }

    fun isWork(cfg: Config, date: LocalDate): Boolean {
        return when (cfg.overrides[date.toEpochDay()]) {
            1 -> true
            2, 3, 4 -> false
            else -> scheduled(cfg, date)
        }
    }

    /** Для календаря: 0 выходной, 1 смена, 2 оплачиваемый отпуск, 3 неоплачиваемый отпуск. */
    fun kind(cfg: Config, date: LocalDate): Int {
        return when (vacationType(cfg, date)) {
            3 -> 2
            4 -> 3
            else -> if (isWork(cfg, date)) 1 else 0
        }
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
        val today = now.toLocalDate()

        val vt = vacationType(cfg, today)
        if (vt != 0) {
            var end = today
            var n = 0
            while (vacationType(cfg, end.plusDays(1)) != 0 && n < 400) {
                end = end.plusDays(1)
                n++
            }
            val title = if (vt == 3) "В отпуске" else "Отпуск за свой счёт"
            return Status(title, "по ${end.format(dayFmt)} включительно", false)
        }

        val next = nextShiftDate(cfg, now) ?: return Status("Нет смен", "Проверьте настройки", false)
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

    /**
     * Часы = (отработанные смены + дни оплачиваемого отпуска, выпавшие на смену по графику)
     * × (длительность смены − обед). Неоплачиваемый отпуск часы не даёт.
     */
    fun monthStats(cfg: Config, ym: YearMonth): MonthStats {
        var shifts = 0
        var vacation = 0
        var paidVacationShifts = 0
        for (d in 1..ym.lengthOfMonth()) {
            val date = ym.atDay(d)
            when (vacationType(cfg, date)) {
                3 -> {
                    vacation++
                    if (scheduled(cfg, date)) paidVacationShifts++
                }
                4 -> vacation++
                else -> if (isWork(cfg, date)) shifts++
            }
        }
        val perShift = maxOf(0, cfg.shiftHours * 60 - cfg.breakMinutes)
        return MonthStats(
            shifts = shifts,
            paidMinutes = (shifts + paidVacationShifts) * perShift,
            offDays = ym.lengthOfMonth() - shifts - vacation,
            vacationDays = vacation
        )
    }
}
