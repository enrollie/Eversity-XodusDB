/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import by.enrollie.data_classes.Name
import by.enrollie.data_classes.User
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*

class XdUser(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdUser>() {

    }

    var id by xdRequiredIntProp()
    var firstName by xdRequiredStringProp()
    var middleName by xdStringProp()
    var lastName by xdRequiredStringProp()
    val roles by xdChildren0_N(XdRole::user)
    val tokens by xdChildren0_N(XdToken::user)

    fun asUser() = User(id, Name(firstName, middleName, lastName))
}
