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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.StreamConfig
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.NavySlate
import com.example.ui.theme.SunshineCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

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

    val resolutions = listOf(
        Pair(1920, 1080) to "1080p (16:9)",
        Pair(1920, 1200) to "1200p (16:10 Laptop)",
        Pair(1280, 720) to "720p (Ultra Fast)",
        Pair(2560, 1440) to "1440p (2K Ultra)"
    )

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(NavySlate)
                .border(1.dp, Color(0xFF22304A), RoundedCornerShape(20.dp))
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Stream Settings",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = SunshineCyan
                    )
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

                Spacer(modifier = Modifier.height(16.dp))

                // Resolution Selection
                Text(
                    text = "RESOLUTION (Laptop Screen Match)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    resolutions.forEach { (res, label) ->
                        val isSelected = selectedRes == res
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SunshineCyan.copy(alpha = 0.15f) else Color(0xFF141D2D))
                                .border(1.dp, if (isSelected) SunshineCyan else Color(0xFF22304A), RoundedCornerShape(10.dp))
                                .clickable { selectedRes = res }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) SunshineCyan else TextPrimary
                            )
                            Text(
                                text = "${res.first}x${res.second}",
                                fontSize = 11.sp,
                                color = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Frame Rate (FPS)
                Text(
                    text = "TARGET FRAMERATE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextMuted
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    listOf(60 to "60 FPS (Smooth)", 30 to "30 FPS (Battery)").forEach { (fpsVal, label) ->
                        val isSelected = selectedFps == fpsVal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) SunshineCyan.copy(alpha = 0.15f) else Color(0xFF141D2D))
                                .border(1.dp, if (isSelected) SunshineCyan else Color(0xFF22304A), RoundedCornerShape(10.dp))
                                .clickable { selectedFps = fpsVal }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) SunshineCyan else TextPrimary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Bitrate Slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "BITRATE (BANDWIDTH)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextMuted
                    )
                    Text(
                        text = "${bitrateMbps.toInt()} Mbps",
                        fontSize = 12.sp,
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
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Audio Streaming Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Internal Audio Stream",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "Stream game sounds & music to Chrome",
                            fontSize = 11.sp,
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

                Spacer(modifier = Modifier.height(20.dp))

                Button(
                    onClick = {
                        onSave(
                            currentConfig.copy(
                                width = selectedRes.first,
                                height = selectedRes.second,
                                fps = selectedFps,
                                bitrateMbps = bitrateMbps.toInt(),
                                audioEnabled = audioEnabled
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SunshineCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_save_config")
                ) {
                    Text(
                        text = "Apply Settings",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
