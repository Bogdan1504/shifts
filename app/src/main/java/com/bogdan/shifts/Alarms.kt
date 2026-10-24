package com.bogdan.shifts

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Alarms {
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    private fun alarmIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 0, Intent(ctx, AlarmReceiver::class.java), FLAGS)

    /** Ставит один будильник — на ближайшую смену. После срабатывания ставится следующий. */
    fun reschedule(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = alarmIntent(ctx)
            am.cancel(pi)
            if (!Prefs.isConfigured(ctx)) return
            val cfg = Prefs.load(ctx)
            if (!cfg.alarmEnabled) return

            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            for (i in 0L..400L) {
                val d = today.plusDays(i)
                if (!ShiftLogic.isWork(cfg, d)) continue
                val at = ShiftLogic.shiftStart(cfg, d).minusMinutes(cfg.alarmMinutesBefore.toLong())
                if (at.isAfter(now)) {
                    val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val show = PendingIntent.getActivity(
                        ctx, 1, Intent(ctx, MainActivity::class.java), FLAGS
                    )
                    val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
                    try {
                        if (canExact) {
                            am.setAlarmClock(AlarmManager.AlarmClockInfo(millis, show), pi)
                        } else {
                            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                        }
                    } catch (e: SecurityException) {
                        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
                    }
                    return
                }
            }
        } catch (e: Exception) {
            // Будильник не должен ронять приложение
        }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val cfg = Prefs.load(ctx)
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        val channel = NotificationChannel(CH, "Будильник смены", NotificationManager.IMPORTANCE_HIGH)
        channel.setSound(
            sound,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        channel.enableVibration(true)
        channel.vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
        nm.createNotificationChannel(channel)

        val open = PendingIntent.getActivity(
            ctx, 3, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val time = cfg.shiftStart.format(DateTimeFormatter.ofPattern("HH:mm"))
        val n = Notification.Builder(ctx, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Смена в $time")
            .setContentText("Пора собираться")
            .setCategory(Notification.CATEGORY_ALARM)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000)
            .build()
        n.flags = n.flags or Notification.FLAG_INSISTENT
        nm.notify(1, n)

        Alarms.reschedule(ctx)
        ShiftWidget.updateAll(ctx)
    }

    companion object {
        const val CH = "shift_alarm"
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        Alarms.reschedule(ctx)
        ShiftWidget.updateAll(ctx)
    }
}
