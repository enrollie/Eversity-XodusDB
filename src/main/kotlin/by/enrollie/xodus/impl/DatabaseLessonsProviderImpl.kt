/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.*
import by.enrollie.exceptions.LessonDoesNotExistException
import by.enrollie.exceptions.ProtectedFieldEditException
import by.enrollie.exceptions.SchoolClassDoesNotExistException
import by.enrollie.providers.DatabaseLessonsProviderInterface
import by.enrollie.providers.Event
import by.enrollie.xodus.classes.*
import by.enrollie.xodus.util.isBetweenOrEqual
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.*
import java.time.LocalDate

class DatabaseLessonsProviderImpl(private val store: TransientEntityStore) : DatabaseLessonsProviderInterface {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private val _eventsFlow = MutableSharedFlow<DatabaseLessonsProviderInterface.LessonEvent>()
    override val eventsFlow: SharedFlow<DatabaseLessonsProviderInterface.LessonEvent>
        get() = _eventsFlow

    private fun findOrNewLesson(lesson: Lesson): XdLesson {
        val newLesson = XdLesson.findOrNew {
            id = lesson.id.toLong()
        }
        newLesson.title = lesson.title
        newLesson.setDate(lesson.date)
        newLesson.placeInTimetable = lesson.placeInTimetable
        newLesson.teacher = lesson.teacher
        newLesson.subgroupID = lesson.subgroupID
        newLesson.journalID = lesson.journalID
        newLesson.schoolClass = XdSchoolClass.query(XdSchoolClass::id eq lesson.classID).firstOrNull()
            ?: throw SchoolClassDoesNotExistException(lesson.classID)
        return newLesson
    }

    override fun createLesson(lesson: Lesson) {
        store.transactional {
            findOrNewLesson(lesson).toLesson()
        }.also {
            coroutineScope.launch {
                _eventsFlow.emit(DatabaseLessonsProviderInterface.LessonEvent(Event.EventType.CREATED, it, null))
            }
        }
    }

    override fun createOrUpdateLessons(lessons: List<Lesson>) {
        store.transactional {
            lessons.map {
                val snapshot = XdLesson.query(XdLesson::id eq it.id.toLong()).firstOrNull()?.toLesson()
                findOrNewLesson(it).toLesson() to snapshot
            }
        }.also {
            coroutineScope.launch {
                it.forEach { lesson ->
                    if (lesson.second == null) {
                        _eventsFlow.emit(
                            DatabaseLessonsProviderInterface.LessonEvent(
                                Event.EventType.CREATED, lesson.first, null
                            )
                        )
                    } else {
                        if (lesson.first != lesson.second)
                            _eventsFlow.emit(
                                DatabaseLessonsProviderInterface.LessonEvent(
                                    Event.EventType.UPDATED, lesson.first, lesson.second
                                )
                            )
                    }
                }
            }
        }
    }

    override fun createSubgroup(
        subgroupID: SubgroupID, schoolClassID: ClassID, subgroupName: String, members: List<UserID>?
    ) {
        store.transactional {
            XdSubgroup.findOrNew {
                id = subgroupID
            }.apply {
                this.subgroupTitle = subgroupName
                this.schoolClass = XdSchoolClass.query(XdSchoolClass::id eq schoolClassID).firstOrNull()
                    ?: throw SchoolClassDoesNotExistException(schoolClassID)
                members?.let {
                    this.currentMembers.addAll(
                        XdRole.query(
                            (XdRole::roleID eq Roles.CLASS.STUDENT.getID()) and (XdRole::user.matches(
                                XdUser::id inValues it
                            )) and (XdRole::revoked eq null)
                        )
                    )
                }
            }
        }
    }

    override fun createSubgroups(subgroupsList: List<Subgroup>) {
        store.transactional {
            subgroupsList.forEach { sub: Subgroup ->
                XdSubgroup.findOrNew {
                    id = sub.id
                }.apply {
                    schoolClass = XdSchoolClass.query(XdSchoolClass::id eq sub.classID).firstOrNull()
                        ?: throw SchoolClassDoesNotExistException(sub.classID)
                    subgroupTitle = sub.title
                    currentMembers.clear()
                    currentMembers.addAll(
                        XdRole.query(
                            (XdRole::roleID eq Roles.CLASS.STUDENT.getID()) and (XdRole::revoked eq null) and (XdRole::user.matches(
                                XdUser::id inValues sub.members
                            ))
                        ).toList().filter { it.getRoleInformation()[Roles.CLASS.STUDENT.classID] == sub.classID }
                    )
                    if (currentMembers.size() < sub.members.size) {
                        throw IllegalArgumentException("Not all supposed members are found in class ${sub.classID}")
                    }
                }
            }
        }
    }

    override fun deleteLesson(lessonID: LessonID) {
        store.transactional {
            (XdLesson.query(XdLesson::id eq lessonID.toLong()).firstOrNull()
                ?: throw LessonDoesNotExistException(lessonID)).also {
                val lesson = it.toLesson()
                coroutineScope.launch {
                    _eventsFlow.emit(
                        DatabaseLessonsProviderInterface.LessonEvent(
                            Event.EventType.DELETED, lesson, null
                        )
                    )
                }
            }.delete()
        }
    }

    override fun deleteLessons(lessonIDs: List<LessonID>) {
        val mapped = lessonIDs.map { it.toLong() }
        store.transactional {
            XdLesson.query(XdLesson::id inValues mapped).toList().onEach {
                val lesson = it.toLesson()
                coroutineScope.launch {
                    _eventsFlow.emit(
                        DatabaseLessonsProviderInterface.LessonEvent(
                            Event.EventType.DELETED, lesson, null
                        )
                    )
                }
                it.delete()
            }
        }
    }

    override fun deleteSubgroup(subgroupID: SubgroupID) {
        store.transactional {
            (XdSubgroup.query(XdSubgroup::id eq subgroupID).firstOrNull()?.delete()
                ?: throw NoSuchElementException("Subgroup with id $subgroupID does not exist"))
        }
    }

    override fun getAllLessons(): List<Lesson> {
        return store.transactional {
            XdLesson.all().toList().map { it.toLesson() }
        }
    }

    override fun getJournalTitles(journals: List<JournalID>): Map<JournalID, String?> =
        store.transactional(readonly = true) {
            val existingJournals = XdJournal.filter { it.id isIn journals }.toList().associate { it.id to it.title }
            journals.associateWith { existingJournals[it] }
        }

    override fun getLesson(lessonID: LessonID): Lesson? {
        return store.transactional(readonly = true) {
            XdLesson.query(XdLesson::id eq lessonID.toLong()).firstOrNull()?.toLesson()
        }
    }

    override fun getLessons(date: LocalDate): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.query(XdLesson::dateAsString eq date.toString()).toList().map { it.toLesson() }
        }
    }

    override fun getLessons(datesRange: Pair<LocalDate, LocalDate>): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.all().toList().filter {
                it.getDate().isBetweenOrEqual(datesRange.first, datesRange.second)
            }.map { it.toLesson() }
        }
    }

    override fun getLessonsForClass(classID: ClassID): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.query(XdLesson::schoolClass.matches(XdSchoolClass::id eq classID)).toList().map { it.toLesson() }
        }
    }

    override fun getLessonsForClass(classID: ClassID, date: LocalDate): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.query(XdLesson::schoolClass.matches(XdSchoolClass::id eq classID) and (XdLesson::dateAsString eq date.toString()))
                .toList().map { it.toLesson() }
        }
    }

    override fun getLessonsForClass(classID: ClassID, datesRange: Pair<LocalDate, LocalDate>): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.filter {
                (it.schoolClass.id eq classID) and (it.getDate()
                    .isBetweenOrEqual(datesRange.first, datesRange.second) eq true)
            }.toList().map { it.toLesson() }
        }
    }

    override fun getLessonsForTeacher(teacherID: UserID): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.query(XdLesson::teacher eq teacherID).toList().map { it.toLesson() }
        }
    }

    override fun getLessonsForTeacher(teacherID: UserID, date: LocalDate): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.query(XdLesson::teacher eq teacherID and (XdLesson::dateAsString eq date.toString())).toList()
                .map { it.toLesson() }
        }
    }

    override fun getLessonsForTeacher(teacherID: UserID, datesRange: Pair<LocalDate, LocalDate>): List<Lesson> {
        return store.transactional(readonly = true) {
            XdLesson.filter {
                (it.teacher eq teacherID) and (it.getDate()
                    .isBetweenOrEqual(datesRange.first, datesRange.second) eq true)
            }.toList().map { it.toLesson() }
        }
    }

    override fun getSubgroup(subgroupID: SubgroupID): Subgroup? = store.transactional(readonly = true) {
        XdSubgroup.query(XdSubgroup::id eq subgroupID).firstOrNull()?.let {
            Subgroup(
                it.id,
                it.subgroupTitle,
                it.schoolClass.id,
                it.currentMembers.query(XdRole::revoked eq null).toList().map { it.user.id }
            )
        }
    }

    override fun setJournalTitles(mappedTitles: Map<JournalID, String>) {
        store.transactional {
            mappedTitles.forEach {
                XdJournal.findOrNew {
                    id = it.key
                }.title = it.value
            }
        }
    }

    override fun <T : Any> updateSubgroup(subgroupID: SubgroupID, field: Field<T>, value: T) {
        store.transactional {
            val subgroup = XdSubgroup.query(XdSubgroup::id eq subgroupID).firstOrNull()
                ?: throw IllegalArgumentException("Subgroup with ID $subgroupID was not found")
            subgroup.apply {
                when (field.name.split(".").last()) {
                    "title" -> {
                        subgroup.subgroupTitle = value as String
                    }

                    "members" -> {
                        subgroup.currentMembers.clear()
                        @Suppress("UNCHECKED_CAST")
                        val usersList: List<UserID> =
                            value as List<UserID> // We just assume that Field was created with a valid value
                        subgroup.currentMembers.addAll(
                            XdRole.query(
                                (XdRole::roleID eq Roles.CLASS.STUDENT.getID()) and (XdRole::user.matches(
                                    XdUser::id inValues (usersList)
                                ))
                            ).toList().filter {
                                it.getRoleInformation()[Roles.CLASS.STUDENT.classID] == subgroup.schoolClass.id
                            }
                        )
                    }

                    "id", "classID" -> throw ProtectedFieldEditException(field.name)
                    else -> throw IllegalArgumentException("Field ${field.name} is not a valid field for Subgroup")
                }
            }
        }
    }
}
