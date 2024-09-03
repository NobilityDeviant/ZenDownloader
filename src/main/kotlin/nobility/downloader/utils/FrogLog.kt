package nobility.downloader.utils

import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.settings.Defaults
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter

object FrogLog {

    fun logError(
        message: String,
        errorMessage: String? = null,
        important: Boolean = false
    ) {
        if (!important && !Defaults.SHOW_DEBUG_MESSAGES.boolean()) {
            return
        }
        println(
            "[${callerClassName()}] [E] $message" +
                    if (!errorMessage.isNullOrEmpty())
                        "\nError: $errorMessage"
                    else
                        ""
        )
    }

    fun logError(
        message: String,
        exception: Throwable,
        important: Boolean = false
    ) {
        if (!important && !Defaults.SHOW_DEBUG_MESSAGES.boolean()) {
            return
        }
        println(
            "[${callerClassName()}] [E] $message" +
                    if (exception.localizedMessage.isNotEmpty())
                        "\nError: ${exception.localizedMessage}"
                    else
                        "Error: Invalid exception error"
        )
        writeMessage("Stacktrace for ${message.trimIndent()}:", true)
        exception.printStackTrace()
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
            System.err.println(message)
        } else {
            println(message)
        }
    }

    fun writeErrorToFile(s: String, fileName: String) {
        if (!Defaults.SHOW_DEBUG_MESSAGES.boolean()) {
            return
        }
        val debugPath = File("./debug/")
        if (!debugPath.exists()) {
            if (!debugPath.mkdir()) {
                logError(
                    "Failed to write error to file. Unable to find/create the debug folder." +
                            "\nWriting it here instead." +
                            "\nError: \n$s"
                )
                return
            }
        }
        val debugFile = File(debugPath.absolutePath + "/$fileName.txt")
        var bufferedWriter: BufferedWriter? = null
        try {
            bufferedWriter = BufferedWriter(FileWriter(debugFile, true))
            bufferedWriter.newLine()
            bufferedWriter.newLine()
            bufferedWriter.write("[${Tools.date}][${Tools.currentTime}][DEBUG ERROR]")
            bufferedWriter.newLine()
            bufferedWriter.write(s)
            bufferedWriter.flush()
            logInfo(
                "Successfully wrote error to file: ${debugFile.absolutePath}"
            )
        } catch (e: Exception) {
            logError(
                "Failed to write error to file.",
                e
            )
        } finally {
            bufferedWriter?.close()
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

    private const val NO_CLASS = "NoClass"
}