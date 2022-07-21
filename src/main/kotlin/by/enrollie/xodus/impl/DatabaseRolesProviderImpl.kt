/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.RoleData
import by.enrollie.data_classes.RoleInformationHolder
import by.enrollie.data_classes.Roles
import by.enrollie.data_classes.UserID
import by.enrollie.exceptions.UserDoesNotExistException
import by.enrollie.providers.DatabaseRolesProviderInterface
import by.enrollie.xodus.classes.XdRole
import by.enrollie.xodus.classes.XdUser
import by.enrollie.xodus.util.toJodaDateTime
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import org.joda.time.DateTime
import java.time.LocalDateTime

class DatabaseRolesProviderImpl(private val store: TransientEntityStore) : DatabaseRolesProviderInterface {
    @OptIn(UnsafeAPI::class)
    private fun newRole(role: RoleData<*>) = XdRole.new {
        user = XdUser.query(XdUser::id eq role.userID).firstOrNull() ?: throw UserDoesNotExistException(role.userID)
        roleID = role.roleID
        roleInformation = role.getRoleInformationHolder()
        granted = role.roleGrantedDateTime.toJodaDateTime()
        revoked = role.roleRevokedDateTime?.toJodaDateTime()
    }

    override fun appendRoleToUser(userID: UserID, role: RoleData<*>) {
        store.transactional {
            newRole(role)
        }
    }

    override fun batchAppendRolesToUsers(users: List<UserID>, roleGenerator: (UserID) -> RoleData<*>) {
        store.transactional {
            users.forEach { userID ->
                val role = roleGenerator(userID)
                newRole(role)
            }
        }
    }

    override fun getAllRolesByMatch(match: (RoleData<*>) -> Boolean): List<RoleData<*>> =
        store.transactional(readonly = true) {
            XdRole.all().toList().filter { match(it.asRoleData) }.map { it.asRoleData }
        }

    override fun getAllRolesByType(type: Roles.Role): List<RoleData<*>> = store.transactional(readonly = true) {
        XdRole.query(XdRole::roleID eq type.getID()).toList().map { it.asRoleData }
    }

    @OptIn(UnsafeAPI::class)
    override fun getAllRolesWithMatchingEntries(vararg entries: Pair<Roles.Role.Field<*>, Any?>): List<RoleData<*>> {
        return store.transactional(readonly = true) {
            XdRole.filter {
                it.roleInformation.getAsMap().entries.map { it.key to it.value }.containsAll(entries.toList()) eq true
            }.toList().map { it.asRoleData }
        }
    }

    override fun getRolesForUser(userID: UserID): List<RoleData<*>> {
        return store.transactional(readonly = true) {
            XdRole.query(XdRole::user.matches(XdUser::id eq userID)).toList().map { it.asRoleData }
        }
    }

    override fun revokeRoleFromUser(userID: UserID, role: RoleData<*>, revokeDateTime: LocalDateTime?) {
        store.transactional {
            XdRole.query(XdRole::user.matches(XdUser::id eq userID) and (XdRole::roleID eq role.roleID) and (XdRole::granted eq role.roleGrantedDateTime.toJodaDateTime()))
                .firstOrNull()?.apply {
                    revoked = revokeDateTime?.toJodaDateTime() ?: DateTime.now()
                }
        }
    }

    @OptIn(UnsafeAPI::class)
    override fun <T : Any> updateRole(role: RoleData<*>, field: Roles.Role.Field<T>, value: T) {
        if (role.role.fieldByID(field.id) == null) throw IllegalArgumentException("Field with id ${field.id} does not exist or does not belong given role")
        store.transactional {
            XdRole.query(XdRole::user.matches(XdUser::id eq role.userID) and (XdRole::roleID eq role.roleID) and (XdRole::granted eq role.roleGrantedDateTime.toJodaDateTime()))
                .firstOrNull()?.apply {
                    roleInformation = RoleInformationHolder(*roleInformation.getAsMap().toMutableMap().apply {
                        this[field] = value
                    }.toList().toTypedArray())
                }
        }
    }
}
