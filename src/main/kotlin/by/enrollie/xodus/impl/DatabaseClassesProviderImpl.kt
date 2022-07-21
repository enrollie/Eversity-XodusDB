/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.*
import by.enrollie.exceptions.ProtectedFieldEditException
import by.enrollie.exceptions.SchoolClassDoesNotExistException
import by.enrollie.providers.DatabaseClassesProviderInterface
import by.enrollie.xodus.classes.XdSchoolClass
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.eq
import kotlinx.dnq.query.firstOrNull
import kotlinx.dnq.query.query
import kotlinx.dnq.query.toList

class DatabaseClassesProviderImpl(private val store: TransientEntityStore) : DatabaseClassesProviderInterface {
    override fun batchCreateClasses(classes: List<SchoolClass>) = store.transactional {
        classes.forEach {
            val clazz = XdSchoolClass.findOrNew {
                id = it.id
            }
            clazz.title = it.title
        }
    }

    override fun createClass(classData: SchoolClass) {
        store.transactional {
            val clazz = XdSchoolClass.findOrNew {
                id = classData.id
            }
            clazz.title = classData.title
        }
    }

    override fun getClass(classID: ClassID): SchoolClass? {
        return store.transactional {
            XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()?.asSchoolClass()
        }
    }

    override fun getClasses(): List<SchoolClass> {
        return store.transactional {
            XdSchoolClass.all().toList().map { it.asSchoolClass() }
        }
    }

    override fun getPupilsOrdering(classID: ClassID): List<Pair<UserID, Int>> = store.transactional(readonly = true) {
        (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()
            ?: throw SchoolClassDoesNotExistException(classID)).ordering
    }


    override fun setPupilsOrdering(classID: ClassID, pupilsOrdering: List<Pair<UserID, Int>>) {
        store.transactional {
            (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()
                ?: throw SchoolClassDoesNotExistException(classID)).ordering = pupilsOrdering
        }
    }

    override fun <T : Any> updateClass(classID: ClassID, field: Field<T>, value: T) {
        when (val fieldName = field.name.substringAfter("SchoolClass.")) {
            "title" -> store.transactional {
                (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()
                    ?: throw SchoolClassDoesNotExistException(classID)).title = value as String
            }
            "shift" -> store.transactional {
                (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()
                    ?: throw SchoolClassDoesNotExistException(classID)).isSecondShift =
                    value is TeachingShift && value == TeachingShift.SECOND
            }
            "id" -> throw ProtectedFieldEditException("ID is protected field")
            else -> throw IllegalArgumentException("Field $fieldName does not exist or belongs to another type")
        }
    }
}
