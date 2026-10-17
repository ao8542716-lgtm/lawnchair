package app.lawnchair.ui.preferences.destinations

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lawnchair.glass.GlassWallpaperStore
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.LocalIsExpandedScreen
import app.lawnchair.ui.preferences.LocalPreferenceInteractor
import app.lawnchair.ui.preferences.components.NavigationActionPreference
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SliderPreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import app.lawnchair.ui.preferences.components.layout.ExpandAndShrink
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.navigation.GeneralIconPack
import app.lawnchair.ui.preferences.navigation.GeneralIconShape
import com.android.launcher3.R
import kotlinx.coroutines.launch

@Composable
fun IosGlassPreferences(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = preferenceManager()
    val scope = rememberCoroutineScope()

    val iconPacks by LocalPreferenceInteractor.current.iconPacks.collectAsStateWithLifecycle()
    val selectedPack = prefs.iconPackPackage.getAdapter().state.value
    val packName = iconPacks.firstOrNull { it.packageName == selectedPack }?.name
        ?: stringResource(id = R.string.ios_glass_icon_pack_none)

    val pickWallpaper = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                if (GlassWallpaperStore.importFrom(context, uri)) {
                    prefs.glassWallpaperVersion.set(System.currentTimeMillis().toString())
                } else {
                    Toast.makeText(context, R.string.ios_glass_wallpaper_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    PreferenceLayout(
        label = stringResource(id = R.string.ios_glass_label),
        backArrowVisible = !LocalIsExpandedScreen.current,
        modifier = modifier,
    ) {
        PreferenceGroup(heading = stringResource(id = R.string.ios_glass_icons_heading)) {
            NavigationActionPreference(
                label = stringResource(id = R.string.ios_glass_icon_pack),
                destination = GeneralIconPack,
                subtitle = packName,
            )
            NavigationActionPreference(
                label = stringResource(id = R.string.ios_glass_icon_shape),
                destination = GeneralIconShape(ShapeRoute.APP_SHAPE),
            )
        }

        PreferenceGroup(heading = stringResource(id = R.string.ios_glass_wallpaper_heading)) {
            ClickablePreference(
                label = stringResource(id = R.string.ios_glass_wallpaper_pick),
                subtitle = stringResource(id = R.string.ios_glass_wallpaper_hint),
                onClick = {
                    pickWallpaper.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
            )
        }

        val enabledAdapter = prefs.glassEnabled.getAdapter()
        PreferenceGroup(heading = stringResource(id = R.string.ios_glass_heading)) {
            SwitchPreference(
                adapter = enabledAdapter,
                label = stringResource(id = R.string.ios_glass_enable),
            )
            ExpandAndShrink(visible = enabledAdapter.state.value) {
                PreferenceGroup {
                    SwitchPreference(
                        adapter = prefs.glassDock.getAdapter(),
                        label = stringResource(id = R.string.ios_glass_dock),
                    )
                    SwitchPreference(
                        adapter = prefs.glassSearch.getAdapter(),
                        label = stringResource(id = R.string.ios_glass_search),
                    )
                    SwitchPreference(
                        adapter = prefs.glassFolders.getAdapter(),
                        label = stringResource(id = R.string.ios_glass_folders),
                    )
                    SwitchPreference(
                        adapter = prefs.glassControlCenter.getAdapter(),
                        label = stringResource(id = R.string.ios_glass_control_center),
                        description = stringResource(id = R.string.ios_glass_control_center_hint),
                    )
                    SwitchPreference(
                        adapter = prefs.glassClear.getAdapter(),
                        label = stringResource(id = R.string.ios_glass_clear),
                    )
                    SwitchPreference(
                        adapter = prefs.glassSensorHighlight.getAdapter(),
                        label = stringResource(id = R.string.ios_glass_sensor),
                    )
                    SliderPreference(
                        label = stringResource(id = R.string.ios_glass_refraction),
                        adapter = prefs.glassRefraction.getAdapter(),
                        step = 0.05f,
                        valueRange = 0f..1f,
                        showAsPercentage = true,
                    )
                    SliderPreference(
                        label = stringResource(id = R.string.ios_glass_dispersion),
                        adapter = prefs.glassDispersion.getAdapter(),
                        step = 0.02f,
                        valueRange = 0f..0.3f,
                        showAsPercentage = true,
                    )
                    SliderPreference(
                        label = stringResource(id = R.string.ios_glass_tint),
                        adapter = prefs.glassTintStrength.getAdapter(),
                        step = 0.05f,
                        valueRange = 0f..0.6f,
                        showAsPercentage = true,
                    )
                }
            }
        }
    }
}
