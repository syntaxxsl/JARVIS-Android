package com.jarvis.assistant.core.common.util

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeUtilsTest {
    @Test
    fun greetingUsesMorningRange() {
        assertEquals("Günaydın", TimeUtils.greetingForHour(5))
        assertEquals("Günaydın", TimeUtils.greetingForHour(11))
    }

    @Test
    fun greetingUsesAfternoonRange() {
        assertEquals("İyi öğleden sonra", TimeUtils.greetingForHour(12))
        assertEquals("İyi öğleden sonra", TimeUtils.greetingForHour(17))
    }

    @Test
    fun greetingUsesEveningRange() {
        assertEquals("İyi akşamlar", TimeUtils.greetingForHour(18))
        assertEquals("İyi akşamlar", TimeUtils.greetingForHour(21))
    }

    @Test
    fun greetingUsesNightRangeAtDayBoundaries() {
        assertEquals("İyi geceler", TimeUtils.greetingForHour(0))
        assertEquals("İyi geceler", TimeUtils.greetingForHour(4))
        assertEquals("İyi geceler", TimeUtils.greetingForHour(22))
        assertEquals("İyi geceler", TimeUtils.greetingForHour(23))
    }
}
