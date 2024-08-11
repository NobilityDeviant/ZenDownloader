package nobility.downloader.core.driver.undetected_chrome

import java.io.File

object SysUtil {

    private val isWindows: Boolean
        get() = osName.startsWith("Windows")

    val isMacOs: Boolean
        get() = osName.startsWith("Mac")

    val isLinux: Boolean
        get() = osName.startsWith("Linux") || (!isWindows && !isMacOs)


    private val osName: String
        get() = System.getProperty("os.name")

    val path: List<String>
        get() {
            val sep = File.pathSeparator
            val paths = System.getenv("PATH")
            return listOf(*paths.split(sep.toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray())
        }

    fun getString(key: String?): String {
        return System.getenv(key)
    }
}
