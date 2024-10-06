package nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function

import java.util.*
import java.util.function.Consumer

/**
 *
 */
fun interface CheckedConsumer<T> {

    @Throws(Throwable::class)
    fun accept(t: T)

    fun unchecked(): Consumer<T>? {
        return Consumer { t: T ->
            try {
                accept(t)
            } catch (throwable: Throwable) {
                sneakyThrow<RuntimeException, Any>(throwable)
            }
        }
    }

    @Suppress("UNUSED")
    fun andThen(after: CheckedConsumer<in T>): CheckedConsumer<T>? {
        Objects.requireNonNull(after, "after is null")
        return CheckedConsumer { t: T ->
            accept(t)
            after.accept(t)
        }
    }

    companion object {
        fun <T> of(methodReference: CheckedConsumer<T>?): CheckedConsumer<T>? {
            return methodReference
        }

        @Suppress("UNCHECKED_CAST")
        fun <T : Throwable, R> sneakyThrow(t: Throwable): R {
            throw t as T
        }
    }
}
