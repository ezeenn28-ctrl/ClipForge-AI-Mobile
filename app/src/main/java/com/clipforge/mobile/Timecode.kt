package com.clipforge.mobile

import java.util.Locale

object Timecode {
    fun format(ms: Long): String {
        val safe = ms.coerceAtLeast(0)
        val hours = safe / 3_600_000
        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, safe / 60_000 % 60, safe / 1000 % 60)
        else String.format(Locale.ROOT, "%02d:%02d", safe / 60_000, safe / 1000 % 60)
    }

    fun precise(ms: Long): String = format(ms) + String.format(Locale.ROOT, ".%03d", ms.coerceAtLeast(0) % 1000)

    fun parse(value: String): Long? {
        val parts = value.trim().split(':')
        if (parts.size !in 1..3) return null
        val seconds = parts.last().split('.')
        if (seconds.size !in 1..2 || seconds[0].isEmpty() || seconds[0].any { !it.isDigit() }) return null
        val whole = seconds[0].toLongOrNull() ?: return null
        val fraction = if (seconds.size == 2) {
            if (seconds[1].length !in 1..3 || seconds[1].any { !it.isDigit() }) return null
            seconds[1].padEnd(3, '0').toLong()
        } else 0
        val leading = parts.dropLast(1).map { part ->
            if (part.isEmpty() || part.any { !it.isDigit() }) return null
            part.toLongOrNull() ?: return null
        }
        if (parts.size > 1 && whole >= 60) return null
        if (parts.size == 3 && leading[1] >= 60) return null
        return try {
            var totalSeconds = 0L
            for (part in leading) totalSeconds = Math.addExact(Math.multiplyExact(totalSeconds, 60), part)
            totalSeconds = Math.addExact(Math.multiplyExact(totalSeconds, 60), whole)
            Math.addExact(Math.multiplyExact(totalSeconds, 1000), fraction)
        } catch (_: ArithmeticException) { null }
    }

    fun validTrim(startMs: Long, endMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && startMs >= 0 && endMs > startMs && endMs <= durationMs
}
