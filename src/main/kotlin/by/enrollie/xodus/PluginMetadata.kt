/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus

import by.enrollie.privateProviders.ApplicationProvider
import by.enrollie.providers.PluginMetadataInterface
import jetbrains.exodus.util.CompressBackupUtil
import java.io.File
import java.util.*

const val PLUGIN_ID = "EversityXodusDB"

class PluginMetadata : PluginMetadataInterface {
    override val author: String = "Enrollie"
    override val title: String = "Eversity-XodusDB"
    override val version: String by lazy {
        Properties().also {
            it.load(javaClass.classLoader.getResourceAsStream("metadata.properties"))
        }.getProperty("version")
    }

    override fun onLoad() {
        // do nothing
    }

    override fun onStart(application: ApplicationProvider) {
        // do nothing
    }

    override fun onStop() {
        DatabaseProviderImplementation.transientEntityStore?.also {
            it.persistentStore.environment.gc()
            if (System.getenv("XODUS_BACKUP_ON_STOP") == "true") {
                val env = it.persistentStore.environment
                CompressBackupUtil.backup(env, File(env.location, "backups"), true)
            }
        }
    }

    override fun onUnload() {
        // do nothing
    }
}
