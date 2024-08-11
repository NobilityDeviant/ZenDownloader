package nobility.downloader.core

import io.objectbox.Box
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import nobility.downloader.core.entities.*
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.entities.settings.SettingsMeta
import nobility.downloader.core.entities.settings.SettingsMeta_
import nobility.downloader.core.settings.Defaults
import nobility.downloader.core.settings.Quality
import nobility.downloader.utils.*
import java.io.File

class BoxHelper {

    //personal data such as downloads & history
    private var dataBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(databasePath + "my_data/core"))
        .build()

    //settings data
    private var settingsBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(databasePath + "my_data/settings"))
        .build()

    //cached website series links
    private var linksBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(databasePath + "links"))
        .build()

    //wco series data
    var wcoBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(databasePath + "wco"))
        .build()
    var settingsBox: Box<SettingsMeta> = settingsBoxStore.boxFor(SettingsMeta::class.java)
    var downloadBox: Box<Download> = dataBoxStore.boxFor(Download::class.java)
    var historyBox: Box<SeriesHistory> = dataBoxStore.boxFor(SeriesHistory::class.java)
    var linksBox: Box<CategoryLink> = linksBoxStore.boxFor(CategoryLink::class.java)
    var wcoSeriesBox: Box<Series> = wcoBoxStore.boxFor(Series::class.java)
    var episodesBox: Box<Episode> = wcoBoxStore.boxFor(Episode::class.java)
    var genreBox: Box<Genre> = wcoBoxStore.boxFor(Genre::class.java)

    companion object {

        val shared = BoxHelper()

        val databasePath get() = "${System.getProperty("user.home")}${File.separator}.zen_database${File.separator}"
        val seriesImagesPath: String get() = databasePath + "series_images${File.separator}"

        fun init() {
            loadSettings()
        }

        fun Defaults.update(newValue: Any) {
            setSetting(this, newValue)
        }

        fun Defaults.string(): String {
            return stringSetting(this)
        }

        fun Defaults.boolean(): Boolean {
            return booleanSetting(this)
        }

        fun Defaults.int(): Int {
            return integerSetting(this)
        }

        fun Defaults.intString(): String {
            return int().toString()
        }

        fun Defaults.long(): Long {
            return longSetting(this)
        }

        fun Defaults.float(): Float {
            return floatSetting(this)
        }

        @Suppress("UNUSED")
        fun Defaults.double(): Double {
            return doubleSetting(this)
        }

        private fun loadSettings() {
            if (shared.settingsBox.isEmpty) {
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
                shared.settingsBox.put(meta)
            }
        }


        private fun setSetting(setting: Defaults, value: Any) {
            setSetting(setting.key, value)
        }

        private fun metaForKey(key: String): SettingsMeta? {
            var meta: SettingsMeta? = null
            try {
                shared.settingsBox.query(SettingsMeta_.key.equal(key)).build().use { query ->
                    meta = query.findUniqueOrFirst()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return meta
        }

        private fun stringSetting(setting: Defaults): String {
            val meta = metaForKey(setting.key)
            return meta?.stringVal() ?: ""
        }

        private fun booleanSetting(setting: Defaults): Boolean {
            val meta = metaForKey(setting.key)
            return meta?.booleanVal() ?: false
        }

        private fun integerSetting(setting: Defaults): Int {
            val meta = metaForKey(setting.key)
            return meta?.intVal() ?: 0
        }

        private fun doubleSetting(setting: Defaults): Double {
            val meta = metaForKey(setting.key)
            return meta?.doubleVal() ?: 0.0
        }

        private fun longSetting(setting: Defaults): Long {
            val meta = metaForKey(setting.key)
            return meta?.longVal() ?: 0L
        }

        private fun floatSetting(setting: Defaults): Float {
            val meta = metaForKey(setting.key)
            return meta?.floatVal() ?: 0f
        }

        private fun loadDefaultSettings(
            filter: Defaults? = null
        ) {
            for (setting in Defaults.entries.filter { it != filter }) {
                setSetting(setting.key, setting.value)
            }
        }

        fun resetSettings() {
            loadDefaultSettings(Defaults.FIRST_LAUNCH)
        }

        private fun checkForNewSettings() {
            for (setting in Defaults.entries) {
                val meta = metaForKey(setting.key)
                if (meta == null) {
                    setSetting(setting.key, setting.value)
                }
            }
        }

        fun downloadForSlugAndQuality(slug: String, quality: Quality): Download? {
            try {
                shared.downloadBox.query()
                    .equal(
                        Download_.slug,
                        slug,
                        QueryBuilder.StringOrder.CASE_SENSITIVE
                    ).and().equal(
                        Download_.resolution,
                        quality.resolution.toLong()
                    ).build().use { query ->
                        return query.findUniqueOrFirst()
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }

        fun addSeries(
            series: Series
        ): Boolean {
            val added = wcoAddOrUpdateSeries(series)
            return added
        }

        private fun wcoAddOrUpdateSeries(series: Series): Boolean {
            try {
                shared.wcoSeriesBox.query()
                    .equal(Series_.name, series.name, QueryBuilder.StringOrder.CASE_SENSITIVE).build().use { query ->
                        val queried = query.find()
                        if (queried.isNotEmpty()) {
                            for (s in queried) {
                                if (s.matches(series)) {
                                    return false
                                }
                            }
                            shared.wcoSeriesBox.remove(queried)
                        }
                        shared.wcoSeriesBox.put(series)
                        return true
                    }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return false
        }

        fun wcoSeriesForSlug(slug: String): Series? {
            shared.wcoSeriesBox.query()
                .equal(Series_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .use { query -> return query.findUniqueOrNull() }
        }

        fun wcoSeriesForEpisodeSlug(slug: String): Series? {
            shared.episodesBox.query()
                .equal(Episode_.slug, slug, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .build().use {
                    val episode = it.findUniqueOrNull()
                    return if (episode != null) {
                        wcoSeriesForSlug(episode.seriesSlug)
                    } else null
                }
        }

        fun areIdentityLinksComplete(identity: SeriesIdentity): Boolean {
            shared.linksBox.query()
                .equal(CategoryLink_.type, identity.type.toLong())
                .build().use {
                    return it.find().size >= 500
                }
        }

        fun addIdentityLinkWithSlug(
            slug: String,
            identity: SeriesIdentity
        ): Boolean {
            if (identityLinkForSeriesSlug(slug) == null) {
                shared.linksBox.put(
                    CategoryLink(
                        slug = slug,
                        type = identity.type
                    )
                )
                return true
            }
            return false
        }

        fun addIdentityLinksWithSlug(
            slugs: List<String>,
            identity: SeriesIdentity
        ): Int {
            var added = 0
            slugs.forEach {
                if (addIdentityLinkWithSlug(it, identity)) {
                    added++
                }
            }
            return added
        }

        private fun identityLinkForSeriesSlug(slug: String): CategoryLink? {
            try {
                shared.linksBox.query()
                    .equal(
                        CategoryLink_.slug,
                        slug,
                        QueryBuilder.StringOrder.CASE_INSENSITIVE
                    ).build()
                    .use {
                        return it.findUnique()
                    }
            } catch (ignored: Exception) {
                try {
                    shared.linksBox.query()
                        .contains(
                            CategoryLink_.slug,
                            slug,
                            QueryBuilder.StringOrder.CASE_INSENSITIVE
                        ).build()
                        .use {
                            return it.findUnique()
                        }
                } catch (ignored: Exception) {
                }
            }
            return null
        }

        fun identityForSeriesSlug(slug: String): SeriesIdentity {
            val fixedSlug = slug.fixedSlug()
            val categoryLink = identityLinkForSeriesSlug(fixedSlug)
            if (categoryLink != null) {
                return categoryLink.identity
            }
            return SeriesIdentity.NONE
        }

        suspend fun downloadSeriesImage(series: Series) {
            if (series.imageLink.isEmpty()) {
                return
            }
            val saveFolder = File(seriesImagesPath)
            if (!saveFolder.exists() && !saveFolder.mkdirs()) {
                FrogLog.writeMessage("Unable to download series image: ${series.imageLink}. Save folder was unable to be created.")
                return
            }
            val saveFile = File(
                "${saveFolder.absolutePath}${File.separator}" +
                        Tools.titleForImages(series.name)
            )
            if (!saveFile.exists()) {
                try {
                    Tools.downloadFile(
                        series.imageLink,
                        saveFile,
                        integerSetting(Defaults.TIMEOUT) * 1000,
                        UserAgents.random
                    )
                    FrogLog.writeMessage("Successfully downloaded image: ${series.imageLink}")
                } catch (e: Exception) {
                    FrogLog.writeMessage("Failed to download image for ${series.imageLink}. Error: ${e.localizedMessage}")
                }
            }
        }
    }
}