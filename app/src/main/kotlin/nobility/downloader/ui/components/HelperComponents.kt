@file:Suppress("FunctionName")

package nobility.downloader.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.settings.Defaults
import nobility.downloader.utils.tone
import kotlin.math.abs

@Composable
fun BasicTextFieldWithCursorAtEnd(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    cursorBrush: Brush = SolidColor(Color.Black),
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() }
) {
    var textFieldValueState by remember {
        mutableStateOf(
            TextFieldValue(
                text = value, selection = when {
                    value.isEmpty() -> TextRange.Zero
                    else -> TextRange(value.length, value.length)
                }
            )
        )
    }

    val textFieldValue = textFieldValueState.copy(text = value)

    SideEffect {
        if (textFieldValue.selection != textFieldValueState.selection ||
            textFieldValue.composition != textFieldValueState.composition
        ) {
            textFieldValueState = textFieldValue
        }
    }
    // Last String value that either text field was recomposed with or updated in the onValueChange
    // callback. We keep track of it to prevent calling onValueChange(String) for same String when
    // CoreTextField's onValueChange is called multiple times without recomposition in between.
    var lastTextValue by remember(value) { mutableStateOf(value) }

    BasicTextField(
        value = textFieldValue,
        onValueChange = { newTextFieldValueState ->
            textFieldValueState = newTextFieldValueState

            val stringChangedSinceLastInvocation = lastTextValue != newTextFieldValueState.text
            lastTextValue = newTextFieldValueState.text

            if (stringChangedSinceLastInvocation) {
                onValueChange(newTextFieldValueState.text)
            }
        },
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
        maxLines = maxLines,
        minLines = minLines,
        visualTransformation = visualTransformation,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        decorationBox = decorationBox,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FixedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    textStyle: TextStyle = LocalTextStyle.current,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    shape: Shape = TextFieldDefaults.shape,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    padding: PaddingValues = PaddingValues(4.dp)
) {
    // If color is not provided via the text style, use content color as a default
    val textColor = textStyle.color.takeOrElse {
        MaterialTheme.colorScheme.onSurface
    }
    val mergedTextStyle = textStyle.merge(TextStyle(color = textColor))
    val customTextSelectionColors = TextSelectionColors(
        handleColor = MaterialTheme.colorScheme.primary,
        backgroundColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
    )
    CompositionLocalProvider(LocalTextSelectionColors provides customTextSelectionColors) {
        BasicTextFieldWithCursorAtEnd(
            value = value,
            modifier = modifier,
            onValueChange = onValueChange,
            textStyle = mergedTextStyle,
            enabled = enabled,
            readOnly = readOnly,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            interactionSource = interactionSource,
            singleLine = singleLine,
            maxLines = maxLines,
            minLines = minLines,
            decorationBox = @Composable { innerTextField ->
                // places leading icon, text field with label and placeholder, trailing icon
                TextFieldDefaults.DecorationBox(
                    value = value,
                    visualTransformation = visualTransformation,
                    innerTextField = innerTextField,
                    placeholder = placeholder,
                    label = label,
                    leadingIcon = leadingIcon,
                    trailingIcon = trailingIcon,
                    supportingText = supportingText,
                    shape = shape,
                    singleLine = singleLine,
                    enabled = enabled,
                    isError = isError,
                    interactionSource = interactionSource,
                    colors = colors,
                    contentPadding = padding
                )
            }
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Tooltip(
    tooltipText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {

    var anchorHovered by remember { mutableStateOf(false) }
    var popupHovered by remember { mutableStateOf(false) }

    val pointerOver = anchorHovered || popupHovered

    var showTooltip by remember { mutableStateOf(false) }

    LaunchedEffect(pointerOver) {
        if (pointerOver) {
            if (!showTooltip) {
                delay(350)
                showTooltip = true
            }
        } else {
            showTooltip = false
        }
    }

    Box(
        modifier = modifier
            .onPointerEvent(PointerEventType.Enter) { anchorHovered = true }
            .onPointerEvent(PointerEventType.Exit) { anchorHovered = false }
    ) {

        content()

        if (Defaults.SHOW_TOOLTIPS.boolean() && tooltipText.isNotEmpty() && showTooltip) {
            Popup(
                offset = IntOffset(64, 84),
                properties = PopupProperties(focusable = false),
                onDismissRequest = { showTooltip = false }
            ) {
                Box(
                    modifier = Modifier
                        .onPointerEvent(PointerEventType.Enter) { popupHovered = true }
                        .onPointerEvent(PointerEventType.Exit) { popupHovered = false }
                        .background(
                            MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = 0.95f),
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.primaryContainer
                                .tone(40.0)
                                .copy(alpha = 0.95f),
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(
                        tooltipText,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Modifier.horizontalWheelScroll(
    scrollMultiplier: Int = -50,
    action: (Float) -> Unit
): Modifier = this then pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val scrollDeltaY = event.changes
                .firstOrNull { it.type == PointerType.Mouse }
                ?.scrollDelta?.y ?: 0f

            if (scrollDeltaY != 0f) {
                action(scrollDeltaY * scrollMultiplier)
            }
        }
    }
}

@Composable
fun rememberScrollSpeed(
    lazyListState: LazyListState
): State<Boolean> {

    val isFastScrolling = remember { mutableStateOf(false) }

    LaunchedEffect(lazyListState) {
        var lastFirstVisible = lazyListState.firstVisibleItemIndex
        var lastTime = withFrameNanos { it }

        snapshotFlow { lazyListState.firstVisibleItemIndex }
            .collect { index ->
                val now = withFrameNanos { it }
                val diff = now - lastTime
                val delta = abs(index - lastFirstVisible)
                //if moving more than 2 items in under 100ms
                isFastScrolling.value = delta > 2 && diff < 100_000_000
                lastFirstVisible = index
                lastTime = now
            }
    }

    return isFastScrolling
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.multiClickable(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    indication: Indication? = null,
    onSecondaryClick: () -> Unit = {},
    onPrimaryClick: () -> Unit = {}
): Modifier = composed {
    this
        .indication(interactionSource, indication)
        .onClick(
            enabled,
            interactionSource = interactionSource,
            matcher = PointerMatcher.mouse(PointerButton.Primary)
        ) {
            onPrimaryClick()
        }
        .onClick(
            enabled,
            interactionSource = interactionSource,
            matcher = PointerMatcher.mouse(PointerButton.Secondary)
        ) {
            onSecondaryClick()
        }
}

data class OverflowOption(
    val icon: ImageVector,
    val tooltip: String = "",
    val visible: Boolean = true,
    val modifier: Modifier = Modifier,
    val contentColor: Color? = null,
    val badge: (@Composable BoxScope.() -> Unit)? = null,
    val onClick: () -> Unit
)

@Composable
fun <T> OverflowRow(
    items: List<T>,
    modifier: Modifier = Modifier,
    horizontalSpacing: Dp = 4.dp,
    contentAlignment: Alignment.Horizontal = Alignment.End,
    itemContent: @Composable (T) -> Unit,
    overflowContent: @Composable (List<T>) -> Unit
) {
    SubcomposeLayout(modifier) { constraints ->

        val spacingPx = horizontalSpacing.roundToPx()

        val loose = Constraints(
            minWidth = 0,
            maxWidth = Int.MAX_VALUE,
            minHeight = 0,
            maxHeight = constraints.maxHeight
        )

        val itemPlaceables = items.mapIndexed { index, item ->
            subcompose(item.hashCode()) {
                itemContent(item)
            }.first().measure(loose)
        }

        val overflowProbe = subcompose("overflow-probe") {
            key("probe") { overflowContent(emptyList()) }
        }.first().measure(loose)


        val maxWidth = constraints.maxWidth

        var fitCount = items.size
        while (fitCount >= 0) {

            val visibleWidth = itemPlaceables
                .take(fitCount)
                .foldIndexed(0) { i, acc, p ->
                acc + p.width + if (i > 0) spacingPx else 0
            }

            val needsOverflow = fitCount < items.size

            val spacingBeforeOverflow = if (needsOverflow && fitCount > 0) spacingPx else 0

            val totalWidth = visibleWidth + (
                    if (needsOverflow)
                        spacingBeforeOverflow + overflowProbe.width else 0
                    )

            if (totalWidth <= maxWidth) {
                break
            }
            fitCount--
        }

        val visiblePlaceables = itemPlaceables.take(fitCount)
        val hiddenItems = items.drop(fitCount)

        val finalOverflowPlaceable = if (hiddenItems.isNotEmpty()) {
            subcompose("overflow-final-${hiddenItems.hashCode()}") {
                key(hiddenItems) { overflowContent(hiddenItems) }
            }.first().measure(loose)
        } else null

        val layoutHeight = listOfNotNull(
            visiblePlaceables.maxOfOrNull { it.height },
            finalOverflowPlaceable?.height
        ).maxOrNull() ?: 0

        layout(maxWidth, layoutHeight) {

            val visibleWidth = visiblePlaceables.foldIndexed(0) { i, acc, p ->
                acc + p.width + if (i > 0) spacingPx else 0
            }

            val overflowWidth = finalOverflowPlaceable?.let { ow ->
                    if (fitCount > 0) spacingPx + ow.width else ow.width
                } ?: 0

            val totalContentWidth = visibleWidth + overflowWidth

            val startX = when (contentAlignment) {
                Alignment.Start -> 0
                Alignment.End -> maxWidth - totalContentWidth
                Alignment.CenterHorizontally -> (maxWidth - totalContentWidth) / 2
                else -> 0
            }

            var x = startX

            visiblePlaceables.forEachIndexed { i, p ->
                if (i > 0) x += spacingPx
                p.placeRelative(x, 0)
                x += p.width
            }

            finalOverflowPlaceable?.let { of ->
                if (fitCount > 0) {
                    x += spacingPx
                }
                of.placeRelative(x, 0)
            }
        }
    }
}