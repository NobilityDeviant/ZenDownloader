package nobility.downloader.core.settings

/**
 * Like Defaults but uses the enums name value
 */
enum class Save(
    val defaultValue: Any
) {
    //DownloadsView
    DV_SORT(""),
    DV_S_WEIGHT(1.5f),
    DV_N_WEIGHT(3f),
    DV_F_WEIGHT(1f),
    DV_DC_WEIGHT(1.5f),
    DV_P_WEIGHT(1.5f),
    //History
    H_SORT(""),
    H_N_WEIGHT(5f),
    H_D_WEIGHT(1.1f),
    H_E_WEIGHT(1f),
    H_I_WEIGHT(2f),
    //Recent
    R_SORT(""),
    R_N_WEIGHT(1f),
    R_D_WEIGHT(0.2f),
    R_I_WEIGHT(0.29f),
    //DownloadQueue
    DQ_SORT(""),
    DQ_N_WEIGHT(1f),
    DQ_P_WEIGHT(0.1f),
    //DownloadedEpisodes
    DE_SORT(""),
    DE_N_WEIGHT(5f),
    DE_D_WEIGHT(1.5f),
    //EpisodeUpdater
    EU_SORT(""),
    EU_N_WEIGHT(5f),
    EU_E_WEIGHT(1f),
    EU_S_WEIGHT(1f),
    //Database
    DB_SORT(""),
    DB_N_WEIGHT(3f),
    DB_D_WEIGHT(2.5f),
    DB_E_WEIGHT(1.1f),
    DB_G_WEIGHT(2f),
    DB_I_WEIGHT(2.5f)

}