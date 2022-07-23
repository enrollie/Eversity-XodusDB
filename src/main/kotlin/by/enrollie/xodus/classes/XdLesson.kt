/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import by.enrollie.data_classes.Lesson
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.simple.max
import kotlinx.dnq.simple.min
import kotlinx.dnq.simple.regex
import java.time.LocalDate

class XdLesson(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdLesson>()

    var id by xdRequiredLongProp { }
    var title by xdRequiredStringProp { }
    var dateAsString by xdRequiredStringProp { regex(Regex("\\d{4}-[0-1][1-9]-[0-3]\\d")) }
    fun getDate() = LocalDate.parse(dateAsString)
    fun setDate(value: LocalDate) {
        dateAsString = value.toString()
    }

    var placeInTimetable by xdRequiredIntProp { max(15); min(0) }
    var teachers by xdSetProp<XdLesson, Int>()
    var schoolClass: XdSchoolClass by xdParent(XdSchoolClass::lessons)
    var subgroupID by xdNullableIntProp { }
    var journalID by xdRequiredIntProp { }

    fun toLesson(): Lesson = Lesson(
        id, title, getDate(), placeInTimetable, teachers, schoolClass.id, journalID, subgroupID
    )
}
