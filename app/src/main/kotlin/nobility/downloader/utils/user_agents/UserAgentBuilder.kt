package nobility.downloader.utils.user_agents

object UserAgentBuilder {

    fun chrome(
        version: String,
        os: UserAgentOS
    ) = "Mozilla/5.0 (${os.uaToken}) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$version Safari/537.36"

    fun firefox(
        version: String,
        os: UserAgentOS
    ) = "Mozilla/5.0 (${os.uaToken}; rv:$version) " +
                "Gecko/20100101 Firefox/$version"

    fun opera(
        chromeVersion: String,
        operaVersion: String,
        os: UserAgentOS
    ) = "Mozilla/5.0 (${os.uaToken}) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36 OPR/$operaVersion"

    fun vivaldi(
        chromeVersion: String,
        vivaldiVersion: String,
        os: UserAgentOS
    ) = "Mozilla/5.0 (${os.uaToken}) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/$chromeVersion Safari/537.36 Vivaldi/$vivaldiVersion"
}

