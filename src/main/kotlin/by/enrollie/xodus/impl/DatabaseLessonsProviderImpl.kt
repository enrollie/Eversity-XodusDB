/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.ClassID
import by.enrollie.data_classes.JournalID
import by.enrollie.data_classes.Lesson
import by.enrollie.data_classes.LessonID
import by.enrollie.exceptions.SchoolClassDoesNotExistException
import by.enrollie.providers.DatabaseLessonsProviderInterface
import by.enrollie.xodus.classes.XdJournal
import by.enrollie.xodus.classes.XdLesson
import by.enrollie.xodus.classes.XdSchoolClass
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.creator.findOrNew
import kotlinx.dnq.query.*
import java.time.LocalDate

class DatabaseLessonsProviderImpl(private val store: TransientEntityStore) : DatabaseLessonsProviderInterface {
    private fun findOrNewLesson(lesson: Lesson) {
        val newLesson = XdLesson.findOrNew {
            id = lesson.id
        }
        newLesson.title = lesson.title
        newLesson.date = lesson.date
        newLesson.placeInTimetable = lesson.placeInTimetable
        newLesson.teachers = lesson.teachers
        newLesson.subgroupID = lesson.subgroupID
        newLesson.journalID = lesson.journalID
        newLesson.schoolClass = XdSchoolClass.query(XdSchoolClass::id eq lesson.classID).firstOrNull()
            ?: throw SchoolClassDoesNotExistException(lesson.classID)
    }

    override fun createLesson(lesson: Lesson) {
        store.transactional {
            findOrNewLesson(lesson)
        }
    }

    override fun createOrUpdateLessons(lessons: List<Lesson>) {
        store.transactional {
            lessons.forEach {
                findOrNewLesson(it)
            }
        }
    }

    override fun deleteLesson(lessonID: LessonID) {
        store.transactional {
            XdLesson.query(XdLesson::id eq lessonID).firstOrNull()?.delete()
        }
    }

    override fun getJournalTitles(journals: List<JournalID>): Map<JournalID, String?> =
        store.transactional(readonly = true) {
            val existingJournals = XdJournal.filter { it.id isIn journals }.toList().associate { it.id to it.title }
            journals.associateWith { existingJournals[it] }
        }

    override fun getLesson(lessonID: LessonID): Lesson? {
        return store.transactional(readonly = true) {
            XdLesson.query(XdLesson::id eq lessonID).firstOrNull()?.toLesson()
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
                it.schoolClass.id eq classID and (it.date between (datesRange.first to datesRange.second))
            }.toList().map { it.toLesson() }
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

}
