package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.OverlayInputService
import com.example.service.ScreenCastService
import com.example.service.SunshineAccessibilityService
import com.example.service.SunshineKeyboardService
import com.example.ui.components.ConfigDialog
import com.example.ui.components.GameCardItem
import com.example.ui.components.KeymapGuideDialog
import com.example.ui.components.MetricPill
import com.example.ui.theme.DarkObsidian
import com.example.ui.theme.NavySlate
import com.example.ui.theme.NavySlateCard
import com.example.ui.theme.SlateBorder
import com.example.ui.theme.SunshineAmber
import com.example.ui.theme.SunshineCyan
import com.example.ui.theme.SunshineEmerald
import com.example.ui.theme.SunshinePurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onStartScreenCast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val serverStatus by viewModel.serverStatus.collectAsState()
    val streamConfig by viewModel.streamConfig.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }
    var showKeymapDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current

    // Real input status - "enabled in settings" is NOT the same as "service is bound".
    // After an app update / force-stop the toggle can stay ON while the service is
    // dead; the middle state below tells the user exactly what to do about it.
    var isAccessBound by remember { mutableStateOf(false) }
    var isAccessEnabled by remember { mutableStateOf(false) }
    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }
    var canOverlay by remember { mutableStateOf(true) }
    var connectionPin by remember { mutableStateOf("") }

    fun checkAccessEnabled(): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        if (am != null) {
            val enabledList = am.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            if (enabledList.any { it.resolveInfo.serviceInfo.packageName == context.packageName }) {
                return true
            }
        }
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains(context.packageName)
    }

    fun checkImeEnabled(): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
        val enabledImes = imm?.enabledInputMethodList ?: emptyList()
        return enabledImes.any { it.packageName == context.packageName }
    }

    fun checkImeSelected(): Boolean {
        if (SunshineKeyboardService.isServiceActive) return true
        val selected = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.DEFAULT_INPUT_METHOD
        ) ?: return false
        return selected.contains(context.packageName)
    }

    fun refreshInputStates() {
        isAccessBound = SunshineAccessibilityService.isServiceActive
        isAccessEnabled = checkAccessEnabled()
        isImeEnabled = checkImeEnabled()
        isImeSelected = checkImeSelected()
        canOverlay = Settings.canDrawOverlays(context)
        if (connectionPin.isEmpty()) {
            connectionPin = ScreenCastService.ensurePin(context)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshInputStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        refreshInputStates()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Keep the cards honest: they refresh every 2 seconds so the state flips the
    // moment the user toggles the service in system settings - no app restart needed.
    LaunchedEffect(Unit) {
        while (true) {
            try {
                refreshInputStates()
            } catch (_: Exception) {
            }
            delay(2000)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = DarkObsidian,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TOP HEADER & BRANDING
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(text = "☀️", fontSize = 28.sp)
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Sunshine Cast",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .background(
                                            Brush.horizontalGradient(listOf(SunshineCyan, SunshinePurple)),
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "HOST",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black
                                    )
                                }
                            }
                            Text(
                                text = "Remote Play for Chrome Browser",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showKeymapDialog = true },
                            modifier = Modifier
                                .testTag("btn_keymap_header")
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Keyboard mapping",
                                tint = SunshineCyan
                            )
                        }
                        IconButton(
                            onClick = { showConfigDialog = true },
                            modifier = Modifier
                                .testTag("btn_settings_header")
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = TextSecondary
                            )
                        }
                    }
                }
            }

            // 2. HERO SERVER CONTROL & URL CARD
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySlate),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (serverStatus.isRunning) SunshineCyan.copy(alpha = 0.5f) else SlateBorder,
                            RoundedCornerShape(20.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        // Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(
                                            if (serverStatus.isRunning) SunshineEmerald else SunshineAmber,
                                            CircleShape
                                        )
                                )
                                Text(
                                    text = if (serverStatus.isRunning) "STREAMING ACTIVE" else "SERVER OFFLINE",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = if (serverStatus.isRunning) SunshineEmerald else SunshineAmber
                                )
                            }

                            if (serverStatus.isRunning) {
                                Box(
                                    modifier = Modifier
                                        .background(SunshineCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "${serverStatus.clientCount} Connected",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SunshineCyan
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (serverStatus.isRunning) {
                            // URL Broadcast Display
                            Text(
                                text = "CHROME BROWSER URL",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextMuted
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF0D1424))
                                    .border(1.dp, Color(0xFF1E2D48), RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serverStatus.serverUrl,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SunshineCyan
                                )

                                Row {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            val clip = ClipData.newPlainText("Sunshine URL", serverStatus.serverUrl)
                                            clipboard.setPrimaryClip(clip)
                                            Toast.makeText(context, "URL copied to clipboard!", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = "Copy URL",
                                            tint = SunshineCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverStatus.serverUrl))
                                            context.startActivity(browserIntent)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.OpenInBrowser,
                                            contentDescription = "Open in browser",
                                            tint = TextSecondary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Telemetry Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetricPill(
                                    label = "FPS",
                                    value = "${streamConfig.fps}",
                                    color = SunshineEmerald,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricPill(
                                    label = "Target",
                                    value = streamConfig.resolutionLabel().split(" ")[0],
                                    color = SunshineCyan,
                                    modifier = Modifier.weight(1f)
                                )
                                MetricPill(
                                    label = "Bitrate",
                                    value = "${streamConfig.bitrateMbps}M",
                                    color = SunshinePurple,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        } else {
                            // Instructional Banner when stopped
                            Text(
                                text = "Open Chrome on your laptop, go to your phone's address, and play games with hardware encoding & laptop keyboard controls.",
                                fontSize = 13.sp,
                                color = TextSecondary,
                                lineHeight = 18.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // START / STOP BUTTON
                        Button(
                            onClick = {
                                if (serverStatus.isRunning) {
                                    viewModel.stopServer(context)
                                } else {
                                    onStartScreenCast()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serverStatus.isRunning) Color(0xFFD32F2F) else SunshineCyan
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_toggle_server")
                        ) {
                            Icon(
                                imageVector = if (serverStatus.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (serverStatus.isRunning) Color.White else Color.Black
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (serverStatus.isRunning) "STOP STREAMING" else "START REMOTE PLAY",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (serverStatus.isRunning) Color.White else Color.Black
                            )
                        }
                    }
                }
            }

            // 2.5. ACCESSIBILITY & REMOTE KEYBOARD PERMISSION CARD
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NavySlateCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isAccessBound) SunshineEmerald.copy(alpha = 0.5f) else SunshineAmber.copy(alpha = 0.5f),
                            RoundedCornerShape(16.dp)
                        )
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = when {
                                        isAccessBound -> "🎮"
                                        isAccessEnabled -> "⚠️"
                                        else -> "🔒"
                                    },
                                    fontSize = 22.sp
                                )
                                Column {
                                    Text(
                                        text = when {
                                            isAccessBound -> "Remote Controls: ACTIVE"
                                            isAccessEnabled -> "Remote Controls: DISCONNECTED"
                                            else -> "Remote Controls: Required"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (isAccessBound) SunshineEmerald else SunshineAmber
                                    )
                                    Text(
                                        text = when {
                                            isAccessBound ->
                                                "Mouse, touches, holds & keyboard injection are live. Calibrate game buttons from the browser (F3)."
                                            isAccessEnabled ->
                                                "The toggle is ON but the service never connected (happens after app updates). " +
                                                "Switch it OFF, then ON once in Settings > Accessibility > Sunshine Cast Controller."
                                            else ->
                                                "Turn on Sunshine Cast Controller in Accessibility Settings so laptop inputs can control games."
                                        },
                                        fontSize = 11.sp,
                                        color = TextSecondary,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }

                            if (!isAccessBound) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        val intent = Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                        }
                                        context.startActivity(intent)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SunshineAmber),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = "Open Settings",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // KDE Connect Remote Keyboard Sub-card
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F1523))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("⌨️", fontSize = 16.sp)
                                    Column {
                                        Text(
                                            text = when {
                                                isImeSelected -> "Remote Keyboard: ACTIVE (laptop typing works)"
                                                isImeEnabled -> "Remote Keyboard: enabled, not selected"
                                                else -> "Remote Keyboard: Optional"
                                            },
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = if (isImeSelected) SunshineCyan else TextPrimary
                                        )
                                        Text(
                                            text = when {
                                                isImeSelected -> "Laptop keys type into focused text fields; games work without it."
                                                isImeEnabled -> "Tap Select and pick Sunshine Remote Keyboard (switch back anytime from its bar)."
                                                else -> "Optional: type with the whole laptop keyboard into text fields on the phone."
                                            },
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                if (!isImeSelected) {
                                    Button(
                                        onClick = {
                                            if (!isImeEnabled) {
                                                val intent = Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                }
                                                context.startActivity(intent)
                                            } else {
                                                try {
                                                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                                    imm.showInputMethodPicker()
                                                } catch (_: Exception) {
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SunshineCyan.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = if (isImeEnabled) "Select" else "Setup",
                                            color = SunshineCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // CONNECTION PIN sub-card
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF0F1523))
                                .padding(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier.weight(1f),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("🔐", fontSize = 16.sp)
                                    Column {
                                        Text(
                                            text = "Connection PIN: " + connectionPin,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = SunshineCyan
                                        )
                                        Text(
                                            text = "Enter once in the browser to connect. Keeps strangers on your Wi-Fi out.",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        connectionPin = ScreenCastService.rotatePin(context)
                                        Toast.makeText(context, "PIN updated - browsers will ask for it again", Toast.LENGTH_SHORT).show()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = SunshineCyan.copy(alpha = 0.2f)),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text("New", color = SunshineCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // OVERLAY PERMISSION sub-card (on-screen remote input feedback)
                        if (!canOverlay) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF0F1523))
                                    .padding(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "On-screen input feedback: disabled",
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = SunshineAmber
                                        )
                                        Text(
                                            text = "Allow 'Display over other apps' to see remote key & touch indicators while gaming.",
                                            fontSize = 10.sp,
                                            color = TextMuted
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + context.packageName)
                                            ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                            context.startActivity(intent)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SunshineAmber.copy(alpha = 0.2f)),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Text("Allow", color = SunshineAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. LAPTOP REAL ESTATE & SCALING INFO CHIP
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF141D2D))
                        .border(1.dp, Color(0xFF22304A), RoundedCornerShape(14.dp))
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Laptop Screen Real Estate: ${streamConfig.resolutionLabel()}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SunshineCyan
                        )
                        Text(
                            text = "Browser client supports 'Fill Screen' & 16:10 laptop aspect modes.",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }

                    Button(
                        onClick = { showConfigDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2D48)),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_adjust_scaling")
                    ) {
                        Text(
                            text = "Adjust",
                            fontSize = 12.sp,
                            color = SunshineCyan,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // 4. GAME & EMULATOR LAUNCHER SECTION (SUNSHINE STYLE)
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "GAME & EMULATOR LIBRARY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted,
                            letterSpacing = 0.5.sp
                        )

                        IconButton(
                            onClick = { viewModel.loadApps() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh apps",
                                tint = TextSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Category Tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("All", "Emulators", "Games").forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(
                                        if (isSelected) SunshineCyan.copy(alpha = 0.2f) else Color(0xFF141D2D)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) SunshineCyan else Color(0xFF22304A),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { viewModel.setSelectedCategory(cat) }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) SunshineCyan else TextSecondary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search installed games & emulators...", fontSize = 13.sp, color = TextMuted) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SunshineCyan,
                            unfocusedBorderColor = Color(0xFF22304A),
                            focusedContainerColor = NavySlate,
                            unfocusedContainerColor = NavySlate,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_apps")
                    )
                }
            }

            // 5. APPS GRID
            if (filteredApps.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching apps found." else "Scanning installed games and emulators...",
                            color = TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                items(filteredApps, key = { it.packageName }) { app ->
                    GameCardItem(
                        app = app,
                        onLaunch = {
                            viewModel.launchApp(app.packageName)
                            Toast.makeText(context, "Launched ${app.appName}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Settings Dialog
    if (showConfigDialog) {
        ConfigDialog(
            currentConfig = streamConfig,
            onSave = { newConfig ->
                viewModel.updateConfig(newConfig)
                showConfigDialog = false
                Toast.makeText(context, "Settings updated!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showConfigDialog = false }
        )
    }

    // Keymap Dialog
    if (showKeymapDialog) {
        KeymapGuideDialog(onDismiss = { showKeymapDialog = false })
    }
}
