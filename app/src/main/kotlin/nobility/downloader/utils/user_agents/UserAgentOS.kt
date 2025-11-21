package nobility.downloader.utils.user_agents

enum class UserAgentOS(val uaToken: String) {
    WINDOWS("Windows NT 10.0; Win64; x64"),
    LINUX("X11; Linux x86_64"),
    MAC_INTEL("Macintosh; Intel Mac OS X 10_15_7"),
    MAC_ARM("Macintosh; arm64; Mac OS X 14_0")
}
