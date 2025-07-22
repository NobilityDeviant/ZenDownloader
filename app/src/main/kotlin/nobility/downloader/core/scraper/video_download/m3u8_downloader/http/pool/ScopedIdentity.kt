package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

import nobility.downloader.core.scraper.video_download.m3u8_downloader.util.Preconditions
import java.util.*

class ScopedIdentity(
    identity: String,
    private val parent: ScopedIdentity? = null
) {

    val identity: String = Preconditions.checkNotBlank(identity)
    val fullIdentity: String

    init {
        this.fullIdentity = toFullIdentity(identity, parent)
    }

    private fun toFullIdentity(identity: String, parent: ScopedIdentity?): String {
        var fullIdentity = ""
        if (null != parent) {
            fullIdentity = parent.fullIdentity + "-"
        }
        return fullIdentity + identity
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null || javaClass != other.javaClass) {
            return false
        }
        val that = other as ScopedIdentity
        return identity == that.identity && fullIdentity == that.fullIdentity && parent == that.parent
    }

    override fun hashCode(): Int {
        return Objects.hash(fullIdentity)
    }
}
