package com.orca.orcaauto.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.orca.orcaauto.ui.theme.AccentCyan
import com.orca.orcaauto.ui.theme.AccentEmerald
import com.orca.orcaauto.ui.theme.BackgroundDark
import com.orca.orcaauto.ui.theme.CardBorder
import com.orca.orcaauto.ui.theme.PrimaryIndigo
import com.orca.orcaauto.ui.theme.SurfaceDark
import com.orca.orcaauto.ui.theme.SurfaceVariantDark
import com.orca.orcaauto.ui.theme.TextMutedDark
import com.orca.orcaauto.ui.theme.TextPrimaryDark
import com.orca.orcaauto.ui.theme.TextSecondaryDark
import kotlinx.coroutines.delay

@Composable
fun MockRewardAdDialog(
    actionTitle: String = "Áp Dụng Hình Nền Cá Nhân",
    description: String = "Xem một video ngắn 5 giây để kích hoạt tính năng hình nền đặc biệt.",
    onDismiss: () -> Unit,
    onRewardGranted: () -> Unit
) {
    val context = LocalContext.current
    var isPlayingAd by remember { mutableStateOf(false) }
    var secondsRemaining by remember { mutableIntStateOf(5) }
    var isAdCompleted by remember { mutableStateOf(false) }

    // Tap tracking for developer easter egg (3 taps within 1.5s outside the dialog card)
    val tapTimestamps = remember { mutableStateListOf<Long>() }

    fun handleBackdropTap() {
        val now = System.currentTimeMillis()
        tapTimestamps.removeAll { now - it > 1500 }
        tapTimestamps.add(now)

        if (tapTimestamps.size >= 3) {
            Toast.makeText(context, "🛠️ Mẹo Nhà phát triển: Đã bỏ qua quảng cáo!", Toast.LENGTH_SHORT).show()
            onRewardGranted()
            onDismiss()
        }
    }

    // Countdown timer simulation
    LaunchedEffect(isPlayingAd) {
        if (isPlayingAd && secondsRemaining > 0) {
            while (secondsRemaining > 0) {
                delay(1000)
                secondsRemaining--
            }
            isAdCompleted = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // Fullscreen Backdrop that detects developer cheat taps outside modal
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { handleBackdropTap() }
                ),
            contentAlignment = Alignment.Center
        ) {
            // Main Modal Card (Consumes clicks to isolate inside from outside)
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { /* consume inside clicks */ }
                    )
                    .background(SurfaceDark, RoundedCornerShape(20.dp))
                    .border(1.dp, CardBorder, RoundedCornerShape(20.dp))
                    .padding(20.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .background(PrimaryIndigo.copy(alpha = 0.25f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CardGiftcard,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Quảng Cáo Nhận Thưởng 🎁",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimaryDark
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Đóng",
                                tint = TextMutedDark,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Text(
                        text = "$actionTitle: $description",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondaryDark,
                        textAlign = TextAlign.Center
                    )

                    // Mock Video Player Box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        PrimaryIndigo.copy(alpha = 0.4f),
                                        SurfaceVariantDark.copy(alpha = 0.8f)
                                    )
                                ),
                                RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, CardBorder, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isPlayingAd && !isAdCompleted) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(40.dp)
                                )
                                Text(
                                    text = "Bấm 'Bắt Đầu Xem' để kích hoạt (5s)",
                                    fontSize = 11.sp,
                                    color = TextSecondaryDark,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        } else if (isPlayingAd && !isAdCompleted) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = AccentEmerald,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Đang phát quảng cáo tài trợ: $secondsRemaining s",
                                        fontWeight = FontWeight.Bold,
                                        color = AccentCyan,
                                        fontSize = 13.sp
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (5 - secondsRemaining) / 5f },
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .height(6.dp),
                                    color = PrimaryIndigo,
                                    trackColor = SurfaceDark
                                )
                            }
                        } else {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Verified,
                                    contentDescription = null,
                                    tint = AccentEmerald,
                                    modifier = Modifier.size(36.dp)
                                )
                                Text(
                                    text = "✓ Đã hoàn thành xem quảng cáo!",
                                    fontWeight = FontWeight.Bold,
                                    color = AccentEmerald,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Bỏ qua / Hủy", color = TextSecondaryDark, fontSize = 12.sp)
                        }

                        if (!isAdCompleted) {
                            Button(
                                onClick = { isPlayingAd = true },
                                modifier = Modifier.weight(1.4f),
                                enabled = !isPlayingAd,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrimaryIndigo,
                                    contentColor = BackgroundDark
                                ),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = if (isPlayingAd) "Đang xem ($secondsRemaining s)" else "Bắt Đầu Xem (5s)",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BackgroundDark
                                )
                            }
                        } else {
                            Button(
                                onClick = {
                                    onRewardGranted()
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1.4f),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentEmerald),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "Áp Dụng Ngay 🎉",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
