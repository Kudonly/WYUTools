package com.wyutools.app.ui

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wyutools.app.auth.AuthViewModel
import com.wyutools.app.update.ReleaseInfo
import com.wyutools.app.update.UpdateChecker
import com.wyutools.app.update.UpdateResult
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    authVm: AuthViewModel,
    onBack: () -> Unit,
    onOpenAdvanced: () -> Unit,
) {
    val scroll = rememberScrollState()
    val fraction = rememberWyuCollapseFraction(scroll)
    val demoMode by authVm.demoMode.collectAsState()
    val context = LocalContext.current
    val dark = LocalWyuDarkTheme.current
    val surface = MiuixTheme.colorScheme.surface
    val primary = MiuixTheme.colorScheme.primary
    val selected = LocalWyuWallpaper.current
    val themeMode by WyuAppearance.themeMode.collectAsState()
    val hasCustom by WyuAppearance.hasCustomWallpaper.collectAsState()
    val customWallpaper by WyuAppearance.customWallpaper.collectAsState()
    val brightnessLight by WyuAppearance.customBrightnessLight.collectAsState()
    val brightnessDark by WyuAppearance.customBrightnessDark.collectAsState()
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val softGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    var cropSource by remember { mutableStateOf<Bitmap?>(null) }
    val versionName = remember(context) { appVersionName(context) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    val uriHandler = LocalUriHandler.current

    fun openLink(url: String) {
        runCatching { uriHandler.openUri(url) }
            .onFailure { Toast.makeText(context, "无法打开链接", Toast.LENGTH_SHORT).show() }
    }

    fun checkUpdate() {
        if (checkingUpdate) return
        scope.launch {
            checkingUpdate = true
            updateResult = UpdateChecker.check(versionName)
            checkingUpdate = false
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                val bitmap = WyuAppearance.decodeWallpaperForCrop(context, uri)
                importing = false
                if (bitmap == null) {
                    Toast.makeText(context, "无法读取所选图片", Toast.LENGTH_SHORT).show()
                } else {
                    cropSource = bitmap
                }
            }
        }
    }

    cropSource?.let { source ->
        WyuWallpaperCropDialog(
            bitmap = source,
            onCancel = { cropSource = null },
            onConfirm = { crop ->
                cropSource = null
                scope.launch {
                    importing = true
                    val saved = WyuAppearance.applyCustomWallpaper(context, source, crop)
                    importing = false
                    if (!saved) {
                        Toast.makeText(context, "无法保存所选图片", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }

    WyuPageScaffold(
        progress = { fraction.value },
        maskCollapseHeight = WyuLargeTitleHeight,
        topBar = {
            WyuActionBar(
                title = "设置",
                collapsedFraction = { fraction.value },
                navigationIcon = {
                    WyuTopBarIconButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        onClick = onBack,
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(top = padding.calculateTopPadding()),
        ) {
            Spacer(Modifier.height(8.dp))
            SmallTitle(
                text = "壁纸",
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
            WallpaperPicker(
                selected = selected,
                dark = dark,
                surface = surface,
                primary = primary,
                hasCustom = hasCustom,
                customImage = customWallpaper,
                importing = importing,
                onSelect = { WyuAppearance.setWallpaper(context, it) },
                onPickImage = {
                    if (!importing) {
                        pickImage.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    }
                },
            )
            if (selected == WyuWallpaper.Custom && hasCustom) {
                CustomBrightnessCard(
                    brightness = if (dark) brightnessDark else brightnessLight,
                    onChange = { WyuAppearance.setCustomBrightness(context, it, dark) },
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            SmallTitle(
                text = "外观",
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "深浅色模式",
                        style = MiuixTheme.textStyles.headline2,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "选择应用的显示模式",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableGroup(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        WyuThemeMode.entries.forEach { mode ->
                            ThemeModeChip(
                                label = mode.label,
                                selected = themeMode == mode,
                                onClick = { WyuAppearance.setThemeMode(context, mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "演示模式",
                            style = MiuixTheme.textStyles.headline2,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = "用预设资料替换“我”页面的个人信息",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = demoMode,
                        onCheckedChange = authVm::setDemoMode,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "柔光玻璃",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = if (softGlassSupported) {
                                    "可能导致功耗增加、卡顿发热，请酌情开启"
                                } else {
                                    "需要 Android 13 及以上系统"
                                },
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Switch(
                            checked = softGlass && softGlassSupported,
                            onCheckedChange = { WyuAppearance.setSoftGlass(context, it) },
                            enabled = softGlassSupported,
                        )
                    }
                    if (softGlass && softGlassSupported) {
                        SoftGlassSliderRow(
                            label = "模糊半径",
                            valueText = "$softGlassBlur%",
                            value = softGlassBlur.toFloat(),
                            valueRange = WyuSoftGlassBlurMin.toFloat()..100f,
                            onChange = { WyuAppearance.setSoftGlassBlur(context, it.roundToInt()) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            // The tuning screen only matters while the material is on.
            if (softGlass && softGlassSupported) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    pressFeedbackType = PressFeedbackType.Sink,
                    onClick = onOpenAdvanced,
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "高级模式",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "调整折射、反射与边缘等材质参数",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.width(16.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            SmallTitle(
                text = "关于",
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                onClick = ::checkUpdate,
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "检查更新",
                            style = MiuixTheme.textStyles.headline2,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = if (checkingUpdate) {
                                "正在通过 GitHub Releases 检查…"
                            } else {
                                "当前版本 $versionName · 通过 GitHub Releases 检查"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    if (checkingUpdate) {
                        CircularProgressIndicator(size = 20.dp, strokeWidth = 2.dp)
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            Text(
                text = "部分接口实现来源于 shiyi，在此感谢。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(horizontal = 20.dp, vertical = 10.dp)
                    .clickable { openLink(ShiyiUrl) },
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    UpdateResultDialog(
        result = updateResult,
        versionName = versionName,
        onDismiss = { updateResult = null },
        onRetry = { checkUpdate() },
        onOpenRelease = { url ->
            updateResult = null
            openLink(url)
        },
    )
}

@Composable
private fun UpdateResultDialog(
    result: UpdateResult?,
    versionName: String,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    WyuOverlayDialog(
        show = result != null,
        title = when (result) {
            is UpdateResult.Available -> "发现新版本"
            is UpdateResult.Latest -> "已是最新版本"
            UpdateResult.NoRelease -> "暂无发布版本"
            is UpdateResult.Failed -> "检查更新失败"
            null -> null
        },
        summary = when (result) {
            is UpdateResult.Available ->
                "当前版本 $versionName · 最新版本 ${result.release.version}"
            is UpdateResult.Latest -> "当前版本 $versionName 已是最新版本。"
            UpdateResult.NoRelease -> "GitHub 上还没有已发布的版本，请稍后再试。"
            is UpdateResult.Failed -> result.message
            null -> null
        },
        onDismissRequest = onDismiss,
    ) {
        if (result is UpdateResult.Available) {
            if (result.release.notes.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = result.release.notes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Spacer(Modifier.height(16.dp))
            WyuDialogButton(
                text = "前往下载",
                onClick = { onOpenRelease(result.release.url) },
            )
            Spacer(Modifier.height(8.dp))
            WyuDialogCancelButton(text = "稍后", onClick = onDismiss)
        } else {
            Spacer(Modifier.height(16.dp))
            if (result is UpdateResult.Failed) {
                WyuDialogButton(text = "重试", onClick = onRetry)
                Spacer(Modifier.height(8.dp))
                WyuDialogCancelButton(text = "取消", onClick = onDismiss)
            } else {
                WyuDialogButton(text = "知道了", onClick = onDismiss)
            }
        }
    }
}

private const val ShiyiUrl = "https://github.com/aglorice/shiyi"

private fun appVersionName(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull().orEmpty().ifBlank { "1.0" }

@Composable
internal fun SoftGlassSliderRow(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valueText,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
    Slider(
        value = value,
        onValueChange = onChange,
        valueRange = valueRange,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ThemeModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(180),
        label = "themeModeChipBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        animationSpec = tween(180),
        label = "themeModeChipContent",
    )
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = hapticClick(enabled = !selected, action = onClick),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
private fun CustomBrightnessCard(
    brightness: Float,
    onChange: (Float) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "背景亮度",
                    style = MiuixTheme.textStyles.headline2,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${(brightness * 100).roundToInt()}%",
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Slider(
                value = brightness,
                onValueChange = onChange,
                valueRange = 0.5f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WallpaperPicker(
    selected: WyuWallpaper,
    dark: Boolean,
    surface: Color,
    primary: Color,
    hasCustom: Boolean,
    customImage: ImageBitmap?,
    importing: Boolean,
    onSelect: (WyuWallpaper) -> Unit,
    onPickImage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WallpaperTile(
            label = "相册",
            selected = false,
            role = Role.Button,
            onClick = onPickImage,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                if (importing) {
                    CircularProgressIndicator(size = 22.dp, strokeWidth = 2.dp)
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
        }
        if (hasCustom) {
            WallpaperTile(
                label = WyuWallpaper.Custom.label,
                selected = selected == WyuWallpaper.Custom,
                role = Role.RadioButton,
                onClick = { onSelect(WyuWallpaper.Custom) },
            ) {
                if (customImage != null) {
                    Image(
                        bitmap = customImage,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MiuixTheme.colorScheme.surfaceContainerHigh),
                    )
                }
            }
        }
        WyuWallpaper.entries
            .filter { it != WyuWallpaper.Custom }
            .forEach { wallpaper ->
                WallpaperTile(
                    label = wallpaper.label,
                    selected = selected == wallpaper,
                    role = Role.RadioButton,
                    onClick = { onSelect(wallpaper) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                wallpaper.background(dark, surface)
                                    ?: wyuHomeBackground(dark, surface, primary),
                            ),
                    )
                }
            }
    }
}

@Composable
private fun WallpaperTile(
    label: String,
    selected: Boolean,
    role: Role,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val primary = MiuixTheme.colorScheme.primary
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.24f)
        },
        animationSpec = tween(180),
        label = "wallpaperBorder",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) {
            primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        animationSpec = tween(180),
        label = "wallpaperLabel",
    )
    val tap = hapticClick(enabled = !selected) { onClick() }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clip(RoundedCornerShape(20.dp))
            .then(
                if (role == Role.RadioButton) {
                    Modifier.selectable(
                        selected = selected,
                        role = role,
                        onClick = tap,
                    )
                } else {
                    Modifier.clickable(
                        role = role,
                        onClick = tap,
                    )
                },
            )
            .padding(vertical = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(116.dp)
                .clip(shape)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = shape,
                ),
        ) {
            content()
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote1,
            color = labelColor,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
    }
}
