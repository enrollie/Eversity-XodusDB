/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import by.enrollie.data_classes.SchoolClass
import by.enrollie.data_classes.TeachingShift
import by.enrollie.data_classes.UserID
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.simple.requireIf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class XdSchoolClass(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdSchoolClass>()

    var id by xdRequiredIntProp()
    var title by xdRequiredStringProp()
    var isSecondShift by xdBooleanProp { requireIf { true } } // It must be set
    val shift: TeachingShift
        get() = if (isSecondShift) TeachingShift.SECOND else TeachingShift.FIRST
    private var _ordering by xdRequiredBlobStringProp()

    /**
     * Do not use in queries.
     */
    var ordering: List<Pair<UserID, Int>>
        get() = Json.decodeFromString(_ordering)
        set(value) {
            _ordering = Json.encodeToString(value)
        }

    val lessons by xdChildren0_N(XdLesson::schoolClass)

    fun asSchoolClass() = SchoolClass(id, title, shift)
}
