package com.bogdan.shifts

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

data class Palette(
    val bg: Color,
    val surface: Color,
    val text: Color,
    val muted: Color,
    val accent: Color,
    val onAccent: Color,
    val vacPaid: Color,
    val vacUnpaid: Color,
    val onVac: Color
)

private val LightPalette = Palette(
    bg = Color(0xFFF6F5F2), surface = Color(0xFFFFFFFF), text = Color(0xFF1B1B1D),
    muted = Color(0xFF8A8A90), accent = Color(0xFF1F6F54), onAccent = Color(0xFFFFFFFF),
    vacPaid = Color(0xFFD98324), vacUnpaid = Color(0xFF6C7BB5), onVac = Color(0xFFFFFFFF)
)
private val DarkPalette = Palette(
    bg = Color(0xFF121214), surface = Color(0xFF1C1C1F), text = Color(0xFFF2F2F3),
    muted = Color(0xFF8E8E93), accent = Color(0xFF6FD3A2), onAccent = Color(0xFF0C2A1D),
    vacPaid = Color(0xFFF0A84E), vacUnpaid = Color(0xFF8D9BD6), onVac = Color(0xFF1E1A12)
)
val LocalPalette = staticCompositionLocalOf { LightPalette }

@Composable
fun ShiftTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(primary = p.accent, background = p.bg, surface = p.surface, onSurface = p.text)
    } else {
        lightColorScheme(primary = p.accent, background = p.bg, surface = p.surface, onSurface = p.text)
    }
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

class MainActivity : ComponentActivity() {
    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Prefs.isConfigured(this)) {
            Alarms.reschedule(this)
            ShiftWidget.updateAll(this)
        }
        setContent { ShiftTheme { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    var cfg by remember { mutableStateOf(Prefs.load(ctx)) }
    var configured by remember { mutableStateOf(Prefs.isConfigured(ctx)) }
    var showSettings by remember { mutableStateOf(!Prefs.isConfigured(ctx)) }

    fun commit(c: Config) {
        Prefs.save(ctx, c)
        cfg = c
        configured = true
        Alarms.reschedule(ctx)
        ShiftWidget.updateAll(ctx)
    }

    BackHandler(enabled = showSettings && configured) { showSettings = false }

    if (showSettings) {
        SettingsScreen(
            cfg = cfg,
            canGoBack = configured,
            onSave = { commit(it); showSettings = false },
            onBack = { showSettings = false }
        )
    } else {
        HomeScreen(
            cfg = cfg,
            onSettings = { showSettings = true },
            onOverride = { day, v -> commit(cfg.withOverride(day, v)) },
            onVacation = { f, t, v -> commit(cfg.withVacation(f, t, v)) }
        )
    }
}

@Composable
fun HomeScreen(
    cfg: Config,
    onSettings: () -> Unit,
    onOverride: (LocalDate, Int) -> Unit,
    onVacation: (LocalDate, LocalDate, Int) -> Unit
) {
    val p = LocalPalette.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalDateTime.now()
        }
    }
    var month by remember { mutableStateOf(YearMonth.now()) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }
    var showVac by remember { mutableStateOf(false) }

    val st = ShiftLogic.status(cfg, now)
    val today = now.toLocalDate()
    val stats = ShiftLogic.monthStats(cfg, month)

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Смены", color = p.text, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSettings) { Text("Настройки", color = p.accent) }
        }
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(p.surface)
                .padding(20.dp)
        ) {
            Text(
                st.title,
                color = if (st.onShift) p.accent else p.text,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(st.subtitle, color = p.muted, fontSize = 15.sp)
        }

        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { month = month.minusMonths(1) }) {
                Text("‹", color = p.text, fontSize = 22.sp)
            }
            val monthName = month.month.getDisplayName(TextStyle.FULL_STANDALONE, ShiftLogic.ru)
                .replaceFirstChar { it.uppercase() }
            Text(
                "$monthName ${month.year}", color = p.text, fontSize = 17.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = { month = month.plusMonths(1) }) {
                Text("›", color = p.text, fontSize = 22.sp)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс").forEach {
                Text(
                    it, color = p.muted, fontSize = 12.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        val lead = month.atDay(1).dayOfWeek.value - 1
        val len = month.lengthOfMonth()
        val rows = (lead + len + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val dayNum = r * 7 + c - lead + 1
                    if (dayNum < 1 || dayNum > len) {
                        Box(Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayNum)
                        DayCell(
                            modifier = Modifier.weight(1f),
                            day = dayNum,
                            kind = ShiftLogic.kind(cfg, date),
                            isToday = date == today,
                            overridden = (cfg.overrides[date.toEpochDay()] ?: 0).let { it == 1 || it == 2 },
                            onClick = { selected = date }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(p.accent, "Смена")
            LegendItem(p.vacPaid, "Отпуск оплач.")
            LegendItem(p.vacUnpaid, "Отпуск неоплач.")
        }

        Spacer(Modifier.height(8.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(p.surface)
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StatItem("Смен", stats.shifts.toString())
            StatItem("Часов", ShiftLogic.formatHours(stats.paidMinutes))
            StatItem("Выходных", stats.offDays.toString())
            if (stats.vacationDays > 0) StatItem("Отпуск", stats.vacationDays.toString())
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Нажмите на день, чтобы отметить подмену, отгул или отпуск",
            color = p.muted, fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
        )
        TextButton(
            onClick = { showVac = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Отметить отпуск за период", color = p.accent)
        }
    }

    if (showVac) {
        VacationDialog(
            initial = today,
            onDismiss = { showVac = false },
            onApply = { f, t, v -> onVacation(f, t, v); showVac = false }
        )
    }

    selected?.let { d ->
        AlertDialog(
            onDismissRequest = { selected = null },
            containerColor = p.surface,
            title = {
                Text(
                    d.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", ShiftLogic.ru)),
                    color = p.text, fontSize = 18.sp
                )
            },
            text = {
                Column {
                    TextButton(onClick = { onOverride(d, 0); selected = null }) {
                        Text("Как по графику", color = p.text)
                    }
                    TextButton(onClick = { onOverride(d, 1); selected = null }) {
                        Text("Рабочая смена (подмена)", color = p.text)
                    }
                    TextButton(onClick = { onOverride(d, 2); selected = null }) {
                        Text("Выходной (отгул)", color = p.text)
                    }
                    TextButton(onClick = { onOverride(d, 3); selected = null }) {
                        Text("Отпуск оплачиваемый", color = p.text)
                    }
                    TextButton(onClick = { onOverride(d, 4); selected = null }) {
                        Text("Отпуск неоплачиваемый", color = p.text)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selected = null }) { Text("Закрыть", color = p.accent) }
            }
        )
    }
}

@Composable
fun StatItem(label: String, value: String) {
    val p = LocalPalette.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = p.text, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(label, color = p.muted, fontSize = 12.sp)
    }
}

@Composable
fun DayCell(
    modifier: Modifier,
    day: Int,
    kind: Int,
    isToday: Boolean,
    overridden: Boolean,
    onClick: () -> Unit
) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(14.dp)
    val fill = when (kind) {
        1 -> p.accent
        2 -> p.vacPaid
        3 -> p.vacUnpaid
        else -> Color.Transparent
    }
    val textColor = when (kind) {
        1 -> p.onAccent
        2, 3 -> p.onVac
        else -> p.text
    }
    val filled = kind != 0
    Box(
        modifier
            .aspectRatio(1f)
            .padding(3.dp)
            .clip(shape)
            .background(fill)
            .then(
                if (isToday) Modifier.border(2.dp, if (filled) p.text else p.accent, shape)
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            day.toString(),
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (filled) FontWeight.SemiBold else FontWeight.Normal
        )
        if (overridden) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(if (filled) textColor else p.accent)
            )
        }
    }
}

@Composable
fun SettingsScreen(cfg: Config, canGoBack: Boolean, onSave: (Config) -> Unit, onBack: () -> Unit) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var start by remember { mutableStateOf(cfg.startDate) }
    var work by remember { mutableStateOf(cfg.workDays) }
    var off by remember { mutableStateOf(cfg.offDays) }
    var time by remember { mutableStateOf(cfg.shiftStart) }
    var hours by remember { mutableStateOf(cfg.shiftHours) }
    var brk by remember { mutableStateOf(cfg.breakMinutes) }
    var alarm by remember { mutableStateOf(cfg.alarmEnabled) }
    var before by remember { mutableStateOf(cfg.alarmMinutesBefore) }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Настройки графика", color = p.text, fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
            )
            if (canGoBack) TextButton(onClick = onBack) { Text("Назад", color = p.accent) }
        }
        Spacer(Modifier.height(12.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(p.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ClickRow(
                "Первый день любой серии смен",
                start.format(DateTimeFormatter.ofPattern("d MMMM yyyy", ShiftLogic.ru))
            ) {
                android.app.DatePickerDialog(
                    ctx,
                    { _, y, m, d -> start = LocalDate.of(y, m + 1, d) },
                    start.year, start.monthValue - 1, start.dayOfMonth
                ).show()
            }
            Text(
                "Выберите дату, когда начиналась любая из серий рабочих дней, например последняя.",
                color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
            )
            StepperRow("Рабочих дней подряд", work, "", 1, 14, 1) { work = it }
            StepperRow("Выходных подряд", off, "", 1, 14, 1) { off = it }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(p.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            ClickRow("Начало смены", time.format(DateTimeFormatter.ofPattern("HH:mm"))) {
                android.app.TimePickerDialog(
                    ctx,
                    { _, h, m -> time = LocalTime.of(h, m) },
                    time.hour, time.minute, true
                ).show()
            }
            StepperRow("Длительность смены", hours, " ч", 1, 24, 1) { hours = it }
            StepperRow("Обед (не оплачивается)", brk, " мин", 0, 180, 15) { brk = it }
        }

        Spacer(Modifier.height(16.dp))

        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(p.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Будильник перед сменой", color = p.text, fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = alarm,
                    onCheckedChange = { alarm = it },
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = p.accent,
                        checkedThumbColor = p.onAccent
                    )
                )
            }
            if (alarm) {
                StepperRow("За сколько до начала", before, " мин", 5, 240, 5) { before = it }
            }
            Text(
                "На Xiaomi разрешите приложению автозапуск и уберите ограничения батареи, " +
                    "иначе будильник может не сработать.",
                color = p.muted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = {
                onSave(
                    Config(
                        startDate = start,
                        workDays = work,
                        offDays = off,
                        shiftStart = time,
                        shiftHours = hours,
                        breakMinutes = brk,
                        alarmEnabled = alarm,
                        alarmMinutesBefore = before,
                        overrides = cfg.overrides
                    )
                )
            },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = p.accent, contentColor = p.onAccent)
        ) {
            Text("Сохранить", fontSize = 16.sp)
        }
    }
}

@Composable
fun ClickRow(label: String, value: String, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = p.accent, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun StepperRow(
    label: String,
    value: Int,
    suffix: String,
    min: Int,
    max: Int,
    step: Int,
    onChange: (Int) -> Unit
) {
    val p = LocalPalette.current
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - step).coerceAtLeast(min)) }) {
            Text("−", color = p.accent, fontSize = 22.sp)
        }
        Text(
            "$value$suffix", color = p.text, fontSize = 16.sp,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 56.dp)
        )
        TextButton(onClick = { onChange((value + step).coerceAtMost(max)) }) {
            Text("+", color = p.accent, fontSize = 22.sp)
        }
    }
}


@Composable
fun LegendItem(color: Color, label: String) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(6.dp))
        Text(label, color = p.muted, fontSize = 12.sp)
    }
}

fun pickDate(ctx: android.content.Context, init: LocalDate, onPicked: (LocalDate) -> Unit) {
    android.app.DatePickerDialog(
        ctx,
        { _, y, m, d -> onPicked(LocalDate.of(y, m + 1, d)) },
        init.year, init.monthValue - 1, init.dayOfMonth
    ).show()
}

@Composable
fun TypeRow(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    val p = LocalPalette.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(color))
        Spacer(Modifier.size(10.dp))
        Text(label, color = p.text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = p.accent, fontSize = 18.sp)
    }
}

@Composable
fun VacationDialog(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onApply: (LocalDate, LocalDate, Int) -> Unit
) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var from by remember { mutableStateOf(initial) }
    var to by remember { mutableStateOf(initial) }
    var type by remember { mutableStateOf(3) }
    val fmt = DateTimeFormatter.ofPattern("d MMMM yyyy", ShiftLogic.ru)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = p.surface,
        title = { Text("Отпуск", color = p.text, fontSize = 18.sp) },
        text = {
            Column {
                ClickRow("С", from.format(fmt)) {
                    pickDate(ctx, from) { picked ->
                        from = picked
                        if (to.isBefore(picked)) to = picked
                    }
                }
                ClickRow("По", to.format(fmt)) {
                    pickDate(ctx, to) { picked ->
                        to = picked
                        if (from.isAfter(picked)) from = picked
                    }
                }
                Spacer(Modifier.height(8.dp))
                TypeRow("Оплачиваемый", type == 3, p.vacPaid) { type = 3 }
                TypeRow("Неоплачиваемый", type == 4, p.vacUnpaid) { type = 4 }
                TextButton(onClick = { onApply(from, to, 0) }) {
                    Text("Снять отпуск за этот период", color = p.muted)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(from, to, type) }) {
                Text("Применить", color = p.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена", color = p.muted) }
        }
    )
}
