package nobility.downloader.ui.windows.m3u8

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import nobility.downloader.core.BoxMaker
import nobility.downloader.core.Core
import nobility.downloader.core.entities.Episode
import nobility.downloader.core.scraper.data.M3U8Data
import nobility.downloader.ui.components.DefaultButton
import nobility.downloader.ui.components.DefaultDropdown
import nobility.downloader.ui.components.DropdownOption
import nobility.downloader.ui.components.FullBox
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.Constants

class ChooseM3U8Window(
    private val masterLink: String,
    private val domain: String,
    private val userAgent: String,
    private val m3u8Source: StringBuilder,
    private val episode: Episode
) {

    val videoOptions = mutableListOf<VideoOption>()
    val audioOptions = mutableListOf(
        AudioOption.none
    )
    val subtitleOptions = mutableListOf(
        SubtitleOption.none
    )

    var videoOption by mutableStateOf<VideoOption?>(null)
    var audioOption by mutableStateOf<AudioOption?>(null)
    var subtitleOption by mutableStateOf<SubtitleOption?>(null)

    init {
        parseM3U8()
    }

    fun open() {
        ApplicationState.Companion.newWindow(
            "M3U8 Options For: ${episode.name}"
        ) {
            Scaffold(
                bottomBar = {
                    Column(
                        modifier = Modifier.Companion.fillMaxWidth()
                            .height(Constants.bottomBarHeight),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally
                    ) {
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.Companion.padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Companion.CenterVertically
                        ) {

                            /*DefaultButton(
                                "Play Video",
                                Modifier.weight(0.5f)
                                    .fillMaxHeight()
                                    .padding(top = 8.dp, bottom = 8.dp),
                                fontSize = 13.sp
                            ) {
                                PlayVideoWithMpv.playM3u8(
                                    masterLink,
                                    userAgent = userAgent,
                                    windowTitle = episodeName
                                )
                            }*/

                            DefaultButton(
                                "Download Video",
                                Modifier.Companion.weight(1f)
                                    .fillMaxHeight()
                                    .padding(top = 8.dp, bottom = 8.dp),
                                fontSize = 13.sp
                            ) {
                                if (videoOption == null) {
                                    showToast("You must select a video option.")
                                    return@DefaultButton
                                }
                                BoxMaker.makeHistory(episode.seriesSlug)
                                if (Core.child.isRunning) {
                                    val added = Core.child.downloadThread.addToQueue(
                                        episode,
                                        M3U8Data(
                                            masterLink,
                                            videoOption!!,
                                            audioOption,
                                            subtitleOption,
                                            userAgent
                                        )
                                    )
                                    if (added) {
                                        ApplicationState.Companion.showToastForMain(
                                            "Added episode to current queue."
                                        )
                                        closeWindow()
                                    } else {
                                        showToast(
                                            "Failed to add video to current queue. They have already been added before."
                                        )
                                    }
                                    return@DefaultButton
                                }
                                Core.child.softStart()
                                Core.child.downloadThread.addToQueue(
                                    episode,
                                    M3U8Data(
                                        masterLink,
                                        videoOption!!,
                                        audioOption,
                                        subtitleOption,
                                        userAgent
                                    )
                                )
                                Core.child.launchStopJob()
                                ApplicationState.showToastForMain(
                                    "Launched video downloader for M3U8 episode."
                                )
                                closeWindow()
                            }
                        }
                    }
                }
            ) { padding ->
                val scrollState = rememberScrollState()
                FullBox {
                    Column(
                        modifier = Modifier.Companion.padding(
                            padding
                        )
                            .background(MaterialTheme.colorScheme.surface)
                            .fillMaxWidth()
                            .verticalScroll(scrollState),
                        horizontalAlignment = Alignment.Companion.CenterHorizontally
                    ) {
                        //image?

                        Text(
                            episode.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Companion.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Companion.Center,
                            modifier = Modifier.Companion.padding(8.dp)
                        )

                        Row(
                            verticalAlignment = Alignment.Companion.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.Companion.border(
                                1.dp,
                                MaterialTheme.colorScheme.primary
                            )
                                .fillMaxWidth()
                                .padding(1.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Companion.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.Companion.weight(0.33f)
                            ) {

                                Text(
                                    "Video Options",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Companion.Bold,
                                    textAlign = TextAlign.Companion.Center,
                                    modifier = Modifier.Companion.padding(8.dp)
                                )

                                Text(
                                    "Resolution | Bandwidth",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Companion.Center,
                                    modifier = Modifier.Companion.padding(4.dp)
                                )

                                var expanded by remember { mutableStateOf(false) }

                                DefaultDropdown(
                                    if (videoOption != null)
                                        videoOption!!.resolution + " | " + videoOption!!.bandwidth
                                    else "Quality",
                                    expanded,
                                    videoOptions.map {
                                        DropdownOption(
                                            it.resolution + " | " + it.bandwidth
                                        ) {
                                            videoOption = it
                                            expanded = false
                                        }
                                    },
                                    boxModifier = Modifier.Companion
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .padding(8.dp),
                                    labelFontSize = 14.sp,
                                    onTextClick = { expanded = true }
                                ) { expanded = false }

                            }

                            Column(
                                horizontalAlignment = Alignment.Companion.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.Companion.weight(0.33f)
                            ) {

                                Text(
                                    "Audio Options",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Companion.Bold,
                                    textAlign = TextAlign.Companion.Center,
                                    modifier = Modifier.Companion.padding(8.dp)
                                )

                                Text(
                                    "Name | Language",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Companion.Center,
                                    modifier = Modifier.Companion.padding(4.dp)
                                )

                                var expanded by remember { mutableStateOf(false) }

                                DefaultDropdown(
                                    if (audioOption != null)
                                        audioOption!!.title
                                    else "None",
                                    expanded,
                                    audioOptions.map {
                                        DropdownOption(
                                            it.title
                                        ) {
                                            audioOption = it
                                            expanded = false
                                        }
                                    },
                                    boxModifier = Modifier.Companion
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .padding(8.dp),
                                    labelFontSize = 14.sp,
                                    onTextClick = { expanded = true }
                                ) { expanded = false }

                            }

                            Column(
                                horizontalAlignment = Alignment.Companion.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.Companion.weight(0.33f)
                            ) {

                                Text(
                                    "Subtitle Options",
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Companion.Bold,
                                    textAlign = TextAlign.Companion.Center,
                                    modifier = Modifier.Companion.padding(8.dp)
                                )

                                Text(
                                    "Name | Language",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Companion.Center,
                                    modifier = Modifier.Companion.padding(4.dp)
                                )

                                var expanded by remember { mutableStateOf(false) }

                                DefaultDropdown(
                                    if (subtitleOption != null)
                                        subtitleOption!!.title else "None",
                                    expanded,
                                    subtitleOptions.map {
                                        DropdownOption(
                                            it.title
                                        ) {
                                            subtitleOption = it
                                            expanded = false
                                        }
                                    },
                                    boxModifier = Modifier.Companion
                                        .fillMaxWidth()
                                        .height(70.dp)
                                        .padding(8.dp),
                                    labelFontSize = 14.sp,
                                    onTextClick = { expanded = true }
                                ) { expanded = false }

                            }
                        }
                    }
                }
            }
            ApplicationState.AddToastToWindow(this)
        }
    }

    fun parseM3U8() {

        val lines = m3u8Source.lines()
        var lastStreamInf: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF") -> {
                    lastStreamInf = line
                }

                lastStreamInf != null && !line.startsWith("#") -> {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(lastStreamInf)?.groupValues?.get(1)?.toInt() ?: 0
                    val res = Regex("RESOLUTION=(\\d+x\\d+)").find(lastStreamInf)?.groupValues?.get(1) ?: "unknown"
                    videoOptions.add(
                        VideoOption(
                            resolution = res,
                            bandwidth = bw,
                            uri = domain.trimEnd('/') + "/" + line.trim()
                        )
                    )
                    lastStreamInf = null
                }

                line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO") -> {
                    val name = Regex("NAME=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "Unknown"
                    val lang = Regex("LANGUAGE=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "und"
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                        ?.let { domain.trimEnd('/') + "/" + it } ?: ""
                    val def = line.contains("DEFAULT=YES")
                    audioOptions.add(AudioOption(name, lang, uri, def))
                }

                line.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES") -> {
                    val name = Regex("NAME=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "Unknown"
                    val lang = Regex("LANGUAGE=\"([^\"]+)\"").find(line)?.groupValues?.get(1) ?: "und"
                    val uri = Regex("URI=\"([^\"]+)\"").find(line)?.groupValues?.get(1)
                        ?.let { domain.trimEnd('/') + "/" + it } ?: ""
                    val def = line.contains("DEFAULT=YES")
                    subtitleOptions.add(SubtitleOption(name, lang, uri, def))
                }
            }
        }
        videoOption = videoOptions.lastOrNull()
        audioOption = audioOptions.find { it.default } ?: audioOptions.firstOrNull()
        subtitleOption = subtitleOptions.find { it.default } ?: subtitleOptions.firstOrNull()
    }
}