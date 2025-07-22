package nobility.downloader.core.scraper.video_download.m3u8_downloader.http.pool

fun interface Recycler<T> {
    fun recycle(slot: Slot<T>)
}
