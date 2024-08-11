package nobility.downloader.utils

//https://gist.github.com/wesleybliss/7d3826ee1ef61e2eb10c8efb30b16b8c
fun interface Observer<T> {
    fun onChange(newValue: T)
}

class Observable<T>(initialValue: T) {

    private val observers = mutableListOf<Observer<T>>()

    var value: T = initialValue
        @Synchronized
        set(value) {
            field = value
            notifyObservers()
        }

    fun observe(observer: Observer<T>) {
        observers.add(observer)
    }

    private fun notifyObservers() {
        observers.forEach { observer ->
            observer.onChange(value)
        }
    }
}