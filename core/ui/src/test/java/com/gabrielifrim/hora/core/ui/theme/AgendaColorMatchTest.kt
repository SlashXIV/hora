package com.gabrielifrim.hora.core.ui.theme

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AgendaColorMatchTest {
    @Test
    fun `couleurs d'un export - la teinte la plus proche, le gris vers l'ardoise`() {
        assertThat(nearestAgendaColor(0xFFF44336.toInt())).isEqualTo(AgendaColorKey.BRICK) // rouge
        assertThat(nearestAgendaColor(-30720)).isEqualTo(AgendaColorKey.OCHRE) // orange de Fossify
        assertThat(nearestAgendaColor(0xFF4CAF50.toInt())).isEqualTo(AgendaColorKey.FIR) // vert
        assertThat(nearestAgendaColor(0xFF2196F3.toInt())).isEqualTo(AgendaColorKey.BLUE) // bleu
        assertThat(nearestAgendaColor(0xFF9C27B0.toInt())).isEqualTo(AgendaColorKey.PLUM) // violet
        assertThat(nearestAgendaColor(0xFF9E9E9E.toInt())).isEqualTo(AgendaColorKey.SLATE) // gris
    }
}
