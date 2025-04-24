package nobility.downloader.core.driver.undetected_chrome

import java.io.File
import java.util.*

object SysUtil {

    val isWindows: Boolean
        get() = osName.startsWith("windows")

    val isMacOs: Boolean
        get() = osName.startsWith("mac")

    val isLinux: Boolean
        get() = osName.startsWith("linux") || isArch || isDebian

    val isArch get() = findLinuxDistro() == DistroBase.ARCH

    val isDebian get() = findLinuxDistro() == DistroBase.DEBIAN

    private enum class DistroBase {
        DEBIAN,
        ARCH,
        OTHER
    }

    private val debianIds get() = listOf(
        "ubuntu",
        "pop",
        "debian"
    )

    private val archIds get() = listOf(
        "fedora",
        "centos",
        "manjaro",
        "arch",
        "blackarch",
        "amzn",
        "almalinux",
        "archcraft"
    )

    private val debianIdLikes get() = listOf(
        "debian",
        "ubuntu"
    )

    private val archIdLikes get() = listOf(
        "arch",
        "fedora",
        "centos"
    )

    private fun findLinuxDistro(): DistroBase {

        val osReleaseFile = listOf("/etc/os-release", "/usr/lib/os-release")
            .map(::File)
            .firstOrNull { it.exists() } ?: return DistroBase.OTHER

        val lines = osReleaseFile.readLines()
        val id = lines.find { it.startsWith("ID=") }?.removePrefix("ID=")?.replace("\"", "")?.trim()
        val idLike = lines.find { it.startsWith("ID_LIKE=") }?.removePrefix("ID_LIKE=")?.replace("\"", "")?.trim()

        val cleanId = id?.lowercase()?.trim()
        val likeList = idLike?.lowercase()?.split(' ', ',')?.map { it.trim() } ?: emptyList()

        if (cleanId in debianIds || likeList.any { it in debianIdLikes }) {
            return DistroBase.DEBIAN
        } else if (cleanId in archIds || likeList.any { it in archIdLikes }) {
            return DistroBase.ARCH
        }
        return DistroBase.OTHER
    }

    private val osName: String
        get() = System.getProperty("os.name").lowercase(Locale.getDefault())

    val path: List<String>
        get() {
            val sep = File.pathSeparator
            val paths = System.getenv("PATH")
            return listOf(
                *paths.split(sep.toRegex())
                .dropLastWhile { it.isEmpty() }
                .toTypedArray())
        }

    fun getString(key: String?): String {
        return System.getenv(key)
    }
}
