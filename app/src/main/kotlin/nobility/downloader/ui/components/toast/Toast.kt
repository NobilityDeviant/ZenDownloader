package nobility.downloader.ui.components.toast

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.layoutId
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.Close
import nobility.downloader.core.BoxHelper.Companion.float
import nobility.downloader.core.settings.Defaults

@Composable
fun Toast(
    modifier: Modifier = Modifier,
    action: @Composable (() -> Unit)? = null,
    dismissAction: @Composable (() -> Unit)? = null,
    shape: Shape = ToastDefaults.shape,
    containerColor: Color = ToastDefaults.color,
    contentColor: Color = ToastDefaults.contentColor,
    actionContentColor: Color = ToastDefaults.actionContentColor,
    dismissActionContentColor: Color = ToastDefaults.dismissActionContentColor,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = containerColor.copy(alpha = Defaults.TOAST_ALPHA.float()),
            contentColor = contentColor,
            shadowElevation = ToastTheme.ContainerElevation
        ) {
            val textStyle = ToastTheme.SupportingTextFont
            val actionTextStyle = ToastTheme.ActionLabelTextFont
            CompositionLocalProvider(LocalTextStyle provides textStyle) {
                CoreToaster(
                    text = content,
                    action = action,
                    dismissAction = dismissAction,
                    actionTextStyle,
                    actionContentColor,
                    dismissActionContentColor
                )
            }
        }
    }
}


@Composable
fun Toast(
    toastData: ToastData,
    modifier: Modifier = Modifier,
    shape: Shape = ToastDefaults.shape,
    containerColor: Color = ToastDefaults.color,
    contentColor: Color = ToastDefaults.contentColor,
    actionColor: Color = ToastDefaults.actionColor,
    actionContentColor: Color = ToastDefaults.actionContentColor,
    dismissActionContentColor: Color = ToastDefaults.dismissActionContentColor,
) {
    val actionLabel = toastData.visuals.actionLabel
    val actionComposable: (@Composable () -> Unit)? = if (actionLabel != null) {
        @Composable {
            TextButton(
                colors = ButtonDefaults.textButtonColors(contentColor = actionColor),
                onClick = { toastData.performAction() },
                content = { Text(actionLabel) }
            )
        }
    } else {
        null
    }
    val dismissActionComposable: (@Composable () -> Unit)? =
        if (toastData.visuals.withDismissAction) {
            @Composable {
                IconButton(
                    onClick = { toastData.dismiss() },
                    content = {
                        Icon(
                            EvaIcons.Fill.Close,
                            contentDescription = null,
                        )
                    }
                )
            }
        } else {
            null
        }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Toast(
            modifier = modifier.padding(12.dp),//.offset(y = 20.dp),
            action = actionComposable,
            dismissAction = dismissActionComposable,
            shape = shape,
            containerColor = containerColor,
            contentColor = contentColor,
            actionContentColor = actionContentColor,
            dismissActionContentColor = dismissActionContentColor,
            content = {
                Text(
                    toastData.visuals.message,
                    textAlign = TextAlign.Center,
                    fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(20.dp)
                )
            }
        )
    }
}

@Composable
private fun CoreToaster(
    text: @Composable () -> Unit,
    action: @Composable (() -> Unit)?,
    dismissAction: @Composable (() -> Unit)?,
    actionTextStyle: TextStyle,
    actionTextColor: Color,
    dismissActionColor: Color
) {
    val textTag = "text"
    val actionTag = "action"
    val dismissActionTag = "dismissAction"
    Column(
        modifier = Modifier
            // Fill max width, up to ContainerMaxWidth.
            .widthIn(max = ContainerMaxWidth)
            .heightIn(max = ContainerMaxHeight)
            .wrapContentSize(Alignment.Center),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.layoutId(textTag).padding(vertical = ToastVerticalPadding),
            contentAlignment = Alignment.Center
        ) { text() }
        if (action != null) {
            Box(Modifier.layoutId(actionTag)) {
                CompositionLocalProvider(
                    LocalContentColor provides actionTextColor,
                    LocalTextStyle provides actionTextStyle,
                    content = action
                )
            }
        }
        if (dismissAction != null) {
            Box(Modifier.layoutId(dismissActionTag)) {
                CompositionLocalProvider(
                    LocalContentColor provides dismissActionColor,
                    content = dismissAction
                )
            }
        }
    }
}

/**
 * Contains the default values used for [Toast].
 */
object ToastDefaults {
    /** Default shape of a snackbar. */
    val shape: Shape @Composable get() = ToastTheme.ContainerShape

    /** Default color of a snackbar. */
    val color: Color @Composable get() = ToastTheme.ContainerColor

    /** Default content color of a snackbar. */
    val contentColor: Color @Composable get() = ToastTheme.SupportingTextColor

    /** Default action color of a snackbar. */
    val actionColor: Color @Composable get() = ToastTheme.ActionLabelTextColor

    /** Default action content color of a snackbar. */
    val actionContentColor: Color @Composable get() = ToastTheme.ActionLabelTextColor

    /** Default dismiss action content color of a snackbar. */
    val dismissActionContentColor: Color @Composable get() = ToastTheme.IconColor
}

private val ContainerMaxWidth = 600.dp
private val ContainerMaxHeight = 300.dp
private val ToastVerticalPadding = 6.dp
