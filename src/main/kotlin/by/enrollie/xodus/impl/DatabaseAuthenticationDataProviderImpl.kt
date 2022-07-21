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
import by.enrollie.xodus.classes.XdToken
import by.enrollie.xodus.classes.XdUser
import by.enrollie.xodus.util.toJavaLocalDateTime
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import org.joda.time.DateTime
import java.util.UUID

class DatabaseAuthenticationDataProviderImpl(private val store: TransientEntityStore) :
    DatabaseAuthenticationDataProviderInterface {
    override fun checkToken(token: String, userID: UserID): Boolean = store.transactional(readonly = true) {
        XdToken.filter {
            (it.user.id eq userID) and (it.token eq token)
        }.isNotEmpty
    }

    override fun generateNewToken(userID: UserID): AuthenticationToken {
        val tokenString = UUID.randomUUID().toString()
        return store.transactional {
            XdToken.new {
                this.user = XdUser.query(XdUser::id eq userID).firstOrNull()
                    ?: throw UserDoesNotExistException(userID)
                this.token = tokenString
                this.issued = DateTime.now()
            }.let {
                AuthenticationToken(tokenString, userID, it.issued.toJavaLocalDateTime())
            }
        }
    }

    override fun getUserByToken(token: String): UserID? = store.transactional(readonly = true){
        XdToken.query(XdToken::token eq token).firstOrNull()?.user?.id
    }

    override fun getUserTokens(userID: UserID): List<AuthenticationToken> {
        return store.transactional(readonly = true) {
            XdToken.query(XdToken::user.matches(XdUser::id eq userID)).toList().map {
                AuthenticationToken(it.token, userID, it.issued.toJavaLocalDateTime())
            }
        }
    }

    override fun revokeToken(token: AuthenticationToken) {
        store.transactional {
            XdToken.query(XdToken::token eq token.token).firstOrNull()?.delete()
        }
    }
}
