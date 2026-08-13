package dev.xenoncolt.jellyflix.core

import java.util.Locale
import kotlin.math.abs

/** Small formatting helpers shared across screens. */
object Format {

    /** 1_610_612_736 -> "1.5 GB". Binary units, one decimal below 10. */
    fun bytes(value: Long): String {
        if (value <= 0) return "—"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = value.toDouble()
        var unit = 0
        while (size >= 1024 && unit < units.lastIndex) {
            size /= 1024
            unit++
        }
        return when {
            unit == 0 -> "${value} B"
            size >= 100 -> String.format(Locale.US, "%.0f %s", size, units[unit])
            size >= 10 -> String.format(Locale.US, "%.1f %s", size, units[unit])
            else -> String.format(Locale.US, "%.2f %s", size, units[unit])
        }
    }

    /** Bytes per second -> "4.2 MB/s". */
    fun speed(bytesPerSecond: Long): String =
        if (bytesPerSecond <= 0) "—" else "${bytes(bytesPerSecond)}/s"

    /** 137 -> "2h 17m"; 45 -> "45 mins". */
    fun runtime(minutes: Int?): String? {
        if (minutes == null || minutes <= 0) return null
        val hours = minutes / 60
        val mins = minutes % 60
        return when {
            hours == 0 -> "$mins mins"
            mins == 0 -> "${hours}h"
            else -> "${hours}h ${mins}m"
        }
    }

    /** Seconds remaining -> "12m left" / "1h 3m left". Null when unknown. */
    fun eta(seconds: Long): String? {
        if (seconds <= 0 || seconds > 60L * 60 * 24 * 30) return null
        val hours = seconds / 3600
        val mins = (seconds % 3600) / 60
        return when {
            hours >= 24 -> "${hours / 24}d ${hours % 24}h left"
            hours > 0 -> "${hours}h ${mins}m left"
            mins > 0 -> "${mins}m left"
            else -> "${seconds}s left"
        }
    }

    /** 1234567 -> "1.2M" for seeder/vote counts. */
    fun compact(value: Int): String = when {
        abs(value) >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000f)
        abs(value) >= 10_000 -> String.format(Locale.US, "%.0fK", value / 1000f)
        abs(value) >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1000f)
        else -> value.toString()
    }

    /** 8.437 -> "8.4" */
    fun rating(value: Double): String = String.format(Locale.US, "%.1f", value)

    /** "2024-05-17" -> "17 May 2024"; passes anything unparseable straight through. */
    fun date(iso: String?): String? {
        if (iso.isNullOrBlank()) return null
        val parts = iso.take(10).split("-")
        if (parts.size != 3) return iso
        val year = parts[0]
        val month = parts[1].toIntOrNull() ?: return iso
        val day = parts[2].toIntOrNull() ?: return iso
        val names = arrayOf(
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
        )
        if (month !in 1..12) return iso
        return "$day ${names[month - 1]} $year"
    }

    /** 1_200_000_000L -> "$1.2B" for revenue/budget. */
    fun money(value: Long?): String? {
        if (value == null || value <= 0) return null
        return when {
            value >= 1_000_000_000 -> String.format(Locale.US, "$%.1fB", value / 1_000_000_000.0)
            value >= 1_000_000 -> String.format(Locale.US, "$%.1fM", value / 1_000_000.0)
            value >= 1_000 -> String.format(Locale.US, "$%.0fK", value / 1_000.0)
            else -> "$$value"
        }
    }
}
