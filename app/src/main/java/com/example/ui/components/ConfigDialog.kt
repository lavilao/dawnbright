package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import com.example.ui.theme.NavySlate
import com.example.ui.theme.SunshineCyan
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
            ResolutionPreset(2560, 1600, "2560 x 1600", "16:10 Laptop", "QHD+ / Retina (MacBook Pro 16/14, Legion)"),
            ResolutionPreset(1920, 1200, "1920 x 1200", "16:10 Laptop", "FHD+ (MacBook Air, Dell XPS, ThinkPad)"),
            ResolutionPreset(1680, 1050, "1680 x 1050", "16:10 Laptop", "Standard 16:10 Laptop"),
            ResolutionPreset(1440, 900, "1440 x 900", "16:10 Laptop", "MacBook Air 13\" Classic"),
            ResolutionPreset(1280, 800, "1280 x 800", "16:10 Laptop", "WXGA / Steam Deck"),

            // 16:9 Standard Laptops & External Displays
            ResolutionPreset(3840, 2160, "3840 x 2160", "16:9 Standard", "4K UHD Gaming"),
            ResolutionPreset(2560, 1440, "2560 x 1440", "16:9 Standard", "2K QHD Gaming"),
            ResolutionPreset(1920, 1080, "1920 x 1080", "16:9 Standard", "1080p Full HD (Most Common)"),
            ResolutionPreset(1600, 900, "1600 x 900", "16:9 Standard", "HD+ Laptop"),
            ResolutionPreset(1366, 768, "1366 x 768", "16:9 Standard", "Standard Budget Laptop"),
            ResolutionPreset(1280, 720, "1280 x 720", "16:9 Standard", "720p HD (Low Latency)"),

            // 3:2 Productivity & Mobile Native
            ResolutionPreset(2256, 1504, "2256 x 1504", "3:2 / Other", "Surface Laptop 13.5\" 3:2"),
            ResolutionPreset(2160, 1440, "2160 x 1440", "3:2 / Other", "Surface Pro 3:2"),
            ResolutionPreset(2400, 1080, "2400 x 1080", "3:2 / Other", "Phone Landscape Native"),
            ResolutionPreset(1080, 2400, "1080 x 2400", "3:2 / Other", "Phone Portrait Native")
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NavySlate)
                .border(1.dp, Color(0xFF22304A), RoundedCornerShape(20.dp))
                .padding(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Stream & Laptop Resolution",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = SunshineCyan
                        )
                        Text(
                            text = "Current: ${selectedRes.first} x ${selectedRes.second}",
                            fontSize = 12.sp,
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

                Spacer(modifier = Modifier.height(12.dp))

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
                                .background(if (isSelected) SunshineCyan.copy(alpha = 0.2f) else Color(0xFF141D2D))
                                .border(1.dp, if (isSelected) SunshineCyan else Color(0xFF22304A), RoundedCornerShape(8.dp))
                                .clickable { selectedTab = tab }
                                .padding(vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tab,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) SunshineCyan else TextSecondary
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
                            .background(Color(0xFF141D2D))
                            .border(1.dp, Color(0xFF22304A), RoundedCornerShape(12.dp))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "ENTER EXACT LAPTOP RESOLUTION",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                        Spacer(modifier = Modifier.height(8.dp))
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
                                    focusedBorderColor = SunshineCyan,
                                    unfocusedBorderColor = Color(0xFF22304A)
                                )
                            )

                            Text(text = "×", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextMuted)

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
                                    focusedBorderColor = SunshineCyan,
                                    unfocusedBorderColor = Color(0xFF22304A)
                                )
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tip: Width and height will be auto-rounded to even pixels for hardware encoding.",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                } else {
                    val filteredPresets = presets.filter { it.category == selectedTab }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredPresets) { preset ->
                            val isSelected = selectedRes.first == preset.width && selectedRes.second == preset.height
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) SunshineCyan.copy(alpha = 0.15f) else Color(0xFF141D2D))
                                    .border(1.dp, if (isSelected) SunshineCyan else Color(0xFF22304A), RoundedCornerShape(8.dp))
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
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                        fontFamily = FontFamily.Monospace,
                                        color = if (isSelected) SunshineCyan else TextPrimary
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
                                    Text(
                                        text = "✓",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SunshineCyan
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // FRAMERATE (60 FPS vs 30 FPS)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FRAMERATE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(60 to "60 FPS", 30 to "30 FPS").forEach { (fpsVal, label) ->
                            val isSelected = selectedFps == fpsVal
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) SunshineCyan.copy(alpha = 0.2f) else Color(0xFF141D2D))
                                    .border(1.dp, if (isSelected) SunshineCyan else Color(0xFF22304A), RoundedCornerShape(6.dp))
                                    .clickable { selectedFps = fpsVal }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) SunshineCyan else TextSecondary
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
                        text = "BANDWIDTH",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "${bitrateMbps.toInt()} Mbps",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SunshineCyan
                    )
                }
                Slider(
                    value = bitrateMbps,
                    onValueChange = { bitrateMbps = it },
                    valueRange = 4f..30f,
                    steps = 25,
                    colors = SliderDefaults.colors(
                        thumbColor = SunshineCyan,
                        activeTrackColor = SunshineCyan,
                        inactiveTrackColor = Color(0xFF22304A)
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
                    Column {
                        Text(
                            text = "Game Sound Capture",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Internal audio capture (Android 10+)",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    }
                    Switch(
                        checked = audioEnabled,
                        onCheckedChange = { audioEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = SunshineCyan,
                            checkedTrackColor = SunshineCyan.copy(alpha = 0.3f),
                            uncheckedThumbColor = TextMuted,
                            uncheckedTrackColor = Color(0xFF141D2D)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

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
                    colors = ButtonDefaults.buttonColors(containerColor = SunshineCyan),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("btn_save_config")
                ) {
                    Text(
                        text = "Apply Resolution (${selectedRes.first} x ${selectedRes.second})",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
