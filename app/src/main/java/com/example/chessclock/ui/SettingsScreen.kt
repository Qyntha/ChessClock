package com.example.chessclock.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.chessclock.R
import com.example.chessclock.model.ClockPresets
import com.example.chessclock.model.ClockSettings
import com.example.chessclock.model.PresetKind
import com.example.chessclock.ui.theme.ChessClockTheme
import com.example.chessclock.ui.theme.ClockBackground
import com.example.chessclock.ui.theme.ClockBorderIdle
import com.example.chessclock.ui.theme.ClockGreen
import com.example.chessclock.ui.theme.ClockSurface
import com.example.chessclock.ui.theme.ClockTextPrimary
import com.example.chessclock.ui.theme.ClockTextSecondary
import kotlin.math.roundToInt

/**
 * 设置页。
 *
 * - 进入时用**已保存的设置**初始化（[currentSettings] 来自 SharedPreferences）；
 * - 页内的修改只放在普通 remember 草稿里，中途返回 / 转屏都会丢弃；
 * - 点底部“开始新对局”才写入并生效；当前有对局进度时会先确认；
 * - 常用值芯片的增删是立即写盘的，不受“开始新对局”影响。
 * - 整页可以上下滑动，横屏小屏也能滑到底看到按钮。
 */
@Composable
fun SettingsScreen(
    currentSettings: ClockSettings,
    presets: ClockPresets,
    hasGameProgress: Boolean,
    onAddPreset: (PresetKind, Int) -> Unit,
    onRemovePreset: (PresetKind, Int) -> Unit,
    onApply: (ClockSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember(currentSettings) { mutableStateOf(currentSettings.normalized()) }
    var showApplyConfirm by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClockBackground)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(text = stringResource(R.string.action_back), color = ClockGreen)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = ClockTextPrimary,
            )
        }

        Text(
            text = stringResource(R.string.settings_presets_hint),
            style = MaterialTheme.typography.labelSmall,
            color = ClockTextSecondary,
        )

        SettingsCard {
            Text(
                text = stringResource(R.string.settings_players),
                style = MaterialTheme.typography.titleSmall,
                color = ClockTextSecondary,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                NameField(
                    label = stringResource(R.string.settings_left),
                    placeholder = ClockSettings.DEFAULT_PLAYER_ONE_NAME,
                    value = draft.playerOneName,
                    onValueChange = { draft = draft.copy(playerOneName = it) },
                    modifier = Modifier.weight(1f),
                )
                NameField(
                    label = stringResource(R.string.settings_right),
                    placeholder = ClockSettings.DEFAULT_PLAYER_TWO_NAME,
                    value = draft.playerTwoName,
                    onValueChange = { draft = draft.copy(playerTwoName = it) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NumberSettingCard(
                title = stringResource(R.string.settings_main_time),
                checked = draft.mainTimeEnabled,
                // 读秒也关着时不允许再关掉主时间，否则就没有任何计时了
                switchEnabled = draft.byoyomiEnabled,
                onCheckedChange = { draft = draft.copy(mainTimeEnabled = it) },
                value = draft.mainTimeMinutes,
                range = ClockSettings.MIN_MAIN_MINUTES..ClockSettings.MAX_MAIN_MINUTES,
                unit = stringResource(R.string.unit_minutes),
                valueEnabled = draft.mainTimeEnabled,
                presets = presets.main,
                presetLabel = { stringResource(R.string.settings_preset_minutes, it) },
                onValueChange = { draft = draft.copy(mainTimeMs = it * 60_000L) },
                onAddPreset = { onAddPreset(PresetKind.MAIN, it) },
                onRemovePreset = { onRemovePreset(PresetKind.MAIN, it) },
                hint = if (!draft.byoyomiEnabled) stringResource(R.string.settings_need_one_clock) else null,
                modifier = Modifier.weight(1f),
            )

            NumberSettingCard(
                title = stringResource(R.string.settings_byoyomi),
                checked = draft.byoyomiEnabled,
                switchEnabled = draft.mainTimeEnabled,
                onCheckedChange = { draft = draft.copy(byoyomiEnabled = it) },
                value = draft.byoyomiSeconds,
                range = ClockSettings.MIN_BYOYOMI_SECONDS..ClockSettings.MAX_BYOYOMI_SECONDS,
                unit = stringResource(R.string.unit_seconds_per_move),
                valueEnabled = draft.byoyomiEnabled,
                presets = presets.byoyomi,
                presetLabel = { stringResource(R.string.settings_preset_seconds, it) },
                onValueChange = { draft = draft.copy(byoyomiMs = it * 1_000L) },
                onAddPreset = { onAddPreset(PresetKind.BYOYOMI, it) },
                onRemovePreset = { onRemovePreset(PresetKind.BYOYOMI, it) },
                hint = if (!draft.mainTimeEnabled) stringResource(R.string.settings_need_one_clock) else null,
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            NumberSettingCard(
                title = stringResource(R.string.settings_fischer),
                checked = draft.fischerEnabled,
                switchEnabled = draft.mainTimeEnabled,
                onCheckedChange = { draft = draft.copy(fischerEnabled = it) },
                value = draft.fischerSeconds,
                range = ClockSettings.MIN_FISCHER_SECONDS..ClockSettings.MAX_FISCHER_SECONDS,
                unit = stringResource(R.string.unit_seconds_per_move),
                valueEnabled = draft.fischerEnabled && draft.mainTimeEnabled,
                presets = presets.fischer,
                presetLabel = { stringResource(R.string.settings_preset_fischer, it) },
                onValueChange = { draft = draft.copy(fischerMs = it * 1_000L) },
                onAddPreset = { onAddPreset(PresetKind.FISCHER, it) },
                onRemovePreset = { onRemovePreset(PresetKind.FISCHER, it) },
                hint = if (draft.mainTimeEnabled) {
                    stringResource(R.string.settings_fischer_hint)
                } else {
                    stringResource(R.string.settings_fischer_need_main)
                },
                modifier = Modifier.weight(1f),
            )

            SwitchSettingCard(
                title = stringResource(R.string.settings_sound),
                checked = draft.soundEnabled,
                onCheckedChange = { draft = draft.copy(soundEnabled = it) },
                hint = stringResource(R.string.settings_sound_hint),
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = stringResource(R.string.settings_note),
            style = MaterialTheme.typography.bodySmall,
            color = ClockTextSecondary,
        )

        Button(
            onClick = {
                // 先让输入框失焦：会触发校验并把超范围的值夹紧，确保生效的是屏幕上显示的数值
                focusManager.clearFocus()
                if (hasGameProgress) showApplyConfirm = true else onApply(draft.normalized())
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ClockGreen,
                contentColor = Color(0xFF06210F),
            ),
        ) {
            Text(
                text = stringResource(R.string.settings_apply),
                fontWeight = FontWeight.Bold,
            )
        }
    }

    if (showApplyConfirm) {
        AlertDialog(
            onDismissRequest = { showApplyConfirm = false },
            containerColor = ClockSurface,
            titleContentColor = ClockTextPrimary,
            textContentColor = ClockTextSecondary,
            title = {
                Text(
                    text = stringResource(R.string.settings_apply_confirm_title),
                    fontWeight = FontWeight.Bold,
                )
            },
            text = { Text(text = stringResource(R.string.settings_apply_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showApplyConfirm = false
                        onApply(draft.normalized())
                    }
                ) {
                    Text(text = stringResource(R.string.action_confirm), color = ClockGreen)
                }
            },
            dismissButton = {
                TextButton(onClick = { showApplyConfirm = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = ClockTextSecondary)
                }
            },
        )
    }
}

/** 设置页统一的卡片容器。 */
@Composable
private fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ClockSurface)
            .border(1.dp, ClockBorderIdle, shape)
            .padding(14.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun NameField(
    label: String,
    placeholder: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(ClockSettings.MAX_NAME_LENGTH)) },
        modifier = modifier,
        label = { Text(text = label) },
        placeholder = { Text(text = placeholder, color = ClockTextSecondary) },
        singleLine = true,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ClockTextPrimary,
            unfocusedTextColor = ClockTextPrimary,
            focusedBorderColor = ClockGreen,
            unfocusedBorderColor = ClockBorderIdle,
            focusedLabelColor = ClockGreen,
            unfocusedLabelColor = ClockTextSecondary,
            cursorColor = ClockGreen,
        ),
    )
}

/**
 * “开关 + 数字输入框 + 滑块 + 常用值”的设置卡片。
 *
 * 输入框和滑块双向绑定：滑块变动会刷新输入框内容，输入框里合法的新值也会立刻带动滑块。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberSettingCard(
    title: String,
    checked: Boolean,
    switchEnabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    value: Int,
    range: IntRange,
    unit: String,
    valueEnabled: Boolean,
    presets: List<Int>,
    presetLabel: @Composable (Int) -> String,
    onValueChange: (Int) -> Unit,
    onAddPreset: (Int) -> Unit,
    onRemovePreset: (Int) -> Unit,
    hint: String?,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = ClockTextSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(
                    if (checked) R.string.settings_switch_on else R.string.settings_switch_off
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) ClockGreen else ClockTextSecondary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = switchEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF06210F),
                    checkedTrackColor = ClockGreen,
                    uncheckedThumbColor = ClockTextSecondary,
                    uncheckedTrackColor = ClockBorderIdle,
                    uncheckedBorderColor = ClockBorderIdle,
                ),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberField(
                value = value,
                range = range,
                enabled = valueEnabled,
                onValueChange = onValueChange,
                modifier = Modifier.width(88.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = unit,
                style = MaterialTheme.typography.labelLarge,
                color = if (valueEnabled) ClockTextSecondary else ClockBorderIdle,
            )
        }

        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = valueEnabled,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = stringResource(R.string.settings_presets),
            style = MaterialTheme.typography.labelSmall,
            color = ClockTextSecondary,
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            presets.forEach { preset ->
                PresetChip(
                    label = presetLabel(preset),
                    selected = preset == value,
                    enabled = valueEnabled,
                    onClick = { onValueChange(preset) },
                    onDelete = { onRemovePreset(preset) },
                    deleteLabel = stringResource(R.string.settings_preset_delete_action),
                )
            }
            AddPresetChip(
                label = stringResource(R.string.settings_preset_add, presetLabel(value)),
                enabled = valueEnabled && value !in presets,
                onClick = { onAddPreset(value) },
            )
        }

        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = ClockTextSecondary,
            )
        }
    }
}

/**
 * 数字输入框：只接受数字，回车 / 失焦时校验并把超范围的值夹回范围内。
 */
@Composable
private fun NumberField(
    value: Int,
    range: IntRange,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 输入过程中的文本；value 变化（例如拖动滑块）时同步刷新
    var text by remember(value) { mutableStateOf(value.toString()) }
    val focusManager = LocalFocusManager.current

    fun commit() {
        val parsed = text.toIntOrNull() ?: value
        val clamped = parsed.coerceIn(range.first, range.last)
        text = clamped.toString()
        if (clamped != value) onValueChange(clamped)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }.take(2)
            text = digits
            val parsed = digits.toIntOrNull()
            // 合法就直接生效（滑块跟着动）；非法先留着，等回车 / 失焦再夹紧
            if (parsed != null && parsed in range) onValueChange(parsed)
        },
        modifier = modifier.onFocusChanged { state -> if (!state.isFocused) commit() },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleLarge.copy(
            fontWeight = FontWeight.Bold,
            color = if (enabled) ClockGreen else ClockTextSecondary,
            textAlign = TextAlign.Center,
        ),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(
            onDone = {
                commit()
                focusManager.clearFocus()
            }
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ClockGreen,
            unfocusedTextColor = ClockGreen,
            disabledTextColor = ClockTextSecondary,
            focusedBorderColor = ClockGreen,
            unfocusedBorderColor = ClockBorderIdle,
            disabledBorderColor = ClockBorderIdle,
            cursorColor = ClockGreen,
        ),
    )
}

/**
 * 常用值芯片：左边点一下直接把该项设成这个值，右边 × 删除。
 */
@Composable
private fun PresetChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    deleteLabel: String,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(if (selected) ClockGreen.copy(alpha = 0.20f) else ClockBackground)
            .border(1.dp, if (selected) ClockGreen else ClockBorderIdle, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) ClockGreen else ClockTextSecondary,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .clickable(
                    enabled = enabled,
                    onClickLabel = deleteLabel,
                    onClick = onDelete,
                )
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "×",
                style = MaterialTheme.typography.labelMedium,
                color = if (enabled) ClockTextSecondary else ClockBorderIdle,
                maxLines = 1,
            )
        }
    }
}

/** 把输入框里的当前值加进常用值。 */
@Composable
private fun AddPresetChip(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(ClockGreen.copy(alpha = if (enabled) 0.10f else 0.04f))
            .border(
                1.dp,
                if (enabled) ClockGreen.copy(alpha = 0.7f) else ClockBorderIdle,
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (enabled) ClockGreen else ClockBorderIdle,
            maxLines = 1,
        )
    }
}

@Composable
private fun SwitchSettingCard(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    SettingsCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = ClockTextSecondary,
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(
                    if (checked) R.string.settings_switch_on else R.string.settings_switch_off
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (checked) ClockGreen else ClockTextSecondary,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color(0xFF06210F),
                    checkedTrackColor = ClockGreen,
                    uncheckedThumbColor = ClockTextSecondary,
                    uncheckedTrackColor = ClockBorderIdle,
                    uncheckedBorderColor = ClockBorderIdle,
                ),
            )
        }

        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = ClockTextSecondary,
            textAlign = TextAlign.Start,
        )
    }
}

@Preview(name = "设置 · 横屏", widthDp = 800, heightDp = 360, showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    ChessClockTheme {
        SettingsScreen(
            currentSettings = ClockSettings.DEFAULT,
            presets = ClockPresets(),
            hasGameProgress = true,
            onAddPreset = { _, _ -> },
            onRemovePreset = { _, _ -> },
            onApply = {},
            onBack = {},
        )
    }
}
