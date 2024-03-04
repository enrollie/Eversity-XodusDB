/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*

class XdToken(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdToken>()

    var user: XdUser by xdParent(XdUser::tokens)
    var token: String by xdRequiredStringProp()
    var issued by xdRequiredDateTimeProp()
    var expired by xdDateTimeProp {  }
}
