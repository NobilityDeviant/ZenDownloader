package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object CollUtil {

    fun <T> addAll(addTo: MutableCollection<T>, iterator: Iterator<T>): Boolean {
        Preconditions.checkNotNull<Collection<T>>(addTo)
        Preconditions.checkNotNull(iterator)
        var wasModified = false
        while (iterator.hasNext()) {
            wasModified = wasModified or addTo.add(iterator.next())
        }
        return wasModified
    }

    fun <E> newArrayListWithCapacity(initialArraySize: Int): ArrayList<E> {
        Preconditions.checkNonNegative(initialArraySize, "initialArraySize")
        return ArrayList(initialArraySize)
    }

    fun <E> newCopyOnWriteArrayList(): CopyOnWriteArrayList<E> {
        return CopyOnWriteArrayList()
    }

    fun <E> newConcurrentHashSet(): MutableSet<E> {
        return ConcurrentHashMap.newKeySet()
    }

}
