package com.cookie.sh.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun LockedDownBanner() {
    val context = LocalContext.current
    val targetDate = remember {
        Calendar.getInstance().apply {
            set(2026, Calendar.SEPTEMBER, 1, 0, 0, 0)
        }.timeInMillis
    }
    
    var timeLeft by remember { mutableStateOf(targetDate - System.currentTimeMillis()) }
    
    LaunchedEffect(Unit) {
        while (true) {
            timeLeft = targetDate - System.currentTimeMillis()
            delay(1000)
        }
    }

    val days = TimeUnit.MILLISECONDS.toDays(timeLeft)
    val hours = TimeUnit.MILLISECONDS.toHours(timeLeft) % 24
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeLeft) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeLeft) % 60

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                drawRect(
                    color = Color(0xFF801313),
                    topLeft = Offset(0f, size.height - 2.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(size.width, 2.dp.toPx())
                )
            }
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFD32F2F), Color(0xFFB71C1C))
                )
            )
            .clickable {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://keepandroidopen.org"))
                context.startActivity(intent)
            }
            .padding(vertical = 4.dp, horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "ANDROID WILL BECOME A LOCKED-DOWN PLATFORM IN ${days}D ${hours}H ${minutes}M ${seconds}S",
            style = TextStyle(
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 1.sp,
                shadow = Shadow(
                    color = Color.Black.copy(alpha = 0.5f),
                    offset = Offset(0f, 2f),
                    blurRadius = 4f
                )
            )
        )
    }
}
