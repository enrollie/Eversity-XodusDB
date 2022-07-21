/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.util

import org.joda.time.DateTime
import java.time.LocalDateTime

fun LocalDateTime.toJodaDateTime() = DateTime(
    year, month.value, dayOfMonth, hour, minute, second, nano / 1_000_000
)

fun DateTime.toJavaLocalDateTime() =
    LocalDateTime.of(
        year,
        monthOfYear,
        dayOfMonth,
        hourOfDay,
        minuteOfHour,
        secondOfMinute,
        millisOfSecond * 1_000_000
    )!!
