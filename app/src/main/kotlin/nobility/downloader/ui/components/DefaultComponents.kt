package nobility.downloader.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CursorDropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalDensity
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
import compose.icons.evaicons.fill.ArrowIosDownward
import compose.icons.evaicons.fill.ArrowIosUpward
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.core.Core
import nobility.downloader.utils.Constants
import nobility.downloader.utils.hover
import nobility.downloader.utils.toColorOnThis
import nobility.downloader.utils.tone

data class DropdownOption(
    val title: String,
    val modifier: Modifier = Modifier,
    val startIcon: ImageVector? = null,
    val endIcon: ImageVector? = null,
    val visible: Boolean = true,
    val onClick: () -> Unit
) {
    constructor(
        title: String,
        startIcon: ImageVector? = null,
        visible: Boolean = true,
        onClick: () -> Unit
    ) : this(
        title,
        modifier = Modifier,
        startIcon = startIcon,
        endIcon = null,
        visible = visible,
        onClick = onClick
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DefaultDropdown(
    label: String,
    expanded: Boolean,
    dropdownOptions: List<DropdownOption>,
    dropdownModifier: Modifier = Modifier.wrapContentWidth(),
    dropdownColor: Color = MaterialTheme.colorScheme.background,
    boxColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    boxTextColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    boxShape: Shape = RoundedCornerShape(4.dp),
    boxModifier: Modifier = Modifier.width(140.dp)
        .height(50.dp),
    labelFontSize: TextUnit = 12.sp,
    labelFontWeight: FontWeight = FontWeight.Bold,
    centerBoxText: Boolean = false,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onTextClick: () -> Unit = {},
    onDismissRequest: () -> Unit = {}
) {
    Box {
        Row(
            modifier = boxModifier
                .background(
                    boxColor,
                    boxShape
                ).onClick { onTextClick() }
                .pointerHoverIcon(PointerIcon.Hand),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp, start = 5.dp)
                    .weight(1f),
                fontSize = labelFontSize,
                fontWeight = labelFontWeight,
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
        DefaultCursorDropdownMenu(
            expanded,
            dropdownOptions,
            modifier = dropdownModifier
                .background(dropdownColor)
                .border(1.dp, primaryColor),
            dropdownColor,
            onDismissRequest = onDismissRequest
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DefaultCursorDropdownMenu(
    expanded: Boolean,
    dropdownOptions: List<DropdownOption>,
    modifier: Modifier = Modifier.wrapContentWidth(),
    dropdownColor: Color = MaterialTheme.colorScheme.background,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onDismissRequest: () -> Unit = {}
) {
    CursorDropdownMenu(
        expanded,
        onDismissRequest,
        modifier = modifier
            .background(dropdownColor)
            .border(
                1.dp,
                primaryColor
            )
    ) {
        dropdownOptions.forEachIndexed { index, option ->
            if (option.visible) {
                DefaultDropdownItem(
                    option.title,
                    option.startIcon,
                    option.endIcon,
                    modifier = option.modifier,
                    contentColor = dropdownColor.toColorOnThis(),
                    primaryColor = primaryColor
                ) {
                    onDismissRequest()
                    option.onClick()
                }
                if (index != dropdownOptions.lastIndex) {
                    HorizontalDivider(
                        color = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultDropdownItem(
    text: String,
    startIcon: ImageVector? = null,
    endIcon: ImageVector? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    primaryColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
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
                    modifier = Modifier.size(24.dp),
                    primaryColor
                )
            }
            Text(
                text,
                fontSize = 12.sp,
                modifier = Modifier.offset(y = (-3).dp),
                color = contentColor
            )
            if (endIcon != null) {
                Icon(
                    endIcon,
                    "",
                    modifier = Modifier.size(24.dp),
                    primaryColor
                )
            }
        }
    }
}

@Composable
fun DefaultTextField(
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
    requestFocus: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    trailingIcon: @Composable (() -> Unit)? = null,
    contextMenuItems: () -> List<ContextMenuItem> = { listOf() }
) {
    val contextMenuRepresentation = DefaultContextMenuRepresentation(
        backgroundColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray,
        textColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
        itemHoverColor = (if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray).hover(),
    )
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
                trailingIcon = trailingIcon,
                textStyle = textStyle,
                modifier = if (requestFocus)
                    modifier.focusRequester(focusRequester)
                else modifier,
                colors = colors,
                keyboardOptions = keyboardOptions
            )
        }
    }
}

@Composable
fun DefaultSettingsTextField(
    value: String,
    onValueChanged: (String) -> Unit,
    hint: String = "",
    singleLine: Boolean = true,
    enabled: Boolean = true,
    numbersOnly: Boolean = false,
    modifier: Modifier = Modifier.height(40.dp),
    settingsDescription: String = "",
    textStyle: TextStyle = MaterialTheme.typography.labelLarge,
    requestFocus: Boolean = false,
    focusRequester: FocusRequester = remember { FocusRequester() },
    passwordMode: Boolean = false,
    trailingIcon: @Composable (() -> Unit)? = null,
    contextMenuItems: () -> List<ContextMenuItem> = { mutableListOf() }
) {
    val contextMenuRepresentation = DefaultContextMenuRepresentation(
        backgroundColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray,
        textColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
        itemHoverColor = (if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray).hover(),
    )
    Tooltip(
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
                    enabled = enabled,
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
                    trailingIcon = trailingIcon,
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
                    padding = PaddingValues(start = 4.dp, end = if (trailingIcon != null) 0.dp else 4.dp),
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
fun DefaultButton(
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
fun PageButton(
    page: Page,
    modifier: Modifier = Modifier
) {
    defaultButton(
        page.title + if (page == Page.DOWNLOADS) " (${Core.child.downloadThread.downloadsInProgress.value})" else "",
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
        padding = PaddingValues(0.dp),
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
fun TabButton(
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
        padding = PaddingValues(0.dp),
        fontColor = MaterialTheme.colorScheme.onTertiaryContainer,
        fontSize = 11.sp
    ) {
        onClick.invoke()
    }
}

@Composable
fun DefaultButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    shape: Shape = RoundedCornerShape(4.dp),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(4.dp),
    fontSize: TextUnit = 11.sp,
    fontColor: Color = MaterialTheme.typography.bodyLarge.color,
    startIcon: ImageVector? = null,
    startIconColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        border = border,
        colors = colors,
        enabled = enabled,
        contentPadding = contentPadding
    ) {
        if (startIcon != null) {
            val density = LocalDensity.current
            val iconSize = with(density) {
                (fontSize * 1.35f).toDp()
            }
            DefaultIcon(
                startIcon,
                Modifier.size(iconSize)
                    .align(Alignment.CenterVertically),
                iconColor = startIconColor
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text,
            fontSize = fontSize,
            color = fontColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(2.dp)
                .align(Alignment.CenterVertically),
            overflow = TextOverflow.Ellipsis
        )
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
    padding: PaddingValues = PaddingValues(5.dp),
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
        contentPadding = padding
    ) {
        Text(
            text,
            fontSize = fontSize,
            color = fontColor,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
                .padding(2.dp),
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultCheckbox(
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

@Suppress("UNUSED")
@Composable
fun TooltipIconButton(
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
    Tooltip(
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
fun TooltipIconButton(
    tooltipText: String,
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentDescription: String = "",
    spacePosition: SpacePosition? = null,
    space: Dp = 0.dp,
    onClick: () -> Unit = {}
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
    Tooltip(tooltipText) {
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
fun BoxScope.VerticalScrollbar(
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
fun BoxScope.VerticalScrollbar(
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

data class HeaderSort(
    val title: String,
    var descending: MutableState<Boolean> = mutableStateOf(true)
) {
    override fun equals(other: Any?): Boolean {
        if (other is HeaderSort) {
            return title == other.title
        }
        return false
    }

    override fun hashCode(): Int {
        var result = descending.hashCode()
        result = 31 * result + title.hashCode()
        return result
    }
}

data class HeaderItem<T>(
    val title: String,
    val weight: Float = 1f,
    val defaultSort: Boolean = false,
    val sortSelector: ((T) -> Comparable<*>)? = null
) {
    val headerSort: HeaderSort? = if (sortSelector != null)
        HeaderSort(title)
    else null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> CustomHeader(
    items: List<HeaderItem<T>>,
    currentSort: MutableState<HeaderSort?>,
    mainColor: Color = MaterialTheme.colorScheme.inversePrimary,
    height: Dp = 40.dp,
    padding: PaddingValues = PaddingValues(end = verticalScrollbarEndPadding)
) {
    val onMainColor = mainColor.toColorOnThis()
    Row(
        modifier = Modifier.background(
            color = mainColor,
            shape = RectangleShape
        ).height(height)
            .fillMaxWidth()
            .padding(padding),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEachIndexed { index, item ->
            if (item.headerSort != null) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(item.weight)
                        .onClick {
                            if (currentSort.value == item.headerSort) {
                                if (currentSort.value?.descending?.value == true) {
                                    currentSort.value?.descending?.value = false
                                } else if (currentSort.value?.descending?.value == false) {
                                    currentSort.value?.descending?.value = true
                                    currentSort.value = null
                                }
                                //currentSort.value?.descending?.value =
                                //  currentSort.value?.descending?.value != true
                            } else {
                                currentSort.value = item.headerSort
                            }
                        }
                        .pointerHoverIcon(PointerIcon.Hand)
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier
                            .padding(4.dp),
                        color = onMainColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    Icon(
                        if (currentSort.value?.descending?.value == true)
                            EvaIcons.Fill.ArrowIosDownward
                        else
                            EvaIcons.Fill.ArrowIosUpward,
                        "",
                        modifier = Modifier.size(16.dp)
                            .alpha(
                                if (currentSort.value == item.headerSort) 1f else 0f
                            ).offset(y = 2.dp),
                        tint = onMainColor
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(item.weight)
                ) {
                    Text(
                        text = item.title,
                        modifier = Modifier
                            .padding(4.dp),
                        color = onMainColor,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            if (index != items.lastIndex) {
                VerticalDivider(
                    modifier = Modifier.fillMaxHeight()
                        .width(1.dp)
                        .background(
                            onMainColor
                        ),
                    color = Color.Transparent
                )
            }
        }
    }
}

/**
 * A lazy column with a custom header created to be used universally.
 * I was trying to figure this out for a while and eventually came up with this.
 * Now we don't need tons of enums and bloated sorting code.
 * All you need to do is: Pass the headerItems, sortState, items and the row.
 * key is also advised or else the LazyColumn's performance will suffer.
 * This is not the best, but you can build on it.
 * The major downside is the weights and dividers needed for the row.
 * Maybe there can be a row class? IDK ill figure it out one day.
 * It could also use some more customization parameters.
 * @author NobilityDev
 */
@Composable
fun <T> SortedLazyColumn(
    headerItems: List<HeaderItem<T>>,
    items: List<T>,
    headerColor: Color = MaterialTheme.colorScheme.inversePrimary,
    modifier: Modifier = Modifier.fillMaxSize()
        .background(MaterialTheme.colorScheme.surface),
    endingComparator: Comparator<T>? = null,
    key: ((T) -> Any)? = null,
    lazyListState: LazyListState = rememberLazyListState(),
    scope: CoroutineScope = rememberCoroutineScope(),
    scrollToPredicate: ((T) -> Boolean)? = null,
    lazyColumnRow: @Composable (Int, T) -> Unit
) {
    val sortState: MutableState<HeaderSort?> = remember {
        mutableStateOf(
            headerItems.firstOrNull { it.defaultSort }?.headerSort
        )
    }

    Column(
        modifier = modifier
    ) {
        CustomHeader(
            headerItems,
            sortState,
            mainColor = headerColor
        )

        val sortedItems by remember(
            items,
            sortState.value,
            endingComparator
        ) {
            derivedStateOf {
                val item = headerItems.find { it.headerSort == sortState.value }
                val selector = item?.sortSelector

                val sortedItems = if (selector != null) {
                    @Suppress("UNCHECKED_CAST")
                    val castSelector = selector as (T) -> Comparable<Any>
                    if (sortState.value?.descending?.value == true) {
                        items.sortedByDescending(castSelector)
                    } else {
                        items.sortedBy(castSelector)
                    }
                } else {
                    items
                }

                if (endingComparator != null) {
                    sortedItems.sortedWith(endingComparator)
                } else sortedItems
            }
        }

        FullBox {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(1.dp),
                modifier = Modifier
                    .padding(
                        top = 1.dp,
                        bottom = 4.dp,
                        end = verticalScrollbarEndPadding
                    )
                    .fillMaxSize()
                    .draggable(
                        state = rememberDraggableState {
                            scope.launch {
                                lazyListState.scrollBy(-it)
                            }
                        },
                        orientation = Orientation.Vertical,
                    ),
                state = lazyListState
            ) {
                itemsIndexed(
                    sortedItems,
                    key = { index, item ->
                        key?.invoke(item) ?: index
                    }
                ) { index, item ->
                    lazyColumnRow(index, item)
                }
            }
            VerticalScrollbar(lazyListState)
        }

        var checkedScroll by remember {
            mutableStateOf(false)
        }

        LaunchedEffect(sortedItems) {
            if (!checkedScroll && sortedItems.isNotEmpty() && scrollToPredicate != null) {
                val index = sortedItems.indexOfFirst(scrollToPredicate)
                if (index >= 0) {
                    lazyListState.animateScrollToItem(index)
                }
                checkedScroll = true
            }
        }
    }
}
