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

/**
 * Used for the asset updater only.
 * It's loading the settings to check for any updates being disabled
 * and closes the box immediately.
 */
class AssetBoxHelper {

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
}