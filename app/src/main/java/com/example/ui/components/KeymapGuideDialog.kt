package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.NavySlate
import com.example.ui.theme.SunshineCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

data class KeyMappingItem(
    val gameButton: String,
    val laptopKey: String,
    val description: String
)

@Composable
fun KeymapGuideDialog(
    onDismiss: () -> Unit
) {
    val mappings = listOf(
        KeyMappingItem("D-Pad Up / Down / Left / Right", "W / S / A / D or Arrows", "Directional movement"),
        KeyMappingItem("Cross (A) / Accept", "K or Space", "Main Action / Jump"),
        KeyMappingItem("Circle (B) / Cancel", "L or Esc", "Back / Crouch"),
        KeyMappingItem("Square (X) / Attack", "J", "Secondary Action / Attack"),
        KeyMappingItem("Triangle (Y) / Special", "I", "Special Action / Menu"),
        KeyMappingItem("L Trigger (Bumper)", "Q", "Left Shoulder"),
        KeyMappingItem("R Trigger (Bumper)", "E", "Right Shoulder"),
        KeyMappingItem("Start / Pause", "Enter", "Pause Game / Menu"),
        KeyMappingItem("Select / Option", "Shift", "Select"),
        KeyMappingItem("Mouse Click & Drag", "Left Mouse Button", "Direct touch swipe & tap")
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
                        text = "Laptop Control Mapping",
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

                Text(
                    text = "Controls mapped directly from Chrome to your Android games & emulators:",
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier.height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mappings) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF141D2D))
                                .border(1.dp, Color(0xFF22304A), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.gameButton,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = item.description,
                                    fontSize = 10.sp,
                                    color = TextMuted
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1E2C44))
                                    .border(1.dp, SunshineCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = item.laptopKey,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = SunshineCyan
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SunshineCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_close_keymap")
                ) {
                    Text(
                        text = "Close",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}
