/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.annotations.UnsafeAPI
import by.enrollie.data_classes.*
import by.enrollie.exceptions.ProtectedFieldEditException
import by.enrollie.exceptions.UserDoesNotExistException
import by.enrollie.providers.DatabaseRolesProviderInterface
import by.enrollie.providers.Event
import by.enrollie.xodus.classes.*
import by.enrollie.xodus.util.SCHOOL_YEAR_BEGINNING
import by.enrollie.xodus.util.SCHOOL_YEAR_END
import by.enrollie.xodus.util.toJodaDateTime
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.dnq.query.*
import org.joda.time.DateTime
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class DatabaseRolesProviderImpl(private val store: TransientEntityStore) : DatabaseRolesProviderInterface {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val _eventsFlow = MutableSharedFlow<DatabaseRolesProviderInterface.RoleEvent>()
    override val eventsFlow: SharedFlow<DatabaseRolesProviderInterface.RoleEvent>
        get() = _eventsFlow
    private val cache: Cache<UserID, List<RoleData>> =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(6)).expireAfterAccess(Duration.ofSeconds(6)).build()

    private fun newRole(role: DatabaseRolesProviderInterface.RoleCreationData) = XdRole.new {
        user = XdUser.query(XdUser::id eq role.userID).firstOrNull() ?: throw UserDoesNotExistException(role.userID)
        roleID = role.role.getID()
        setRoleInformation(role.informationHolder)
        granted = role.creationDate.toJodaDateTime()
        revoked = role.expirationDate?.toJodaDateTime()
        uniqueID = UUID.nameUUIDFromBytes(XdDatabase.get().roleIDs.increment().toString().toByteArray()).toString()
    }

    override fun appendRoleToUser(userID: UserID, role: DatabaseRolesProviderInterface.RoleCreationData): RoleData {
        return store.transactional {
            newRole(role).getAsRoleData()
        }.also {
            cache.invalidate(userID)
            coroutineScope.launch {
                _eventsFlow.emit(DatabaseRolesProviderInterface.RoleEvent(Event.EventType.CREATED, it, null))
            }
        }
    }

    override fun batchAppendRolesToUsers(
        users: List<UserID>, roleGenerator: (UserID) -> DatabaseRolesProviderInterface.RoleCreationData
    ) {
        store.transactional {
            users.map {
                newRole(roleGenerator(it)).getAsRoleData()
            }
        }.also {
            cache.invalidateAll(users)
            coroutineScope.launch {
                it.forEach {
                    _eventsFlow.emit(DatabaseRolesProviderInterface.RoleEvent(Event.EventType.CREATED, it, null))
                }
            }
        }
    }

    override fun getAllRolesByMatch(match: (RoleData) -> Boolean): List<RoleData> =
        store.transactional(readonly = true) {
            XdRole.all().toList().map { it.getAsRoleData() }.filter(match)
        }

    override fun getAllRolesByType(type: Roles.Role): List<RoleData> = store.transactional(readonly = true) {
        XdRole.query(XdRole::roleID eq type.getID()).toList().map { it.getAsRoleData() }
    }

    @OptIn(UnsafeAPI::class)
    override fun getAllRolesWithMatchingEntries(vararg entries: Pair<Roles.Role.Field<*>, Any?>): List<RoleData> {
        return store.transactional(readonly = true) {
            XdRole.all().toList().filter { xdRole ->
                xdRole.getRoleInformation().getAsMap().entries.map { it.key to it.value }.containsAll(entries.toList())
            }.map { it.getAsRoleData() }
        }
    }

    override fun getRolesForUser(userID: UserID): List<RoleData> {
        return cache.get(userID) {
            store.transactional(readonly = true) {
                XdRole.query(XdRole::user.matches(XdUser::id eq userID)).toList().map { it.getAsRoleData() }
            }
        }
    }

    override fun revokeRole(roleID: String, revokeDateTime: LocalDateTime?) {
        store.transactional {
            (XdRole.query(XdRole::uniqueID eq roleID).firstOrNull()
                ?: throw NoSuchElementException("No role with ID $roleID was found")).let { role ->
                val snapshot = role.getAsRoleData()
                role.revoked = revokeDateTime?.toJodaDateTime() ?: DateTime.now()
                XdSubgroup.query(XdSubgroup::currentMembers.contains(role)).toList().forEach {
                    it.currentMembers.remove(role)
                }
                role.getAsRoleData() to snapshot
            }
        }.also {
            cache.invalidate(it.first.userID)
            coroutineScope.launch {
                if (it.first != it.second)
                    _eventsFlow.emit(
                        DatabaseRolesProviderInterface.RoleEvent(
                            Event.EventType.UPDATED,
                            it.first,
                            it.second
                        )
                    )
            }
        }
    }

    private fun recalculateTeachers(): List<XdRole> {
        val (allTeacherRoles, _) = XdRole.query(XdRole::roleID eq Roles.CLASS.TEACHER.getID()).toList()
            .let {
                it.map { it.getAsRoleData() } to it.map { it.user.id }.distinct()
            }
        val allUsers = XdUser.all().toList().map { it.id }
        return XdLesson.query(XdLesson::teacher inValues allUsers).toList().filterNot { lesson ->
            allTeacherRoles.any { it.getField(Roles.CLASS.TEACHER.classID) == lesson.schoolClass.id && it.getField(Roles.CLASS.TEACHER.journalID) == lesson.journalID }
        }.distinctBy { it.teacher to it.journalID }.map {
            newRole(
                DatabaseRolesProviderInterface.RoleCreationData(
                    it.teacher, Roles.CLASS.TEACHER, RoleInformationHolder(
                        Roles.CLASS.TEACHER.classID to it.schoolClass.id, Roles.CLASS.TEACHER.journalID to it.journalID
                    ), SCHOOL_YEAR_BEGINNING.atStartOfDay(), SCHOOL_YEAR_END.atStartOfDay()
                )
            )
        }
    }

    override fun triggerRolesUpdate() {
        store.transactional {
            recalculateTeachers().map { it.getAsRoleData() }
        }.also { roleDataList ->
            cache.invalidateAll(roleDataList.map { it.userID })
            coroutineScope.launch {
                roleDataList.forEach { role ->
                    _eventsFlow.emit(DatabaseRolesProviderInterface.RoleEvent(Event.EventType.CREATED, role, null))
                }
            }
        }
    }

    override fun <T : Any> updateRole(roleID: String, field: Field<T>, value: T) {
        store.transactional {
            (XdRole.query(XdRole::roleID eq roleID).firstOrNull()
                ?: throw NoSuchElementException("No role with ID $roleID was found")).let {
                val snapshot = it.getAsRoleData()
                when (field.name.split(".").last()) {
                    "role" -> {
                        it.roleID = (value as Roles.Role).getID()
                    }

                    "roleGrantedDateTime" -> {
                        it.granted = (value as LocalDateTime).toJodaDateTime()
                    }

                    "roleRevokedDateTime" -> {
                        it.revoked = (value as LocalDateTime?)?.toJodaDateTime()
                    }

                    "uniqueID", "userID" -> throw ProtectedFieldEditException("Cannot edit protected field $field")
                    else -> throw IllegalArgumentException("Unknown field $field")
                }
                it.getAsRoleData() to snapshot
            }
        }.also {
            cache.invalidate(it.first.userID)
            coroutineScope.launch {
                if (it.first != it.second)
                    _eventsFlow.emit(
                        DatabaseRolesProviderInterface.RoleEvent(
                            Event.EventType.UPDATED,
                            it.first,
                            it.second
                        )
                    )
            }
        }
    }
}
