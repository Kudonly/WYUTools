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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wyutools.app.auth.AuthViewModel
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
    val textScale by WyuAppearance.textScale.collectAsState()
    val systemFontScale = LocalDensity.current.fontScale
    val hasCustom by WyuAppearance.hasCustomWallpaper.collectAsState()
    val customWallpaper by WyuAppearance.customWallpaper.collectAsState()
    val brightnessLight by WyuAppearance.customBrightnessLight.collectAsState()
    val brightnessDark by WyuAppearance.customBrightnessDark.collectAsState()
    val softGlass by WyuAppearance.softGlass.collectAsState()
    val cardTheme by WyuAppearance.cardTheme.collectAsState()
    val paletteScheme by WyuAppearance.paletteScheme.collectAsState()
    val monetSeed by WyuAppearance.monetSeed.collectAsState()
    val softGlassBlur by WyuAppearance.softGlassBlur.collectAsState()
    val liquidGlassBlur by WyuAppearance.liquidGlassBlur.collectAsState()
    val softGlassSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var palettePickerOpen by remember { mutableStateOf(false) }
    // Exactly the seed the theme derives from, so the picker previews the
    // palettes that will actually be applied.
    val paletteSeed = remember(selected, dark, monetSeed) {
        if (selected == WyuWallpaper.Custom) {
            monetSeed
        } else {
            selected.seedColor(dark)?.toArgb()
        }
    }
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
                // 与顶栏大标题同一条左边线（卡片本身仍在 16dp 页边距上）。
                insideMargin = PaddingValues(horizontal = WyuTextInset, vertical = 8.dp),
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
                insideMargin = PaddingValues(horizontal = WyuTextInset, vertical = 8.dp),
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 14.dp,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
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
                    }
                    SettingDropdown(
                        current = themeMode,
                        options = WyuThemeMode.entries,
                        optionLabel = { it.label },
                        onSelect = { WyuAppearance.setThemeMode(context, it) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            // 主题也统一成「当前值 + 弹出菜单」，和深浅色、电费范围一个形态。
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(
                    modifier = Modifier.padding(
                        start = 20.dp,
                        end = 14.dp,
                        top = 14.dp,
                        bottom = 14.dp,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "主题",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = cardTheme.summary,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        SettingDropdown(
                            current = cardTheme,
                            options = WyuCardTheme.entries,
                            optionLabel = { it.label },
                            optionEnabled = {
                                it == WyuCardTheme.Default || softGlassSupported
                            },
                            onSelect = { WyuAppearance.setCardTheme(context, it) },
                        )
                    }
                    if (cardTheme != WyuCardTheme.Default && !softGlassSupported) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "玻璃主题需要 Android 13 及以上系统",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        
            // The blur is the one knob everyone touches, so it gets its own card
            // next to the tuning screen instead of hiding under 主题.
            if (cardTheme != WyuCardTheme.Default && softGlassSupported) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // One label only: the card is about a single number, so
                        // the title says what it is and the value rides beside
                        // it instead of repeating as a slider label.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "模糊度",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = "${if (cardTheme == WyuCardTheme.Liquid) liquidGlassBlur else softGlassBlur}%",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Slider(
                            value = (if (cardTheme == WyuCardTheme.Liquid) liquidGlassBlur else softGlassBlur)
                                .toFloat(),
                            onValueChange = {
                                if (cardTheme == WyuCardTheme.Liquid) {
                                    WyuAppearance.setLiquidGlassBlur(context, it.roundToInt())
                                } else {
                                    WyuAppearance.setSoftGlassBlur(context, it.roundToInt())
                                }
                            },
                            valueRange = WyuSoftGlassBlurMin.toFloat()..100f,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
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
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // 拖动中只改这里，松手才写进 Appearance：字号一变整页都要重新
                    // 排版，实时生效会让滑条自己跟着跳，手指像拖不动。
                    var dragScale by remember { mutableStateOf<Float?>(null) }
                    // 跟随系统时滑条停在系统当前比例上，一拖动就切到自定义。
                    val committedFollowing = textScale <= TextScaleFollowSystem
                    val following = committedFollowing && dragScale == null
                    val shownScale = dragScale
                        ?: if (committedFollowing) systemFontScale else textScale
                    val sliderScale = shownScale.coerceIn(TextScaleMin, TextScaleMax)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "字号",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = "单独调节后只影响本应用",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        FollowSystemChip(
                            selected = following,
                            onClick = {
                                WyuAppearance.setTextScale(context, TextScaleFollowSystem)
                            },
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "大小",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (following) {
                                "跟随系统"
                            } else {
                                "${(shownScale * 100).roundToInt()}%"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                    Slider(
                        value = sliderScale,
                        onValueChange = { dragScale = it },
                        // 松手时才落盘，顺便按 1% 收敛。
                        onValueChangeFinished = {
                            dragScale?.let { WyuAppearance.setTextScale(context, it) }
                            dragScale = null
                        },
                        valueRange = TextScaleMin..TextScaleMax,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    // 预览行直接用正文样式，拖动滑条时能立刻看出差别。
                    Text(
                        text = "预览：今天有 3 节课，热水余额 16.04 元",
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
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
            // 取色方案对默认与柔光玻璃主题同样生效：壁纸种子重映射强调色。
            
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                pressFeedbackType = PressFeedbackType.Sink,
                onClick = { palettePickerOpen = true },
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "取色方案",
                                style = MiuixTheme.textStyles.headline2,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = paletteScheme.summary,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = paletteScheme.label,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.primary,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    PaletteStripe(
                        colors = remember(paletteSeed, paletteScheme, dark) {
                            paletteSeed?.let { palettePreview(it, paletteScheme, dark) }
                        },
                        height = 10.dp,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            SmallTitle(
                text = "关于",
                insideMargin = PaddingValues(horizontal = WyuTextInset, vertical = 8.dp),
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
                    .padding(start = WyuTextInset, end = 20.dp, top = 10.dp, bottom = 10.dp)
                    .clickable { openLink(ShiyiUrl) },
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    PaletteSchemePickerDialog(
        show = palettePickerOpen,
        selected = paletteScheme,
        seed = paletteSeed,
        dark = dark,
        onSelect = { WyuAppearance.setPaletteScheme(context, it) },
        onDismiss = { palettePickerOpen = false },
    )

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

/**
 * Small sheet for picking the surface language. Every row previews its material
 * so the choice reads before it is applied. 柔光玻璃 needs the shader path, so
 * it is disabled (and explained) below API 33 instead of silently doing nothing.
 */


/**
 * Palette styles for 莫奈. Every row previews the palette that scheme would
 * build from the current seed, so the difference reads at a glance instead of
 * asking the user to apply six themes to compare.
 */
@Composable
private fun PaletteSchemePickerDialog(
    show: Boolean,
    selected: WyuPaletteScheme,
    seed: Int?,
    dark: Boolean,
    onSelect: (WyuPaletteScheme) -> Unit,
    onDismiss: () -> Unit,
) {
    val previews = remember(seed, dark) {
        WyuPaletteScheme.entries.associateWith { scheme ->
            seed?.let { palettePreview(it, scheme, dark) }
        }
    }
    WyuOverlayDialog(
        show = show,
        title = "取色方案",
        summary = "壁纸颜色如何铺开，只影响莫奈主题的配色",
        onDismissRequest = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            WyuPaletteScheme.entries.forEach { scheme ->
                PaletteSchemeRow(
                    scheme = scheme,
                    colors = previews[scheme],
                    selected = scheme == selected,
                    onClick = {
                        onSelect(scheme)
                        onDismiss()
                    },
                )
                if (scheme != WyuPaletteScheme.entries.last()) {
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        WyuDialogButton(text = "完成", onClick = onDismiss)
    }
}

@Composable
private fun PaletteSchemeRow(
    scheme: WyuPaletteScheme,
    colors: List<Color>?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val schemeColors = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    val background by animateColorAsState(
        targetValue = if (selected) {
            schemeColors.primary.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        },
        animationSpec = tween(160),
        label = "paletteOptionBackground",
    )
    val tap = hapticClick(enabled = !selected, action = onClick)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .clickable(role = Role.RadioButton, onClick = tap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PaletteTile(colors = colors)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scheme.label,
                style = MiuixTheme.textStyles.headline2,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = scheme.summary,
                style = MiuixTheme.textStyles.footnote1,
                color = schemeColors.onSurfaceVariantSummary,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = schemeColors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** 40dp tile: the scheme's primary, secondary container and tertiary stacked. */
@Composable
private fun PaletteTile(colors: List<Color>?) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(13.dp)
    val bands = colors ?: listOf(scheme.primary, scheme.secondaryContainer, scheme.tertiaryContainer)
    Column(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .border(0.5.dp, scheme.outline.copy(alpha = 0.3f), shape),
    ) {
        bands.forEach { color ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(color),
            )
        }
    }
}

/** Wide version of [PaletteTile] for the settings card. */
@Composable
private fun PaletteStripe(colors: List<Color>?, height: Dp) {
    val scheme = MiuixTheme.colorScheme
    val shape = RoundedCornerShape(height / 2)
    val bands = colors ?: listOf(scheme.primary, scheme.secondaryContainer, scheme.tertiaryContainer)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape),
    ) {
        bands.forEach { color ->
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(1f)
                    .background(color),
            )
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

/**
 * 设置里的「当前值 + 弹出菜单」：触发器显示当前选项，点开是 [WyuDropdownMenu]。
 *
 * 深浅色、主题共用这一套，外观与电费页的范围切换完全一致。
 */
@Composable
private fun <T> SettingDropdown(
    current: T,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    optionEnabled: (T) -> Boolean = { true },
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorRect by remember { mutableStateOf(Rect.Zero) }
    Box(modifier = Modifier.onGloballyPositioned { anchorRect = it.boundsInWindow() }) {
        WyuDropdownTrigger(
            label = optionLabel(current),
            onClick = { expanded = true },
        )
        WyuDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            anchor = anchorRect,
        ) {
            options.forEach { option ->
                WyuDropdownItem(
                    text = optionLabel(option),
                    selected = current == option,
                    enabled = optionEnabled(option),
                    onClick = {
                        expanded = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/** 「跟随系统」的小胶囊：只在选中时染色，未选中时是浅底。 */
@Composable
private fun FollowSystemChip(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = tween(180),
        label = "followSystemChipBackground",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) {
            MiuixTheme.colorScheme.onPrimary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary
        },
        animationSpec = tween(180),
        label = "followSystemChipContent",
    )
    Box(
        modifier = Modifier
            // 用 heightIn：字号调到很大时胶囊要跟着长高，不能把字裁掉。
            .heightIn(min = 38.dp)
            .clip(CircleShape)
            .background(background)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = hapticClick(enabled = !selected, action = onClick),
            )
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "跟随系统",
            style = MiuixTheme.textStyles.footnote1,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
        )
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
