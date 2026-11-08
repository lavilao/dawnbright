package com.example.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GameApp
import com.example.ui.theme.SunshineCyan
import com.example.ui.theme.SunshinePurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
fun GameCardItem(
    app: GameApp,
    onLaunch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(app.iconBase64) {
        if (app.iconBase64.isNotEmpty()) {
            try {
                val bytes = Base64.decode(app.iconBase64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    Column(
        modifier = modifier
            .testTag("game_card_${app.packageName.replace(".", "_")}")
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141D2D))
            .border(1.dp, if (app.isEmulator) SunshinePurple.copy(alpha = 0.5f) else Color(0xFF22304A), RoundedCornerShape(16.dp))
            .clickable { onLaunch() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Icon
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A2438)),
            contentAlignment = Alignment.Center
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.appName,
                    modifier = Modifier.size(48.dp)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.SportsEsports,
                    contentDescription = app.appName,
                    tint = if (app.isEmulator) SunshinePurple else SunshineCyan,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // App Name
        Text(
            text = app.appName,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Category Tag
        Box(
            modifier = Modifier
                .background(
                    if (app.isEmulator) SunshinePurple.copy(alpha = 0.2f) else SunshineCyan.copy(alpha = 0.15f),
                    RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (app.isEmulator) "EMULATOR" else app.category.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (app.isEmulator) Color(0xFFB388FF) else SunshineCyan
            )
        }
    }
}
