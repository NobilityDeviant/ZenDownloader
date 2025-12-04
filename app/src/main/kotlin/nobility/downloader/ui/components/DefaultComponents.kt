package nobility.downloader.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
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
import compose.icons.evaicons.fill.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.float
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.BoxHelper.Companion.update
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Save
import nobility.downloader.utils.Constants
import nobility.downloader.utils.hover
import nobility.downloader.utils.toColorOnThis
import nobility.downloader.utils.tone

data class DropdownOption(
    val title: String,
    val modifier: Modifier = Modifier,
    val startIcon: ImageVector? = null,
    val visible: Boolean = true,
    val contentColor: Color? = null,
    val onClick: () -> Unit
) {
    constructor(
        title: String,
        startIcon: ImageVector? = null,
        visible: Boolean = true,
        contentColor: Color? = null,
        onClick: () -> Unit
    ) : this(
        title,
        modifier = Modifier,
        startIcon = startIcon,
        visible = visible,
        contentColor = contentColor,
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
                if (hovered) MaterialTheme.colorScheme.surface.hover()
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
    page: Page
) {

    DefaultButton(
        page.title + if (page == Page.DOWNLOADS) " (${Core.child.downloadThread.downloadsInProgress.value})" else "",
        Modifier.size(120.dp, 40.dp)
            .pointerHoverIcon(PointerIcon.Hand),
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
        contentPadding = PaddingValues(0.dp),
        fontColor = if (Core.currentPage == page)
            MaterialTheme.colorScheme.onSurface
        else
            MaterialTheme.colorScheme.onPrimaryContainer,
        fontSize = 11.sp
    ) {
        Core.changePage(page)
    }
}

@Composable
fun TabButton(
    title: String,
    onClick: () -> Unit
) {
    DefaultButton(
        title,
        Modifier.size(120.dp, 40.dp)
            .pointerHoverIcon(PointerIcon.Hand),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(
            topStart = 7.dp,
            topEnd = 7.dp
        ),
        contentPadding = PaddingValues(0.dp),
        fontColor = MaterialTheme.colorScheme.onPrimaryContainer,
        fontSize = 11.sp
    ) {
        onClick()
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

//todo phase this out
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TooltipIconButton(
    tooltipText: String,
    icon: ImageVector,
    iconSize: Dp = Constants.mediumIconSize,
    iconColor: Color = MaterialTheme.colorScheme.onPrimary,
    contentDescription: String = "",
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Tooltip(tooltipText) {
        IconButton(
            onClick = onClick,
            modifier.pointerHoverIcon(PointerIcon.Hand)
        ) {
            Icon(
                icon,
                contentDescription,
                tint = iconColor,
                modifier = Modifier.size(iconSize)
                    .offset(y = 4.dp)
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

@get:Composable
val DefaultScrollbarStyle
    get() = ScrollbarStyle(
        minimalHeight = 24.dp,
        thickness = 16.dp,
        shape = RoundedCornerShape(4.dp),
        hoverDurationMillis = 300,
        unhoverColor = MaterialTheme.colorScheme.primary.tone(50.0),
        hoverColor = MaterialTheme.colorScheme.primary.tone(70.0).copy(alpha = 0.90f)
    )

@Composable
fun BoxScope.VerticalScrollbar(
    scrollState: ScrollState
) {
    VerticalScrollbar(
        modifier = Modifier.align(Alignment.CenterEnd)
            .padding(top = 3.dp, bottom = 3.dp)
            .background(MaterialTheme.colorScheme.surface.tone(20.0))
            .clipToBounds()
            .fillMaxHeight(),
        style = DefaultScrollbarStyle,
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
            .padding(top = 3.dp, bottom = 3.dp)
            .background(MaterialTheme.colorScheme.surface.tone(20.0))
            .clipToBounds()
            .fillMaxHeight(),
        style = DefaultScrollbarStyle,
        adapter = rememberScrollbarAdapter(
            scrollState = scrollState
        )
    )
}

@Composable
private fun DefaultVerticalDivider(
    modifier: Modifier = Modifier.fillMaxHeight(),
    thickness: Dp = 1.dp,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    VerticalDivider(
        modifier = modifier,
        thickness = thickness,
        color = color
    )
}

data class ColumnItem<T>(
    val title: String,
    var initialWeight: Float = 1f,
    //enabled to descending
    val defaultSort: Pair<Boolean, Boolean> = false to false,
    val contentAlignment: Alignment = Alignment.Center,
    var forceUpdate: MutableState<Int> = mutableStateOf(0),
    val weightSaveKey: Save? = null,
    val sortSelector: ((T) -> Comparable<*>)? = null,
    val cell: @Composable (Int, T) -> Unit
) {
    init {
        if (weightSaveKey != null) {
            try {
                initialWeight = weightSaveKey.defaultValue as Float
            } catch (_: Exception) {
            }
        }
    }

    val savedWeight = weightSaveKey?.float() ?: initialWeight
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
fun <T> LazyTable(
    columns: List<ColumnItem<T>>,
    items: List<T>,
    headerColor: Color = MaterialTheme.colorScheme.inversePrimary,
    headerHeight: Dp = 40.dp,
    modifier: Modifier = Modifier.fillMaxSize(),
    endingComparator: Comparator<T>? = null,
    key: ((T) -> Any)? = null,
    lazyListState: LazyListState = rememberLazyListState(),
    scope: CoroutineScope = rememberCoroutineScope(),
    scrollToPredicate: ((T) -> Boolean)? = null,
    rowHeight: Dp = 65.dp,
    rowModifier: ((T) -> Modifier)? = null,
    sortSaveKey: Save? = null,
    rightClickOptions: @Composable (Int, T) -> (List<DropdownOption>) = { i, item ->
        listOf()
    }
) {

    val onHeaderColor = headerColor.toColorOnThis()

    val columnWeights = remember {
        columns.map { mutableStateOf(it.savedWeight) }
    }

    val sortState = remember {
        mutableStateOf<Pair<Int, Boolean>?>(null)
    }

    val defaultSortApplied = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!defaultSortApplied.value) {
            if (sortSaveKey != null) {
                val value = sortSaveKey.string()
                if (value.isNotEmpty()) {
                    val split = sortSaveKey.string().split(":")
                    val index = split[0].toIntOrNull()
                    val desc = split[1].toBoolean()
                    if (index != null && index <= columns.lastIndex) {
                        sortState.value = index to desc
                        defaultSortApplied.value = true
                        return@LaunchedEffect
                    }
                }
            }
            val index = columns.indexOfFirst { it.defaultSort.first }
            if (index >= 0 && columns[index].sortSelector != null) {
                sortState.value = index to columns[index].defaultSort.second
            }
            defaultSortApplied.value = true
        }
    }

    val sortedItems by remember(
        items,
        sortState.value,
        endingComparator
    ) {
        derivedStateOf {
            val (index, descending) = sortState.value ?: return@derivedStateOf items

            val selector = columns[index].sortSelector

            val sorted = if (selector != null) {
                @Suppress("UNCHECKED_CAST")
                val castSelector = selector as (T) -> Comparable<Any>
                if (descending)
                    items.sortedByDescending { castSelector(it) }
                else
                    items.sortedBy { castSelector(it) }
            } else items

            if (endingComparator != null)
                sorted.sortedWith(endingComparator)
            else sorted
        }
    }

    var checkedScroll by remember { mutableStateOf(false) }
    LaunchedEffect(sortedItems) {
        if (!checkedScroll && scrollToPredicate != null) {
            val index = sortedItems.indexOfFirst(scrollToPredicate)
            if (index >= 0) {
                lazyListState.animateScrollToItem(index)
            }
            checkedScroll = true
        }
    }

    Column(modifier) {
        var showMenu by remember {
            mutableStateOf(false)
        }
        DefaultCursorDropdownMenu(
            showMenu,
            listOf(
                DropdownOption(
                    "Reset Sizes",
                    EvaIcons.Fill.Refresh
                ) {
                    columns.forEachIndexed { i, col ->
                        columnWeights[i].value = col.initialWeight
                        col.weightSaveKey?.update(col.initialWeight)
                    }
                },
                DropdownOption(
                    "Scroll To Top",
                    EvaIcons.Fill.ArrowUpward
                ) {
                    scope.launch {
                        lazyListState.scrollToItem(0)
                    }
                },
                DropdownOption(
                    "Scroll To Bottom",
                    EvaIcons.Fill.ArrowDownward
                ) {
                    scope.launch {
                        lazyListState.scrollToItem(items.size)
                    }
                }
            )
        ) { showMenu = false }
        Row(
            modifier = Modifier
                .background(headerColor)
                .fillMaxWidth()
                .height(headerHeight)
                .padding(end = verticalScrollbarEndPadding)
                .multiClickable(
                    onSecondaryClick = {
                        showMenu = true
                    }
                ),
            horizontalArrangement = Arrangement.Start
        ) {
            for ((i, col) in columns.withIndex()) {
                if (col.title.isEmpty()) {
                    continue
                }
                Row(
                    modifier = Modifier
                        .weight(columnWeights[i].value)
                        .fillMaxHeight()
                        .pointerHoverIcon(
                            if (col.sortSelector != null)
                                PointerIcon.Hand else PointerIcon.Default
                        )
                        .onClick(true) {
                            if (col.sortSelector == null) {
                                return@onClick
                            }
                            val current = sortState.value
                            sortState.value = if (current?.first == i)
                                if (!current.second) i to true
                                else null
                            else
                                i to false
                            val sortValue = sortState.value
                            if (sortValue != null) {
                                sortSaveKey?.update(
                                    "${sortValue.first}:${sortValue.second}"
                                )
                            } else {
                                sortSaveKey?.update("")
                            }
                            scope.launch {
                                lazyListState.scrollToItem(0)
                            }
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Spacer(Modifier.width(12.dp))
                    Text(
                        col.title,
                        color = onHeaderColor,
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val isSorted = sortState.value?.first == i
                    val isDescending = sortState.value?.second == true

                    if (col.sortSelector != null) {
                        Icon(
                            imageVector =
                                if (isDescending)
                                    EvaIcons.Fill.ArrowIosDownward
                                else
                                    EvaIcons.Fill.ArrowIosUpward,
                            contentDescription = "",
                            modifier = Modifier
                                .size(14.dp)
                                .offset(y = 2.dp)
                                .alpha(if (isSorted) 1f else 0f),
                            tint = onHeaderColor
                        )
                    }
                }

                Box(
                    Modifier
                        .width(10.dp)
                        .fillMaxHeight()
                        .pointerHoverIcon(PointerIcon.Crosshair)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->

                                val (dx, dy) = dragAmount

                                if (dx != 0f || dy != 0f) {
                                    val newWeight = (columnWeights[i].value + dx / 400f + dy / 400f)
                                        .coerceIn(0.5f, 5f)
                                    columnWeights[i].value = newWeight
                                    col.weightSaveKey?.update(newWeight)
                                }
                            }
                        }
                ) {
                    DefaultVerticalDivider(
                        Modifier.padding(top = 2.dp),
                        color = onHeaderColor
                    )
                }
            }
        }

        HorizontalDivider(
            color = onHeaderColor
        )

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
                        key?.invoke(item) ?: item.hashCode()
                    }
                ) { index, item ->
                    var isHovered by remember { mutableStateOf(false) }
                    val rowBackgroundColor by animateColorAsState(
                        if (isHovered) MaterialTheme.colorScheme.secondaryContainer.hover()
                        else MaterialTheme.colorScheme.secondaryContainer,
                        label = "rowHover"
                    )
                    var showMenu by remember {
                        mutableStateOf(false)
                    }
                    DefaultCursorDropdownMenu(
                        showMenu,
                        rightClickOptions(
                            index,
                            item
                        ),
                        onDismissRequest = { showMenu = false }
                    )
                    Row(
                        modifier = Modifier
                            .height(rowHeight)
                            .fillMaxWidth()
                            .onPointerEvent(PointerEventType.Move) {
                            }
                            .onPointerEvent(PointerEventType.Enter) {
                                isHovered = true
                            }
                            .onPointerEvent(PointerEventType.Exit) {
                                isHovered = false
                            }
                            .multiClickable(
                                indication = ripple(
                                    color = MaterialTheme.colorScheme.secondaryContainer.hover()
                                ),
                                onSecondaryClick = {
                                    showMenu = showMenu.not()
                                }
                            ) {
                                showMenu = showMenu.not()
                            }
                            .background(
                                color = rowBackgroundColor,
                                shape = RoundedCornerShape(5.dp)
                            ).then(rowModifier?.invoke(item) ?: Modifier),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        for ((i, col) in columns.withIndex()) {
                            if (col.title.isEmpty()) {
                                continue
                            }
                            Box(
                                modifier = Modifier
                                    .weight(columnWeights[i].value)
                                    .fillMaxHeight()
                                    .padding(6.dp),
                                contentAlignment = col.contentAlignment
                            ) {
                                col.cell(i, item)
                            }
                            Box(
                                Modifier
                                    .width(10.dp)
                                    .fillMaxHeight()
                                    .pointerHoverIcon(PointerIcon.Crosshair)
                                    .pointerInput(Unit) {
                                        detectDragGestures { change, dragAmount ->

                                            val (dx, dy) = dragAmount

                                            if (dx != 0f || dy != 0f) {
                                                val newWeight = (columnWeights[i].value + dx / 400f + dy / 400f)
                                                    .coerceIn(0.5f, 5f)
                                                columnWeights[i].value = newWeight
                                                col.weightSaveKey?.update(newWeight)
                                            }
                                        }
                                    }
                            ) {
                                DefaultVerticalDivider(
                                    color = onHeaderColor
                                )
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(lazyListState)
        }
    }
}

