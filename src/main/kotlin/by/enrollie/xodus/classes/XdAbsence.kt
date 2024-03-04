/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import by.enrollie.data_classes.AbsenceRecord
import by.enrollie.data_classes.AbsenceType
import by.enrollie.data_classes.AuthorizedChangeAuthor
import by.enrollie.xodus.util.toJavaLocalDateTime
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.link.OnDeletePolicy
import kotlinx.dnq.simple.alpha
import kotlinx.dnq.simple.min
import kotlinx.dnq.simple.regex
import java.time.LocalDate

class XdAbsence(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdAbsence>()

    var id by xdRequiredLongProp { min(1) }
    var student by xdLink1(XdUser, onDelete = OnDeletePolicy.CLEAR, onTargetDelete = OnDeletePolicy.FAIL)
    var absenceDateString by xdRequiredStringProp { regex(Regex("\\d{4}-[0-1]\\d-[0-3]\\d")) }
    var schoolClass by xdLink1(XdSchoolClass)

    /**
     * String representation of [AbsenceType] enum.
     */
    var absenceType by xdRequiredStringProp {
        regex(Regex("^${
            AbsenceType.values().joinToString(separator = "|") {
                "(${it.name})"
            } 
        }")) // One may ask: "Why don't you use Xodus DNQ' constraints for this?". That would be my choice if only it's module wasn't named `dnq.transient.store`
             // (notice `transient` part - it is a reserved keyword in module-info.java, which makes it impossible to require `dnq.transient.store` module, hence I decided to use built-in regex constraint)
    }
    var lessonsList by xdSetProp<XdAbsence, Int>()
    var createdBy by xdLink1(XdRole, onDelete = OnDeletePolicy.CLEAR, onTargetDelete = OnDeletePolicy.FAIL)
    var createdAt by xdRequiredDateTimeProp()
    var updatedBy by xdLink0_1(XdRole, onDelete = OnDeletePolicy.CLEAR, onTargetDelete = OnDeletePolicy.FAIL)
    var updatedAt by xdDateTimeProp()

    fun getDate(): LocalDate = LocalDate.parse(absenceDateString)
    fun getAsAbsenceRecord() = AbsenceRecord(
        id,
        student.asUser(),
        getDate(),
        schoolClass.id,
        AbsenceType.valueOf(absenceType),
        lessonsList.toList(),
        AuthorizedChangeAuthor(createdBy.user.asUser(), createdBy.getAsRoleData()),
        createdAt.toJavaLocalDateTime(),
        updatedBy?.let { AuthorizedChangeAuthor(it.user.asUser(), it.getAsRoleData()) },
        updatedAt?.toJavaLocalDateTime()
    )
}
