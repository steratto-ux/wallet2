package com.example.finanzecasa.domain

import com.example.finanzecasa.data.Cadence
import java.time.LocalDate
import java.time.YearMonth

object RecurringSchedule {
    /** Scadenza che segue [due] (stesso giorno del mese di riferimento [anchorDay], limitato alla lunghezza del mese). */
    fun following(due: LocalDate, cadence: Cadence, anchorDay: Int): LocalDate {
        val ym = YearMonth.from(due)
        return when (cadence) {
            Cadence.MONTHLY -> DateUtil.clamp(ym.plusMonths(1), anchorDay)
            Cadence.YEARLY -> DateUtil.clamp(ym.plusYears(1), anchorDay)
            Cadence.WEEKLY -> due.plusWeeks(1) // stesso giorno della settimana
        }
    }
}
