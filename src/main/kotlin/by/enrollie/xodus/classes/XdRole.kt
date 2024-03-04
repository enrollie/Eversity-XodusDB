/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.classes

import by.enrollie.data_classes.RoleData
import by.enrollie.data_classes.RoleInformationHolder
import by.enrollie.data_classes.Roles
import by.enrollie.xodus.util.toJavaLocalDateTime
import jetbrains.exodus.entitystore.Entity
import kotlinx.dnq.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDateTime

class XdRole(entity: Entity) : XdEntity(entity) {
    companion object : XdNaturalEntityType<XdRole>()

    var user: XdUser by xdParent(XdUser::roles)
    var roleID by xdRequiredStringProp()
    private var roleRawData by xdRequiredBlobStringProp()

    fun setRoleInformation(roleInformation: RoleInformationHolder) {
        roleRawData = Json.encodeToString(roleInformation)
    }

    fun getRoleInformation(): RoleInformationHolder {
        return Json.decodeFromString(roleRawData)
    }

    var uniqueID by xdRequiredStringProp(unique = true) { }

    var granted by xdRequiredDateTimeProp { }
    var revoked by xdDateTimeProp { }

    fun getAsRoleData(): RoleData = RoleData(
        uniqueID,
        user.id,
        Roles.getRoleByID(roleID) ?: throw IllegalStateException("Role with role ID \"$roleID\" is not defined"),
        getRoleInformation(),
        granted.toJavaLocalDateTime(),
        revoked?.toJavaLocalDateTime()
    )
}
