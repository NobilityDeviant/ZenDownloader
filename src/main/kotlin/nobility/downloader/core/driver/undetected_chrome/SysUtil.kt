package nobility.downloader.core.driver.undetected_chrome

import java.io.File

object SysUtil {

    private val isWindows: Boolean
        get() = osName.startsWith("Windows")

    val isMacOs: Boolean
        get() = osName.startsWith("Mac")

    val isLinux: Boolean
        get() = osName.startsWith("Linux") || (!isWindows && !isMacOs)

    val isArch get() = linuxDistro == LinuxDistro.ARCH

    val isDebian get() = linuxDistro == LinuxDistro.DEBIAN

    private val linuxDistro: LinuxDistro get() {

        File("/etc/os-release").takeIf { it.exists() }?.readLines()?.let { lines ->
            val id = lines.find { it.startsWith("ID=") }?.removePrefix("ID=")?.replace("\"", "")?.trim()
            val idLike = lines.find { it.startsWith("ID_LIKE=") }?.removePrefix("ID_LIKE=")?.replace("\"", "")?.trim()

            return when {
                id == "arch" -> LinuxDistro.ARCH
                id == "debian" || idLike?.contains("debian") == true -> LinuxDistro.DEBIAN
                else -> LinuxDistro.OTHER
            }
        }

        return when {
            File("/etc/arch-release").exists() -> LinuxDistro.ARCH
            File("/etc/debian_version").exists() -> LinuxDistro.DEBIAN
            File("/etc/alpine-release").exists() -> LinuxDistro.OTHER
            else -> LinuxDistro.OTHER
        }
    }

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

    private enum class LinuxDistro {
        DEBIAN,
        ARCH,
        OTHER
    }
}
