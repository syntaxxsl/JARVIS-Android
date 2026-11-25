package com.jarvis.assistant.core.common.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtils {
    fun nowFormatted(): String {
        val sdf = SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr", "TR"))
        return sdf.format(Date())
    }
    fun greetingForHour(hour: Int): String = when (hour) {
        in 5..11 -> "Günaydın"
        in 12..17 -> "İyi öğleden sonra"
        in 18..21 -> "İyi akşamlar"
        else -> "İyi geceler"
    }
}