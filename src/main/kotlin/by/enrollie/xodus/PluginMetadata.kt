/*
 * Copyright © 2021 - 2022
 * Author: Pavel Matusevich
 * Licensed under MIT License
 * All rights are reserved.
 */

package by.enrollie.xodus

import by.enrollie.privateProviders.ApplicationProvider
import by.enrollie.privateProviders.EnvironmentInterface
import by.enrollie.providers.PluginMetadataInterface
import jetbrains.exodus.ExodusException
import jetbrains.exodus.database.TransientEntityChange
import jetbrains.exodus.database.TransientStoreSession
import jetbrains.exodus.database.TransientStoreSessionListener
import jetbrains.exodus.database.exceptions.DataIntegrityViolationException
import jetbrains.exodus.util.CompressBackupUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.dnq.management.DnqStatistics
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

const val PLUGIN_ID = "EversityXodusDB"

class PluginMetadata : PluginMetadataInterface {
    override val author: String = "Enrollie"
    override val pluginApiVersion: String
    override val name: String = PLUGIN_ID
    override val version: String

    init {
        Properties().also {
            it.load(javaClass.classLoader.getResourceAsStream("xodusMetadata.properties"))
        }.also {
            version = it.getProperty("version")
            pluginApiVersion = it.getProperty("apiVersion")
        }
    }

    private var application: ApplicationProvider? = null
    private val logger= LoggerFactory.getLogger("XodusDB")

    override fun onLoad() {
        // do nothing
    }

    override fun onStart(application: ApplicationProvider) {
        this.application = application
        if (application.environment.environmentVariables["XODUS_AUTO_BACKUP"]?.toBooleanStrictOrNull() == true) {
            application.eventScheduler.scheduleRepeating(
                Duration.ofHours(5).toMillis(),
                Duration.ofHours(5).toMillis()
            ) {
                logger.info("Starting backup...")
                DatabaseProviderImplementation.transientEntityStore?.also {
                    val env = it.persistentStore.environment
                    val backupFile = File(
                        env.location,
                        "backup-${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"))
                        }.zip"
                    )
                    CompressBackupUtil.backup(env, backupFile, true)
                    logger.info("Backup completed. File: ${backupFile.absolutePath}")
                }
            }
        }
    }

    override fun onStop() {
        DatabaseProviderImplementation.transientEntityStore?.also {
            it.persistentStore.environment.gc()
            if (System.getenv("XODUS_BACKUP_ON_STOP") == "true") {
                val env = it.persistentStore.environment
                val backupFile = File(
                    env.location,
                    "backup-${
                        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH-mm-ss"))
                    }.zip"
                )
                CompressBackupUtil.backup(env, backupFile, true)
            }
            runBlocking {
                while(true) {
                    try {
                        it.persistentStore.environment.close()
                        break
                    } catch (e: ExodusException) {
                        delay(1000)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        break
                    }
                }
            }
        }
    }

    override fun onUnload() {
        // do nothing
    }
}
