package nobility.downloader.utils.computer_info

data class ComputerInfo(
    val operatingSystem: OperatingSystem,
    val architecture: Architecture
) {
    override fun toString(): String {
        return operatingSystem.name + ":" + architecture.name
    }

    companion object {

        fun myComputer(): ComputerInfo {

            val osName = System.getProperty("os.name").lowercase()
            val osArch = System.getProperty("os.arch").lowercase()

            val operatingSystem: OperatingSystem = when {
                osName.contains("win") -> OperatingSystem.WINDOWS
                osName.contains("mac") -> OperatingSystem.MAC
                osName.contains("nux") || osName.contains("nix") -> OperatingSystem.LINUX
                else -> OperatingSystem.UNSUPPORTED
            }

            val architecture: Architecture = when {
                osArch.contains("aarch64") || osArch.contains("arm64") -> Architecture.ARM64
                osArch.contains("64") -> Architecture.AMD64
                else -> Architecture.UNSUPPORTED
            }

            return ComputerInfo(operatingSystem, architecture)
        }

    }
}