package nobility.downloader.core.settings

import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.float
import nobility.downloader.core.BoxHelper.Companion.int
import nobility.downloader.core.BoxHelper.Companion.long
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.ui.windows.database.DatabaseSort
import nobility.downloader.ui.windows.database.DatabaseType
import nobility.downloader.utils.Constants
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
            Example: C:\Users\CuratedDev\Desktop\portable-chrome\chrome.exe
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
            Example: C:\Users\CuratedDev\Desktop\chromedriver-win64\chromedriver.exe
            Default: Empty
            Requires: Chrome Browser Path
        """.trimIndent()
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
    LAST_DOWNLOAD("last_dl", ""),
    DENIED_UPDATE("denied_update", false),
    UPDATE_VERSION("update_version", "1.0"),
    WCO_DOMAIN("wco_website", "wcofun"), //used to combat the constantly changing domain
    WCO_EXTENSION("wco_ext", "org"), //used to combat the constantly changing domain
    WCO_LAST_UPDATED("wco_last_update", 0L),
    ENABLE_PROXY("enable_proxy", false),
    FIRST_LAUNCH("1st-launch", false),
    DARK_MODE("dark_mode", true),
    ELSE("else", false),
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
    VIDEO_RETRIES(
        "vid_retries",
        15,
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
            If it throws an error this amount of times, it will default to the regular download method.
            An error can be thrown if the login fails or it fails to load the webpage or video.
            Minimum: 1 | Maximum: 100
            Default: 5
        """.trimIndent()
    ),
    WCO_GENRES_LAST_UPDATED("wco_genres_last_updated", 0L),
    WCO_RECENT_LAST_UPDATED("wco_recent_last_updated", 0L),
    DB_LAST_SCROLL_POS("db_last_scroll_pos", 0),
    DB_LAST_TYPE_USED("db_last_type", DatabaseType.ALL.id),
    DB_LAST_SORT_USED("db_last_sort", DatabaseSort.NAME.id),
    DB_SEARCH_GENRE("db_search_genre", true),
    DB_SEARCH_DESC("db_search_desc", true);

    companion object {

        @Suppress("UNUSED")
        fun Defaults.savedValue(): Any {
            return if (value is String) {
                string()
            } else if (value is Boolean) {
                boolean()
            } else if (value is Int) {
                int()
            } else if (value is Long) {
                long()
            } else if (value is Float) {
                float()
            } else {
                value
            }
        }

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
            DISABLE_CARTOON_UPDATE,
            DISABLE_DUBBED_UPDATE,
            DISABLE_MOVIES_UPDATE,
            DISABLE_SUBBED_UPDATE,
            DISABLE_USER_AGENTS_UPDATE,
            DISABLE_WCO_DATA_UPDATE,
            DISABLE_WCO_SERIES_LINKS_UPDATE,
            DISABLE_WCO_URLS_UPDATE,
            WCO_PREMIUM_USERNAME,
            WCO_PREMIUM_PASSWORD,
            SHOW_WCO_PREMIUM_PASSWORD,
            VIDEO_RETRIES,
            FILE_SIZE_RETRIES,
            QUALITY_RETRIES,
            SIMPLE_RETRIES,
            FULL_RETRIES,
            PREMIUM_RETRIES
        )

        val intFields get() = listOf(
            DOWNLOAD_THREADS,
            TIMEOUT,
            VIDEO_RETRIES,
            FILE_SIZE_RETRIES,
            QUALITY_RETRIES,
            SIMPLE_RETRIES,
            FULL_RETRIES,
            PREMIUM_RETRIES
        )

        val checkBoxes get() = listOf(
            SEPARATE_SEASONS,
            SHOW_TOOLTIPS,
            CTRL_FOR_HOTKEYS,
            CONSOLE_ON_TOP,
            AUTO_SCROLL_CONSOLES,
            HEADLESS_MODE,
            SHOW_DEBUG_MESSAGES,
            ENABLE_RANDOM_SERIES
        )

        val updateCheckBoxes get() = listOf(
            DISABLE_USER_AGENTS_UPDATE,
            DISABLE_WCO_URLS_UPDATE,
            DISABLE_DUBBED_UPDATE,
            DISABLE_SUBBED_UPDATE,
            DISABLE_MOVIES_UPDATE,
            DISABLE_CARTOON_UPDATE,
            DISABLE_WCO_SERIES_LINKS_UPDATE,
            DISABLE_WCO_DATA_UPDATE
        )
    }
}