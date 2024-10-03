package nobility.downloader.core.scraper.video_download.m3u8_downloader.util.function

import java.io.Serializable
import java.util.*
import java.util.concurrent.Callable
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Supplier

@Suppress("UNUSED")
interface Try<T> : Serializable {
    fun get(): T

    val cause: Throwable

    val isFailure: Boolean

    val isSuccess: Boolean

    override fun equals(other: Any?): Boolean

    override fun hashCode(): Int

    override fun toString(): String

    fun andThenTry(consumer: CheckedConsumer<in T>): Try<T> {
        if (isFailure) {
            return this
        } else {
            try {
                consumer.accept(get())
                return this
            } catch (t: Throwable) {
                return Failure(t)
            }
        }
    }

    fun andThenTry(runnable: CheckedRunnable): Try<T> {
        Objects.requireNonNull(runnable, "runnable is null")
        if (isFailure) {
            return this
        } else {
            try {
                runnable.run()
                return this
            } catch (t: Throwable) {
                return Failure(t)
            }
        }
    }

    fun andThen(consumer: Consumer<in T>): Try<T>? {
        Objects.requireNonNull(consumer, "consumer is null")
        return andThenTry { t: T -> consumer.accept(t) }
    }

    fun andThen(runnable: Runnable): Try<T>? {
        Objects.requireNonNull(runnable, "runnable is null")
        return andThenTry(CheckedRunnable { runnable.run() })
    }

    fun failed(): Try<Throwable>? {
        return if (isFailure) {
            IsSuccess(cause)
        } else {
            Failure(NoSuchElementException("Success.failed()"))
        }
    }

    fun onFailure(action: Consumer<in Throwable?>): Try<T>? {
        Objects.requireNonNull(action, "action is null")
        if (isFailure) {
            action.accept(cause)
        }
        return this
    }

    fun <X : Throwable?> onFailure(exceptionType: Class<X>, action: Consumer<in X>): Try<T>? {
        Objects.requireNonNull(exceptionType, "exceptionType is null")
        Objects.requireNonNull(action, "action is null")
        if (isFailure && exceptionType.isAssignableFrom(cause.javaClass)) {
            action.accept(cause as X)
        }
        return this
    }

    fun onSuccess(action: Consumer<in T>): Try<T>? {
        Objects.requireNonNull(action, "action is null")
        if (isSuccess) {
            action.accept(get())
        }
        return this
    }

    fun orElse(other: Try<out T>): Try<T> {
        return if (isSuccess) this else other as Try<T>
    }

    fun orElse(supplier: Supplier<out Try<out T>>): Try<T> {
        return if (isSuccess) this else supplier.get() as Try<T>
    }

    fun getOrElseGet(other: Function<in Throwable, out T>): T {
        return if (isFailure) {
            other.apply(cause)
        } else {
            get()
        }
    }

    fun orElseRun(action: Consumer<in Throwable?>) {
        Objects.requireNonNull(action, "action is null")
        if (isFailure) {
            action.accept(cause)
        }
    }

    fun <X : Throwable> getOrElseThrow(exceptionProvider: Function<in Throwable, X>): T {
        Objects.requireNonNull(exceptionProvider, "exceptionProvider is null")
        if (isFailure) {
            throw exceptionProvider.apply(cause)
        } else {
            return get()
        }
    }

    fun <X> fold(ifFail: Function<in Throwable?, out X>, f: Function<in T, out X>): X {
        return if (isFailure) {
            ifFail.apply(cause)
        } else {
            f.apply(get())
        }
    }

    fun peek(action: Consumer<in T>): Try<T>? {
        Objects.requireNonNull(action, "action is null")
        if (isSuccess) {
            action.accept(get())
        }
        return this
    }

    fun <X : Throwable?> recover(
        exceptionType: Class<X>,
        f: Function<in X, out T>
    ): Try<T>? {
        if (isFailure) {
            val cause = cause
            if (exceptionType.isAssignableFrom(cause.javaClass)) {
                return of { f.apply(cause as X) }
            }
        }
        return this
    }

    fun <X : Throwable?> recover(exceptionType: Class<X>, value: T): Try<T>? {
        Objects.requireNonNull(exceptionType, "exceptionType is null")
        return if ((isFailure && exceptionType.isAssignableFrom(cause.javaClass))
        ) success(value)
        else this
    }

    fun recover(f: Function<in Throwable, out T>): Try<T>? {
        return if (isFailure) {
            of {
                f.apply(cause)
            }
        } else {
            this
        }
    }

    fun <X : Throwable> recoverWith(exceptionType: Class<X>, f: Function<in X, Try<out T>>): Try<T> {
        Objects.requireNonNull(exceptionType, "exceptionType is null")
        Objects.requireNonNull(f, "f is null")
        if (isFailure) {
            val cause = cause
            if (exceptionType.isAssignableFrom(cause.javaClass)) {
                return try {
                    narrow(f.apply(cause as X))
                } catch (t: Throwable) {
                    Failure(t)
                }
            }
        }
        return this
    }

    fun <X : Throwable?> recoverWith(
        exceptionType: Class<X>,
        recovered: Try<out T>
    ): Try<T>? {
        return if ((isFailure && exceptionType.isAssignableFrom(cause.javaClass))
        ) narrow(recovered)
        else this
    }

    fun recoverWith(
        f: Function<in Throwable, out Try<out T>>): Try<T> {
        Objects.requireNonNull(f, "f is null")
        return if (isFailure) {
            try {
                f.apply(cause) as Try<T>
            } catch (t: Throwable) {
                Failure(t)
            }
        } else {
            this
        }
    }

    fun <U> transform(f: Function<in Try<T>?, out U>): U {
        Objects.requireNonNull(f, "f is null")
        return f.apply(this)
    }

    fun andFinally(runnable: Runnable): Try<T>? {
        Objects.requireNonNull(runnable, "runnable is null")
        return andFinallyTry { runnable.run() }
    }

    fun andFinallyTry(runnable: CheckedRunnable): Try<T>? {
        Objects.requireNonNull(runnable, "runnable is null")
        try {
            runnable.run()
            return this
        } catch (t: Throwable) {
            return Failure(t)
        }
    }

    class IsSuccess<T>(
        private val value: T,
        override val cause: Throwable = UnsupportedOperationException("getCause on Success"),
        override val isFailure: Boolean = false,
        override val isSuccess: Boolean = true
    ) : Try<T>, Serializable {
        override fun get(): T {
            return value
        }

        override fun equals(other: Any?): Boolean {
            return (other === this) || ((other is IsSuccess<*>) && value == other.value)
        }

        override fun hashCode(): Int {
            return Objects.hashCode(value)
        }

        fun stringPrefix(): String {
            return "Success"
        }

        override fun toString(): String {
            return stringPrefix() + "(" + value + ")"
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    class Failure<T>(cause: Throwable) : Try<T>, Serializable {

        override val cause: Throwable
        override val isFailure: Boolean
            get() = true
        override val isSuccess: Boolean
            get() = false

        init {
            if (isFatal(cause)) {
                sneakyThrow<RuntimeException, Any>(cause)
            }
            this.cause = cause
        }

        override fun get(): T {
            return sneakyThrow<RuntimeException, T>(cause)
        }

        override fun equals(other: Any?): Boolean {
            return (other === this) || (other is Failure<*> && cause.stackTrace.contentDeepEquals(
                other.cause.stackTrace
            ))
        }

        private fun stringPrefix(): String {
            return "Failure"
        }

        override fun hashCode(): Int {
            return cause.stackTrace.contentHashCode()
        }

        override fun toString(): String {
            return stringPrefix() + "(" + cause + ")"
        }

        companion object {
            private const val serialVersionUID = 1L
        }
    }

    companion object {
        fun <T> of(value: T): Try<T> {
            return IsSuccess(value)
        }

        fun <T> of(supplier: CheckedSupplier<out T>): Try<T> {
            return try {
                IsSuccess(supplier.get())
            } catch (t: Throwable) {
                Failure(t)
            }
        }

        fun <T> ofSupplier(supplier: Supplier<out T>): Try<T> {
            return of { supplier.get() }
        }

        fun <T> ofCallable(callable: Callable<out T>): Try<T> {
            Objects.requireNonNull(callable, "callable is null")
            return of { callable.call() }
        }

        fun run(runnable: CheckedRunnable): Try<Unit> {
            try {
                runnable.run()
                return IsSuccess(Unit) // null represents the absence of an value, i.e. Void
            } catch (t: Throwable) {
                return Failure(t)
            }
        }

        fun runRunnable(runnable: Runnable): Try<Unit> {
            return run { runnable.run() }
        }

        fun <T> success(value: T): Try<T> {
            return IsSuccess(value)
        }

        fun <T> failure(exception: Throwable): Try<T> {
            return Failure(exception)
        }

        fun <T> narrow(t: Try<out T>): Try<T> {
            return t as Try<T>
        }

        fun isFatal(throwable: Throwable?): Boolean {
            return (throwable is InterruptedException
                    || throwable is LinkageError
                    || throwable is ThreadDeath
                    || throwable is VirtualMachineError)
        }

        fun <T : Throwable, R> sneakyThrow(t: Throwable): R {
            throw t as T
        }

        const val serialVersionUID: Long = 1L
    }
}

