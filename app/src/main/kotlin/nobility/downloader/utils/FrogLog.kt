package nobility.downloader.utils

import AppInfo
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import org.jsoup.Jsoup
import java.io.File
import java.nio.file.Files

//ribbit
object FrogLog {

    fun error(
        message: String,
        errorMessage: String? = null,
        important: Boolean = false
    ) {
        var fullMessage = "[${callerClassName()}] [E] $message" +
                if (!errorMessage.isNullOrEmpty())
                    "\nError: $errorMessage"
                else
                    ""
        fullMessage = Tools.removeDuplicateWord(
            fullMessage,
            "Error:"
        )
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

    fun error(
        message: String,
        exception: Throwable?,
        important: Boolean = false
    ) {
        var fullMessage = "[${callerClassName()}] [E] $message" +
                if (exception?.localizedMessage.isNullOrEmpty())
                    "\nError: Invalid exception."
                else
                    ""
        fullMessage = Tools.removeDuplicateWord(
            fullMessage,
            "Error:"
        )
        if (important) {
            println(fullMessage)

            @Suppress("KotlinConstantConditions")
            if (AppInfo.USE_CUSTOM_ERROR_PS) {
                Core.errorPrintStream?.println(fullMessage)
            } else {
                System.err.println(fullMessage)
            }

        } else {
            @Suppress("KotlinConstantConditions")
            if (AppInfo.USE_CUSTOM_ERROR_PS) {
                Core.errorPrintStream?.println(fullMessage)
            } else {
                System.err.println(fullMessage)
            }
        }
        if (exception != null) {
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

    fun debug(
        message: String
    ) {
        if (!Defaults.SHOW_DEBUG_MESSAGES.boolean()) {
            return
        }
        println("[${callerClassName()}] [D] $message")
    }

    fun info(information: String) {
        if (!Defaults.SHOW_DEBUG_MESSAGES.boolean()) {
            return
        }
        println("[I] $information")
    }

    fun message(
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


    fun writeErrorToTxt(
        name: String,
        error: String,
        headerInfo: String = "",
        html: Boolean = true
    ) {
        val date = Tools.dateFormatted
        val time = Tools.timeFormatted
        val title = "error_${name.replace(" ", "_")}_$time.txt".fixForFiles()
        val folderName = AppInfo.databasePath + "errors" + File.separator + date.fixForFiles() + File.separator
        File(folderName).mkdirs()
        val file = File(folderName + title)
        if (!file.exists() && !file.createNewFile()) {
            debug(
                "Failed to create error log file: ${file.absolutePath}"
            )
            return
        }
        val sb = StringBuilder()
        sb.appendLine("Name: $name")
        sb.appendLine("Date: $date")
        if (headerInfo.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("Additional Info: $headerInfo")
        }
        sb.appendLine()
        sb.appendLine()
        if (html) {
            val doc = Jsoup.parse(error)
            doc.outputSettings().prettyPrint(true)
                .indentAmount(4)
            sb.appendLine(doc.html())
        } else {
            sb.appendLine(error)
        }
        Files.writeString(
            file.toPath(),
            sb.toString()
        )
        if (file.exists()) {
            debug("Wrote error log ($name) to ${file.absolutePath}")
        } else {
            debug("Failed to write error log ($name) to ${file.absolutePath}")
        }
    }

    private const val NO_CLASS = "NoClass"
}