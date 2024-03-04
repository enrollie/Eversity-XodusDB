/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.Field
import by.enrollie.data_classes.User
import by.enrollie.data_classes.UserID
import by.enrollie.exceptions.UserIDConflictException
import by.enrollie.providers.DatabaseUserProviderInterface
import by.enrollie.providers.Event
import by.enrollie.xodus.classes.XdUser
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.query
import kotlinx.dnq.query.toList
import kotlinx.dnq.util.isDefined
import java.time.Duration

class DatabaseUserProviderImpl(private val store: TransientEntityStore) : DatabaseUserProviderInterface {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val _eventsFlow = MutableSharedFlow<DatabaseUserProviderInterface.UserEvent>()
    override val eventsFlow: SharedFlow<DatabaseUserProviderInterface.UserEvent>
        get() = _eventsFlow
    private val cache: Cache<UserID, User> =
        Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(10)).expireAfterWrite(Duration.ofMinutes(10)).build()


    override fun batchCreateUsers(users: List<User>) {
        store.transactional {
            users.map { user ->
                XdUser.findOrNew {
                    id = user.id
                }.also {
                    if (it.isDefined(XdUser::firstName)) throw UserIDConflictException(user.id)
                    it.firstName = user.name.first
                    it.middleName = user.name.middle
                    it.lastName = user.name.last
                }.asUser()
            }
        }.also {
            coroutineScope.launch {
                it.forEach {
                    cache.put(it.id, it)
                    _eventsFlow.emit(
                        DatabaseUserProviderInterface.UserEvent(
                            Event.EventType.CREATED, it, null
                        )
                    )
                }
            }
        }
    }

    override fun createUser(user: User) {
        store.transactional {
            XdUser.findOrNew {
                id = user.id
            }.also {
                if (it.isDefined(XdUser::firstName)) throw UserIDConflictException(user.id)
                it.firstName = user.name.first
                it.middleName = user.name.middle
                it.lastName = user.name.last
            }.asUser()
        }.also {
            cache.put(it.id, it)
            coroutineScope.launch {
                _eventsFlow.emit(DatabaseUserProviderInterface.UserEvent(Event.EventType.CREATED, it, null))
            }
        }
    }

    override fun deleteUser(userID: UserID) {
        store.transactional {
            XdUser.query(XdUser::id eq userID).firstOrNull()?.let {
                val snapshot = it.asUser()
                it.delete()
                snapshot
            }
        }.also {
            if (it != null) {
                cache.invalidate(it.id)
                coroutineScope.launch {
                    _eventsFlow.emit(DatabaseUserProviderInterface.UserEvent(Event.EventType.DELETED, it, null))
                }
            }
        }
    }

    override fun getUser(userID: UserID): User? {
        return cache.getIfPresent(userID) ?: store.transactional(readonly = true) {
            XdUser.query(XdUser::id eq userID).firstOrNull()?.asUser()
        }?.also {
            cache.put(it.id, it)
        }
    }

    override fun getUsers(): List<User> = store.transactional(readonly = true) {
        XdUser.all().toList().map { it.asUser() }
    }

    override fun <T : Any> updateUser(userID: UserID, field: Field<T>, value: T) {
        if (field.name.split(".").let { it[it.lastIndex - 1] } !in listOf(
                "Name", "User"
            )) throw IllegalArgumentException("Field $field is not defined for User")
        store.transactional {
            XdUser.query(XdUser::id eq userID).firstOrNull()?.let {
                val snapshot = it.asUser()
                when (field.name.split(".").last()) {
                    "first" -> it.apply {
                        firstName = value as String
                    }
                    "middle" -> it.apply {
                        middleName = value as? String?
                    }
                    "last" -> it.apply {
                        lastName = value as String
                    }
                    else -> throw IllegalArgumentException("Field $field is not defined for User")
                }
                it.asUser() to snapshot
            }
        }.also {
            if (it != null) {
                cache.put(it.first.id, it.first)
                coroutineScope.launch {
                    _eventsFlow.emit(
                        DatabaseUserProviderInterface.UserEvent(
                            Event.EventType.UPDATED, it.first, it.second
                        )
                    )
                }
            }
        }
    }
}
