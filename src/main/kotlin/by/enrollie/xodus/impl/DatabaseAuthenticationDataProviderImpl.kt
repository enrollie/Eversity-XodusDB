/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.AuthenticationToken
import by.enrollie.data_classes.UserID
import by.enrollie.exceptions.UserDoesNotExistException
import by.enrollie.providers.DatabaseAuthenticationDataProviderInterface
import by.enrollie.providers.Event
import by.enrollie.xodus.classes.XdToken
import by.enrollie.xodus.classes.XdUser
import by.enrollie.xodus.util.toJavaLocalDateTime
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
import java.util.*

class DatabaseAuthenticationDataProviderImpl(private val store: TransientEntityStore) :
    DatabaseAuthenticationDataProviderInterface {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val _eventsFlow = MutableSharedFlow<DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent>()
    override val eventsFlow: SharedFlow<DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent>
        get() = _eventsFlow
    private val validityCache: Cache<Pair<String, UserID>, Boolean> =
        Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(1)).expireAfterAccess(
            Duration.ofMinutes(5)
        ).build()


    override fun checkToken(token: String, userID: UserID): Boolean = validityCache.get(token to userID) { data ->
        store.transactional(readonly = true) {
            XdToken.filter {
                (it.user.id eq data.second) and (it.token eq data.first) and (it.expired eq null)
            }.isNotEmpty
        }
    }

    override fun generateNewToken(userID: UserID): AuthenticationToken {
        val tokenString = UUID.randomUUID().toString()
        return store.transactional {
            XdToken.new {
                this.user = XdUser.query(XdUser::id eq userID).firstOrNull() ?: throw UserDoesNotExistException(userID)
                this.token = tokenString
                this.issued = DateTime.now()
            }.let {
                AuthenticationToken(tokenString, userID, it.issued.toJavaLocalDateTime())
            }
        }.also {
            validityCache.put(it.token to userID, true)
            coroutineScope.launch {
                _eventsFlow.emit(
                    DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent(
                        Event.EventType.CREATED, it, null
                    )
                )
            }
        }
    }

    override fun getToken(token: String): AuthenticationToken? = store.transactional(readonly = true){
        XdToken.query(XdToken::token eq token).firstOrNull()?.let {
            AuthenticationToken(it.token, it.user.id, it.issued.toJavaLocalDateTime())
        }
    }

    override fun getUserByToken(token: String): UserID? = store.transactional(readonly = true) {
        XdToken.query(XdToken::token eq token).firstOrNull()?.also {
            if (it.expired == null) {
                validityCache.put(token to it.user.id, true)
            }
        }?.user?.id
    }

    override fun getUserTokens(userID: UserID): List<AuthenticationToken> {
        return store.transactional(readonly = true) {
            XdToken.query(XdToken::user.matches(XdUser::id eq userID)).toList().map {
                if (it.expired == null) {
                    validityCache.put(it.token to userID, true)
                }
                AuthenticationToken(it.token, userID, it.issued.toJavaLocalDateTime())
            }
        }
    }

    override fun revokeAllTokens(userID: UserID) {
        store.transactional {
            XdToken.query(XdToken::user.matches(XdUser::id eq userID)).toList().let {
                it.map { AuthenticationToken(it.token,it.user.id, it.issued.toJavaLocalDateTime()) } to it
            }.also {
                it.second.onEach { token ->
                    validityCache.put(token.token to userID, false)
                    token.delete()
                }
            }.first
        }.onEach { token ->
            validityCache.put(token.token to userID, false)
            coroutineScope.launch {
                _eventsFlow.emit(
                    DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent(
                        Event.EventType.DELETED, token, null
                    )
                )
            }
        }
    }

    override fun revokeToken(token: AuthenticationToken) {
        store.transactional {
            XdToken.query(XdToken::token eq token.token).firstOrNull()?.also {
                validityCache.invalidate(token.token to it.user.id)
            }?.also {
                it.expired = DateTime.now()
            }?.let {
                AuthenticationToken(it.token, it.user.id, it.issued.toJavaLocalDateTime())
            }
        }?.also {
            coroutineScope.launch {
                _eventsFlow.emit(
                    DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent(
                        Event.EventType.DELETED, it, null
                    )
                )
            }
        }
    }

    override fun revokeToken(token: String) {
        store.transactional {
            XdToken.query(XdToken::token eq token).firstOrNull()?.let {
                AuthenticationToken(it.token, it.user.id, it.issued.toJavaLocalDateTime()) to it
            }?.also {
                validityCache.invalidate(token to it.first.userID)
                it.second.delete()
            }?.first
        }?.also {
            coroutineScope.launch {
                _eventsFlow.emit(
                    DatabaseAuthenticationDataProviderInterface.AuthenticationDataEvent(
                        Event.EventType.DELETED, it, null
                    )
                )
            }
        }
    }
}
