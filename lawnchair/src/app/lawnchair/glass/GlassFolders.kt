package app.lawnchair.glass

import android.graphics.Rect
import android.view.View
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.CellLayout
import com.android.launcher3.Utilities
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.folder.ClippedFolderIconLayoutRule
import com.android.launcher3.folder.Folder
import com.android.launcher3.folder.FolderIcon
import com.example.liquidglass.LiquidGlassView

/**
 * Glass for folders.
 *  - Closed folders: one glass tile per folder icon, behind the workspace. The icon's own flat
 *    background is switched off through [GlassHooks.folderIconGlass].
 *  - Open folder: one glass panel just under the Folder view, following its open/close animation.
 */
class GlassFolders(
    private val launcher: LawnchairLauncher,
    private val dragLayer: DragLayer,
    private val backdrop: View,
    private val prefs: PreferenceManager,
    private val density: Float,
) {
    private val tiles = ArrayList<GlassFollower>()
    private val openPanel = GlassFollower(dragLayer, newGlass())
    private var openFolder: Folder? = null
    private var openAttached = false
    private var enabled = false
    private val rect = Rect()

    private fun newGlass(): LiquidGlassView = LiquidGlassView(launcher).also {
        GlassStyle.configureSurface(it)
        it.backdropSource = backdrop
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        GlassHooks.folderIconGlass = value
        restyle()
        if (!value) {
            tiles.forEach { it.hide() }
            openPanel.hide()
            restoreFolderBackground()
        }
        launcher.workspace?.invalidate()
    }

    fun restyle() {
        tiles.forEach { GlassStyle.apply(it.glass, prefs, density, bevelDp = 14f, refractionDp = 10f) }
        GlassStyle.apply(openPanel.glass, prefs, density, bevelDp = 26f, refractionDp = 20f)
    }

    fun detach() {
        GlassHooks.folderIconGlass = false
        tiles.forEach { dragLayer.removeView(it.glass) }
        tiles.clear()
        if (openAttached) dragLayer.removeView(openPanel.glass)
        openAttached = false
        restoreFolderBackground()
    }

    /** Called every frame from the controller's pre-draw hook. */
    fun sync() {
        if (!enabled) return
        syncClosedFolders()
        syncOpenFolder()
    }

    private fun syncClosedFolders() {
        val workspace = launcher.workspace ?: return
        val anchor = dragLayer.indexOfChild(workspace)
        if (anchor < 0) return
        var used = 0
        for (p in 0 until workspace.childCount) {
            val page = workspace.getChildAt(p) as? CellLayout ?: continue
            val container = page.shortcutsAndWidgets
            for (i in 0 until container.childCount) {
                val icon = container.getChildAt(i) as? FolderIcon ?: continue
                if (!icon.isShown || icon.folder?.isOpen == true) continue
                icon.getPreviewBounds(rect)
                Utilities.scaleRectAboutCenter(rect, 1f / ClippedFolderIconLayoutRule.ICON_OVERLAP_FACTOR)
                if (rect.width() <= 0) continue
                val follower = tileAt(used++, anchor)
                follower.follow(icon, rect, rect.width() * 0.30f)
            }
        }
        for (i in used until tiles.size) tiles[i].hide()
    }

    private fun tileAt(index: Int, anchor: Int): GlassFollower {
        while (tiles.size <= index) {
            val follower = GlassFollower(dragLayer, newGlass())
            GlassStyle.apply(follower.glass, prefs, density, bevelDp = 14f, refractionDp = 10f)
            // Behind the workspace, so icons and previews draw over the glass.
            dragLayer.addView(follower.glass, anchor, GlassFollower.layoutParams())
            tiles.add(follower)
        }
        return tiles[index]
    }

    private fun syncOpenFolder() {
        val folder = Folder.getOpen(launcher)
        if (folder == null || !folder.isOpen || !folder.isShown) {
            openPanel.hide()
            if (openFolder != null) restoreFolderBackground()
            return
        }
        if (folder !== openFolder) {
            restoreFolderBackground()
            openFolder = folder
            if (openAttached) dragLayer.removeView(openPanel.glass)
            val at = dragLayer.indexOfChild(folder)
            if (at < 0) return
            dragLayer.addView(openPanel.glass, at, GlassFollower.layoutParams())
            openAttached = true
            GlassStyle.apply(openPanel.glass, prefs, density, bevelDp = 26f, refractionDp = 20f)
        }
        folder.background?.alpha = 0
        rect.set(0, 0, folder.width, folder.height)
        openPanel.follow(folder, rect, 32f * density, folder.alpha)
    }

    private fun restoreFolderBackground() {
        openFolder?.background?.alpha = 255
        openFolder = null
    }
}
