package nobility.downloader.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import coil3.PlatformContext
import coil3.asImage
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import kotlinx.coroutines.Dispatchers
import nobility.downloader.ui.windows.ImagePopoutWindow
import AppInfo
import nobility.downloader.utils.ImageUtils
import nobility.downloader.utils.Tools
import nobility.downloader.utils.hover
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DefaultImage(
    imagePath: String,
    urlBackup: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier.fillMaxSize(),
    pointerIcon: PointerIcon? = PointerIcon.Hand,
    fastScrolling: Boolean = false,
    onRightClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val context = LocalPlatformContext.current

    val imageFileExists = remember(imagePath) {
        File(imagePath).exists()
    }

    LaunchedEffect(imageFileExists) {
        if (!imageFileExists && urlBackup != null) {
            Tools.downloadFileWithRetries(
                urlBackup,
                File(imagePath)
            )
        }
    }

    AsyncImage(
        model = imageRequest(
            context,
            imagePath
        ),
        contentDescription = null,
        contentScale = contentScale,
        filterQuality = if (fastScrolling) FilterQuality.Low
        else FilterQuality.High,
        modifier = modifier
            .then(
                Modifier.onClick {
                    if (onClick != null) {
                        onClick()
                    } else {
                        ImagePopoutWindow.open(imagePath, urlBackup)
                    }
                }
            )
            .onClick(
                matcher = PointerMatcher.mouse(PointerButton.Secondary)
            ) {
                onRightClick?.invoke()
            }
            .then(
                if (pointerIcon != null)
                    Modifier.pointerHoverIcon(pointerIcon)
                else Modifier
            ),
        fallback = rememberAsyncImagePainter(
            model = imageRequest(
                context,
                urlBackup ?: AppInfo.NO_IMAGE_PATH
            ),
            filterQuality = FilterQuality.Low
        ),
        error = rememberAsyncImagePainter(
            model = imageRequest(
                context,
                urlBackup ?: AppInfo.NO_IMAGE_PATH
            ),
            filterQuality = FilterQuality.Low
        )
    )
}


fun imageRequest(
    context: PlatformContext,
    data: String,
    fallbackImage: ImageBitmap? = ImageUtils.noImage
): ImageRequest {

    val builder = ImageRequest.Builder(context)
        .data(data)
        .precision(Precision.EXACT)
        .crossfade(true)
        .diskCachePolicy(CachePolicy.ENABLED)
        .memoryCachePolicy(CachePolicy.ENABLED)
        .coroutineContext(Dispatchers.IO)

    if (fallbackImage != null) {
        val skiaImage = fallbackImage.asSkiaBitmap().asImage()
        builder.error { skiaImage }
            .fallback { skiaImage }
    }

    return builder.build()
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DefaultIcon(
    image: ImageVector,
    modifier: Modifier = Modifier,
    iconColor: Color = MaterialTheme.colorScheme.onSurface,
    description: String? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    onLongClick: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Icon(
        image,
        description,
        modifier = modifier.then(
            if (onClick != null)
                Modifier.combinedClickable(
                    onLongClick = onLongClick,
                    onClick = onClick
                )
            else Modifier
        ).indication(
            interactionSource,
            indication = ripple(
                color = iconColor.hover()
            )
        ),
        tint = iconColor
    )
}