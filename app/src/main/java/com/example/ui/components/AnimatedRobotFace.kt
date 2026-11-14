package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.analysis.SignalDirection
import com.example.ui.theme.BearishCrimson
import com.example.ui.theme.BullishEmerald
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberPanel
import com.example.ui.theme.ElectricAmber
import com.example.ui.theme.NeonYellow
import com.example.ui.theme.ObsidianVoid
import kotlin.math.abs
import kotlin.math.sin

private val StaticPulseState = object : State<Float> {
    override val value: Float = 0.85f
}
private val StaticZeroState = object : State<Float> {
    override val value: Float = 0f
}
private val StaticIdleRingState = object : State<Float> {
    override val value: Float = 24f
}

@Composable
fun AnimatedRobotFace(
    isAnalyzing: Boolean,
    isSpeaking: Boolean,
    isListening: Boolean,
    direction: SignalDirection?,
    statusLabel: String,
    modifier: Modifier = Modifier
) {
    val isActive = isAnalyzing || isSpeaking || isListening

    // Only run GPU/Compose animation frames when the robot is actively scanning, speaking, or listening.
    // Reading State.value exclusively inside onDrawBehind prevents 60fps @Composable recompositions.
    val transition = if (isActive) rememberInfiniteTransition(label = "robot_hud") else null

    val pulseState: State<Float> = if (transition != null) {
        transition.animateFloat(
            initialValue = 0.65f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(1300, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pulse"
        )
    } else {
        StaticPulseState
    }

    val scanProgressState: State<Float> = if (transition != null && isAnalyzing) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scan"
        )
    } else {
        StaticZeroState
    }

    val wavePhaseState: State<Float> = if (transition != null) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(550, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wave"
        )
    } else {
        StaticZeroState
    }

    val ringRotationState: State<Float> = if (transition != null) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(16000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "ring"
        )
    } else {
        StaticIdleRingState
    }

    val accentColor = when {
        isListening -> CyberCyan
        isAnalyzing -> NeonYellow
        direction == SignalDirection.UP -> BullishEmerald
        direction == SignalDirection.DOWN -> BearishCrimson
        direction == SignalDirection.WAIT || direction == SignalDirection.UNCERTAIN -> ElectricAmber
        else -> NeonYellow
    }

    val cardBgBrush = remember {
        Brush.linearGradient(colors = listOf(CyberPanel, ObsidianVoid))
    }
    val analyzingState = rememberUpdatedState(isAnalyzing)
    val speakingState = rememberUpdatedState(isSpeaking)
    val listeningState = rememberUpdatedState(isListening)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(cardBgBrush)
            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(104.dp),
            contentAlignment = Alignment.Center
        ) {
            Spacer(
                modifier = Modifier
                    .size(104.dp)
                    .drawWithCache {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val outerR = size.minDimension * 0.47f
                        val arcTopLeft = Offset(cx - outerR, cy - outerR)
                        val arcSize = Size(outerR * 2f, outerR * 2f)

                        val haloRadius = outerR * 1.15f
                        val haloBrush = Brush.radialGradient(
                            colors = listOf(accentColor.copy(alpha = 0.24f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = haloRadius
                        )

                        val hw = size.width * 0.31f
                        val hh = size.height * 0.34f
                        val cut = size.width * 0.08f
                        val headPath = Path().apply {
                            moveTo(cx - hw + cut, cy - hh)
                            lineTo(cx + hw - cut, cy - hh)
                            lineTo(cx + hw, cy - hh + cut)
                            lineTo(cx + hw, cy + hh - cut)
                            lineTo(cx + hw - cut, cy + hh)
                            lineTo(cx - hw + cut, cy + hh)
                            lineTo(cx - hw, cy + hh - cut)
                            lineTo(cx - hw, cy - hh + cut)
                            close()
                        }

                        val strokeOuter = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        val strokeInner = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round)
                        val headStroke = Stroke(width = 1.8.dp.toPx())
                        val headFillColor = Color(0xFF0D1118)
                        val headBorderColor = accentColor.copy(alpha = 0.7f)
                        val arcColor1 = accentColor.copy(alpha = 0.55f)
                        val arcColor2 = CyberCyan.copy(alpha = 0.35f)

                        val visorW = hw * 1.48f
                        val visorH = hh * 0.54f
                        val visorTop = cy - hh * 0.38f
                        val visorLeft = cx - visorW / 2f
                        val visorCorner = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        val visorColor = Color(0xFF05070B)

                        val baseEyeRadius = 4.2.dp.toPx()
                        val leftEyeX = cx - visorW * 0.25f
                        val rightEyeX = cx + visorW * 0.25f
                        val eyeY = visorTop + visorH / 2f
                        val beamStrokeW = 2.dp.toPx()

                        val barCount = 7
                        val mouthW = hw * 1.15f
                        val barSpacing = mouthW / barCount
                        val mouthY = cy + hh * 0.45f
                        val barStrokeW = 2.4.dp.toPx()
                        val activeAmp9 = 9.dp.toPx()
                        val activeAmp7 = 7.dp.toPx()
                        val idleAmp2 = 2.dp.toPx()

                        onDrawBehind {
                            val pulse = pulseState.value
                            val ringRotation = ringRotationState.value
                            val scanProgress = scanProgressState.value
                            val wavePhase = wavePhaseState.value
                            val analyzing = analyzingState.value
                            val speaking = speakingState.value
                            val listening = listeningState.value

                            // Ambient glow halo
                            drawCircle(
                                brush = haloBrush,
                                radius = haloRadius,
                                center = Offset(cx, cy),
                                alpha = pulse
                            )

                            // Rotating segmented HUD arc
                            drawArc(
                                color = arcColor1,
                                startAngle = ringRotation,
                                sweepAngle = 70f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = strokeOuter
                            )
                            drawArc(
                                color = arcColor2,
                                startAngle = ringRotation + 180f,
                                sweepAngle = 55f,
                                useCenter = false,
                                topLeft = arcTopLeft,
                                size = arcSize,
                                style = strokeInner
                            )

                            // Robot head chassis (chamfered octagon)
                            drawPath(path = headPath, color = headFillColor)
                            drawPath(path = headPath, color = headBorderColor, style = headStroke)

                            // Visor slot
                            drawRoundRect(
                                color = visorColor,
                                topLeft = Offset(visorLeft, visorTop),
                                size = Size(visorW, visorH),
                                cornerRadius = visorCorner
                            )

                            // Optical eyes inside visor
                            val eyeRadius = baseEyeRadius * (if (analyzing) 1.15f else pulse.coerceAtLeast(0.85f))
                            drawCircle(color = accentColor, radius = eyeRadius, center = Offset(leftEyeX, eyeY))
                            drawCircle(color = accentColor, radius = eyeRadius, center = Offset(rightEyeX, eyeY))

                            // Scanning laser beam when analyzing
                            if (analyzing) {
                                val beamX = visorLeft + visorW * scanProgress
                                drawLine(
                                    color = NeonYellow,
                                    start = Offset(beamX, visorTop + 2f),
                                    end = Offset(beamX, visorTop + visorH - 2f),
                                    strokeWidth = beamStrokeW
                                )
                            }

                            // Voice equalizer mouth bars
                            val barColor = if (listening) CyberCyan else accentColor
                            for (i in 0 until barCount) {
                                val bx = cx - mouthW / 2f + i * barSpacing + barSpacing * 0.5f
                                val amp = when {
                                    speaking || listening ->
                                        (0.35f + 0.65f * abs(sin(wavePhase + i * 0.85f))) * activeAmp9
                                    analyzing ->
                                        (0.25f + 0.45f * abs(sin(wavePhase + i * 0.6f))) * activeAmp7
                                    else -> idleAmp2
                                }
                                drawLine(
                                    color = barColor,
                                    start = Offset(bx, mouthY - amp / 2f),
                                    end = Offset(bx, mouthY + amp / 2f),
                                    strokeWidth = barStrokeW,
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "MN SHAH AI ROBOT",
                    style = MaterialTheme.typography.titleMedium,
                    color = NeonYellow,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = statusLabel,
                style = MaterialTheme.typography.labelMedium,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    isListening -> "Sun raha hoon... Boliye: \"MN SHAH mujhe signal do\""
                    isSpeaking -> "Urdu mein live market tajziya sunaya ja raha hai..."
                    isAnalyzing -> "1M, 5M aur 15M charts + RSI/MACD/EMA scan ho rahe hain..."
                    else -> "Autonomous Urdu Voice & Multi-Timeframe Quantitative Engine Ready"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
            )
        }
    }
}
