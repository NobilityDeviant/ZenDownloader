package nobility.downloader.utils

import androidx.compose.ui.input.key.*
import nobility.downloader.Page
import nobility.downloader.core.BoxHelper.Companion.boolean
import nobility.downloader.core.BoxHelper.Companion.string
import nobility.downloader.core.Core
import nobility.downloader.core.settings.Defaults

typealias AwtKey = java.awt.event.KeyEvent

class KeyEvents {

    companion object {

        val shared = KeyEvents()
        val shortcuts = mutableListOf<Shortcut>()
        var keyGuide = """
            Anything with + CTRL means you must be holding down the Control Key.
            
            CTRL is optional inside the settings. CTRL Enabled: ${Defaults.CTRL_FOR_HOTKEYS.boolean()}
            
            
        """.trimIndent()
            private set

        data class Shortcut(
            val key: Key,
            val name: String,
            val ctrl: Boolean = false,
            val func: () -> Unit
        )

        init {
            Page.entries.forEach { page ->
                var key1: Key
                when (page) {
                    Page.DOWNLOADER -> {
                        key1 = Key.F1
                    }
                    Page.DOWNLOADS -> {
                        key1 = Key.F2
                    }
                    Page.HISTORY -> {
                        key1 = Key.F3
                    }
                    Page.RECENT -> {
                        key1 = Key.F4
                    }
                    Page.SETTINGS -> {
                        key1 = Key.F5
                    }
                    Page.ERROR_CONSOLE -> {
                        key1 = Key.F6
                    }
                }
                shortcuts.add(
                    Shortcut(key1, page.title) {
                        Core.changePage(page)
                    }
                )
            }
            shortcuts.add(
                Shortcut(Key.Escape, "Settings/Last Page") {
                    if (Core.currentPage == Page.SETTINGS) {
                        Core.changePage(Core.lastPage)
                    } else {
                        Core.changePage(Page.SETTINGS)
                    }
                }
            )
            shortcuts.add(
                Shortcut(Key.O, "Open Save Folder", true) {
                    if (Core.currentPage == Page.SETTINGS && Core.settingsFieldFocused) {
                        return@Shortcut
                    }
                    Tools.openFile(
                        Defaults.SAVE_FOLDER.string()
                    )
                }
            )
            shortcuts.add(
                Shortcut(Key.W, "Open Video Database", true) {
                    if (Core.currentPage == Page.SETTINGS && Core.settingsFieldFocused) {
                        return@Shortcut
                    }
                    Core.openWco()
                }
            )
            shortcuts.add(
                Shortcut(Key.DirectionLeft, "Cycle Through Tabs", true) {
                    if (Core.currentPage == Page.SETTINGS && Core.settingsFieldFocused) {
                        return@Shortcut
                    }
                    val before = Page.beforePage(Core.currentPage)
                    if (before != null) {
                        Core.changePage(before)
                    }
                }
            )
            shortcuts.add(
                Shortcut(Key.DirectionRight, "Cycle Through Tabs", true) {
                    if (Core.currentPage == Page.SETTINGS && Core.settingsFieldFocused) {
                        return@Shortcut
                    }
                    val next = Page.nextPage(Core.currentPage)
                    if (next != null) {
                        Core.changePage(next)
                    }
                }
            )

            shortcuts.forEachIndexed { i, s ->
                keyGuide += """
                    ${s.name} = ${AwtKey.getKeyText(s.key.keyCode.toInt())} ${if (s.ctrl) " + CTRL" else ""}
                """.trimIndent()
                if (i != shortcuts.lastIndex) {
                    keyGuide += "\n"
                }
            }
        }
    }

    fun loadKeyEvents(): (KeyEvent) -> Boolean = core@ {
        val up = it.type == KeyEventType.KeyUp
        val ctrl = if (Defaults.CTRL_FOR_HOTKEYS.boolean())
            it.isCtrlPressed else !Core.currentUrlFocused
        if (up) {
            shortcuts.forEach { s ->
                if (it.key == s.key) {
                    if (s.ctrl && ctrl) {
                        s.func()
                        return@core true
                    } else if (!s.ctrl) {
                        s.func()
                        return@core true
                    }
                }
            }
        }
        false
    }
}