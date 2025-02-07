package nobility.downloader.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.EvaIcons
import compose.icons.evaicons.Fill
import compose.icons.evaicons.fill.ArrowDown
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.utils.Constants
import nobility.downloader.utils.tone

data class DropdownOption(
    val title: String,
    val modifier: Modifier = Modifier,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun defaultDropdown(
    label: String,
    expanded: Boolean,
    dropdownOptions: List<DropdownOption>,
    dropdownModifier: Modifier = Modifier,
    boxWidth: Dp = 120.dp,
    boxHeight: Dp = Dp.Unspecified,
    boxPadding: Dp = 0.dp,
    boxColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    boxTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    centerBoxText: Boolean = false,
    onTextClick: () -> Unit = {},
    onDismissRequest: () -> Unit = {}
) {
    Box {
        Row(
            modifier = Modifier
                .width(boxWidth)
                .height(boxHeight)
                .padding(boxPadding)
                .background(
                    boxColor,
                    RoundedCornerShape(4.dp)
                ).onClick { onTextClick() },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp, start = 5.dp)
                    .weight(1f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = if (centerBoxText) TextAlign.Center else TextAlign.Start,
                color = boxTextColor
            )
            Icon(
                EvaIcons.Fill.ArrowDown,
                "",
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp, end = 1.dp)
                    .size(18.dp),
                tint = boxTextColor
            )
        }
        DropdownMenu(
            expanded,
            onDismissRequest,
            modifier = dropdownModifier
        ) {
            dropdownOptions.forEach {
                defaultDropdownItem(
                    it.title
                ) {
                    it.onClick()
                }
            }
        }
    }
}

@Composable
fun defaultDropdownItem(
    text: String,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interaction =  remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(
            Modifier.background(
                if (hovered) MaterialTheme.colorScheme.surface.tone(35.0)
                else MaterialTheme.colorScheme.surface
            )
        ),
        interactionSource = interaction
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (startIcon != null) {
                Icon(
                    startIcon,
                    "",
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text,
                fontSize = 12.sp,
                modifier = Modifier.offset(y = (-3).dp)
            )
            if (endIcon != null) {
                Icon(
                    endIcon,
                    "",
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun defaultTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    readOnly: Boolean = false,
    enabled: Boolean = true,
    colors: TextFieldColors = TextFieldDefaults.colors(),
    textStyle: TextStyle = LocalTextStyle.current,
    keyboardOptions: KeyboardOptions = KeyboardOptions(),
    modifier: Modifier = Modifier,
    contextMenuItems: () -> List<ContextMenuItem> = { listOf() }
) {
    val contextMenuRepresentation = if (isSystemInDarkTheme()) {
        DarkDefaultContextMenuRepresentation
    } else {
        LightDefaultContextMenuRepresentation
    }
    CompositionLocalProvider(LocalContextMenuRepresentation provides contextMenuRepresentation) {
        ContextMenuDataProvider(
            items = contextMenuItems
        ) {
            TextField(
                value,
                onValueChange = onValueChanged,
                singleLine = singleLine,
                readOnly = readOnly,
                enabled = enabled,
                placeholder = {
                    if (hint.isNotEmpty()) {
                        Text(
                            hint,
                            modifier = Modifier.alpha(0.3f)
                        )
                    }
                },
                textStyle = textStyle,
                modifier = modifier,
                colors = colors,
                keyboardOptions = keyboardOptions
            )
        }
    }
}

@Composable
fun defaultSettingsTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    enabled: MutableState<Boolean> = mutableStateOf(true),
    numbersOnly: Boolean = false,
    modifier: Modifier = Modifier.height(30.dp),
    settingsDescription: String = "",
    textStyle: TextStyle = MaterialTheme.typography.labelSmall,
    requestFocus: Boolean = false,
    passwordMode: Boolean = false,
    contextMenuItems: () -> List<ContextMenuItem> = { listOf() }
) {
    val focusRequester = remember { FocusRequester() }
    val stateEnabled by remember { enabled }
    val contextMenuRepresentation = if (isSystemInDarkTheme()) {
        DarkDefaultContextMenuRepresentation
    } else {
        LightDefaultContextMenuRepresentation
    }
    tooltip(
        tooltipText = settingsDescription
    ) {
        CompositionLocalProvider(
            LocalContextMenuRepresentation provides contextMenuRepresentation
        ) {
            ContextMenuDataProvider(
                items = contextMenuItems
            ) {
                FixedTextField(
                    value,
                    enabled = stateEnabled,
                    onValueChange = onValueChanged,
                    singleLine = singleLine,
                    modifier = if (requestFocus)
                        modifier.focusRequester(focusRequester)
                    else modifier,
                    textStyle = textStyle,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = if (numbersOnly)
                            KeyboardType.Number
                        else
                            KeyboardType.Text
                    ),
                    placeholder = {
                        if (hint.isNotEmpty()) {
                            Text(
                                hint,
                                modifier = Modifier.alpha(0.3f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    colors = TextFieldDefaults.colors(),
                    padding = PaddingValues(start = 4.dp, end = 4.dp),
                    visualTransformation = if (passwordMode)
                        PasswordVisualTransformation()
                    else
                        VisualTransformation.None
                )
            }
        }
    }
    if (requestFocus) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
}

@Composable
fun defaultButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: MutableState<Boolean>,
    height: Dp = Dp.Unspecified,
    width: Dp = Dp.Unspecified,
    padding: Dp = 5.dp,
    onClick: () -> Unit
) {
    val stateEnabled by remember { enabled }
    Button(
        onClick = onClick,
        modifier = Modifier.padding(padding)
            .height(height)
            .width(width)
            .then(modifier),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(),
        enabled = stateEnabled,
        contentPadding = PaddingValues(5.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun pageButton(
    page: Page,
    modifier: Modifier = Modifier
) {
    defaultButton(
        page.title + if (page == Page.DOWNLOADS) " (${Core.child.downloadsInProgress.value})" else "",
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (Core.currentPage == page)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(
            topStart = 7.dp,
            topEnd = 7.dp
        ),
        height = 35.dp,
        width = 110.dp,
        padding = 0.dp,
        fontColor = if (Core.currentPage == page)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onSecondaryContainer,
        fontSize = 11.sp
    ) {
        Core.changePage(page)
    }
}

@Composable
fun tabButton(
    title: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    defaultButton(
        title,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(
            topStart = 7.dp,
            topEnd = 7.dp
        ),
        height = 35.dp,
        width = 110.dp,
        padding = 0.dp,
        fontColor = MaterialTheme.colorScheme.onTertiaryContainer,
        fontSize = 11.sp
    ) {
        onClick.invoke()
    }
}

@Composable
fun defaultButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 40.dp,
    width: Dp = 120.dp,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shape: Shape = RoundedCornerShape(4.dp),
    border: BorderStroke? = null,
    padding: Dp = 5.dp,
    fontSize: TextUnit = 11.sp,
    fontColor: Color = MaterialTheme.typography.bodyLarge.color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.padding(padding)
            .height(height)
            .width(width)
            .then(modifier),
        shape = shape,
        border = border,
        colors = colors,
        enabled = enabled,
        contentPadding = PaddingValues(5.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.CenterVertically)
        ) {
            Text(
                text,
                fontSize = fontSize,
                color = fontColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun defaultCheckbox(
    checked: Boolean,
    modifier: Modifier = Modifier,
    onCheckedChanged: ((Boolean) -> Unit)
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 3.dp) {
        Checkbox(
            checked,
            onCheckedChanged,
            colors = CheckboxDefaults.colors(),
            modifier = modifier
        )
    }
}

@Composable
fun defaultIcon(
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconModifier: Modifier = Modifier,
    contentDescription: String = "",
) {
    Icon(
        icon,
        contentDescription,
        tint = iconColor,
        modifier = iconModifier.size(iconSize)
    )
}

@Suppress("UNUSED")
@Composable
fun tooltipIconButton(
    tooltipText: String,
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    iconModifier: Modifier = Modifier,
    buttonModifier: Modifier = Modifier,
    tooltipModifier: Modifier = Modifier,
    contentDescription: String = "",
    onClick: () -> Unit
) {
    tooltip(
        tooltipText,
        tooltipModifier
    ) {
        IconButton(
            onClick = onClick,
            modifier = buttonModifier
        ) {
            Icon(
                icon,
                contentDescription,
                tint = iconColor,
                modifier = iconModifier.size(iconSize)
            )
        }
    }
}

@Composable
fun tooltipIconButton(
    tooltipText: String,
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentDescription: String = "",
    spacePosition: SpacePosition? = null,
    space: Dp = 0.dp,
    onClick: () -> Unit
) {
    val modifier = Modifier
    if (spacePosition != null) {
        if (spacePosition == SpacePosition.START) {
            modifier.apply {
                padding(start = space)
            }
        } else if (spacePosition == SpacePosition.END) {
            modifier.apply {
                padding(end = space)
            }
        }
    }
    tooltip(tooltipText) {
        IconButton(
            onClick = onClick
        ) {
            Icon(
                icon,
                contentDescription,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

val verticalScrollbarEndPadding = 10.dp

@Suppress("FunctionName")
@Composable
fun FullBox(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        content = content
    )
}

@Composable
fun BoxScope.verticalScrollbar(
    scrollState: ScrollState
) {
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd)
            .background(MaterialTheme.colorScheme.surface.tone(20.0))
            .fillMaxHeight()
            .padding(top = 3.dp, bottom = 3.dp),
        style = ScrollbarStyle(
            minimalHeight = 16.dp,
            thickness = 10.dp,
            shape = RoundedCornerShape(10.dp),
            hoverDurationMillis = 300,
            unhoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.70f),
            hoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.90f)
        ),
        adapter = rememberScrollbarAdapter(
            scrollState = scrollState
        )
    )
}

@Composable
fun BoxScope.verticalScrollbar(
    scrollState: LazyListState
) {
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd)
            .background(MaterialTheme.colorScheme.surface.tone(20.0))
            .fillMaxHeight()
            .padding(top = 3.dp, bottom = 3.dp),
        style = ScrollbarStyle(
            minimalHeight = 16.dp,
            thickness = 10.dp,
            shape = RoundedCornerShape(10.dp),
            hoverDurationMillis = 300,
            unhoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.70f),
            hoverColor = MaterialTheme.colorScheme.surface.tone(50.0).copy(alpha = 0.90f)
        ),
        adapter = rememberScrollbarAdapter(
            scrollState = scrollState
        )
    )
}

enum class SpacePosition {
    START, END
}