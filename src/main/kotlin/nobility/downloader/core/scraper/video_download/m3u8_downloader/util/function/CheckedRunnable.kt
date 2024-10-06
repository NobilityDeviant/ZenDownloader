package nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function

/**
 *
 */
fun interface CheckedRunnable {
    @Throws(Throwable::class)
    fun run()

    fun unchecked(): Runnable? {
        return Runnable {
            try {
                run()
            } catch (throwable: Throwable) {
                sneakyThrow<RuntimeException, Any>(throwable)
            }
        }
    }

    companion object {
        fun of(methodReference: CheckedRunnable?): CheckedRunnable? {
            return methodReference
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Throwable, R> sneakyThrow(t: Throwable): R {
            throw t as T
        }
    }
}
