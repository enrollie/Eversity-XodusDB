/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.XdEntity
import kotlinx.dnq.singleton.XdSingletonEntityType
import kotlinx.dnq.xdRequiredIntProp
import kotlinx.dnq.xdSequenceProp

class XdDatabase(entity: Entity) : XdEntity(entity) {
    companion object : XdSingletonEntityType<XdDatabase>() {
        override fun XdDatabase.initSingleton() {
            roleIDs.set(1)
            absenceIDs.set(1)
            databaseVersion = 2
        }
    }

    val roleIDs by xdSequenceProp()
    val absenceIDs by xdSequenceProp()
    var databaseVersion by xdRequiredIntProp { }
}
