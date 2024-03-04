/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.link.OnDeletePolicy
import java.time.LocalDate

class XdDummyAbsence(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdDummyAbsence>()

    var schoolClass by xdLink1(XdSchoolClass)
    var dateAsString by xdRequiredStringProp { }
    var createdBy by xdLink1(XdRole, onDelete = OnDeletePolicy.CLEAR, onTargetDelete = OnDeletePolicy.FAIL)
    var createdAt by xdRequiredDateTimeProp { }

    fun getDate(): LocalDate = LocalDate.parse(dateAsString)
}
