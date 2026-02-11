package com.braven.karoodashboard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.braven.karoodashboard.extension.BravenDashboardExtension
import com.braven.karoodashboard.server.IpAddressUtil

/**
 * Main activity displayed on the Karoo screen.
 * Shows the dashboard URL so lab staff can connect displays.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BravenDashboardScreen()
        }
    }

    @Composable
    private fun BravenDashboardScreen() {
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
                .background(Color(0xFF0F0F1A)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(24.dp),
            ) {
                Text(
                    text = "BRAVEN",
                    color = Color(0xFF00D4FF),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 4.sp,
                )

                Text(
                    text = "PERFORMANCE LAB",
                    color = Color(0xFF808080),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    letterSpacing = 3.sp,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Dashboard URL",
                    color = Color(0xFFAAAAAA),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = dashboardUrl,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "Coach: $dashboardUrl/coach",
                    color = Color(0xFF00D4FF),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "Athlete: $dashboardUrl/athlete",
                    color = Color(0xFF00FF88),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Open any URL above in a browser\non a device connected to the same Wi-Fi",
                    color = Color(0xFF666666),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}
