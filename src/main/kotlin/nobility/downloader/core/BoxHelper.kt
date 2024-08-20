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
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.findUniqueOrFirst
import nobility.downloader.utils.findUniqueOrNull
import nobility.downloader.utils.fixedSlug
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
        .directory(File(wcoPath + "links"))
        .build()

    //for wco genres and misc data
    var wcoBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(wcoPath + "data"))
        .build()

    private var dubbedBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(seriesPath + "dubbed"))
        .build()

    private var subbedBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(seriesPath + "subbed"))
        .build()

    private var movieBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(seriesPath + "movies"))
        .build()

    private var cartoonBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(seriesPath + "cartoon"))
        .build()

    //series not in any other category
    private var miscBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(seriesPath + "misc"))
        .build()

    val settingsBox: Box<SettingsMeta> = settingsBoxStore.boxFor(SettingsMeta::class.java)
    val downloadBox: Box<Download> = dataBoxStore.boxFor(Download::class.java)
    val historyBox: Box<SeriesHistory> = dataBoxStore.boxFor(SeriesHistory::class.java)
    val dubbedSeriesBox: Box<Series> = dubbedBoxStore.boxFor(Series::class.java)
    val dubbedEpisodeBox: Box<Episode> = dubbedBoxStore.boxFor(Episode::class.java)
    val subbedSeriesBox: Box<Series> = subbedBoxStore.boxFor(Series::class.java)
    val subbedEpisodeBox: Box<Episode> = subbedBoxStore.boxFor(Episode::class.java)
    val cartoonSeriesBox: Box<Series> = cartoonBoxStore.boxFor(Series::class.java)
    val cartoonEpisodeBox: Box<Episode> = cartoonBoxStore.boxFor(Episode::class.java)
    val moviesSeriesBox: Box<Series> = movieBoxStore.boxFor(Series::class.java)
    val moviesEpisodeBox: Box<Episode> = movieBoxStore.boxFor(Episode::class.java)
    val miscSeriesBox: Box<Series> = miscBoxStore.boxFor(Series::class.java)
    val miscEpisodeBox: Box<Episode> = miscBoxStore.boxFor(Episode::class.java)

    val wcoLinksBox: Box<CategoryLink> = linksBoxStore.boxFor(CategoryLink::class.java)
    val wcoGenreBox: Box<Genre> = wcoBoxStore.boxFor(Genre::class.java)

    companion object {

        val shared = BoxHelper()

        val databasePath get() = "${System.getProperty("user.home")}${File.separator}.zen_database${File.separator}"
        val wcoPath get() = databasePath + File.separator + "wco" + File.separator
        val seriesPath get() = databasePath + File.separator + "series" + File.separator
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
            series: Series,
            identity: SeriesIdentity
        ): Boolean {
            try {
                when (identity) {
                    SeriesIdentity.DUBBED -> {
                        shared.dubbedSeriesBox.query()
                            .equal(
                                Series_.name,
                                series.name,
                                QueryBuilder.StringOrder.CASE_INSENSITIVE
                            ).build().use { query ->
                                val queried = query.find()
                                if (queried.isNotEmpty()) {
                                    for (s in queried) {
                                        if (s.matches(series)) {
                                            return false
                                        }
                                    }
                                    shared.dubbedSeriesBox.remove(queried)
                                }
                                shared.dubbedSeriesBox.put(series)
                                return true
                            }
                    }
                    SeriesIdentity.SUBBED -> {
                        shared.subbedSeriesBox.query()
                            .equal(
                                Series_.name,
                                series.name,
                                QueryBuilder.StringOrder.CASE_INSENSITIVE
                            ).build().use { query ->
                                val queried = query.find()
                                if (queried.isNotEmpty()) {
                                    for (s in queried) {
                                        if (s.matches(series)) {
                                            return false
                                        }
                                    }
                                    shared.subbedSeriesBox.remove(queried)
                                }
                                shared.subbedSeriesBox.put(series)
                                return true
                            }
                    }
                    SeriesIdentity.CARTOON -> {
                        shared.cartoonSeriesBox.query()
                            .equal(
                                Series_.name,
                                series.name,
                                QueryBuilder.StringOrder.CASE_INSENSITIVE
                            ).build().use { query ->
                                val queried = query.find()
                                if (queried.isNotEmpty()) {
                                    for (s in queried) {
                                        if (s.matches(series)) {
                                            return false
                                        }
                                    }
                                    shared.cartoonSeriesBox.remove(queried)
                                }
                                shared.cartoonSeriesBox.put(series)
                                return true
                            }
                    }
                    SeriesIdentity.MOVIE -> {
                        shared.moviesSeriesBox.query()
                            .equal(
                                Series_.name,
                                series.name,
                                QueryBuilder.StringOrder.CASE_INSENSITIVE
                            ).build().use { query ->
                                val queried = query.find()
                                if (queried.isNotEmpty()) {
                                    for (s in queried) {
                                        if (s.matches(series)) {
                                            return false
                                        }
                                    }
                                    shared.moviesSeriesBox.remove(queried)
                                }
                                shared.moviesSeriesBox.put(series)
                                return true
                            }
                    }
                    SeriesIdentity.NEW -> {
                        shared.miscSeriesBox.query()
                            .equal(
                                Series_.name,
                                series.name,
                                QueryBuilder.StringOrder.CASE_INSENSITIVE
                            ).build().use { query ->
                                val queried = query.find()
                                if (queried.isNotEmpty()) {
                                    for (s in queried) {
                                        if (s.matches(series)) {
                                            return false
                                        }
                                    }
                                    shared.miscSeriesBox.remove(queried)
                                }
                                shared.miscSeriesBox.put(series)
                                return true
                            }
                    }
                }
            } catch (e: Exception) {
                FrogLog.logError(
                    "Failed to add series: ${series.name} with identity: $identity.",
                    e
                )
            }
            return false
        }

        fun seriesForSlug(
            slug: String,
            identity: SeriesIdentity
        ): Series? {
            when (identity) {
                SeriesIdentity.DUBBED -> {
                    shared.dubbedSeriesBox.query()
                        .equal(Series_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build()
                        .use { query -> return query.findUniqueOrNull() }
                }
                SeriesIdentity.SUBBED -> {
                    shared.subbedSeriesBox.query()
                        .equal(Series_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build()
                        .use { query -> return query.findUniqueOrNull() }
                }
                SeriesIdentity.MOVIE -> {
                    shared.moviesSeriesBox.query()
                        .equal(Series_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build()
                        .use { query -> return query.findUniqueOrNull() }
                }
                SeriesIdentity.CARTOON -> {
                    shared.cartoonSeriesBox.query()
                        .equal(Series_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build()
                        .use { query -> return query.findUniqueOrNull() }
                }
                else -> {
                    shared.miscSeriesBox.query()
                        .equal(Series_.slug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build()
                        .use { query -> return query.findUniqueOrNull() }
                }
            }
        }

        fun seriesForSlug(
            slug: String,
            identity: Int
        ): Series? {
            return seriesForSlug(slug, SeriesIdentity.idForType(identity))
        }

        fun seriesForSlug(
            slug: String
        ): Series? {
            SeriesIdentity.entries.forEach {
                val series = seriesForSlug(slug, it)
                if (series != null) {
                    return series
                }
            }
            return null
        }

        fun seriesForEpisodeSlug(
            slug: String
        ): Pair<Series?, Episode>? {
            SeriesIdentity.entries.forEach { identity ->
                val query: QueryBuilder<Episode> = when (identity) {
                    SeriesIdentity.DUBBED ->
                        shared.dubbedEpisodeBox.query()

                    SeriesIdentity.SUBBED ->
                        shared.subbedEpisodeBox.query()

                    SeriesIdentity.MOVIE ->
                        shared.moviesEpisodeBox.query()

                    SeriesIdentity.CARTOON ->
                        shared.cartoonEpisodeBox.query()

                    else -> shared.miscEpisodeBox.query()
                }
                query.equal(Episode_.slug, slug, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                    .build().use {
                        val episode = it.findUniqueOrNull()
                        if (episode != null) {
                            return Pair(seriesForSlug(episode.seriesSlug, identity), episode)
                        }
                    }
            }
            return null
        }

        fun areIdentityLinksComplete(identity: SeriesIdentity): Boolean {
            shared.wcoLinksBox.query()
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
                shared.wcoLinksBox.put(
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
                shared.wcoLinksBox.query()
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
                    shared.wcoLinksBox.query()
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
            return SeriesIdentity.NEW
        }

        fun genreForName(name: String): Genre {
            shared.wcoGenreBox.query()
                .equal(Genre_.name, name, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                .build().use {
                    val genre = it.findUniqueOrNull()
                    if (genre != null) {
                        return genre
                    } else {
                        return Genre("Null")
                    }
                }
        }
    }
}