package nobility.downloader.core.settings

import com.materialkolor.PaletteStyle
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.ui.theme.seedColor
import nobility.downloader.ui.windows.database.DatabaseType
import nobility.downloader.utils.Constants
import nobility.downloader.utils.fileExists
import java.io.File

/**
 * An enum used for easy settings management.
 * The type must be specified when calling the value.
 * Settings only support numbers, strings and booleans.
 * Custom saves must be implemented inside SettingsMeta
 * @see nobility.downloader.core.entities.settings.SettingsMeta
 */
enum class Defaults(
    val key: String,
    val value: Any,
    val description: String = "",
    val alternativeName: String = ""
) {
    TIMEOUT(
        "timeout",
        30,
        """
            The timeout in seconds used to know when a network action takes too long to respond.
            This is used globally for all actions including element detection.
            Lower = Faster Internet | Higher = Slower Internet
            Minimum: ${Constants.minTimeout} | Maximum: ${Constants.maxTimeout}
            Default: 30
        """.trimIndent(),
        "Network Timeout"
    ),
    SAVE_FOLDER(
        "save_folder",
        System.getProperty("user.home") + "${File.separator}zen_videos${File.separator}",
        """
            The folder where all your downloads get saved.
            Default: ${System.getProperty("user.home")}
        """.trimIndent(),
        "Download Folder"
    ),
    DOWNLOAD_THREADS(
        "download_threads",
        2,
        """
            The amount of browser instances used to download files at the same time.
            Each thread is considered a download process.
            If you want to download more than 1 file at a time, you can increase this.
            Each instance opens up the browser in the background and it consumes quite a bit of ram.
            Cloudflare can also block your IP if you use too many.
            Lower = Slower PC | Higher = Faster PC
            Minimum: ${Constants.minThreads} | Maximum: ${Constants.maxThreads}
            Default: 2
        """.trimIndent()
    ),
    TOAST_ALPHA(
        "toast_alpha",
        0.6f,
        """
            The transparency for toasts.
            Toasts are just the little messages that show up and go away after some time.
            Default: 0.6f
        """.trimIndent()
    ),
    SHOW_DEBUG_MESSAGES(
        "debug_messages",
        false,
        """
            Shows debug logs in the console.
            Debug logs are useful for reporting issues to Github or just wanting to know what's going on in the background.
            Default: Off
        """.trimIndent()
    ),
    QUALITY(
        "video_quality",
        Quality.LOW.tag,
        """
            The quality videos download in.
            Sometimes a quality option is available for certain videos.
            This option will try to get your selected option, but if it doesn't exist, it will default to the one underneath it.
            Default: 576p
        """.trimIndent(),
        "Download Quality"
    ),
    VIDEO_PLAYER(
        "video_player",
        VideoPlayer.FFPLAY.name,
        """
            The video player used for the "Watch Online" feature.
            - Ffplay should be inside the database folder.
            - Mpv should be installed and be usable via the command prompt or terminal. (command: mpv)
            Default: ${VideoPlayer.FFPLAY.name}
        """.trimIndent(),
        "Watch Online Video Player"
    ),
    SHOW_TOOLTIPS(
        "show_tooltips",
        true,
        """
            Show these types of boxes on certain components.
            Default: On
        """.trimIndent()
    ),
    CONSOLE_ON_TOP(
        "console_on_top",
        true,
        """
            When popping out the console, it will always stay on top of other windows.
            Default: On
        """.trimIndent(),
        "Popout Console Window Always On Top"
    ),
    HEADLESS_MODE(
        "headless_mode",
        true,
        """
            Headless mode is used to hide the browsers during downloading or scraping.
            If you turn this off, then all browser instances will show.
            The only reason this option is here is for debugging or if you're curious to see the process.
            Note: With this option off, your download threads will be capped at 1 to prevent window spam.
            Default: On
        """.trimIndent()
    ),
    SEPARATE_SEASONS(
        "separate_seasons",
        true,
        """
            Used to give each found season inside a series it's own folder.
            A season folder is created if the episodes name contains "Season *" with * as any number.
            If the season folder is created from the episode name, that episode will go inside.
            Also if the episode doesn't have "Season *" then it defaults to Season 01.
            Default: On
        """.trimIndent()
    ),
    AUTO_SCROLL_CONSOLES(
        "auto_scroll_consoles",
        true,
        """
            Auto scrolls all consoles to the bottom when a new text is added to it.
            Default: On
        """.trimIndent()
    ),
    CTRL_FOR_HOTKEYS(
      "ctrl_for_hotkeys",
        true,
        """
            Toggles the use of CTRL when using hotkeys.
            If turned on, you will have to hold CTRL + the key.
            If turned off, you just use the key if the url field isn't focused.
            Default: On
        """.trimIndent()
    ),
    CHROME_BROWSER_PATH(
        "chrome_browser_path",
        "",
        """
            The path to your Chrome browser's executable file.
            You can adjust this if the app is failing to use your correct chrome version.
            You don't need to use this option as the program will find chrome for you.
            Example: C:\\Users\\CuratedDev\\Desktop\\portable-chrome\\chrome.exe
            Default: Empty
            Requires: Chrome Driver Path
        """.trimIndent()
    ),
    CHROME_DRIVER_PATH(
        "chrome_driver_path",
        "",
        """
            The path to your ChromeDriver file.
            You can adjust this if the app is failing to use your correct chrome version.
            You don't need to use this option as the program will get it for you.
            Example: C:\\Users\\CuratedDev\\Desktop\\chromedriver-win64\\chromedriver.exe
            Default: Empty
            Requires: Chrome Browser Path
        """.trimIndent()
    ),
    VLC_PATH(
        "vlc_path",
        "",
        """
            Your custom path to VLC to play videos.
            This path has to be pointed to a vlc executable.
            In Windows it'll be vlc.exe and yes it can be the portable version.
            Mac & Linux will also have an executable called vlc.
            If VLC is installed and usable in the command prompt or terminal, this path isn't needed.
            Example: "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe"
            Default: Empty
        """.trimIndent(),
        "VLC Path"
    ),
    MPV_PATH(
        "mpv_path",
        "",
        """
            Your custom path to MPV to play videos.
            This path has to be pointed to a mpv executable.
            In Windows it'll be mpv.exe.
            Mac & Linux will also have an executable called mpv.
            If MPV is installed and usable in the command prompt or terminal, this path isn't needed.
            This path will override any installs. Keep it empty if it's already installed.
            Example: "C:\\Program Files\\MPV\\mpv.exe"
            Example: "C:\Users\Score\Desktop\mpv-macos-15-arm\mpv.app\Contents\MacOs\mpv"
            Default: Empty
        """.trimIndent(),
        "MPV Path"
    ),
    DISABLE_USER_AGENTS_UPDATE(
        "disable_ua_update",
        false,
        """
            Used to disable user_agents.txt from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_WCO_URLS_UPDATE(
        "disable_wco_urls_update",
        false,
        """
            Used to disable wco_urls.txt from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_DUBBED_UPDATE(
        "disable_dubbed_update",
        false,
        """
            Used to disable the dubbed database from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_SUBBED_UPDATE(
        "disable_subbed_update",
        false,
        """
            Used to disable the subbed database from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_CARTOON_UPDATE(
        "disable_cartoon_update",
        false,
        """
            Used to disable the cartoon database from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_MOVIES_UPDATE(
        "disable_movies_update",
        false,
        """
            Used to disable the movies database from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_WCO_SERIES_LINKS_UPDATE(
        "disable_wco_series_links_update",
        false,
        """
            Used to disable the links database from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_MOVIE_LIST_UPDATE(
        "disable_movie_list_update",
        false,
        """
            Used to disable the movies.txt list updating at launch.
            Default: Off
        """.trimIndent()
    ),
    DISABLE_WCO_DATA_UPDATE(
        "disable_wco_data_update",
        false,
        """
            Used to disable the wcodata database from updating at launch.
            This option can be useful if you're updating them on your own.
            Default: Off
        """.trimIndent()
    ),
    WCO_PREMIUM_USERNAME(
        "wco_prem_username",
        "",
        """
            This is your username/email for logging into your https://www.wcopremium.tv/ account.
            A wco subscription allows you to download everything at a higher quality and a different server.
            Your wco account must have a subscription for this to be useful.
            You can sign up here: https://www.wcopremium.tv/wp-login.php?action=register
            Default: Empty
        """.trimIndent()
    ),
    WCO_PREMIUM_PASSWORD(
        "wco_prem_password",
        "",
        """
            This is your password for logging into your https://www.wcopremium.tv/ account.
            A wco subscription allows you to download everything at a higher quality and a different server.
            Your wco account must have a subscription for this to be useful.
            You can sign up here: https://www.wcopremium.tv/wp-login.php?action=register
            This option doesn't have encryption and will always save.
            Please be careful when storing your password.
            Default: Empty
            Requires: Wco Premium Username
        """.trimIndent()
    ),
    SHOW_WCO_PREMIUM_PASSWORD(
        "show_wco_prem_password",
        false
    ),
    ENABLE_RANDOM_SERIES(
        "enable_random_series",
        true,
        """
            Used to toggle the Downloader pages random series rows
            that appear below the url bar.
            Default: On
        """.trimIndent(),
        "Enable Random Series Rows"
    ),
    AUTO_SCROLL_RANDOM_SERIES(
        "auto_scroll_random_series",
        true,
        """
            Used to toggle random series auto scroll.
            Default: On
        """.trimIndent()
    ),
    VIDEO_RETRIES(
        "vid_retries",
        10,
        """
            This is the amount of retries for each video download process.
            After this amount of errors, it will skip the video entirely.
            Increase it if you're experiencing any issues with retries.
            Minimum: 1 | Maximum: 100
            Default: 15
        """.trimIndent()
    ),
    FILE_SIZE_RETRIES(
        "file_size_retries",
        3,
        """
            This is the amount of retries for each videos file size check.
            After this amount of errors, it will skip the videos selected quality and try a different one.
            Minimum: 1 | Maximum: 100
            Default: 3
        """.trimIndent()
    ),
    QUALITY_RETRIES(
        "quality_retries",
        5,
        """
            This is the amount of retries for each video quality check.
            Each quality will be checked for a video in a loop.
            IE: 576, 720, 1080
            If searching for a quality throws an error, it will retry this amount of times.
            If it reaches this amount, it will skip that quality and check the next one.
            Minimum: 1 | Maximum: 100
            Default: 5
        """.trimIndent()
    ),
    SIMPLE_RETRIES(
        "simple_retries",
        5,
        """
            This is the amount of Simple Mode retries.
            Simple Mode is when the program tries to retrieve the pages source code purely using HTTP Requests.
            This results in a faster download process and is used first.
            If it throws an error this amount of times, it will move on to trying Full Mode.
            Minimum: 1 | Maximum: 100
            Default: 5
        """.trimIndent()
    ),
    FULL_RETRIES(
        "full_retries",
        5,
        """
            This is the amount of Full Mode retries.
            Full Mode is when the program tries to retrieve the pages source code using Selenium.
            This is slower than Simple Mode and is used as a backup option.
            If it throws an error this amount of times, it will fail and increase the Video Retries.
            Minimum: 1 | Maximum: 100
            Default: 5
        """.trimIndent()
    ),
    PREMIUM_RETRIES(
        "premium_retries",
        5,
        """
            This is the amount of wcopremium.tv download retries.
            This is used when downloading a movie and you have added your Wco Premium Username/Password
            If it throws an error this amount of times, it will skip the movie entirely.
            An error can be thrown if the login fails or it fails to load the webpage or video.
            Minimum: 1 | Maximum: 100
            Default: 5
        """.trimIndent()
    ),
    M3U8_RETRIES(
        "m3u8_retries",
        3,
        """
            This is the amount of retries for each m3u8 video download process.
            After this amount of errors, it will check a new quality.
            If there's no more qualities to check, it will then search for a 2nd video.
            If there's no more qualities or a 2nd video, it will increase the Video Retries.
            Increase it if you're experiencing any issues with m3u8 retries.
            Minimum: 1 | Maximum: 100
            Default: 3
        """.trimIndent()
    ),
    EPISODE_ORGANIZERS(
        "eps_org",
        "\"Movie, Film*\", OVA, Special*",
        """
            These are the keywords that can organize series episodes inside the Series Details window.
            For instance, lets say you add the keyword 'Final'. Now every episode with the word 'Final' 
            inside of it's title will have it's own section.
            You can also group more than 1 keyword by encasing them inside of double quotes like so: "movie, film"
            Keywords or keyword groups must be split by a comma [,] to be counted.
            Notes: 
            - By default nothing is case sensitive and keywords can't contain double quotes or commas.
            - By default keywords get checked as "contains", so if you use the keyword 'Special', it will add 'Specials' as well.
            - If you want to match as "exact", you have to append your keyword with a *. such as: Specials*
            - Matching as exact is case sensitive.
            - The order is first to last and duplicates won't be counted even in groups.
            - The keyword "Season" is not changeable. That will always be enabled and used after the organizer keywords.
            Default: "Movie, Film*", OVA, Special*
        """.trimIndent(),
        "Episode Organizer Keywords"
    ),
    CHOOSE_M3U8_STREAM(
      "choose_m3_stream",
        false,
        """
            For every single m3u8 video found, it will show a window to allow you to choose
            m3u8 options such as video quality, audio and subtitles before downloading or watching online.
            Subtitles will have minimum support as I learn how to incorporate them.
            This will only show the options that are actually available for the video.
            Not every video will have the same options.
            Default: false
        """.trimIndent()
    ),
    AUTOMATIC_USER_AGENT_DOWNLOAD(
      "auto_ua_dl",
        true,
        """
            Expired UserAgents are a very big reason why cloudflare blocks browsers.
            On every launch, this will update the new auto_user_agents.txt if the update criteria is met.
            The update criteria is every 2 days by default.
            When this is disabled, the auto_user_agents.txt will not be used.
            Default: true
        """.trimIndent()
    ),
    USER_AGENTS_LAST_UPDATED("ua_last_updated", 0L),
    WCO_GENRES_LAST_UPDATED("wco_genres_last_updated", 0L), //unused
    WCO_RECENT_LAST_UPDATED("wco_recent_last_updated", 0L),
    DB_LAST_SCROLL_POS("db_last_scroll_pos", 0),
    DB_LAST_TYPE_USED("db_last_type", DatabaseType.ALL.id),
    DB_SEARCH_GENRE("db_search_genre", true),
    DB_SEARCH_DESC("db_search_desc", true),
    EPISODE_UPDATER_THREADS("ep_updater_threads", 3),
    LAST_DOWNLOAD("last_dl", ""),
    DENIED_UPDATE("denied_update", false),
    UPDATE_VERSION("update_version", "1.0"),
    WCO_DOMAIN("wco_website", "wcofun"), //used to combat the constantly changing domain
    WCO_EXTENSION("wco_ext", "org"), //used to combat the constantly changing domain
    WCO_LAST_UPDATED("wco_last_update", 0L),
    ENABLE_PROXY("enable_proxy", false),
    FIRST_LAUNCH("1st-launch", false),
    DARK_MODE("dark_mode", true),
    MAIN_COLOR("main_color", seedColor.value),
    MAIN_PALETTE_STYLE(
        "main_palette_style",
        PaletteStyle.TonalSpot.name
    ),
    ELSE("else", false),
    ;

    companion object {

        /**
         * Since there are a lot of Defaults, everything that will be used
         * inside SettingsView has to be included in here to be auto loaded.
         */
        val settings get() = listOf(
            DOWNLOAD_THREADS,
            TIMEOUT,
            SAVE_FOLDER,
            WCO_DOMAIN,
            WCO_EXTENSION,
            CHROME_BROWSER_PATH,
            CHROME_DRIVER_PATH,
            QUALITY,
            SHOW_DEBUG_MESSAGES,
            SHOW_TOOLTIPS,
            CONSOLE_ON_TOP,
            HEADLESS_MODE,
            SEPARATE_SEASONS,
            AUTO_SCROLL_CONSOLES,
            CTRL_FOR_HOTKEYS,
            ENABLE_RANDOM_SERIES,
            AUTO_SCROLL_RANDOM_SERIES,
            DISABLE_CARTOON_UPDATE,
            DISABLE_DUBBED_UPDATE,
            DISABLE_MOVIES_UPDATE,
            DISABLE_SUBBED_UPDATE,
            DISABLE_USER_AGENTS_UPDATE,
            DISABLE_WCO_DATA_UPDATE,
            DISABLE_WCO_SERIES_LINKS_UPDATE,
            DISABLE_MOVIE_LIST_UPDATE,
            DISABLE_WCO_URLS_UPDATE,
            WCO_PREMIUM_USERNAME,
            WCO_PREMIUM_PASSWORD,
            SHOW_WCO_PREMIUM_PASSWORD,
            VIDEO_RETRIES,
            FILE_SIZE_RETRIES,
            QUALITY_RETRIES,
            SIMPLE_RETRIES,
            FULL_RETRIES,
            PREMIUM_RETRIES,
            M3U8_RETRIES,
            EPISODE_ORGANIZERS,
            VLC_PATH,
            VIDEO_PLAYER,
            MPV_PATH,
            CHOOSE_M3U8_STREAM,
            AUTOMATIC_USER_AGENT_DOWNLOAD
        )

        /**
         * Used for integer options inside SettingsView
         */
        val intFields get() = listOf(
            DOWNLOAD_THREADS,
            TIMEOUT,
            VIDEO_RETRIES,
            FILE_SIZE_RETRIES,
            QUALITY_RETRIES,
            SIMPLE_RETRIES,
            FULL_RETRIES,
            PREMIUM_RETRIES,
            M3U8_RETRIES
        )

        /**
         * Used for checkboxes inside the SettingsView
         */
        val checkBoxes get() = listOf(
            SEPARATE_SEASONS,
            SHOW_TOOLTIPS,
            CTRL_FOR_HOTKEYS,
            CONSOLE_ON_TOP,
            AUTO_SCROLL_CONSOLES,
            HEADLESS_MODE,
            SHOW_DEBUG_MESSAGES,
            ENABLE_RANDOM_SERIES,
            AUTO_SCROLL_RANDOM_SERIES,
            CHOOSE_M3U8_STREAM,
            AUTOMATIC_USER_AGENT_DOWNLOAD
        )

        /**
         * Used for disabling auto updates
         */
        val updateCheckBoxes get() = listOf(
            DISABLE_USER_AGENTS_UPDATE,
            DISABLE_WCO_URLS_UPDATE,
            DISABLE_DUBBED_UPDATE,
            DISABLE_SUBBED_UPDATE,
            DISABLE_MOVIES_UPDATE,
            DISABLE_CARTOON_UPDATE,
            DISABLE_WCO_SERIES_LINKS_UPDATE,
            DISABLE_MOVIE_LIST_UPDATE,
            DISABLE_WCO_DATA_UPDATE
        )

        val isUsingCustomChrome: Boolean get() {
            val driver =  CHROME_DRIVER_PATH.string()
            val browser = CHROME_BROWSER_PATH.string()
            return driver.isNotEmpty() && driver.fileExists()
                    && browser.isNotEmpty() && browser.fileExists()
        }
    }
}