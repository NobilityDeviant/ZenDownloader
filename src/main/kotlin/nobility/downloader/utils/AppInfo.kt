package nobility.downloader.utils

object AppInfo {

    private const val NAME = "ZenDownloader"
    const val VERSION = "1.0.0" //would be nice to access this from build.gradle.kts
    const val TITLE = "$NAME v$VERSION"
    const val RELEASES_LINK = "https://github.com/NobilityDeviant/ZenDownloader/releases"
    const val GITHUB_LATEST = "https://api.github.com/repos/NobilityDeviant/ZenDownloader/releases/latest"
    const val GITHUB_URL = "https://github.com/NobilityDeviant/ZenDownloader"
    const val EXAMPLE_SERIES = "anime/negima"
    const val EXAMPLE_EPISODE = "strange-planet-episode-1-the-flying-machine"
    const val DEBUG_MODE = true
    private const val IMAGE_PATH = "/images/"
    const val APP_ICON_PATH = IMAGE_PATH + "icon.png"
    const val NO_IMAGE_PATH = IMAGE_PATH + "no-image.png"
    const val WCOFUN_WEBSITE_URLS_LINK = "https://raw.githubusercontent.com/NobilityDeviant/ZenDownloader/master/wcourls.txt"
}