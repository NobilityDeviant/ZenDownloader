package nobility.downloader.core.scraper.video_download.m3u8_downloader.util

import java.util.*
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

    @JvmStatic
    fun <E> newArrayList(): ArrayList<E> {
        return ArrayList()
    }

    @SafeVarargs
    fun <E> newArrayList(vararg elements: E): ArrayList<E> {
        Preconditions.checkNotNull(elements)
        val capacity = computeArrayListCapacity(elements.size)
        val list = ArrayList<E>(capacity)
        Collections.addAll(list, *elements)
        return list
    }

    private fun <E> newArrayList(elements: Iterator<E>): ArrayList<E> {
        val list = newArrayList<E>()
        addAll(list, elements)
        return list
    }

    fun <E> newArrayList(elements: Iterable<E>): ArrayList<E> {
        return if ((elements is Collection<*>)) ArrayList(elements as Collection<E>)
        else newArrayList(elements.iterator())
    }

    fun <E> newArrayListWithCapacity(initialArraySize: Int): ArrayList<E> {
        Preconditions.checkNonNegative(initialArraySize, "initialArraySize")
        return ArrayList(initialArraySize)
    }

    fun <E> newCopyOnWriteArrayList(): CopyOnWriteArrayList<E> {
        return CopyOnWriteArrayList()
    }

    @JvmStatic
    fun <E> newArrayDeque(): ArrayDeque<E> {
        return ArrayDeque()
    }

    fun <E> newArrayDequeWithCapacity(initialQueueSize: Int): ArrayDeque<E> {
        return ArrayDeque(initialQueueSize)
    }

    fun <E> newArrayDeque(elements: Iterable<E>): ArrayDeque<E> {
        if (elements is Collection<*>) {
            return ArrayDeque(elements as Collection<E>)
        }
        val deque = ArrayDeque<E>()
        addAll(deque, elements.iterator())
        return deque
    }

    fun <E> newConcurrentHashSet(): MutableSet<E> {
        return ConcurrentHashMap.newKeySet()
    }

    private fun computeArrayListCapacity(arraySize: Int): Int {
        return saturatedCast(5L + arraySize + (arraySize / 10))
    }

    private fun saturatedCast(value: Long): Int {
        if (value > Int.MAX_VALUE) {
            return Int.MAX_VALUE
        }
        if (value < Int.MIN_VALUE) {
            return Int.MIN_VALUE
        }
        return value.toInt()
    }
}
