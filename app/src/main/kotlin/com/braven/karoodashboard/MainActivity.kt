package com.braven.karoodashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.braven.karoodashboard.extension.BravenDashboardExtension
import com.braven.karoodashboard.server.IpAddressUtil
import com.braven.karoodashboard.trainer.FtmsController

// ─── Color theme ───────────────────────────────────────────
private val bgColor = Color(0xFF0F0F1A)
private val surfaceColor = Color(0xFF1A1A2E)
private val accentAmber = Color(0xFFF59E0B)
private val textPrimary = Color.White
private val textSecondary = Color(0xFF9CA3AF)
private val textMuted = Color(0xFF6B7280)
private val greenColor = Color(0xFF10B981)
private val redColor = Color(0xFFEF4444)
private val cyanColor = Color(0xFF06B6D4)

/**
 * Main activity displayed on the Karoo screen.
 * Two-tab layout:
 *   Tab 1 (Home): Dashboard URL + branding
 *   Tab 2 (Trainer): BLE FTMS scan, connect, ERG control
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen(onClose = { finish() })
        }
    }

    @Composable
    private fun MainScreen(onClose: () -> Unit) {
        var selectedTab by remember { mutableIntStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
        ) {
            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (selectedTab == 0) {
                    HomeTab(onClose = onClose)
                } else {
                    TrainerTab()
                }
            }

            // Bottom Navigation Bar
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surfaceColor)
                    .height(56.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BottomNavItem(label = "Home", selected = selectedTab == 0, onClick = { selectedTab = 0 })
                BottomNavItem(label = "Trainer", selected = selectedTab == 1, onClick = { selectedTab = 1 })
            }
        }
    }

    @Composable
    private fun BottomNavItem(label: String, selected: Boolean, onClick: () -> Unit) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 32.dp, vertical = 8.dp),
        ) {
            Text(
                text = label,
                color = if (selected) accentAmber else textMuted,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (selected) {
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(accentAmber, RoundedCornerShape(1.dp)),
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TAB 1: HOME
    // ═══════════════════════════════════════════════════════════
    @Composable
    private fun HomeTab(onClose: () -> Unit) {
        var dashboardUrl by remember { mutableStateOf("Loading...") }

        LaunchedEffect(Unit) {
            dashboardUrl = IpAddressUtil.getDashboardUrl(
                this@MainActivity,
                BravenDashboardExtension.SERVER_PORT,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Image(
                    painter = painterResource(id = R.drawable.bpl_logo),
                    contentDescription = "Braven Performance Lab Logo",
                    modifier = Modifier.size(120.dp),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = dashboardUrl,
                    color = textPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = surfaceColor,
                        contentColor = textPrimary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Text(text = "Close", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // TAB 2: TRAINER
    // ═══════════════════════════════════════════════════════════
    @Composable
    private fun TrainerTab() {
        val ext = BravenDashboardExtension.instance

        if (ext == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "Extension service not running", color = textSecondary, fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = "Start a ride to activate BLE", color = textMuted, fontSize = 13.sp)
                }
            }
            return
        }

        val status by ext.ftmsController.status.collectAsState()
        var powerInput by remember { mutableStateOf("") }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(bgColor)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // ── Header ──
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "KICKR Trainer", color = textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.weight(1f))
                    StateBadge(state = status.state)
                }
            }

            // ── Error ──
            if (status.errorMessage != null) {
                item {
                    Text(
                        text = status.errorMessage ?: "",
                        color = redColor,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(redColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(12.dp),
                    )
                }
            }

            // ── State-dependent UI ──
            when (status.state) {
                FtmsController.TrainerState.DISCONNECTED,
                FtmsController.TrainerState.ERROR -> {
                    item {
                        Button(
                            onClick = { ext.requestBleAndScan() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentAmber.copy(alpha = 0.2f),
                                contentColor = accentAmber,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                        ) {
                            Text("SCAN FOR TRAINER", fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
                        }
                    }
                }

                FtmsController.TrainerState.SCANNING -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(color = cyanColor, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Scanning for FTMS trainers...", color = cyanColor, fontSize = 14.sp)
                        }
                    }
                    items(status.scannedDevices) { device ->
                        DeviceRow(device = device, onClick = { ext.ftmsController.connect(device.address) })
                    }
                }

                FtmsController.TrainerState.CONNECTING -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(color = accentAmber, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Connecting...", color = accentAmber, fontSize = 14.sp)
                        }
                    }
                }

                FtmsController.TrainerState.CONNECTED -> {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                            CircularProgressIndicator(color = accentAmber, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Connected — requesting FTMS control...", color = accentAmber, fontSize = 14.sp)
                        }
                    }
                }

                FtmsController.TrainerState.CONTROLLING -> {
                    // Connected device info
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(greenColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .border(1.dp, greenColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(16.dp),
                        ) {
                            Text(status.deviceName ?: "Unknown", color = greenColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("FTMS Control Active", color = greenColor.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }

                    // Current target display
                    item {
                        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                text = "${status.targetPower ?: "--"}",
                                color = accentAmber, fontSize = 56.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("W", color = textMuted, fontSize = 20.sp, modifier = Modifier.padding(bottom = 8.dp))
                            Spacer(modifier = Modifier.weight(1f))
                            Text("TARGET", color = textMuted, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }

                    // Power input + set
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = powerInput,
                                onValueChange = { powerInput = it },
                                placeholder = { Text("e.g. 200", color = textMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = textPrimary,
                                    unfocusedTextColor = textPrimary,
                                    focusedBorderColor = accentAmber,
                                    unfocusedBorderColor = textMuted.copy(alpha = 0.3f),
                                    cursorColor = accentAmber,
                                ),
                                modifier = Modifier.weight(1f).height(52.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    powerInput.toIntOrNull()?.let { watts ->
                                        ext.ftmsController.setTargetPower(watts)
                                        powerInput = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accentAmber, contentColor = Color.Black),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(52.dp),
                            ) {
                                Text("SET", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }

                    // Quick-set presets
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(100, 150, 200, 250, 300).forEach { watts ->
                                Button(
                                    onClick = { ext.ftmsController.setTargetPower(watts) },
                                    colors = ButtonDefaults.buttonColors(containerColor = surfaceColor, contentColor = textSecondary),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                ) {
                                    Text("$watts", fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                                }
                            }
                        }
                    }

                    // +/- increment buttons
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                            listOf(-25 to redColor, -5 to redColor, 5 to greenColor, 25 to greenColor).forEach { (delta, color) ->
                                Button(
                                    onClick = {
                                        val current = status.targetPower ?: 0
                                        ext.ftmsController.setTargetPower((current + delta).coerceAtLeast(0))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.15f), contentColor = color),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier.weight(1f).height(40.dp),
                                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                                ) {
                                    Text("${if (delta > 0) "+" else ""}${delta}W", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Disconnect
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                ext.ftmsController.disconnect()
                                ext.releaseBle()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = redColor.copy(alpha = 0.15f), contentColor = redColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp),
                        ) {
                            Text("DISCONNECT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun StateBadge(state: FtmsController.TrainerState) {
        val (text, bgCol, textCol) = when (state) {
            FtmsController.TrainerState.DISCONNECTED -> Triple("DISCONNECTED", textMuted.copy(alpha = 0.2f), textMuted)
            FtmsController.TrainerState.SCANNING -> Triple("SCANNING", cyanColor.copy(alpha = 0.2f), cyanColor)
            FtmsController.TrainerState.CONNECTING -> Triple("CONNECTING", accentAmber.copy(alpha = 0.2f), accentAmber)
            FtmsController.TrainerState.CONNECTED -> Triple("CONNECTED", accentAmber.copy(alpha = 0.2f), accentAmber)
            FtmsController.TrainerState.CONTROLLING -> Triple("CONTROLLING", greenColor.copy(alpha = 0.2f), greenColor)
            FtmsController.TrainerState.ERROR -> Triple("ERROR", redColor.copy(alpha = 0.2f), redColor)
        }
        Text(
            text = text, color = textCol, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
            modifier = Modifier.background(bgCol, RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }

    @Composable
    private fun DeviceRow(device: FtmsController.ScannedDevice, onClick: () -> Unit) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .background(surfaceColor, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Box(
                modifier = Modifier.size(8.dp).background(
                    when {
                        device.rssi > -60 -> greenColor
                        device.rssi > -80 -> accentAmber
                        else -> redColor
                    }, CircleShape,
                ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(device.address, color = textMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            }
            Text("${device.rssi} dBm", color = textSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }
    }
}
