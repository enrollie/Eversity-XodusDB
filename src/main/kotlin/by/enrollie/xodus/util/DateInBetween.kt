/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.util

import java.time.LocalDate
import java.time.LocalDateTime

fun LocalDateTime.isBetweenOrEqual(firstDateTime: LocalDateTime, secondDateTime: LocalDateTime): Boolean {
    return (this.isAfter(firstDateTime) && this.isBefore(secondDateTime)) || (firstDateTime == this) || (secondDateTime == this)
}

fun LocalDate.isBetweenOrEqual(firstDate: LocalDate, secondDate: LocalDate): Boolean {
    return (this.isAfter(firstDate) && this.isBefore(secondDate)) || (firstDate == this) || (secondDate == this)
}
