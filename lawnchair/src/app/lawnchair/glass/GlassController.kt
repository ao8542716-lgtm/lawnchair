package app.lawnchair.glass

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.BubbleTextView
import com.android.launcher3.Hotseat
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.dragndrop.DragLayer
import com.android.launcher3.folder.FolderIcon
import com.example.liquidglass.LiquidGlassView
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adds the iOS 26 look on top of Launcher3, all of it through QWEA0's LiquidGlassView:
 *  - [GlassWallpaperView] behind the launcher, used as the glass backdrop;
 *  - a glass pill behind the dock icons;
 *  - a glass pill behind the hotseat search bar;
 *  - glass folders, closed and open ([GlassFolders]);
 *  - a glass Control Center overlay ([GlassControlCenter]).
 */
class GlassController(private val launcher: LawnchairLauncher) {
    private val prefs = PreferenceManager.getInstance(launcher)
    private val density = launcher.resources.displayMetrics.density
    private val dragLayer: DragLayer = launcher.dragLayer
    private val hotseat: Hotseat = launcher.hotseat
    private val root: ViewGroup = dragLayer.parent as ViewGroup

    private val wallpaperView = GlassWallpaperView(launcher)
    private val dock = LiquidGlassView(launcher)
    private val search = GlassFollower(dragLayer, LiquidGlassView(launcher))
    private val folders = GlassFolders(launcher, dragLayer, wallpaperView, prefs, density)
    private val controlCenter = GlassControlCenter(launcher, root, prefs)

    private var attached = false
    private var hasWallpaper = false
    private var dockWanted = false
    private var searchWanted = false

    /** Dock pill bounds in DragLayer coordinates. */
    private val pill = Rect()
    private val tmp = Rect()

    private val preDrawListener = ViewTreeObserver.OnPreDrawListener {
        syncDock()
        syncSearch()
        folders.sync()
        true
    }
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { updatePill() }

    fun attach() {
        if (attached) return
        attached = true

        root.addView(
            wallpaperView,
            0,
            InsettableFrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                .apply { ignoreInsets = true },
        )

        val hotseatIndex = dragLayer.indexOfChild(hotseat)
        GlassStyle.configureSurface(dock)
        dock.backdropSource = wallpaperView
        dragLayer.addView(dock, hotseatIndex, GlassFollower.layoutParams())

        GlassStyle.configureSurface(search.glass)
        search.glass.backdropSource = wallpaperView
        dragLayer.addView(search.glass, dragLayer.indexOfChild(hotseat), GlassFollower.layoutParams())

        dragLayer.viewTreeObserver.addOnPreDrawListener(preDrawListener)
        dragLayer.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)

        val restyle = Runnable { applyStyle() }
        prefs.glassEnabled.subscribeChanges(launcher, restyle)
        prefs.glassDock.subscribeChanges(launcher, restyle)
        prefs.glassSearch.subscribeChanges(launcher, restyle)
        prefs.glassFolders.subscribeChanges(launcher, restyle)
        prefs.glassClear.subscribeChanges(launcher, restyle)
        prefs.glassRefraction.subscribeChanges(launcher, restyle)
        prefs.glassDispersion.subscribeChanges(launcher, restyle)
        prefs.glassTintStrength.subscribeChanges(launcher, restyle)
        prefs.glassSensorHighlight.subscribeChanges(launcher, restyle)
        prefs.glassWallpaperVersion.subscribeChanges(launcher, Runnable { reloadWallpaper() })

        reloadWallpaper()
        applyStyle()
    }

    fun detach() {
        if (!attached) return
        attached = false
        dragLayer.viewTreeObserver.run {
            if (isAlive) {
                removeOnPreDrawListener(preDrawListener)
                removeOnGlobalLayoutListener(layoutListener)
            }
        }
        controlCenter.detach()
        folders.detach()
        dock.backdropSource = null
        search.glass.backdropSource = null
        dragLayer.removeView(dock)
        dragLayer.removeView(search.glass)
        root.removeView(wallpaperView)
    }

    /** Opens the glass Control Center. Returns false if the feature is switched off. */
    fun showControlCenter(): Boolean {
        if (!prefs.glassControlCenter.get()) return false
        if (controlCenter.isShowing) controlCenter.hide() else controlCenter.show()
        return true
    }

    private fun reloadWallpaper() {
        launcher.lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.IO) { GlassWallpaperStore.load(launcher) }
            wallpaperView.setBitmap(bitmap)
            hasWallpaper = bitmap != null
            applyStyle()
        }
    }

    private fun applyStyle() {
        val enabled = prefs.glassEnabled.get() && hasWallpaper
        wallpaperView.visibility = if (enabled) View.VISIBLE else View.GONE
        dockWanted = enabled && prefs.glassDock.get()
        searchWanted = enabled && prefs.glassSearch.get()

        GlassStyle.apply(dock, prefs, density)
        GlassStyle.apply(search.glass, prefs, density, bevelDp = 16f, refractionDp = 12f)
        folders.setEnabled(enabled && prefs.glassFolders.get())
        controlCenter.restyle()
        updatePill()
    }

    /** Fits the dock pill around the hotseat icons: full icon-row width, icon height plus padding. */
    private fun updatePill() {
        if (!dockWanted) {
            pill.setEmpty()
            return
        }
        val container = hotseat.shortcutsAndWidgets
        if (container == null) {
            pill.setEmpty()
            return
        }
        val rowRect = Rect()
        if (!boundsInHotseat(container, rowRect)) {
            pill.setEmpty()
            return
        }
        val icons = Rect()
        var found = false
        for (i in 0 until container.childCount) {
            val c = container.getChildAt(i)
            if (c.visibility != View.VISIBLE) continue
            if (c !is BubbleTextView && c !is FolderIcon) continue
            val r = Rect()
            if (!boundsInHotseat(c, r)) continue
            if (found) icons.union(r) else icons.set(r)
            found = true
        }
        if (!found) {
            pill.setEmpty()
            return
        }

        val hInset = (10 * density).toInt()
        val vPad = (12 * density).toInt()
        val next = Rect(
            hotseat.left + rowRect.left + hInset,
            hotseat.top + icons.top - vPad,
            hotseat.left + rowRect.right - hInset,
            hotseat.top + icons.bottom + vPad,
        )
        if (next.width() <= 0 || next.height() <= 0) {
            pill.setEmpty()
            return
        }
        if (next == pill) return
        pill.set(next)

        val lp = dock.layoutParams as InsettableFrameLayout.LayoutParams
        lp.width = pill.width()
        lp.height = pill.height()
        lp.leftMargin = pill.left
        lp.topMargin = pill.top
        dock.layoutParams = lp
        dock.cornerRadius = min(pill.height() / 2f, 36f * density)
    }

    /** Every frame: copy the hotseat's animated alpha, translation and scale onto the dock pill. */
    private fun syncDock() {
        var alpha = hotseat.alpha
        var tx = hotseat.translationX
        var ty = hotseat.translationY
        var visible = hotseat.visibility == View.VISIBLE
        var v: View? = hotseat.shortcutsAndWidgets
        while (v != null && v !== hotseat) {
            alpha *= v.alpha
            tx += v.translationX
            ty += v.translationY
            if (v.visibility != View.VISIBLE) visible = false
            v = v.parent as? View
        }
        val show = dockWanted && !pill.isEmpty && visible && alpha > 0.01f
        val target = if (show) View.VISIBLE else View.INVISIBLE
        if (dock.visibility != target) dock.visibility = target
        if (!show) return

        dock.alpha = alpha
        dock.translationX = tx
        dock.translationY = ty
        dock.scaleX = hotseat.scaleX
        dock.scaleY = hotseat.scaleY
        dock.pivotX = hotseat.left + hotseat.pivotX - pill.left
        dock.pivotY = hotseat.top + hotseat.pivotY - pill.top
    }

    /** The hotseat search bar is a child of the hotseat; find it by class name. */
    private fun findSearchBar(): View? {
        for (i in 0 until hotseat.childCount) {
            val c = hotseat.getChildAt(i)
            if (c.javaClass.simpleName.contains("Qsb", ignoreCase = true)) return c
        }
        return null
    }

    private fun syncSearch() {
        val bar = findSearchBar()
        if (!searchWanted || bar == null || !bar.isShown || bar.width <= 0 || bar.alpha < 0.01f) {
            search.hide()
            return
        }
        tmp.set(0, 0, bar.width, bar.height)
        search.follow(bar, tmp, bar.height / 2f, bar.alpha)
    }

    private fun boundsInHotseat(view: View, out: Rect): Boolean {
        var x = 0
        var y = 0
        var cur: View = view
        while (cur !== hotseat) {
            x += cur.left
            y += cur.top
            val parent = cur.parent as? View ?: return false
            x -= parent.scrollX
            y -= parent.scrollY
            cur = parent
        }
        out.set(x, y, x + view.width, y + view.height)
        return true
    }
}
