package app.lawnchair.glass

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.PreferenceManager
import com.android.launcher3.InsettableFrameLayout
import com.android.launcher3.R
import com.example.liquidglass.LiquidGlassView

/**
 * iOS 26 style Control Center: a dimmed scrim with glass tiles. Opened from Lawnchair's
 * "Open quick settings" gesture. Android does not let a launcher flip Wi-Fi, Bluetooth or airplane
 * mode, so those tiles open the matching system panel instead.
 */
class GlassControlCenter(
    private val launcher: LawnchairLauncher,
    private val root: ViewGroup,
    private val prefs: PreferenceManager,
) {
    private val density = launcher.resources.displayMetrics.density
    private var overlay: FrameLayout? = null
    private var content: LinearLayout? = null
    private val glassViews = ArrayList<LiquidGlassView>()

    private val cameraManager = launcher.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var torchId: String? = null
    private var torchOn = false
    private var torchTile: FrameLayout? = null
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) {
                torchOn = enabled
                updateTorchTile()
            }
        }
    }

    private var backCallback: Any? = null

    private fun registerBack() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback == null) {
            val callback = OnBackInvokedCallback { hide() }
            launcher.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
            backCallback = callback
        }
    }

    private fun unregisterBack() {
        if (Build.VERSION.SDK_INT >= 33) {
            (backCallback as? OnBackInvokedCallback)?.let {
                launcher.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(it)
            }
        }
        backCallback = null
    }

    val isShowing: Boolean get() = overlay?.parent != null

    private fun dp(v: Float) = (v * density).toInt()

    fun show() {
        if (isShowing) return
        val view = build()
        overlay = view
        root.addView(
            view,
            InsettableFrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                .apply { ignoreInsets = true },
        )
        registerBack()
        registerTorch()
        view.alpha = 0f
        content?.apply {
            translationY = -dp(36f).toFloat()
            scaleX = 0.96f
            scaleY = 0.96f
            alpha = 0f
        }
        view.animate().alpha(1f).setDuration(180).start()
        content?.animate()?.translationY(0f)?.scaleX(1f)?.scaleY(1f)?.alpha(1f)?.setDuration(260)?.start()
    }

    fun hide() {
        val view = overlay ?: return
        unregisterBack()
        unregisterTorch()
        content?.animate()?.translationY(-dp(24f).toFloat())?.alpha(0f)?.setDuration(160)?.start()
        view.animate().alpha(0f).setDuration(180).withEndAction {
            root.removeView(view)
            glassViews.clear()
            if (overlay === view) overlay = null
        }.start()
    }

    fun restyle() {
        glassViews.forEach { GlassStyle.apply(it, prefs, density, bevelDp = 18f, refractionDp = 14f) }
    }

    fun detach() {
        unregisterBack()
        unregisterTorch()
        overlay?.let { root.removeView(it) }
        overlay = null
        glassViews.clear()
    }

    private fun newGlass(radiusDp: Float): LiquidGlassView = LiquidGlassView(launcher).also {
        GlassStyle.configureSurface(it)
        it.visibility = View.VISIBLE
        it.backdropSource = root
        it.cornerRadius = radiusDp * density
        GlassStyle.apply(it, prefs, density, bevelDp = 18f, refractionDp = 14f)
        glassViews.add(it)
    }

    private fun build(): FrameLayout {
        glassViews.clear()
        val frame = FrameLayout(launcher)

        // Beta 2 of iOS 26 darkened and blurred the area behind Control Center for contrast.
        val scrim = View(launcher).apply {
            setBackgroundColor(0x73000000)
            setOnClickListener { hide() }
        }
        frame.addView(scrim, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val column = LinearLayout(launcher).apply {
            orientation = LinearLayout.VERTICAL
            val inset = launcher.deviceProfile.insets
            setPadding(dp(20f), inset.top + dp(18f), dp(20f), dp(24f))
            // Swallow touches in the gaps so only the scrim dismisses.
            isClickable = false
        }
        content = column
        frame.addView(column, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        val gap = dp(12f)

        // Row 1: connectivity block, brightness, volume.
        val row1 = LinearLayout(launcher).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(connectivityBlock(), LinearLayout.LayoutParams(0, dp(176f), 2f))
        row1.addView(brightnessSlider(), LinearLayout.LayoutParams(0, dp(176f), 1f).apply { leftMargin = gap })
        row1.addView(volumeSlider(), LinearLayout.LayoutParams(0, dp(176f), 1f).apply { leftMargin = gap })
        column.addView(row1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Row 2: flashlight and three app shortcuts.
        val row2 = LinearLayout(launcher).apply { orientation = LinearLayout.HORIZONTAL }
        row2.addView(torchTile(), tileParams(gap, first = true))
        appTile(Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR))
            ?.let { row2.addView(it, tileParams(gap, first = false)) }
        appTile(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            ?.let { row2.addView(it, tileParams(gap, first = false)) }
        appTile(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            ?.let { row2.addView(it, tileParams(gap, first = false)) }
        column.addView(row2, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76f)).apply { topMargin = gap })
        return frame
    }

    private fun tileParams(gap: Int, first: Boolean) =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (!first) leftMargin = gap }

    private fun connectivityBlock(): View {
        val block = newGlass(34f)
        val grid = LinearLayout(launcher).apply { orientation = LinearLayout.VERTICAL }
        fun row(a: Pair<Int, () -> Unit>, b: Pair<Int, () -> Unit>) = LinearLayout(launcher).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(circleButton(a.first, a.second), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            addView(circleButton(b.first, b.second), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        }
        grid.addView(
            row(
                R.drawable.ic_cc_airplane to { openSettings(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
                R.drawable.ic_cc_cellular to { openPanel(internet = true) },
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        grid.addView(
            row(
                R.drawable.ic_cc_wifi to { openPanel(internet = false) },
                R.drawable.ic_cc_bluetooth to { openSettings(Settings.ACTION_BLUETOOTH_SETTINGS) },
            ),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        grid.setPadding(dp(8f), dp(8f), dp(8f), dp(8f))
        block.addView(grid, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return block
    }

    private fun circleButton(iconRes: Int, action: () -> Unit): View {
        val cell = FrameLayout(launcher)
        val size = dp(56f)
        val disc = FrameLayout(launcher).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x40FFFFFF)
            }
            setOnClickListener { action() }
        }
        val icon = ImageView(launcher).apply {
            setImageResource(iconRes)
            setColorFilter(Color.WHITE)
        }
        disc.addView(icon, FrameLayout.LayoutParams(dp(26f), dp(26f), Gravity.CENTER))
        cell.addView(disc, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        return cell
    }

    private fun brightnessSlider(): View {
        val resolver = launcher.contentResolver
        val current = runCatching { Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) }
            .getOrDefault(128) / 255f
        return GlassSlider(
            launcher,
            R.drawable.ic_cc_brightness,
            current,
            root,
            canStart = {
                if (Settings.System.canWrite(launcher)) {
                    true
                } else {
                    runCatching {
                        launcher.startActivity(
                            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${launcher.packageName}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    hide()
                    false
                }
            },
            onProgress = { p ->
                runCatching {
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                    Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, (p * 255f).toInt().coerceIn(1, 255))
                }
            },
        ).also { glassViews.add(it.glass); GlassStyle.apply(it.glass, prefs, density, 18f, 14f) }
    }

    private fun volumeSlider(): View {
        val audio = launcher.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val current = audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
        return GlassSlider(
            launcher,
            R.drawable.ic_cc_volume,
            current,
            root,
            canStart = { true },
            onProgress = { p -> audio.setStreamVolume(AudioManager.STREAM_MUSIC, (p * max).toInt(), 0) },
        ).also { glassViews.add(it.glass); GlassStyle.apply(it.glass, prefs, density, 18f, 14f) }
    }

    private fun torchTile(): View {
        val tile = newGlass(28f)
        val icon = ImageView(launcher).apply {
            setImageResource(R.drawable.ic_cc_bolt)
            setColorFilter(Color.WHITE)
        }
        tile.addView(icon, FrameLayout.LayoutParams(dp(28f), dp(28f), Gravity.CENTER))
        tile.setOnClickListener {
            val id = torchId ?: return@setOnClickListener
            runCatching { cameraManager?.setTorchMode(id, !torchOn) }
        }
        tile.isClickable = true
        torchTile = tile
        updateTorchTile()
        return tile
    }

    private fun updateTorchTile() {
        val tile = torchTile ?: return
        (tile.getChildAt(tile.childCount - 1) as? ImageView)
            ?.setColorFilter(if (torchOn) 0xFFFFD60A.toInt() else Color.WHITE)
    }

    private fun appTile(intent: Intent): View? {
        val pm = launcher.packageManager
        val info = pm.resolveActivity(intent, 0) ?: return null
        val tile = newGlass(28f)
        val icon = ImageView(launcher).apply { setImageDrawable(info.loadIcon(pm)) }
        tile.addView(icon, FrameLayout.LayoutParams(dp(40f), dp(40f), Gravity.CENTER))
        tile.isClickable = true
        tile.setOnClickListener {
            runCatching { launcher.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            hide()
        }
        return tile
    }

    private fun openSettings(action: String) {
        runCatching { launcher.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        hide()
    }

    private fun openPanel(internet: Boolean) {
        val action = when {
            Build.VERSION.SDK_INT >= 29 && internet -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            Build.VERSION.SDK_INT >= 29 -> Settings.Panel.ACTION_WIFI
            internet -> Settings.ACTION_WIRELESS_SETTINGS
            else -> Settings.ACTION_WIFI_SETTINGS
        }
        openSettings(action)
    }

    private fun registerTorch() {
        val cm = cameraManager ?: return
        runCatching {
            torchId = cm.cameraIdList.firstOrNull {
                cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
            cm.registerTorchCallback(torchCallback, null)
        }
    }

    private fun unregisterTorch() {
        runCatching { cameraManager?.unregisterTorchCallback(torchCallback) }
    }
}
