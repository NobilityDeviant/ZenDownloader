package nobility.downloader.ui.windows.utils

import androidx.compose.runtime.Composable

data class WindowData(
    val title: String,
    val scope: AppWindowScope,
    var content: @Composable () -> Unit
)