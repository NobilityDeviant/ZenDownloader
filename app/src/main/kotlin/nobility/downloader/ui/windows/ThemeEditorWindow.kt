package nobility.downloader.ui.windows

import AppInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.darkrockstudios.libraries.mpfilepicker.FilePicker
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.ImageColorPicker
import com.github.skydoves.colorpicker.compose.PaletteContentScale
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import com.materialkolor.PaletteStyle
import com.materialkolor.ktx.toHex
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Moon
import compose.icons.evaicons.fill.Sun
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nobility.downloader.core.BoxHelper
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults
import nobility.downloader.ui.components.DefaultButton
import nobility.downloader.ui.components.DefaultDropdown
import nobility.downloader.ui.components.DropdownOption
import nobility.downloader.ui.theme.seedColor
import nobility.downloader.ui.windows.utils.ApplicationState
import nobility.downloader.utils.ImageUtils
import nobility.downloader.utils.Theme
import nobility.downloader.utils.colorName
import nobility.downloader.utils.fileExists
import java.io.File
import kotlin.random.Random

object ThemeEditorWindow {

    @OptIn(ExperimentalComposeUiApi::class)
    fun open() {
        ApplicationState.newWindow(
            "Theme Editor"
        ) {

            Column(
                Modifier.fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.background
                    )
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {

                var initialColorPicked by remember {
                    mutableStateOf(false)
                }

                var showFilePicker by remember {
                    mutableStateOf(false)
                }

                var hexColor by remember {
                    mutableStateOf(Core.mainColor.toHex())
                }

                var image by remember {
                    mutableStateOf(
                        ImageUtils.loadImageBitmapFromResource(
                            AppInfo.COLOR_WHEEL_PATH
                        )
                    )
                }

                val wheelImage by remember {
                    mutableStateOf(
                        ImageUtils.loadImageBitmapFromResource(
                            if (Random.nextInt(2) == 1)
                                AppInfo.CURSOR_PATH else AppInfo.CURSOR2_PATH
                        )
                    )
                }

                val controller = rememberColorPickerController()

                ImageColorPicker(
                    modifier = Modifier.fillMaxWidth(0.9f)
                        .height(310.dp)
                        .padding(8.dp),
                    paletteImageBitmap = image,
                    wheelImageBitmap = wheelImage,
                    controller = controller,
                    paletteContentScale = PaletteContentScale.FIT,
                    onColorChanged = {
                        if (initialColorPicked) {
                            hexColor = it.hexCode
                                .replaceFirst("ff", "#")
                                .uppercase()
                            Core.mainColor = it.color
                            Defaults.MAIN_COLOR.update(
                                it.color.value.toString()
                            )
                        } else {
                            initialColorPicked = true
                        }
                    }
                )

                BrightnessSlider(
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .height(35.dp)
                        .padding(vertical = 4.dp),
                    controller = controller,
                    borderSize = 1.dp,
                    borderColor = MaterialTheme.colorScheme.onBackground,
                    wheelImageBitmap = wheelImage
                )

                Text(
                    Core.mainColor.colorName() + " ($hexColor)",
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(4.dp),
                    style = TextStyle(
                        color = Core.mainColor,
                        shadow = Shadow(
                            color = Theme.colorScheme.onBackground,
                            offset = Offset(1f, 1f),
                            blurRadius = 1f
                        )
                    )
                )

                FlowRow(
                    Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {

                    DefaultButton(
                        "Choose Image",
                        Modifier.height(40.dp)
                            .width(160.dp)
                    ) {
                        showFilePicker = true
                    }

                    DefaultButton(
                        "Random Series Image",
                        Modifier.height(40.dp)
                            .width(160.dp)
                    ) {
                        Core.taskScope.launch {
                            val allSeries = BoxHelper.allSeries
                            var imagePath = ""

                            var attempts = 0
                            while (!imagePath.fileExists()) {
                                if (attempts >= 50) {
                                    showToast(
                                        "Failed to find a valid series image after 50 attempts."
                                    )
                                    break
                                }
                                imagePath = allSeries.random().imagePath
                                attempts++
                            }
                            if (imagePath.fileExists()) {
                                image = ImageUtils.loadImageBitmapFromFile(imagePath)
                            }
                        }
                    }

                    var resetting by remember {
                        mutableStateOf(false)
                    }

                    val scope = rememberCoroutineScope()

                    DefaultButton(
                        "Reset To Default",
                        Modifier.height(40.dp)
                            .width(160.dp)
                    ) {
                        if (resetting) {
                            return@DefaultButton
                        }
                        scope.launch {
                            resetting = true
                            image = ImageUtils.loadImageBitmapFromResource(
                                AppInfo.COLOR_WHEEL_PATH
                            )
                            delay(1000)
                            Core.mainColor = seedColor
                            Defaults.MAIN_COLOR.update(
                                seedColor.value.toString()
                            )
                            controller.setBrightness(1f, true)
                            controller.selectByCoordinate(
                                550f,
                                325f,
                                true
                            )
                            resetting = false
                        }
                    }
                }

                FlowRow(
                    Modifier.padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically
                ) {

                    DefaultButton(
                        if (Core.darkMode.value) "Dark Mode" else "Light Mode",
                        Modifier.height(40.dp)
                            .width(160.dp),
                        startIcon = if (Core.darkMode.value) EvaIcons.Fill.Moon else EvaIcons.Fill.Sun
                    ) {
                        Core.darkMode.value = Core.darkMode.value.not()
                        Defaults.DARK_MODE.update(
                            Defaults.DARK_MODE.boolean().not()
                        )
                    }

                    var showPaletteStylePicker by remember {
                        mutableStateOf(false)
                    }

                    DefaultDropdown(
                        Core.mainPaletteStyle.name,
                        showPaletteStylePicker,
                        PaletteStyle.entries.map {
                            DropdownOption(
                                it.name
                            ) {
                                Core.mainPaletteStyle = it
                                Defaults.MAIN_PALETTE_STYLE.update(it.name)
                            }
                        },
                        boxModifier = Modifier.size(130.dp, 40.dp),
                        boxColor = MaterialTheme.colorScheme.primary,
                        boxTextColor = MaterialTheme.colorScheme.onPrimary,
                        onTextClick = { showPaletteStylePicker = true}
                    ) { showPaletteStylePicker = false }
                }

                FilePicker(
                    show = showFilePicker,
                    initialDirectory = BoxHelper.seriesImagesPath,
                    fileExtensions = listOf("png", "jpg", "jpeg"),
                    title = "Choose Image File"
                ) {
                    if (it != null) {
                        val platformFile = it.platformFile
                        if (platformFile is File) {
                            image = ImageUtils.loadImageBitmapFromFile(
                                platformFile.absolutePath
                            )
                        }
                    }
                    showFilePicker = false
                }
            }
        }
    }
}