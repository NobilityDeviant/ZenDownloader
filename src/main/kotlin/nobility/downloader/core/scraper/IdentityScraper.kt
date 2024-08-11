package nobility.downloader.core.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.addIdentityLinkWithSlug
import nobility.downloader.core.BoxHelper.Companion.addIdentityLinksWithSlug
import nobility.downloader.core.driver.DriverBase
import nobility.downloader.core.entities.data.SeriesIdentity
import nobility.downloader.utils.FrogLog
import nobility.downloader.utils.Tools
import nobility.downloader.utils.fixedSlug
import nobility.downloader.utils.slugToLink
import org.jsoup.Jsoup

/**
 * Used to scrape wco links and take the slugs from those links.
 * These slugs will be used to identify series.
 */
object IdentityScraper {

    private class Scraper: DriverBase()

    private suspend fun scrapeLinksToSlugs(
        identity: SeriesIdentity
    ) = withContext(Dispatchers.IO) {
        val fullIdentityLink = identity.slug.slugToLink()
        val slugs = mutableListOf<String>()
        val scraper = Scraper()
        scraper.driver.get(fullIdentityLink)
        val doc = Jsoup.parse(scraper.driver.pageSource)
        scraper.killDriver()
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
            FrogLog.writeMessage("Failed to find link for $identity")
            return@withContext
        }
        val added = addIdentityLinksWithSlug(slugs, identity)
        if (added > 0) {
            when (identity) {
                SeriesIdentity.SUBBED -> {
                    FrogLog.writeMessage("Successfully downloaded $added missing subbed links.")
                }

                SeriesIdentity.DUBBED -> {
                    FrogLog.writeMessage("Successfully downloaded $added missing dubbed links.")
                }

                SeriesIdentity.CARTOON -> {
                    FrogLog.writeMessage("Successfully downloaded $added missing cartoon links.")
                }

                SeriesIdentity.MOVIE -> {
                    FrogLog.writeMessage("Successfully downloaded $added missing movie links.")
                }

                else -> {}
            }
        }
    }

    suspend fun findIdentityForSlugLocally(
        slug: String
    ): SeriesIdentity {
        SeriesIdentity.filteredValues().forEach {
            if (!BoxHelper.areIdentityLinksComplete(it)) {
                FrogLog.writeMessage("Identity links for: $it aren't fully downloaded. Launching IdentityScraper now.")
                scrapeLinksToSlugs(it)
            }
        }
        return BoxHelper.identityForSeriesSlug(slug)
    }

    fun findIdentityForSlugOnline(
        slug: String
    ): SeriesIdentity {
        val fixedSlug = slug.fixedSlug()
        val fullSeriesLink = slug.slugToLink()
        FrogLog.writeMessage("Looking for identity for $fixedSlug online")
        for (identity in SeriesIdentity.filteredValues()) {
            val fullIdentityLink = identity.slug.slugToLink()
            val scraper = Scraper()
            scraper.driver.get(fullIdentityLink)
            val doc = Jsoup.parse(scraper.driver.pageSource)
            scraper.killDriver()
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
                        FrogLog.writeMessage("Successfully labeled $fullSeriesLink as $identity")
                        return identity
                    }
                }
            }
        }
        val new = SeriesIdentity.NEW
        FrogLog.writeMessage("Failed to find identity for $fixedSlug")
        FrogLog.writeMessage("Labeling it as $new")
        addIdentityLinkWithSlug(slug, new)
        return new
    }

}