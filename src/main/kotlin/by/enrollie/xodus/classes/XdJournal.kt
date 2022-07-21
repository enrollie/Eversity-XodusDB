/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdEntity
import kotlinx.dnq.XdNaturalEntityType
import kotlinx.dnq.simple.min
import kotlinx.dnq.xdRequiredIntProp
import kotlinx.dnq.xdRequiredStringProp

class XdJournal(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdJournal>()

    var id by xdRequiredIntProp { min(0) }
    var title by xdRequiredStringProp()
}
