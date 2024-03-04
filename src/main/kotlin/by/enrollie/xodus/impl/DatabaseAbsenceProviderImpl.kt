/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.*
import by.enrollie.exceptions.*
import by.enrollie.providers.DatabaseAbsenceProviderInterface
import by.enrollie.providers.Event
import by.enrollie.xodus.classes.*
import by.enrollie.xodus.util.isBetweenOrEqual
import by.enrollie.xodus.util.toJavaLocalDateTime
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.*
import kotlinx.dnq.util.isDefined
import org.joda.time.DateTime
import java.time.Duration
import java.time.LocalDate

class DatabaseAbsenceProviderImpl(private val store: TransientEntityStore) : DatabaseAbsenceProviderInterface {
    private val _eventsFlow: MutableSharedFlow<DatabaseAbsenceProviderInterface.AbsenceEvent> = MutableSharedFlow()
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val cache: Cache<AbsenceID, AbsenceRecord> =
        Caffeine.newBuilder().expireAfterAccess(Duration.ofMinutes(1)).expireAfterWrite(Duration.ofMinutes(1)).build()

    private fun createAbsenceInTransaction(
        absence: DatabaseAbsenceProviderInterface.NewAbsenceRecord
    ): XdAbsence {
        val schoolClass = XdSchoolClass.query(XdSchoolClass::id eq absence.classID).firstOrNull()
            ?: throw SchoolClassDoesNotExistException(absence.classID)
        val creator = XdRole.query(XdRole::uniqueID eq absence.creatorRoleID).firstOrNull()
            ?: throw NoMatchingRoleException(absence.creatorRoleID)
        val student = XdUser.query(XdUser::id eq absence.studentUserID).firstOrNull()
            ?: throw UserDoesNotExistException(absence.studentUserID)
        XdDummyAbsence.query(XdDummyAbsence::schoolClass eq schoolClass and (XdDummyAbsence::dateAsString eq absence.absenceDate.toString()))
            .toList().forEach {
                it.delete()
            }
        return XdAbsence.findOrNew {
            this.schoolClass = schoolClass
            this.student = student
            absenceDateString = absence.absenceDate.toString()
        }.apply {
            if (isDefined(XdAbsence::id)) throw AbsenceRecordsConflictException("Absence record already exists")
            id = XdDatabase.get().absenceIDs.increment()
            if (!isDefined(XdAbsence::createdBy)) {
                createdBy = creator
                createdAt = DateTime.now()
            } else {
                updatedBy = creator
                updatedAt = DateTime.now()
            }
            absenceType = absence.absenceType.name
            lessonsList = absence.skippedLessons.toSet()
        }
    }

    override val eventsFlow: SharedFlow<DatabaseAbsenceProviderInterface.AbsenceEvent>
        get() = _eventsFlow

    override fun createAbsence(record: DatabaseAbsenceProviderInterface.NewAbsenceRecord): AbsenceRecord {
        return store.transactional {
            createAbsenceInTransaction(record).getAsAbsenceRecord()
        }.also {
            cache.put(it.id, it)
            coroutineScope.launch {
                _eventsFlow.emit(
                    DatabaseAbsenceProviderInterface.AbsenceEvent(
                        Event.EventType.CREATED, it, null
                    )
                )
            }
        }
    }

    override fun createAbsences(absences: List<DatabaseAbsenceProviderInterface.NewAbsenceRecord>): List<AbsenceRecord> {
        return store.transactional {
            absences.map {
                createAbsenceInTransaction(it).getAsAbsenceRecord()
            }
        }.also {
            cache.putAll(it.associateBy { it.id })
            coroutineScope.launch {
                it.forEach {
                    _eventsFlow.emit(
                        DatabaseAbsenceProviderInterface.AbsenceEvent(
                            Event.EventType.CREATED, it, null
                        )
                    )
                }
            }
        }
    }

    override fun getAbsence(absenceID: AbsenceID): AbsenceRecord? {
        return cache.getIfPresent(absenceID) ?: store.transactional {
            XdAbsence.query(XdAbsence::id eq absenceID).firstOrNull()?.getAsAbsenceRecord()
        }?.also {
            cache.put(it.id, it)
        }
    }

    override fun getAbsences(date: LocalDate): List<AbsenceRecord> {
        return store.transactional {
            XdAbsence.query(XdAbsence::absenceDateString eq date.toString()).toList().map {
                it.getAsAbsenceRecord()
            }
        }.also {
            cache.putAll(it.associateBy { it.id })
        }
    }

    override fun getAbsences(datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord> {
        return store.transactional {
            XdAbsence.all().toList().filter {
                it.getDate().isBetweenOrEqual(datesRange.first, datesRange.second)
            }.map {
                it.getAsAbsenceRecord()
            }
        }.also {
            cache.putAll(it.associateBy { it.id })
        }
    }

    override fun getAbsencesForClass(classID: ClassID, date: LocalDate): List<AbsenceRecord> {
        return store.transactional(readonly = true) {
            XdAbsence.filter {
                it.schoolClass.id eq classID and (it.absenceDateString eq date.toString())
            }.toList().map { it.getAsAbsenceRecord() }
        }
    }

    override fun getAbsencesForClass(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord> {
        return store.transactional(readonly = true) {
            XdAbsence.filter {
                it.schoolClass.id eq classID
            }.toList().map { it.getAsAbsenceRecord() }
                .filter { it.absenceDate.isBetweenOrEqual(datesRange.first, datesRange.second) }
        }
    }

    override fun getAbsencesForUser(userID: UserID, datesRange: Pair<LocalDate, LocalDate>): List<AbsenceRecord> {
        return store.transactional(readonly = true) {
            XdAbsence.filter {
                it.student.id eq userID
            }.toList().map { it.getAsAbsenceRecord() }
                .filter { it.absenceDate.isBetweenOrEqual(datesRange.first, datesRange.second) }
        }
    }

    override fun getAllAbsences(): List<AbsenceRecord> {
        return store.transactional(readonly = true) {
            XdAbsence.all().toList().map { it.getAsAbsenceRecord() }
        }.also {
            cache.putAll(it.associateBy { it.id })
        }
    }

    override fun getClassesWithoutAbsenceInfo(date: LocalDate): List<ClassID> {
        return store.transactional(readonly = true) {
            XdSchoolClass.query(
                not(XdSchoolClass::id inValues (XdAbsence.query(XdAbsence::absenceDateString eq date.toString())
                    .toList()
                    .map { it.schoolClass.id } + XdDummyAbsence.query(XdDummyAbsence::dateAsString eq date.toString())
                    .toList().map { it.schoolClass.id }).toSet()
                )
            ).toList().map { it.id }
        }
    }

    override fun getClassesWithoutAbsenceInfo(datesRange: Pair<LocalDate, LocalDate>): List<ClassID> {
        return store.transactional(readonly = true) {
            XdSchoolClass.query(not(XdSchoolClass::id inValues (XdAbsence.all().toList().filter {
                it.getDate().isBetweenOrEqual(datesRange.first, datesRange.second)
            }.map { it.schoolClass.id }.distinct() + XdDummyAbsence.all().toList()
                .filter { it.getDate().isBetweenOrEqual(datesRange.first, datesRange.second) }.map { it.schoolClass.id }
                .distinct()))).toList().map { it.id }
        }
    }

    override fun getDatesWithoutAbsenceInfo(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<LocalDate> {
        return store.transactional(readonly = true) {
            val datesWithInfo = XdAbsence.filter { it.schoolClass.id eq classID }.toList().filter {
                it.getDate().isBetweenOrEqual(datesRange.first, datesRange.second)
            }.map { it.getDate() }.distinct() + XdDummyAbsence.filter { it.schoolClass.id eq classID }.toList()
                .filter {
                    it.getDate().isBetweenOrEqual(datesRange.first, datesRange.second)
                }.map { it.getDate() }.distinct()
            return@transactional (datesRange.first.datesUntil(datesRange.second.plusDays(1))).toList().filter {
                !datesWithInfo.contains(it)
            }
        }
    }

    override fun markClassAsDataRich(sentByRole: String, classID: ClassID, date: LocalDate) {
        store.transactional {
            XdDummyAbsence.findOrNew {
                this.schoolClass = XdSchoolClass.query(XdSchoolClass::id eq classID).firstOrNull()
                    ?: throw SchoolClassDoesNotExistException(classID)
                this.dateAsString = date.toString()
            }.let { dummy ->
                if (!dummy.isDefined(XdDummyAbsence::createdBy)) {
                    dummy.createdBy =
                        XdRole.query(XdRole::uniqueID eq sentByRole).firstOrNull() ?: throw NoMatchingRoleException(
                            sentByRole
                        )
                    dummy.createdAt = DateTime.now()
                    XdAbsence.filter { // Clear all absence records since XdDummyAbsence is defined just now
                        it.schoolClass.id eq classID and (it.absenceDateString eq date.toString())
                    }.toList().map {
                        val snapshot = it.getAsAbsenceRecord()
                        it.lessonsList = setOf()
                        it.updatedBy = dummy.createdBy
                        it.updatedAt = dummy.createdAt
                        val updated = snapshot.copy(
                            lessonsList = listOf(),
                            lastUpdatedBy = AuthorizedChangeAuthor(
                                dummy.createdBy.user.asUser(),
                                dummy.createdBy.getAsRoleData()
                            ),
                            lastUpdated = dummy.createdAt.toJavaLocalDateTime()
                        )
                        updated to snapshot
                    }
                } else listOf()
            }
        }.also { list ->
            coroutineScope.launch {
                list.forEach {
                    _eventsFlow.emit(
                        DatabaseAbsenceProviderInterface.AbsenceEvent(
                            Event.EventType.UPDATED, it.first, it.second
                        )
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> updateAbsence(updatedByRole: String, absenceID: AbsenceID, field: Field<T>, value: T) {
        store.transactional {
            (XdAbsence.query(XdAbsence::id eq absenceID).firstOrNull()
                ?: throw NoSuchElementException("No absence with ID $absenceID was found")).let { absence ->
                val snapshot = absence.getAsAbsenceRecord()
                when (field.name.split(".").last()) {
                    "absenceType" -> {
                        absence.absenceType = (value as AbsenceType).name
                        absence.updatedBy = XdRole.query(XdRole::uniqueID eq updatedByRole).firstOrNull()
                            ?: throw NoMatchingRoleException(updatedByRole)
                        absence.updatedAt = DateTime.now()
                    }
                    "lessonsList" -> {
                        absence.lessonsList = (value as List<TimetablePlace>).toSet()
                        absence.updatedBy =
                            XdRole.query(XdRole::uniqueID eq updatedByRole).firstOrNull()
                                ?: throw NoMatchingRoleException(updatedByRole)
                        absence.updatedAt = DateTime.now()
                    }
                    else -> throw ProtectedFieldEditException("Cannot edit field ${field.name} as either it is protected or is not defined in the AbsenceRecord")
                }
                absence.getAsAbsenceRecord() to snapshot
            }
        }.also {
            cache.invalidate(absenceID)
            coroutineScope.launch {
                _eventsFlow.emit(
                    DatabaseAbsenceProviderInterface.AbsenceEvent(
                        Event.EventType.UPDATED, it.first, it.second
                    )
                )
            }
        }
    }
}
