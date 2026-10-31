package com.wyutools.app.ui

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
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.material.icons.outlined.BrightnessMedium
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wyutools.app.auth.AuthViewModel
import kotlinx.coroutines.launch
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
) {
    val scroll = rememberScrollState()
    val fraction = rememberWyuCollapseFraction(scroll)
    val demoMode by authVm.demoMode.collectAsState()
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val surface = MiuixTheme.colorScheme.surface
    val primary = MiuixTheme.colorScheme.primary
    val selected = LocalWyuWallpaper.current
    val hasCustom by WyuAppearance.hasCustomWallpaper.collectAsState()
    val customWallpaper by WyuAppearance.customWallpaper.collectAsState()
    val customBrightness by WyuAppearance.customBrightness.collectAsState()
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                val imported = WyuAppearance.importCustomWallpaper(context, uri)
                importing = false
                if (!imported) {
                    Toast.makeText(context, "无法读取所选图片", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
            Text(
                text = "壁纸会应用到所有页面；预设壁纸会随系统深浅色调整，点按“相册”可以使用自己的图片。",
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            if (selected == WyuWallpaper.Custom && hasCustom) {
                CustomBrightnessCard(
                    brightness = customBrightness,
                    dark = dark,
                    onChange = { WyuAppearance.setCustomBrightness(context, it) },
                )
                Spacer(Modifier.height(12.dp))
            }
            Spacer(Modifier.height(12.dp))
            SmallTitle(
                text = "通用",
                insideMargin = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            )
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
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun CustomBrightnessCard(
    brightness: Float,
    dark: Boolean,
    onChange: (Float) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.BrightnessMedium,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
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
                valueRange = 0.2f..1f,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (dark) {
                    "深色模式下背景会强制压暗，滑块可在此基础上继续调整"
                } else {
                    "调低亮度可让壁纸上的内容更清晰"
                },
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
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
                        onClick = onClick,
                    )
                } else {
                    Modifier.clickable(
                        role = role,
                        onClick = onClick,
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
