package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Brand
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.MtkModel
import com.example.protocol.AuthHandlerType
import com.example.ui.theme.MtkBackground
import com.example.ui.theme.MtkBorder
import com.example.ui.theme.MtkOnPrimary
import com.example.ui.theme.MtkPrimary
import com.example.ui.theme.MtkSurface
import com.example.ui.theme.MtkSurfaceVariant
import com.example.ui.theme.MtkTerminalBg
import com.example.ui.theme.MtkTerminalCyan
import com.example.ui.theme.MtkTerminalGreen
import com.example.ui.theme.MtkTerminalPurple
import com.example.ui.theme.MtkTerminalRed
import com.example.ui.theme.MtkTerminalYellow
import com.example.ui.theme.MtkTextPrimary
import com.example.ui.theme.MtkTextSecondary

@Composable
fun MtkServiceScreen(
    viewModel: MtkServiceViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    // Auto-scroll terminal log
    LaunchedEffect(state.logs.size) {
        if (state.logs.isNotEmpty()) {
            listState.animateScrollToItem(state.logs.size - 1)
        }
    }

    Scaffold(
        containerColor = MtkBackground,
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = modifier.fillMaxSize()
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            // 1. Header with App Identity & USB Status Indicator
            HeaderSection(
                isUsbConnected = state.isUsbConnected,
                isRunning = state.isRunning,
                onInfoClick = { viewModel.toggleExplanation() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 2. Collapsible Workflow & Logic Explanation Banner
            AnimatedVisibility(visible = state.isExplanationExpanded) {
                Column {
                    ExplanationCard(onDismiss = { viewModel.toggleExplanation() })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 3. Selectors: Brand, Model, and Multi-Auth Handler
            SelectionSection(
                brands = state.brands,
                selectedBrand = state.selectedBrand,
                selectedModel = state.selectedModel,
                authHandlers = state.authHandlers,
                selectedAuthHandler = state.selectedAuthHandler,
                onBrandSelected = { viewModel.selectBrand(it) },
                onModelSelected = { viewModel.selectModel(it) },
                onAuthHandlerSelected = { viewModel.selectAuthHandler(it) },
                enabled = !state.isRunning
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 4. START SERVICE Button
            Button(
                onClick = { viewModel.startService() },
                enabled = !state.isRunning,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(
                        elevation = if (state.isRunning) 0.dp else 8.dp,
                        shape = RoundedCornerShape(12.dp),
                        ambientColor = MtkPrimary,
                        spotColor = MtkPrimary
                    )
                    .testTag("start_service_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MtkPrimary,
                    contentColor = MtkOnPrimary,
                    disabledContainerColor = MtkPrimary.copy(alpha = 0.5f),
                    disabledContentColor = MtkOnPrimary.copy(alpha = 0.7f)
                )
            ) {
                if (state.isRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = MtkOnPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "EXECUTING BROM ROUTINE...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Usb,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START SERVICE",
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 5. Terminal Log Box (Flexible weight ensuring safe drawing)
            TerminalLogBox(
                logs = state.logs,
                listState = listState,
                onClearLogs = { viewModel.clearLogs() },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
        }
    }
}

@Composable
private fun HeaderSection(
    isUsbConnected: Boolean,
    isRunning: Boolean,
    onInfoClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MtkPrimary)
                    .shadow(elevation = 6.dp, shape = RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "M",
                    color = MtkOnPrimary,
                    fontWeight = FontWeight.Black,
                    fontSize = 19.sp
                )
            }

            Column {
                Text(
                    text = "MTK Service Tool",
                    color = MtkTextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp
                )
                Text(
                    text = "BROM Mode / Byte-to-Byte Protocol",
                    color = MtkTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onInfoClick,
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, MtkBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Show MTK Protocol Workflow",
                    tint = MtkPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Connection Status Dot
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                val dotColor = when {
                    isRunning -> MtkTerminalCyan
                    isUsbConnected -> MtkTerminalGreen
                    else -> MtkTerminalYellow
                }
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .scale(if (isRunning || isUsbConnected) pulseScale else 1f)
                        .clip(CircleShape)
                        .background(dotColor)
                        .shadow(6.dp, CircleShape, spotColor = dotColor)
                )
            }
        }
    }
}

@Composable
private fun SelectionSection(
    brands: List<Brand>,
    selectedBrand: Brand,
    selectedModel: MtkModel,
    authHandlers: List<AuthHandlerType>,
    selectedAuthHandler: AuthHandlerType,
    onBrandSelected: (Brand) -> Unit,
    onModelSelected: (MtkModel) -> Unit,
    onAuthHandlerSelected: (AuthHandlerType) -> Unit,
    enabled: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Brand Selector Dropdown
            Box(modifier = Modifier.weight(1f)) {
                DropdownSelector(
                    label = "Brand",
                    selectedText = selectedBrand.name,
                    items = brands,
                    getItemLabel = { it.name },
                    onItemSelected = onBrandSelected,
                    enabled = enabled,
                    testTag = "brand_spinner"
                )
            }

            // Model Selector Dropdown
            Box(modifier = Modifier.weight(1f)) {
                DropdownSelector(
                    label = "Model",
                    selectedText = selectedModel.name,
                    items = selectedBrand.models,
                    getItemLabel = { it.name },
                    onItemSelected = onModelSelected,
                    enabled = enabled,
                    testTag = "model_spinner"
                )
            }
        }

        // Multi-Auth Handler Selector Dropdown
        DropdownSelector(
            label = "Auth Bypass Handler",
            selectedText = selectedAuthHandler.title,
            items = authHandlers,
            getItemLabel = { it.title },
            onItemSelected = onAuthHandlerSelected,
            enabled = enabled,
            testTag = "auth_handler_spinner"
        )
    }
}

@Composable
private fun <T> DropdownSelector(
    label: String,
    selectedText: String,
    items: List<T>,
    getItemLabel: (T) -> String,
    onItemSelected: (T) -> Unit,
    enabled: Boolean,
    testTag: String
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Container box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, if (expanded) MtkPrimary else MtkBorder, RoundedCornerShape(12.dp))
                .background(MtkSurface)
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = 12.dp)
                .testTag(testTag),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = selectedText,
                    color = if (enabled) MtkTextPrimary else MtkTextSecondary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MtkTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Floating label
        Surface(
            color = MtkBackground,
            modifier = Modifier
                .padding(start = 10.dp)
                .offset(y = (-7).dp)
        ) {
            Text(
                text = " $label ",
                color = if (expanded) MtkPrimary else MtkPrimary.copy(alpha = 0.85f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(MtkSurfaceVariant)
                .border(1.dp, MtkBorder, RoundedCornerShape(8.dp))
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = getItemLabel(item),
                            color = MtkTextPrimary,
                            fontSize = 12.5.sp
                        )
                    },
                    onClick = {
                        onItemSelected(item)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TerminalLogBox(
    logs: List<LogEntry>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Terminal Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "TERMINAL LOG",
                    color = MtkTextSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MtkTerminalGreen)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Baud: 115200",
                    color = MtkPrimary,
                    fontSize = 9.5.sp,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear logs",
                        tint = MtkTextSecondary,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Terminal Output Screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(MtkTerminalBg)
                .border(1.dp, MtkBorder, RoundedCornerShape(14.dp))
                .padding(8.dp)
                .testTag("terminal_log_box")
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(logs, key = { it.id }) { log ->
                    LogItemView(log)
                }
            }
        }
    }
}

@Composable
private fun LogItemView(log: LogEntry) {
    val (badgeColor, textColor) = when (log.level) {
        LogLevel.SYSTEM -> Pair(MtkTerminalCyan, MtkTextPrimary)
        LogLevel.USB -> Pair(MtkTerminalGreen, MtkTextPrimary)
        LogLevel.BROM -> Pair(MtkTerminalPurple, MtkTextPrimary)
        LogLevel.SLA -> Pair(MtkTerminalYellow, MtkTerminalYellow)
        LogLevel.INFO -> Pair(MtkPrimary, MtkTextPrimary)
        LogLevel.SUCCESS -> Pair(MtkTerminalGreen, MtkTerminalGreen)
        LogLevel.WARNING -> Pair(MtkTerminalYellow, MtkTerminalYellow)
        LogLevel.ERROR -> Pair(MtkTerminalRed, MtkTerminalRed)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "[${log.level.name}] ",
            color = badgeColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = log.message,
            color = textColor,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            lineHeight = 14.sp
        )
    }
}

@Composable
private fun ExplanationCard(onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MtkSurfaceVariant),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MtkPrimary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MTK BROM Auth Bypass Architecture",
                    color = MtkPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Close",
                        tint = MtkTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "၁။ BROM Handshake: Android UsbManager မှ OTG ချိတ်ဆက်ထားသော MTK Chipset (VID:0x0E8D) သို့ 0xA0 0x0A 0x50 0x05 byte sequence ကို Bitwise Inverted Complement (0x5F, 0xF5, 0xAF, 0xFA) စစ်ဆေးကာ Strict Sync ပြုလုပ်ပါသည်။\n" +
                       "၂။ Multi-Auth Bypass: EP0 Control Transfer (0xA1/0xC0), Chunked Bulk Delivery သို့မဟုတ် Direct Register Override (Write32) နည်းလမ်းများဖြင့် WDT နှင့် SLA/DAA Flag များကို patch လုပ်ပါသည်။\n" +
                       "၃။ Device Info & Reboot: HW Code (0xFD), Target Config (0xD8), MEID (0xE1), SOC ID (0xE7) ဖတ်ယူပြီး Watchdog Timer ဖြင့် Auto-Reboot ပြုလုပ်ပေးပါသည်။",
                color = MtkTextPrimary,
                fontSize = 10.sp,
                lineHeight = 14.sp
            )
        }
    }
}
