package nobility.downloader.utils

import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults

object FrogLog {

    fun logError(
        message: String,
        errorMessage: String? = null,
        important: Boolean = false
    ) {
        val fullMessage = "[${callerClassName()}] [E] $message" +
                if (!errorMessage.isNullOrEmpty())
                    "\nError: $errorMessage"
                else
                    ""
        if (important) {
            println(fullMessage)
        } else {
            @Suppress("KotlinConstantConditions")
            if (AppInfo.USE_CUSTOM_ERROR_PS) {
                Core.errorPrintStream?.println(fullMessage)
            } else {
                System.err.println(fullMessage)
            }
        }
    }

    fun logError(
        message: String,
        exception: Throwable?,
        important: Boolean = false
    ) {
        val fullMessage = "[${callerClassName()}] [E] $message" +
                if (exception?.localizedMessage.isNullOrEmpty())
                    "\nError: Invalid exception."
                else
                    ""
        if (important) {
            println(fullMessage)
        } else {
            @Suppress("KotlinConstantConditions")
            if (AppInfo.USE_CUSTOM_ERROR_PS) {
                Core.errorPrintStream?.println(fullMessage)
            } else {
                System.err.println(fullMessage)
            }
        }
        if (exception != null) {
            //writeMessage("Filtered Stacktrace for ${message.trimIndent()}:", true)
            @Suppress("KotlinConstantConditions")
            if (AppInfo.USE_CUSTOM_ERROR_PS) {
                val stacktrace = filterStackTrace(exception)
                stacktrace.forEach { Core.errorPrintStream?.println(it) }
                exception.printStackTrace(Core.errorPrintStream)
            } else {
                exception.printStackTrace()
            }
        }
    }

    fun logDebug(
        message: String
    ) {
        if (!Defaults.SHOW_DEBUG_MESSAGES.boolean()) {
            return
        }
        println("[${callerClassName()}] [D] $message")
    }

    fun logInfo(information: String) {
        if (!Defaults.SHOW_DEBUG_MESSAGES.boolean()) {
            return
        }
        println("[I] $information")
    }

    /**
     * Redundant but might make an option to disable or filter messages.
     */
    fun writeMessage(
        message: String,
        toErrorStream: Boolean = false
    ) {
        if (toErrorStream) {
            @Suppress("KotlinConstantConditions")
            if (AppInfo.USE_CUSTOM_ERROR_PS) {
                Core.errorPrintStream?.println(message)
            } else {
                System.err.println(message)
            }
        } else {
            println(message)
        }
    }

    private fun callerClassName(): String {
        val stElements = Thread.currentThread().stackTrace
        var callerClassName: String? = null
        try {
            stElements.forEachIndexed { index, ste ->
                val name = ste.className
                if (name != FrogLog::class.java.name
                    && name.indexOf("java.lang.Thread") != 0
                ) {
                    if (name.contains("UndispatchedKt")) {
                        return shortClassName(stElements[index - 1].className)
                    }
                    if (callerClassName == null) {
                        callerClassName = name
                    } else if (callerClassName != name) {
                        return shortClassName(name)
                    }
                }
            }
        } catch (_: Exception) {}
        return NO_CLASS
    }

    private fun shortClassName(className: String): String {
        if (className.isEmpty()) {
            return NO_CLASS
        }
        if (className.contains(".")) {
            return className.split(".").lastOrNull()?: NO_CLASS
        }
        return className
    }

    fun filterStackTrace(throwable: Throwable): List<StackTraceElement> {
        var enteredOurCode = false
        return throwable.stackTrace.filter {
            if (it.className.startsWith("nobility.downloader")) {
                enteredOurCode = true
                true
            } else enteredOurCode
        }
    }

    private const val NO_CLASS = "NoClass"
}