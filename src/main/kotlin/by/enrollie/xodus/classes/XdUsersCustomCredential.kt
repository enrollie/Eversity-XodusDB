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
import kotlinx.dnq.xdLink1

class XdUsersCustomCredential(entity: Entity): XdEntity(entity) {
    companion object: XdNaturalEntityType<XdUsersCustomCredential>()

    var user by xdLink1(XdUser)

    fun getCustomCredential(credentialType: String): String? {
        require(credentialType != "user") { "Credential type 'user' is reserved" }
        return entity.getProperty(credentialType) as? String?
    }

    fun setCustomCredential(credentialType: String, value: String) {
        require(credentialType != "user") { "Credential type 'user' is reserved" }
        entity.setProperty(credentialType, value)
    }

    fun unsetCustomCredential(credentialType: String){
        require(credentialType != "user") { "Cannot unset user property" }
        entity.deleteProperty(credentialType)
    }
}
