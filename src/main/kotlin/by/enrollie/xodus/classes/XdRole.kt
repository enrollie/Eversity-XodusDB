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

    var granted by xdRequiredDateTimeProp { }
    var revoked by xdDateTimeProp {  }

    fun getAsRoleData(): RoleData<*> = RoleData(user.id,
        Roles.getRoleByID(roleID) ?: error("Role with ID $roleID not found"),
        getRoleInformation(),
        LocalDateTime.of(
            granted.year,
            granted.monthOfYear,
            granted.dayOfMonth,
            granted.hourOfDay,
            granted.minuteOfHour,
            granted.secondOfMinute,
            granted.millisOfSecond
        ),
        revoked?.let {
            LocalDateTime.of(
                it.year,
                it.monthOfYear,
                it.dayOfMonth,
                it.hourOfDay,
                it.minuteOfHour,
                it.secondOfMinute,
                it.millisOfSecond
            )
        })
}
