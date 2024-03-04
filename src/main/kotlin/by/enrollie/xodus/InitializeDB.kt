/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus

import by.enrollie.data_classes.AbsenceType
import by.enrollie.xodus.classes.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.XdModel
import kotlinx.dnq.query.filter
import kotlinx.dnq.query.toList
import kotlinx.dnq.store.container.StaticStoreContainer
import kotlinx.dnq.util.initMetaData
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File

fun initializeDB(): TransientEntityStore {
    XdModel.registerNodes(
        XdJournal,
        XdLesson,
        XdRole,
        XdSchoolClass,
        XdTimetablePlacing,
        XdToken,
        XdUser,
        XdDatabase,
        XdUsersCustomCredential,
        XdAbsence,
        XdDummyAbsence,
        XdSubgroup
    )
    val databaseHome = File(System.getenv("XODUS_DATABASE_HOME") ?: "./database").also {
        it.mkdirs()
    }

    val store = StaticStoreContainer.init(databaseHome, entityStoreName = "eversity") {
        logCacheWarmup = true
        logCacheFreePhysicalMemoryThreshold = 400_000_000L
        logCacheUseNio = true
        useVersion1Format = false
        envGatherStatistics = true
        envMonitorTxnsTimeout = 30000
        envCloseForcedly = true
        memoryUsagePercentage = 65
        gcMinUtilization = 65
        logCacheUseSoftReferences = true // Eversity is expected to run on a server with not much RAM
        logCacheReadAheadMultiple = 2
        if (System.getenv()["XODUS_USE_ENCRYPTION"] != null) {
            cipherId = "jetbrains.exodus.crypto.streamciphers.ChaChaStreamCipherProvider"
            setCipherKey(
                System.getenv()["XODUS_CIPHER_KEY"] ?: error("XODUS_USE_ENCRYPTION is set, but XODUS_CIPHER_KEY isn't")
            )
            cipherBasicIV = System.getenv()["XODUS_CIPHER_BASIC_IV"]?.toLongOrNull()
                ?: error("XODUS_USE_ENCRYPTION is set, but XODUS_CIPHER_BASIC_IV isn't")
        }
    }

    initMetaData(XdModel.hierarchy, store)
    val logger =LoggerFactory.getLogger("by.enrollie.xodus.InitializeDB")
    (store as TransientEntityStore).transactional {
        if(XdDatabase.get().databaseVersion == 1){
            migrateFromV1ToV2(logger)
        }
    }
    return store
}

private fun migrateFromV1ToV2(logger: Logger) {
    logger.info("Migrating database from version 1 to version 2...")
    for (absence in XdAbsence.filter {
        (it.absenceType eq "OTHER_RESPECTFUL")
    }.toList()) {
        logger.debug("Migrating absence record ${absence.id} from v1 (${absence.absenceType}) to v2 (${AbsenceType.COMPETITION.name})")
        absence.absenceType = AbsenceType.COMPETITION.name
    }
    XdDatabase.get().databaseVersion = 2
    logger.info("Done migrating database from version 1 to version 2")
}
