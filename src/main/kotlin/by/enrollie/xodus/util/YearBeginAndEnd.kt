/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.util

import kotlinx.dnq.query.FilteringContext.le
import java.time.LocalDate

val SCHOOL_YEAR_BEGINNING
    get() = LocalDate.now().withDayOfMonth(1).let { if (it.monthValue in 1..6) it.minusYears(1) else it }.withMonth(9)

val SCHOOL_YEAR_END
    get() = LocalDate.now().withDayOfMonth(1).let { if (it.monthValue in 9..12) it.plusYears(1) else it }.withMonth(6)
