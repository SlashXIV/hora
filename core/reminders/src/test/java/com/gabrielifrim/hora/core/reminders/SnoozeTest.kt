package com.gabrielifrim.hora.core.reminders

import com.google.common.truth.Truth.assertThat
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Test

class SnoozeTest {
    private val zone = ZoneId.of("Europe/Paris")
    private fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 14, h, m).atZone(zone).toInstant()

    @Test fun demain_matin_le_soir_c_est_le_lendemain_8_h() {
        assertThat(SnoozeReceiver.snoozeUntil(SnoozeReceiver.TOMORROW_MORNING, at(21, 0), zone))
            .isEqualTo(LocalDateTime.of(2026, 11, 15, 8, 0).atZone(zone).toInstant())
    }

    @Test fun demain_matin_apres_minuit_c_est_8_h_le_jour_meme() {
        assertThat(SnoozeReceiver.snoozeUntil(SnoozeReceiver.TOMORROW_MORNING, at(0, 30), zone)).isEqualTo(at(8, 0))
    }

    @Test fun dix_minutes_et_une_heure() {
        assertThat(SnoozeReceiver.snoozeUntil(SnoozeReceiver.TEN_MINUTES, at(9, 0), zone)).isEqualTo(at(9, 10))
        assertThat(SnoozeReceiver.snoozeUntil(SnoozeReceiver.ONE_HOUR, at(9, 0), zone)).isEqualTo(at(10, 0))
    }
}
