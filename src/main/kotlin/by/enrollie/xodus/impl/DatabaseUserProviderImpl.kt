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
import by.enrollie.providers.DatabaseUserProviderInterface
import by.enrollie.xodus.classes.XdUser
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.query
import kotlinx.dnq.query.toList

class DatabaseUserProviderImpl(private val store: TransientEntityStore) : DatabaseUserProviderInterface {
    override fun batchCreateUsers(users: List<User>) {
        store.transactional {
            users.forEach { user ->
                XdUser.findOrNew {
                    id = user.id
                }.also {
                    it.firstName = user.name.first
                    it.middleName = user.name.middle
                    it.lastName = user.name.last
                }
            }
        }
    }

    override fun createUser(user: User) {
        store.transactional {
            XdUser.findOrNew {
                id = user.id
            }.also {
                it.firstName = user.name.first
                it.middleName = user.name.middle
                it.lastName = user.name.last
            }
        }
    }

    override fun deleteUser(userID: UserID) {
        store.transactional {
            XdUser.query(XdUser::id eq userID).firstOrNull()?.delete()
        }
    }

    override fun getUser(userID: UserID): User? {
        return store.transactional(readonly = true) {
            XdUser.query(XdUser::id eq userID).firstOrNull()?.asUser
        }
    }

    override fun getUsers(): List<User> = store.transactional(readonly = true) {
        XdUser.all().toList().map { it.asUser }
    }

    override fun <T : Any> updateUser(userID: UserID, field: Field<T>, value: T) {
        if (field.name.split(".").let { it[it.lastIndex-1] } !in listOf("Name", "User"))
            throw IllegalArgumentException("Field $field is not defined for User")
        store.transactional {
            XdUser.query(XdUser::id eq userID).firstOrNull()?.also {
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
            }
        }
    }

}
