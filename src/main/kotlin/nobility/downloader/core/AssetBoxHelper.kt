package nobility.downloader.core

import io.objectbox.Box
import io.objectbox.BoxStore
import nobility.downloader.core.entities.MyObjectBox
import nobility.downloader.core.entities.settings.SettingsMeta
import nobility.downloader.core.entities.settings.SettingsMeta_
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.AppInfo
import nobility.downloader.utils.findUniqueOrFirst
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyStore

/**
 * Used for the asset updater only.
 * It's loading the settings to check for any updates being disabled
 * and closes the box immediately.
 */
class AssetBoxHelper {

    init {
        try {
            fixTruststore()
        } catch (e: Exception) {
            println("""
                    The app won't be usable without a valid cacerts.
                    First you have to uninstall your current JDK or JRE.
                    Then try installing the JDK for your OS at: https://adoptium.net/temurin/releases/?os=any&arch=any&version=21
                    Note: The JDK must be version 21 or above.
                """.trimIndent())
            e.printStackTrace()
        }
    }

    //settings data
    private var settingsBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(AppInfo.databasePath + "my_data/settings"))
        .build()

    private val settingsBox: Box<SettingsMeta> = settingsBoxStore.boxFor(SettingsMeta::class.java)

    init {
        loadSettings()
    }

    fun booleanSetting(setting: Defaults): Boolean {
        val meta = metaForKey(setting.key)
        return meta?.booleanVal() == true
    }

    fun close() {
        settingsBoxStore.closeThreadResources()
        settingsBoxStore.close()
    }

    private fun loadSettings() {
        if (settingsBox.isEmpty) {
            loadDefaultSettings()
        } else {
            checkForNewSettings()
        }
    }

    @Synchronized
    private fun setSetting(key: String, value: Any?) {
        synchronized(this) {
            var meta = metaForKey(key)
            if (meta != null) {
                if (meta.value() != value) {
                    meta.setValue(value)
                }
            } else {
                meta = SettingsMeta(
                    key = key
                )
                meta.setValue(value)
            }
            settingsBox.put(meta)
        }
    }


    private fun metaForKey(key: String): SettingsMeta? {
        var meta: SettingsMeta? = null
        try {
            settingsBox.query(SettingsMeta_.key.equal(key)).build().use { query ->
                meta = query.findUniqueOrFirst()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return meta
    }

    private fun loadDefaultSettings(
        filter: Defaults? = null
    ) {
        for (setting in Defaults.entries.filter { it != filter }) {
            setSetting(setting.key, setting.value)
        }
    }

    private fun checkForNewSettings() {
        Defaults.entries.forEach {
            val meta = metaForKey(it.key)
            if (meta == null) {
                setSetting(it.key, it.value)
            }
        }
    }

    private fun isDefaultTruststoreUsable(): Boolean {

        val trustStorePath = System.getProperty("javax.net.ssl.trustStore")
            ?: File(System.getProperty("java.home"), "lib/security/cacerts").absolutePath

        val trustStoreFile = File(trustStorePath)

        if (!trustStoreFile.exists()) {
            return false
        }

        return try {
            FileInputStream(trustStoreFile).use { input ->
                val ks = KeyStore.getInstance(KeyStore.getDefaultType())
                ks.load(input, "changeit".toCharArray())
                ks.size() > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun fixTruststore() {
        if (!isDefaultTruststoreUsable()) {
            val inputStream = ClassLoader.getSystemResourceAsStream("/certs/cacerts")
            val fallbackTrustFolder = File(AppInfo.databasePath + "certs/")
            if (!fallbackTrustFolder.exists() && !fallbackTrustFolder.mkdirs()) {
                throw Exception(
                    "Failed to find or create: ${fallbackTrustFolder.absolutePath}"
                )
            }
            val fallbackTrust = File(AppInfo.databasePath + "certs/cacerts")
            if (inputStream != null) {
                Files.copy(
                    inputStream,
                    fallbackTrust.toPath(),
                    StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING
                )
                println("Copied cacerts to: ${fallbackTrust.absolutePath}")
            } else {
                throw Exception("Failed to find cacerts in resources.")
            }
            if (fallbackTrust.exists()) {
                println("Using fallback truststore: ${fallbackTrust.absolutePath}")
                System.setProperty("javax.net.ssl.trustStore", fallbackTrust.absolutePath)
                System.setProperty("javax.net.ssl.trustStorePassword", "changeit")
            } else {
                throw Exception("Failed to export cacerts to: ${fallbackTrust.absolutePath}")
            }
        }
    }
}