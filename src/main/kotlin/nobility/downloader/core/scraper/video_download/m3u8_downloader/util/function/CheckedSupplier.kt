package nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function

import java.io.Serializable

fun interface CheckedSupplier<R> : Serializable {
    @Throws(Throwable::class)
    fun get(): R

    companion object {
        fun <R> constant(value: R): CheckedSupplier<R> {
            return CheckedSupplier<R> { value }
        }

        fun <R> of(methodReference: CheckedSupplier<R>?): CheckedSupplier<R>? {
            return methodReference
        }

        fun <R> narrow(f: CheckedSupplier<out R>): CheckedSupplier<R> {
            return f as CheckedSupplier<R>
        }

        const val serialVersionUID: Long = 1L
    }
}
