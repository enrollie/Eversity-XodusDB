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
import by.enrollie.xodus.classes.XdSubgroup
import com.github.benmanes.caffeine.cache.Caffeine
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.*
import java.time.Duration

class DatabaseClassesProviderImpl(private val store: TransientEntityStore) : DatabaseClassesProviderInterface {
    private val classesCache =
        Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).expireAfterWrite(Duration.ofMinutes(5))
            .build<Int, SchoolClass>()
    private val orderingCache =
        Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(5)).expireAfterWrite(Duration.ofMinutes(5))
            .build<Int, List<Pair<UserID, Int>>>()

    override fun batchCreateClasses(classes: List<SchoolClass>) = store.transactional {
        classes.forEach {
            val clazz = XdSchoolClass.findOrNew {
                id = it.id
            }
            clazz.title = it.title
            clazz.shift = it.shift
            classesCache.put(clazz.id, clazz.asSchoolClass())
        }
    }

    override fun createClass(classData: SchoolClass) {
        store.transactional {
            val clazz = XdSchoolClass.findOrNew {
                id = classData.id
            }
            clazz.shift = classData.shift
            clazz.title = classData.title
            classesCache.put(clazz.id, clazz.asSchoolClass())
        }
    }

    override fun getClass(classID: ClassID): SchoolClass? {
        return classesCache.get(classID) {
            store.transactional {
                XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()?.asSchoolClass()
            }
        }
    }

    override fun getClasses(): List<SchoolClass> {
        return store.transactional {
            XdSchoolClass.all().toList().map { it.asSchoolClass() }
        }
    }

    override fun getPupilsOrdering(classID: ClassID): List<Pair<UserID, Int>> = orderingCache.get(classID) {
        store.transactional(readonly = true) {
            (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull() ?: throw SchoolClassDoesNotExistException(
                classID
            )).getOrdering()
        }
    }

    override fun getSubgroups(classID: ClassID): List<Subgroup> {
        return store.transactional(readonly = true) {
            if (XdSchoolClass.query(XdSchoolClass::id eq classID).isEmpty)
                throw SchoolClassDoesNotExistException(classID)
            XdSubgroup.query(XdSubgroup::schoolClass.matches(XdSchoolClass::id eq classID)).toList().map {
                Subgroup(
                    it.id,
                    it.subgroupTitle,
                    it.schoolClass.id,
                    it.currentMembers.toList().map { pupilRole -> pupilRole.user.id })
            }
        }
    }


    override fun setPupilsOrdering(classID: ClassID, pupilsOrdering: List<Pair<UserID, Int>>) {
        orderingCache.invalidate(classID)
        store.transactional {
            (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull() ?: throw SchoolClassDoesNotExistException(
                classID
            )).setOrdering(pupilsOrdering)
        }
    }

    override fun <T : Any> updateClass(classID: ClassID, field: Field<T>, value: T) {
        classesCache.invalidate(classID)
        when (val fieldName = field.name.substringAfter("SchoolClass.")) {
            "title" -> store.transactional {
                (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()
                    ?: throw SchoolClassDoesNotExistException(classID)).title = value as String
            }

            "shift" -> store.transactional {
                (XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()
                    ?: throw SchoolClassDoesNotExistException(classID)).shift = value as TeachingShift
            }

            "id" -> throw ProtectedFieldEditException("ID is protected field")
            else -> throw IllegalArgumentException("Field $fieldName does not exist or belongs to another type")
        }
    }
}
