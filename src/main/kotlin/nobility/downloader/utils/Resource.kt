package nobility.downloader.utils

sealed class Resource<T>(
    val data: T? = null,
    @Suppress("UNUSED")
    val dataCode: Int? = -1,
    val message: String? = null,
    val errorCode: Int? = -1
) {

    class Success<T>(data: T, dataCode: Int = -1) : Resource<T>(data, dataCode)

    class Error<T>(message: String?) :
        Resource<T>(message = message) {
        constructor(message: String?, exception: Throwable?):
                this(
                    message = (if (!message.isNullOrEmpty())
                        "$message | "
                    else "") + if (exception != null)
                        "Error: " + exception.localizedMessage else ""//"No error found."
                )
        constructor(ex: Throwable?) : this("", exception = ex)
        constructor() : this("")
        constructor(message: String?, errorMessage: String?) :
                this(
                    message = (if (!message.isNullOrEmpty())
                        "$message | "
                    else "") + if (!errorMessage.isNullOrEmpty())
                        "Error: $errorMessage" else "No error found."
                )
    }

    class ErrorCode<T>(
        message: String?,
        errorCode: Int
    ) : Resource<T>(message = message, errorCode = errorCode) {
        constructor(errorCode: Int) : this("", errorCode)
    }

    val isFailed: Boolean get() = !message.isNullOrEmpty() || this is Error || this is ErrorCode
    val isSuccess: Boolean get() = !isFailed

}