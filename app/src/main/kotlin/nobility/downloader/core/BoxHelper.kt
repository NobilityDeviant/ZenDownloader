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

/**
 * A class used to manage the database using ObjectBox.
 * The singleton "shared" inside the companion object can be used
 * to access the stored data globally.
 * It's created after you first call anything using that object and stays persistent
 * throughout the apps lifespan.
 *
 * MyObjectBox must be generated by successfully building the app first.
 * All the EntityInfo classes need to be generated as well.
 * The EntityInfo classes are the entity classes appended by _ such as Episode_.
 */
class BoxHelper {

    //personal data such as downloads & history
    private var dataBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(AppInfo.databasePath + "my_data/core"))
        .build()

    //settings data
    private var settingsBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(AppInfo.databasePath + "my_data/settings"))
        .build()

    private var favoriteBoxStore: BoxStore = MyObjectBox.builder()
        .directory(File(AppInfo.databasePath + "my_data/favorites"))
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
    val favoriteBox: Box<Favorite> = favoriteBoxStore.boxFor(Favorite::class.java)
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
    val wcoRecentBox: Box<RecentData> = wcoBoxStore.boxFor(RecentData::class.java)

    companion object {

        val shared = BoxHelper()

        val wcoPath get() = AppInfo.databasePath + "wco" + File.separator
        val seriesPath get() = AppInfo.databasePath + "series" + File.separator
        val seriesImagesPath: String get() = AppInfo.databasePath + "series_images${File.separator}"

        val dubbed get() = shared.dubbedSeriesBox.all.filter { it.name.isNotEmpty() }
        val subbed get() = shared.subbedSeriesBox.all.filter { it.name.isNotEmpty() }
        val cartoons get() = shared.cartoonSeriesBox.all.filter { it.name.isNotEmpty() }
        val movies: List<Series> get() = shared.moviesSeriesBox.all
        val misc: List<Series> get() = shared.miscSeriesBox.all
        val allSeriesNoMovies get() = dubbed.plus(subbed)
            .plus(cartoons)

        val allSeries get() = dubbed
            .plus(subbed)
            .plus(cartoons)
            .plus(movies)
            .plus(misc)
            .filter { it.name.isNotEmpty() }

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
            return meta?.booleanVal() == true
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
            Defaults.entries.forEach {
                val meta = metaForKey(it.key)
                if (meta == null) {
                    setSetting(it.key, it.value)
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
                FrogLog.error(
                    "Failed to find download for slug and quality.",
                    e
                )
                return null
            }
        }

        fun downloadForNameAndQuality(
            name: String,
            quality: Quality
        ): Download? {
            try {
                shared.downloadBox.query()
                    .equal(
                        Download_.name,
                        name,
                        QueryBuilder.StringOrder.CASE_SENSITIVE
                    ).and().equal(
                        Download_.resolution,
                        quality.resolution.toLong()
                    ).build().use { query ->
                        return query.findUniqueOrFirst()
                    }
            } catch (e: Exception) {
                FrogLog.error(
                    "Failed to find download for name and quality.",
                    e
                )
                return null
            }
        }

        fun addSeries(
            series: Series,
            identity: SeriesIdentity = series.seriesIdentity
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
                                series.updateLastUpdated()
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
                                series.updateLastUpdated()
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
                                series.updateLastUpdated()
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
                                series.updateLastUpdated()
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
                                series.updateLastUpdated()
                                shared.miscSeriesBox.put(series)
                                return true
                            }
                    }
                }
            } catch (e: Exception) {
                FrogLog.error(
                    "Failed to add series: ${series.name} with identity: $identity.",
                    e
                )
            }
            return false
        }

        fun seriesForSlugAndIdentity(
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
            slug: String
        ): Series? {
            SeriesIdentity.entries.forEach {
                val series = seriesForSlugAndIdentity(slug, it)
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
                            return Pair(seriesForSlugAndIdentity(episode.seriesSlug, identity), episode)
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
            } catch (_: Exception) {
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
                } catch (_: Exception) {
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
                    return genre ?: Genre(name)
                }
        }

        fun isSeriesFavorited(series: Series): Boolean {
            shared.favoriteBox.query()
                .equal(Favorite_.seriesSlug, series.slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use {
                    return it.findUniqueOrNull() != null
                }
        }

        fun removeSeriesFavorite(slug: String) {
            shared.favoriteBox.query()
                .equal(Favorite_.seriesSlug, slug, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use {
                    val favorite = it.findUniqueOrNull()
                    if (favorite != null) {
                        shared.favoriteBox.remove(favorite)
                    }
                }
        }
    }
}