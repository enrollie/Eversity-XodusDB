/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import by.enrollie.data_classes.EventConstraints
import by.enrollie.data_classes.TimetablePlace
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class XdTimetablePlacing(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdTimetablePlacing>()

    private var rawFirstShift by xdRequiredBlobStringProp()
    private var rawSecondShift by xdRequiredBlobStringProp()
    var firstShift: Map<TimetablePlace, EventConstraints>
        get() = Json.decodeFromString(rawFirstShift)
        set(value) {
            rawFirstShift = Json.encodeToString(value)
        }
    var secondShift: Map<TimetablePlace, EventConstraints>
        get() = Json.decodeFromString(rawSecondShift)
        set(value) {
            rawSecondShift = Json.encodeToString(value)
        }

    var effectiveSince by xdRequiredDateTimeProp { }
    var effectiveUntil by xdDateTimeProp { }
}
