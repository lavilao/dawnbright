package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.StreamConfig
import com.example.ui.theme.Accent
import com.example.ui.theme.Hairline
import com.example.ui.theme.OnAccent
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

data class ResolutionPreset(
    val width: Int,
    val height: Int,
    val label: String,
    val category: String,
    val note: String = ""
)

@Composable
fun ConfigDialog(
    currentConfig: StreamConfig,
    onSave: (StreamConfig) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedRes by remember { mutableStateOf(Pair(currentConfig.width, currentConfig.height)) }
    var selectedFps by remember { mutableIntStateOf(currentConfig.fps) }
    var bitrateMbps by remember { mutableFloatStateOf(currentConfig.bitrateMbps.toFloat()) }
    var audioEnabled by remember { mutableStateOf(currentConfig.audioEnabled) }

    var selectedTab by remember { mutableStateOf("16:10 Laptop") }
    var customWidthStr by remember { mutableStateOf(currentConfig.width.toString()) }
    var customHeightStr by remember { mutableStateOf(currentConfig.height.toString()) }

    val presets = remember {
        listOf(
            // 16:10 Modern Laptops (MacBook Air/Pro, Dell XPS, ThinkPad, ASUS ROG, Lenovo Legion)
            ResolutionPreset(2560, 1600, "2560 x 1600", "16:10 Laptop", "QHD+ / Retina"),
            ResolutionPreset(1920, 1200, "1920 x 1200", "16:10 Laptop", "FHD+"),
            ResolutionPreset(1680, 1050, "1680 x 1050", "16:10 Laptop", "Standard 16:10"),
            ResolutionPreset(1440, 900, "1440 x 900", "16:10 Laptop", "MacBook Air 13\""),
            ResolutionPreset(1280, 800, "1280 x 800", "16:10 Laptop", "WXGA / Steam Deck"),

            // 16:9 Standard Laptops & External Displays
            ResolutionPreset(3840, 2160, "3840 x 2160", "16:9 Standard", "4K UHD"),
            ResolutionPreset(2560, 1440, "2560 x 1440", "16:9 Standard", "2K QHD"),
            ResolutionPreset(1920, 1080, "1920 x 1080", "16:9 Standard", "1080p Full HD"),
            ResolutionPreset(1600, 900, "1600 x 900", "16:9 Standard", "HD+"),
            ResolutionPreset(1366, 768, "1366 x 768", "16:9 Standard", "Budget laptop"),
            ResolutionPreset(1280, 720, "1280 x 720", "16:9 Standard", "720p low latency"),

            // 3:2 Productivity & Mobile Native
            ResolutionPreset(2256, 1504, "2256 x 1504", "3:2 / Other", "Surface Laptop 13.5\""),
            ResolutionPreset(2160, 1440, "2160 x 1440", "3:2 / Other", "Surface Pro"),
            ResolutionPreset(2400, 1080, "2400 x 1080", "3:2 / Other", "Phone landscape"),
            ResolutionPreset(1080, 2400, "1080 x 2400", "3:2 / Other", "Phone portrait")
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Surface)
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Stream settings",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "${selectedRes.first} x ${selectedRes.second}",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextMuted
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = TextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // RESOLUTION CATEGORY TABS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("16:10 Laptop", "16:9 Standard", "3:2 / Other", "Custom").forEach { tab ->
                        val isSelected = selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) SurfaceHigh else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (isSelected) Accent else TextSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // RESOLUTION CONTENT LIST OR CUSTOM INPUTS
                if (selectedTab == "Custom") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceHigh)
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = customWidthStr,
                                onValueChange = {
                                    customWidthStr = it.filter { c -> c.isDigit() }
                                    val w = customWidthStr.toIntOrNull()
                                    val h = customHeightStr.toIntOrNull()
                                    if (w != null && h != null && w > 0 && h > 0) {
                                        selectedRes = Pair(w, h)
                                    }
                                },
                                label = { Text("Width", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Accent,
                                    unfocusedBorderColor = Hairline,
                                    focusedContainerColor = Surface,
                                    unfocusedContainerColor = Surface,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Text(text = "×", fontSize = 16.sp, color = TextMuted)

                            OutlinedTextField(
                                value = customHeightStr,
                                onValueChange = {
                                    customHeightStr = it.filter { c -> c.isDigit() }
                                    val w = customWidthStr.toIntOrNull()
                                    val h = customHeightStr.toIntOrNull()
                                    if (w != null && h != null && w > 0 && h > 0) {
                                        selectedRes = Pair(w, h)
                                    }
                                },
                                label = { Text("Height", fontSize = 12.sp) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Accent,
                                    unfocusedBorderColor = Hairline,
                                    focusedContainerColor = Surface,
                                    unfocusedContainerColor = Surface,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )
                        }
                    }
                } else {
                    val filteredPresets = presets.filter { it.category == selectedTab }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 168.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(filteredPresets) { preset ->
                            val isSelected = selectedRes.first == preset.width && selectedRes.second == preset.height
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) Accent.copy(alpha = 0.12f) else Color.Transparent)
                                    .clickable {
                                        selectedRes = Pair(preset.width, preset.height)
                                        customWidthStr = preset.width.toString()
                                        customHeightStr = preset.height.toString()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = preset.label,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) Accent else TextPrimary
                                    )
                                    if (preset.note.isNotEmpty()) {
                                        Text(
                                            text = preset.note,
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Accent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // FRAMERATE
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Framerate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(60 to "60 fps", 30 to "30 fps").forEach { (fpsVal, label) ->
                            val isSelected = selectedFps == fpsVal
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SurfaceHigh else Color.Transparent)
                                    .clickable { selectedFps = fpsVal }
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) Accent else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // BITRATE SLIDER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Bitrate",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Text(
                        text = "${bitrateMbps.toInt()} Mbps",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
                Slider(
                    value = bitrateMbps,
                    onValueChange = { bitrateMbps = it },
                    valueRange = 4f..30f,
                    steps = 25,
                    colors = SliderDefaults.colors(
                        thumbColor = Accent,
                        activeTrackColor = Accent,
                        inactiveTrackColor = Hairline
                    ),
                    modifier = Modifier.height(28.dp)
                )

                // AUDIO STREAM TOGGLE
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Game sound",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = TextSecondary
                    )
                    Switch(
                        checked = audioEnabled,
                        onCheckedChange = { audioEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = OnAccent,
                            checkedTrackColor = Accent,
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = SurfaceHigh
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val w = selectedRes.first.coerceAtLeast(320)
                        val h = selectedRes.second.coerceAtLeast(240)
                        onSave(
                            currentConfig.copy(
                                width = (w / 2) * 2,
                                height = (h / 2) * 2,
                                fps = selectedFps,
                                bitrateMbps = bitrateMbps.toInt(),
                                audioEnabled = audioEnabled
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Accent,
                        contentColor = OnAccent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .testTag("btn_save_config")
                ) {
                    Text(
                        text = "Apply",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
