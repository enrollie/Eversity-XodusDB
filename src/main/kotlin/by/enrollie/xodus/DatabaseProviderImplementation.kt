/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus

import by.enrollie.providers.*
import by.enrollie.xodus.impl.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

class DatabaseProviderImplementation : DatabaseProviderInterface {
    override val databasePluginID: String = PLUGIN_ID

    private val store: TransientEntityStore = initializeDB()
    override val authenticationDataProvider: DatabaseAuthenticationDataProviderInterface =
        DatabaseAuthenticationDataProviderImpl(store)

    override val classesProvider: DatabaseClassesProviderInterface = DatabaseClassesProviderImpl(store)
    override val customCredentialsProvider: DatabaseCustomCredentialsProviderInterface =
        DatabaseCustomCredentialsProviderImpl(store)
    override val lessonsProvider: DatabaseLessonsProviderInterface = DatabaseLessonsProviderImpl(store)
    override val rolesProvider: DatabaseRolesProviderInterface = DatabaseRolesProviderImpl(store)
    override val timetablePlacingProvider: DatabaseTimetablePlacingProviderInterface =
        DatabaseTimetablePlacingProviderImpl(store)
    override val usersProvider: DatabaseUserProviderInterface = DatabaseUserProviderImpl(store)
    override fun <T> runInSingleTransaction(block: (database: DatabaseProviderInterface) -> T): T =
        store.transactional { block(this@DatabaseProviderImplementation) }


    override suspend fun <T> runInSingleTransactionAsync(block: suspend (database: DatabaseProviderInterface) -> T): T {
        return store.transactional { CoroutineScope(Dispatchers.Default).async { block(this@DatabaseProviderImplementation) } }
            .await()
    }

    override val absenceProvider: DatabaseAbsenceProviderInterface = DatabaseAbsenceProviderImpl(store)


    init {
        transientEntityStore = store
    }

    companion object {
        var transientEntityStore: TransientEntityStore? = null
    }
}
