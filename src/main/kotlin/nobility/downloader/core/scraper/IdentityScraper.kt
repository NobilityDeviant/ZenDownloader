package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.addIdentityLinkWithSlug
import nobility.downloader.core.BoxHelper.Companion.addIdentityLinksWithSlug
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.core.scraper.video_download.Functions
import nobility.downloader.utils.*
import org.jsoup.Jsoup

/**
 * Used to scrape wco links and take the slugs from those links.
 * These slugs will be used to identify series.
 */
object IdentityScraper {

    private suspend fun scrapeLinksToSlugs(
        identity: SeriesIdentity
    ) = withContext(Dispatchers.Default) {
        val fullIdentityLink = identity.slug.slugToLink()
        val slugs = mutableListOf<String>()
        try {
            val result = Functions.readUrlLines(
                fullIdentityLink,
                "IdentityScraper"
            )
            val data = result.data
            if (data == null) {
                FrogLog.error(
                    "Failed to find slugs for $identity",
                    result.message
                )
                return@withContext
            }
            val doc = Jsoup.parse(data.toString())
            val list = doc.getElementsByClass("ddmcc")
            val uls = list.select("ul")
            for (ul in uls) {
                val lis = ul.select("li")
                for (li in lis) {
                    var s = li.select("a").attr("href")
                    if (s.contains("//")) {
                        s = Tools.extractSlugFromLink(s)
                    }
                    if (s.startsWith("/")) {
                        s = s.replaceFirst("/", "")
                    }
                    s = s.fixedSlug()
                    slugs.add(s)
                }
            }
            if (slugs.isEmpty()) {
                FrogLog.error(
                    "Failed to find slugs for $identity",
                    "The slugs list returned empty."
                )
                return@withContext
            }
            val added = addIdentityLinksWithSlug(slugs, identity)
            if (added > 0) {
                when (identity) {
                    SeriesIdentity.SUBBED -> {
                        FrogLog.message("Successfully downloaded $added missing subbed link(s).")
                    }

                    SeriesIdentity.DUBBED -> {
                        FrogLog.message("Successfully downloaded $added missing dubbed link(s).")
                    }

                    SeriesIdentity.CARTOON -> {
                        FrogLog.message("Successfully downloaded $added missing cartoon link(s).")
                    }

                    SeriesIdentity.MOVIE -> {
                        FrogLog.message("Successfully downloaded $added missing movie link(s).")
                    }

                    else -> {}
                }
            }
        } catch (e: Exception) {
            FrogLog.error(
                "Failed to scrape identity links for: $identity",
                e,
                true
            )
        }
    }

    suspend fun findIdentityForSlugLocally(
        slug: String
    ): SeriesIdentity = withContext(Dispatchers.Default) {
        SeriesIdentity.filteredValues().forEach {
            if (!BoxHelper.areIdentityLinksComplete(it)) {
                FrogLog.message("Identity links for: $it aren't fully downloaded. Launching IdentityScraper now.")
                try {
                    scrapeLinksToSlugs(it)
                } catch (e: Exception) {
                    FrogLog.error(
                        "Failed to scrape identity links for: $it",
                        e,
                        true
                    )
                }

            }
        }
        return@withContext BoxHelper.identityForSeriesSlug(slug)
    }

    suspend fun findIdentityForSlugOnline(
        slug: String
    ): Resource<SeriesIdentity> = withContext(Dispatchers.IO) {
        val fixedSlug = slug.fixedSlug()
        val fullSeriesLink = slug.slugToLink()
        FrogLog.message("Looking for identity for $fixedSlug online")
        for (identity in SeriesIdentity.filteredValues()) {
            val fullIdentityLink = identity.slug.slugToLink()
            val result = Functions.readUrlLines(
                fullIdentityLink,
                "findIdentityForSlugOnline"
            )
            val data = result.data
            if (data == null) {
                return@withContext Resource.Error(
                    "Failed to read the source code.",
                    result.message
                )
            }
            val doc = Jsoup.parse(data.toString())
            val list = doc.getElementsByClass("ddmcc")
            val ul = list.select("ul")
            for (uls in ul) {
                val lis = uls.select("li")
                for (li in lis) {
                    var s = li.select("a").attr("href")
                    if (s.contains("//")) {
                        s = Tools.extractSlugFromLink(s)
                    }
                    if (s.startsWith("/")) {
                        s = s.replaceFirst("/", "")
                    }
                    if (s.contains(fixedSlug) || s == fixedSlug) {
                        addIdentityLinkWithSlug(fixedSlug, identity)
                        FrogLog.message("Successfully labeled $fullSeriesLink as $identity")
                        return@withContext Resource.Success(identity)
                    }
                }
            }
        }
        val new = SeriesIdentity.NEW
        FrogLog.message("Failed to find identity for $fixedSlug")
        FrogLog.message("Labeling it as $new")
        addIdentityLinkWithSlug(slug, new)
        return@withContext Resource.Success(new)
    }

}