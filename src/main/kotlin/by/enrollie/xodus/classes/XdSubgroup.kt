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

class XdSubgroup(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdSubgroup>()

    var id by xdRequiredIntProp { }
    var schoolClass by xdLink1(XdSchoolClass)
    var subgroupTitle by xdRequiredStringProp { }
    val currentMembers by xdLink0_N(XdRole, onDelete = OnDeletePolicy.CLEAR, onTargetDelete = OnDeletePolicy.CLEAR)
}
