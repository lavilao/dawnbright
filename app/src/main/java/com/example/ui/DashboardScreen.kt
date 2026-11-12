package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.SunshineAccessibilityService
import com.example.service.SunshineKeyboardService
import com.example.ui.components.ConfigDialog
import com.example.ui.components.GameCardItem
import com.example.ui.components.KeymapGuideDialog
import com.example.ui.theme.Accent
import com.example.ui.theme.Bg
import com.example.ui.theme.Hairline
import com.example.ui.theme.OnAccent
import com.example.ui.theme.StatusErr
import com.example.ui.theme.StatusOk
import com.example.ui.theme.Surface
import com.example.ui.theme.SurfaceHigh
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
    // dead; the setup row below tells the user exactly what to do about it.
    var isAccessBound by remember { mutableStateOf(false) }
    var isAccessEnabled by remember { mutableStateOf(false) }
    var isImeEnabled by remember { mutableStateOf(false) }
    var isImeSelected by remember { mutableStateOf(false) }
    var canOverlay by remember { mutableStateOf(true) }

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

    // Keep the setup rows honest: they refresh every 2 seconds so the state flips the
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
        containerColor = Bg,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = innerPadding.calculateTopPadding() + 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 28.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. TOP BAR
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sunshine Cast",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(
                            onClick = { showKeymapDialog = true },
                            modifier = Modifier
                                .testTag("btn_keymap_header")
                                .size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Keyboard,
                                contentDescription = "Keyboard mapping",
                                tint = TextSecondary
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

            // 2. STREAM CARD - status, address, primary action
            item(span = { GridItemSpan(maxLineSpan) }) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (serverStatus.isRunning) StatusOk else TextMuted,
                                        CircleShape
                                    )
                            )
                            Text(
                                text = if (serverStatus.isRunning) "Streaming" else "Ready",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimary
                            )
                            if (serverStatus.isRunning && serverStatus.clientCount > 0) {
                                Text(
                                    text = "· ${serverStatus.clientCount} client" +
                                            (if (serverStatus.clientCount > 1) "s" else ""),
                                    fontSize = 13.sp,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (serverStatus.isRunning) {
                            // Address row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(SurfaceHigh)
                                    .padding(horizontal = 14.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = serverStatus.serverUrl,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextPrimary,
                                    maxLines = 1,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("Sunshine Cast address", serverStatus.serverUrl)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Address copied", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy address",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(serverStatus.serverUrl))
                                        context.startActivity(browserIntent)
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.OpenInBrowser,
                                        contentDescription = "Open in browser",
                                        tint = TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "${streamConfig.width} × ${streamConfig.height} · " +
                                        "${streamConfig.fps} fps · ${streamConfig.bitrateMbps} Mbps",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        } else {
                            Text(
                                text = "Stream this phone to any browser on your Wi-Fi.",
                                fontSize = 13.sp,
                                color = TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (serverStatus.isRunning) {
                                    viewModel.stopServer(context)
                                } else {
                                    onStartScreenCast()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (serverStatus.isRunning) SurfaceHigh else Accent,
                                contentColor = if (serverStatus.isRunning) TextPrimary else OnAccent
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_toggle_server")
                        ) {
                            Icon(
                                imageVector = if (serverStatus.isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (serverStatus.isRunning) StatusErr else OnAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (serverStatus.isRunning) "Stop streaming" else "Start streaming",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }

            // 3. SETUP - visible only while something actually needs attention
            if (!isAccessBound || !isImeSelected || !canOverlay) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(modifier = Modifier.padding(top = 10.dp)) {
                        Text(
                            text = "Setup",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                        )

                        if (!isAccessBound) {
                            SetupRow(
                                icon = { tint ->
                                    Icon(
                                        imageVector = Icons.Outlined.SportsEsports,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                title = "Remote controls",
                                hint = if (isAccessEnabled)
                                    "Toggle Sunshine Cast Controller off and on in Accessibility settings"
                                else
                                    "Enable Sunshine Cast Controller in Accessibility settings",
                                action = "Enable",
                                onAction = {
                                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    context.startActivity(intent)
                                }
                            )
                        }

                        if (!isImeSelected) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SetupRow(
                                icon = { tint ->
                                    Icon(
                                        imageVector = Icons.Default.Keyboard,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                title = "Remote keyboard",
                                hint = if (isImeEnabled)
                                    "Select Sunshine Keyboard as the input method"
                                else
                                    "Optional - type into text fields from the laptop",
                                action = if (isImeEnabled) "Select" else "Enable",
                                onAction = {
                                    if (!isImeEnabled) {
                                        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
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
                                }
                            )
                        }

                        if (!canOverlay) {
                            Spacer(modifier = Modifier.height(8.dp))
                            SetupRow(
                                icon = { tint ->
                                    Icon(
                                        imageVector = Icons.Outlined.Layers,
                                        contentDescription = null,
                                        tint = tint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                title = "Touch feedback",
                                hint = "Optional - show input indicators while gaming",
                                action = "Allow",
                                onAction = {
                                    val intent = Intent(
                                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        Uri.parse("package:" + context.packageName)
                                    ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
                                    context.startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }

            // 4. LIBRARY HEADER, SEARCH, FILTERS
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Games",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondary,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        IconButton(
                            onClick = { viewModel.loadApps() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh apps",
                                tint = TextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search games", fontSize = 13.sp, color = TextMuted) },
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
                            focusedBorderColor = Accent,
                            unfocusedBorderColor = Hairline,
                            focusedContainerColor = Surface,
                            unfocusedContainerColor = Surface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedPlaceholderColor = TextMuted,
                            unfocusedPlaceholderColor = TextMuted
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_search_apps")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        listOf("All", "Emulators", "Games").forEach { cat ->
                            val isSelected = selectedCategory == cat
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { viewModel.setSelectedCategory(cat) }
                                    .padding(vertical = 2.dp)
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) Accent else TextSecondary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .width(20.dp)
                                        .height(2.dp)
                                        .background(
                                            if (isSelected) Accent else Color.Transparent,
                                            RoundedCornerShape(1.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // 5. APPS GRID
            if (filteredApps.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No apps found" else "Scanning installed games…",
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
            },
            onDismiss = { showConfigDialog = false }
        )
    }

    // Keymap Dialog
    if (showKeymapDialog) {
        KeymapGuideDialog(onDismiss = { showKeymapDialog = false })
    }
}

@Composable
private fun SetupRow(
    icon: @Composable (Color) -> Unit,
    title: String,
    hint: String,
    action: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon(TextSecondary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
                Text(
                    text = hint,
                    fontSize = 12.sp,
                    color = TextMuted,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onAction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = action,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Accent
                )
            }
        }
    }
}
