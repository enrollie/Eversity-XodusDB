/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import by.enrollie.data_classes.Roles
import by.enrollie.data_classes.SchoolClass
import by.enrollie.data_classes.TeachingShift
import by.enrollie.data_classes.UserID
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.dnq.query.FilteringContext.le
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.filter
import kotlinx.dnq.query.query
import kotlinx.dnq.query.toList
import kotlinx.dnq.simple.requireIf
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class XdSchoolClass(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdSchoolClass>()

    var id by xdRequiredIntProp()
    var title by xdRequiredStringProp()
    private var _shift by xdRequiredStringProp { }
    var shift: TeachingShift
        get() = TeachingShift.valueOf(_shift)
        set(value) {
            _shift = value.name
        }

    private var _ordering by xdBlobStringProp()
    fun getOrdering(): List<Pair<UserID, Int>> =
        _ordering?.let { Json.decodeFromString<List<Pair<UserID, Int>>>(it) } ?: XdRole.all().toList().filter {
            (it.roleID == Roles.CLASS.STUDENT.getID()) && ((it.getRoleInformation()[Roles.CLASS.STUDENT.classID] as? Int) == id) && (it.revoked == null)
        }.toList().sortedBy { "${it.user.lastName} ${it.user.firstName}" }
            .mapIndexed { index, xdRole -> xdRole.user.id to index + 1 }

    fun setOrdering(ordering: List<Pair<UserID, Int>>) {
        _ordering = Json.encodeToString(ordering)
    }

    val lessons by xdChildren0_N(XdLesson::schoolClass)

    fun asSchoolClass() = SchoolClass(id, title, shift)
}
