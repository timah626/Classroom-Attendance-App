package com.example.testapplication2.utils



import java.time.Instant
import java.time.OffsetDateTime

internal fun parseInstant(raw: String): java.time.Instant =
    java.time.Instant.parse(if (raw.endsWith("Z")) raw else "${raw}Z")

internal fun parseSupabaseTimestamp(ts: String): Instant {
    var cleaned = ts.trim()
    val tIndex = cleaned.indexOf('T')
    if (tIndex != -1) {
        val colons = mutableListOf<Int>()
        for (i in tIndex until cleaned.length) {
            if (cleaned[i] == ':') colons.add(i)
        }
        if (colons.size >= 3) {
            val lastColon = colons.last()
            cleaned = cleaned.substring(0, lastColon) + "." + cleaned.substring(lastColon + 1)
        }
    }
    if (cleaned.contains('+') || (cleaned.lastIndexOf('-') > 10)) {
        return try {
            OffsetDateTime.parse(cleaned).toInstant()
        } catch (e: Exception) {
            cleaned = cleaned.replace(Regex("[+-]\\d{2}:\\d{2}$"), "")
            if (!cleaned.endsWith("Z")) cleaned += "Z"
            Instant.parse(cleaned)
        }
    }
    if (!cleaned.endsWith("Z")) cleaned += "Z"
    return Instant.parse(cleaned)
}

internal fun formatDate(raw: String): String {
    return try {
        val instant = parseInstant(raw)
        val zoned   = instant.atZone(java.time.ZoneId.systemDefault())
        val day     = zoned.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        val month   = zoned.month.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.ENGLISH)
        "$day, ${zoned.dayOfMonth} $month ${zoned.year}"
    } catch (e: Exception) { "Unknown date" }
}

internal fun formatTime(raw: String): String {
    return try {
        val instant = parseInstant(raw)
        val zoned   = instant.atZone(java.time.ZoneId.systemDefault())
        "%02d:%02d %s".format(
            if (zoned.hour % 12 == 0) 12 else zoned.hour % 12,
            zoned.minute,
            if (zoned.hour < 12) "AM" else "PM"
        )
    } catch (e: Exception) { "--:--" }
}