/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus.impl

import by.enrollie.data_classes.TimetableCell
import by.enrollie.data_classes.TimetablePlaces
import by.enrollie.providers.DatabaseTimetablePlacingProviderInterface
import by.enrollie.xodus.classes.XdTimetablePlacing
import by.enrollie.xodus.util.toJodaDateTime
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.query.*
import org.joda.time.DateTime
import java.time.LocalDate

class DatabaseTimetablePlacingProviderImpl(private val store: TransientEntityStore) :
    DatabaseTimetablePlacingProviderInterface {
    override fun getTimetablePlaces(): TimetablePlaces {
        return store.transactional(readonly = true) {
            XdTimetablePlacing.query((XdTimetablePlacing::effectiveSince le DateTime.now()) and (XdTimetablePlacing::effectiveUntil eq null))
                .first().let {
                    TimetablePlaces(it.getFirstShift().map { TimetableCell(it.key, it.value) }, it.getSecondShift().map { TimetableCell(it.key, it.value) })
                }
        }
    }

    override fun getTimetablePlaces(date: LocalDate): TimetablePlaces? {
        return store.transactional(readonly = true) {
            XdTimetablePlacing.query(
                (XdTimetablePlacing::effectiveSince le date.atStartOfDay()
                    .toJodaDateTime()) and ((XdTimetablePlacing::effectiveUntil eq null) or (XdTimetablePlacing::effectiveUntil ge date.atStartOfDay()
                    .toJodaDateTime()))
            ).firstOrNull()?.let {
                TimetablePlaces(it.getFirstShift().map { TimetableCell(it.key, it.value) }, it.getSecondShift().map { TimetableCell(it.key, it.value) })
            }
        }
    }

    override fun updateTimetablePlaces(timetablePlaces: TimetablePlaces) {
        store.transactional {
            XdTimetablePlacing.query(XdTimetablePlacing::effectiveUntil eq null).firstOrNull()?.also {
                it.effectiveUntil = DateTime.now().withTimeAtStartOfDay()
            }
            XdTimetablePlacing.new {
                setFirstShift(timetablePlaces.firstShift.associate { it.place to it.timeConstraints })
                setSecondShift(timetablePlaces.secondShift.associate { it.place to it.timeConstraints })
                effectiveSince = DateTime.now().withTimeAtStartOfDay()
            }
        }
    }
}
