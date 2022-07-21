/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus

import by.enrollie.xodus.classes.*
import jetbrains.exodus.database.TransientEntityStore
import kotlinx.dnq.XdModel
import kotlinx.dnq.store.container.StaticStoreContainer
import kotlinx.dnq.util.initMetaData
import java.io.File

fun initializeDB(): TransientEntityStore {
    XdModel.registerNodes(XdJournal, XdLesson, XdRole, XdSchoolClass, XdTimetablePlacing, XdToken, XdUser)
    val databaseHome = File(System.getenv("XODUS_DATABASE_HOME") ?: "./database").also {
        it.mkdirs()
    }

    val store = StaticStoreContainer.init(databaseHome, entityStoreName = "eversity") {
        gcRunEvery = 1800
        logCacheWarmup = true
        logCacheFreePhysicalMemoryThreshold = 400_000_000L
        logCacheUseNio = true
        useVersion1Format = false
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
    return store
}
