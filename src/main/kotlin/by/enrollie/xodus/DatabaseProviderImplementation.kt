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

class DatabaseProviderImplementation : DatabaseProviderInterface {
    private val store: TransientEntityStore = initializeDB()

    override val databaseID: String = PLUGIN_ID

    override val authenticationDataProvider: DatabaseAuthenticationDataProviderInterface =
        DatabaseAuthenticationDataProviderImpl(store)
    override val classesProvider: DatabaseClassesProviderInterface = DatabaseClassesProviderImpl(store)
    override val lessonsProvider: DatabaseLessonsProviderInterface = DatabaseLessonsProviderImpl(store)
    override val rolesProvider: DatabaseRolesProviderInterface = DatabaseRolesProviderImpl(store)
    override val timetablePlacingProvider: DatabaseTimetablePlacingProviderInterface =
        DatabaseTimetablePlacingProviderImpl(store)
    override val usersProvider: DatabaseUserProviderInterface = DatabaseUserProviderImpl(store)


    init {
        transientEntityStore = store
    }

    companion object {
        var transientEntityStore: TransientEntityStore? = null
    }
}
