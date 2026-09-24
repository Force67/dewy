package dev.dewy.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** The time now, ticking every [periodMillis] while in composition. */
@Composable
fun rememberNow(periodMillis: Long = 30_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMillis) {
        while (true) {
            delay(periodMillis)
            now = System.currentTimeMillis()
        }
    }
    return now
}

private val dayFormat = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

/** "now", "4m", "3h", "Tue", "12 Sep": the way a mail list dates its rows. */
fun shortAge(then: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val seconds = (now - then) / 1000
    return when {
        seconds < 60 -> "now"
        seconds < 3600 -> "${seconds / 60}m"
        seconds < 86_400 -> "${seconds / 3600}h"
        else -> {
            val date = Instant.ofEpochMilli(then).atZone(zone)
            val days = ChronoUnit.DAYS.between(date.toLocalDate(), Instant.ofEpochMilli(now).atZone(zone).toLocalDate())
            if (days < 7) dayFormat.format(date) else dateFormat.format(date)
        }
    }
}

/** "12s", "4 min", "1 h 5 min": how long something has been going. */
fun span(millis: Long): String {
    val s = millis / 1000
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "${s / 60} min"
        else -> "${s / 3600} h ${(s % 3600) / 60} min"
    }
}
