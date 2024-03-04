/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.UserID
import by.enrollie.exceptions.UserDoesNotExistException
import by.enrollie.providers.DatabaseCustomCredentialsProviderInterface
import by.enrollie.xodus.classes.XdUser
import by.enrollie.xodus.classes.XdUsersCustomCredential
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.matches
import kotlinx.dnq.query.query
import java.time.Duration

class DatabaseCustomCredentialsProviderImpl(private val store: TransientEntityStore) :
    DatabaseCustomCredentialsProviderInterface {
    private val cache: Cache<Pair<UserID, String>, String?> =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).expireAfterAccess(
            Duration.ofMinutes(5)
        ).build()

    override fun clearCredentials(userID: UserID, credentialsType: String) {
        store.transactional {
            XdUsersCustomCredential.query(XdUsersCustomCredential::user.matches(XdUser::id eq userID)).firstOrNull()
                ?.unsetCustomCredential(credentialsType)
        }
        cache.invalidate(userID to credentialsType)
    }

    override fun getCredentials(userID: UserID, credentialsType: String): String? {
        return cache.getIfPresent(userID to credentialsType) ?: store.transactional {
            XdUsersCustomCredential.query(XdUsersCustomCredential::user.matches(XdUser::id eq userID)).firstOrNull()
                ?.getCustomCredential(credentialsType)?.also {
                    cache.put(userID to credentialsType, it)
                }
        }
    }

    override fun setCredentials(userID: UserID, credentialsType: String, credentials: String) {
        store.transactional {
            XdUsersCustomCredential.findOrNew {
                user = XdUser.query(XdUser::id eq userID).firstOrNull() ?: throw UserDoesNotExistException(userID)
            }.setCustomCredential(credentialsType, credentials).also {
                cache.put(userID to credentialsType, credentials)
            }
        }
    }
}
