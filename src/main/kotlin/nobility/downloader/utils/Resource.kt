package nobility.downloader.utils

sealed class Resource<T>(
    val data: T? = null,
    val dataCode: Int? = -1,
    val message: String? = null,
    val errorCode: Int? = -1
) {

    class Success<T>(data: T, dataCode: Int = -1) : Resource<T>(data, dataCode)
    class Error<T>(message: String?) : Resource<T>(message = message) {
        constructor(message: String?, exception: Throwable? = null): this(
            "$message | Error: " + if (exception != null)
                exception.localizedMessage else "No error found."
        )
        constructor(exception: Throwable? = null) : this("Error: " + if (exception != null)
            exception.localizedMessage else "No error found.")
    }

    class ErrorCode<T>(
        message: String?,
        errorCode: Int
    ) : Resource<T>(message = message, errorCode = errorCode) {
        constructor(errorCode: Int): this("", errorCode)
        constructor(e: Exception, errorCode: Int): this(
            if (!e.localizedMessage.isNullOrEmpty())
                e.localizedMessage else "No error found.",
            errorCode
        )
    }

    val isFailed: Boolean get() = !message.isNullOrEmpty()
    val isSuccess: Boolean get() = !isFailed

}