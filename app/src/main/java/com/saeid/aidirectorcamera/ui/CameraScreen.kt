package com.saeid.aidirectorcamera.ui

import androidx.camera.core.CameraSelector
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.saeid.aidirectorcamera.ai.GuidanceEngine
import com.saeid.aidirectorcamera.ai.GuidanceInput
import com.saeid.aidirectorcamera.ai.NormRect
import com.saeid.aidirectorcamera.camera.CameraSessionManager
import com.saeid.aidirectorcamera.camera.QualityMode
import com.saeid.aidirectorcamera.data.ProjectRepository
import com.saeid.aidirectorcamera.data.ShotEntity
import com.saeid.aidirectorcamera.sensors.MotionAnalyzer
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CameraScreen(
    camera: CameraSessionManager,
    motion: MotionAnalyzer,
    repository: ProjectRepository,
    activeProjectId: Long?,
    coach: ShotTechnique?,
    onCoachChange: ((ShotTechnique) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val cameraState by camera.state.collectAsState()
    val analysis by camera.analysis.collectAsState()
    val motionState by motion.state.collectAsState()
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    DisposableEffect(lifecycleOwner, previewView) {
        camera.bind(lifecycleOwner, previewView, cameraState.quality)
        onDispose { camera.unbind() }
    }

    val guidanceInput = GuidanceInput(
        subjectRect = analysis.subjectRect,
        averageLuma = analysis.lighting.averageLuma,
        highlightFraction = analysis.lighting.highlightFraction,
        horizonDegrees = motionState.horizonDegrees,
        smoothness = motionState.smoothness
    )
    val recommendation = GuidanceEngine.recommendation(guidanceInput)
    val consistency = GuidanceEngine.compositionConsistency(guidanceInput)
    val shotReady = isShotReady(coach, analysis.subjectRect, motionState.horizonDegrees, motionState.smoothness)
    LaunchedEffect(shotReady) {
        if (shotReady) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    var consumedUri by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(cameraState.lastSavedUri, activeProjectId) {
        val uri = cameraState.lastSavedUri
        if (uri != null && uri != consumedUri && activeProjectId != null) {
            repository.saveShot(
                ShotEntity(
                    projectId = activeProjectId,
                    type = coach?.label ?: "Free Camera",
                    mediaUri = uri,
                    durationMs = cameraState.lastDurationMs,
                    lens = if (cameraState.lensFacing == CameraSelector.LENS_FACING_BACK) "Rear" else "Front",
                    resolution = cameraState.quality.label,
                    fpsLabel = "Device-selected",
                    recommendation = recommendation,
                    movement = motionState.movementLabel
                )
            )
            consumedUri = uri
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures { p -> camera.tapToFocus(previewView, p.x, p.y) }
            }
    ) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        CameraOverlay(analysis.subjectRect, motionState.horizonDegrees, coach, shotReady)

        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HudTop(cameraState.arCoreStatus, cameraState.thermalStatus, cameraState.quality.label, consistency, activeProjectId != null)
                GuidanceBubble(recommendation, shotReady)
                if (coach != null && onCoachChange != null) CoachSelector(coach, onCoachChange)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                cameraState.error?.let { ErrorBubble(it) }
                ExposureZoomControls(cameraState, camera::setZoom, camera::setExposure)
                QualitySelectorRow(cameraState.availableQualities, cameraState.quality) { camera.rebind(it) }
                CameraControls(
                    isRecording = cameraState.isRecording,
                    torchOn = cameraState.torchOn,
                    hasFlash = cameraState.hasFlash,
                    onTorch = camera::toggleTorch,
                    onSwitch = camera::switchCamera,
                    onRecord = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        camera.toggleRecording()
                    }
                )
            }
        }
    }
}

@Composable
private fun HudTop(arCore: String, thermal: String, quality: String, consistency: Int, projectActive: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        GlassPill("AI DIRECTOR • LIVE")
        GlassPill("$quality • C $consistency%")
    }
    Spacer(Modifier.height(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GlassPill(arCore)
        GlassPill(thermal)
        GlassPill(if (projectActive) "PROJECT ACTIVE" else "NO PROJECT")
    }
}

@Composable
private fun GlassPill(text: String) {
    Text(
        text,
        modifier = Modifier.clip(RoundedCornerShape(100.dp)).background(Color.Black.copy(alpha = 0.50f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(100.dp)).padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color.White,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1
    )
}

@Composable
private fun GuidanceBubble(text: String, ready: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.56f)),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(if (ready) Color(0xFF7DFFAA) else MaterialTheme.colorScheme.primary))
            Text(if (ready) "SHOT READY ✓" else text, color = Color.White, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CoachSelector(selected: ShotTechnique, onSelect: (ShotTechnique) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        ShotTechnique.entries.take(5).forEach { t ->
            Button(
                onClick = { onSelect(t) },
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 3.dp, vertical = 6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (t == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.48f)
                )
            ) { Text(t.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
    }
}

@Composable
private fun ExposureZoomControls(
    state: com.saeid.aidirectorcamera.camera.CameraState,
    onZoom: (Float) -> Unit,
    onExposure: (Int) -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.54f)), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("ZOOM  ${"%.1f".format(state.zoomRatio)}×", style = MaterialTheme.typography.labelSmall)
            Slider(
                value = state.zoomRatio,
                onValueChange = onZoom,
                valueRange = state.minZoomRatio..state.maxZoomRatio.coerceAtLeast(state.minZoomRatio + 0.01f)
            )
            if (state.exposureMax > state.exposureMin) {
                Text("EXPOSURE  ${state.exposureIndex}", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = state.exposureIndex.toFloat(),
                    onValueChange = { onExposure(it.toInt()) },
                    valueRange = state.exposureMin.toFloat()..state.exposureMax.toFloat()
                )
            }
        }
    }
}

@Composable
private fun QualitySelectorRow(available: List<QualityMode>, selected: QualityMode, onSelect: (QualityMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        available.forEach { q ->
            Button(
                onClick = { onSelect(q) },
                colors = ButtonDefaults.buttonColors(containerColor = if (q == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.28f) else Color.Black.copy(alpha = 0.52f)),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            ) { Text(q.label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun CameraControls(
    isRecording: Boolean,
    torchOn: Boolean,
    hasFlash: Boolean,
    onTorch: () -> Unit,
    onSwitch: () -> Unit,
    onRecord: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = onTorch, enabled = hasFlash, colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
            Text(if (torchOn) "TORCH ON" else "TORCH")
        }
        Box(
            Modifier.size(76.dp).clip(CircleShape).border(3.dp, Color.White.copy(alpha = 0.86f), CircleShape)
                .padding(8.dp).clip(CircleShape).background(if (isRecording) Color(0xFFFF384F) else Color.White)
                .pointerInput(isRecording) { detectTapGestures { onRecord() } }
        )
        Button(onClick = onSwitch, colors = ButtonDefaults.buttonColors(containerColor = Color.Black.copy(alpha = 0.6f))) {
            Text("FLIP")
        }
    }
}

@Composable
private fun ErrorBubble(message: String) {
    Text(
        message,
        color = Color.White,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF6C1B26)).padding(10.dp)
    )
}

@Composable
private fun CameraOverlay(rect: NormRect?, horizonDegrees: Float, coach: ShotTechnique?, shotReady: Boolean) {
    Canvas(Modifier.fillMaxSize()) {
        val guideColor = if (shotReady) Color(0xFF7DFFAA) else Color(0xFF63E7FF)
        val thin = 1.dp.toPx()
        drawLine(Color.White.copy(alpha = 0.24f), Offset(size.width/3f, 0f), Offset(size.width/3f, size.height), thin)
        drawLine(Color.White.copy(alpha = 0.24f), Offset(size.width*2f/3f, 0f), Offset(size.width*2f/3f, size.height), thin)
        drawLine(Color.White.copy(alpha = 0.24f), Offset(0f, size.height/3f), Offset(size.width, size.height/3f), thin)
        drawLine(Color.White.copy(alpha = 0.24f), Offset(0f, size.height*2f/3f), Offset(size.width, size.height*2f/3f), thin)

        rect?.let {
            val r = Rect(it.left*size.width, it.top*size.height, it.right*size.width, it.bottom*size.height)
            drawRect(guideColor, topLeft = r.topLeft, size = r.size, style = Stroke(width = 2.dp.toPx()))
            drawCircle(guideColor, radius = 4.dp.toPx(), center = r.center)
        }

        val angle = Math.toRadians(horizonDegrees.toDouble())
        val half = size.width * 0.22f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val dx = (cos(angle) * half).toFloat()
        val dy = (sin(angle) * half).toFloat()
        drawLine(Color.White.copy(alpha = 0.58f), Offset(cx-dx, cy-dy), Offset(cx+dx, cy+dy), 2.dp.toPx(), cap = StrokeCap.Round)

        if (coach != null) {
            drawCoachGuide(coach, guideColor)
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCoachGuide(technique: ShotTechnique, color: Color) {
    val cx = size.width / 2f
    val cy = size.height * 0.62f
    when (technique) {
        ShotTechnique.DOLLY -> {
            drawLine(color.copy(alpha=.8f), Offset(cx, size.height*.78f), Offset(cx, size.height*.45f), 5.dp.toPx(), cap=StrokeCap.Round)
            drawCircle(color, 8.dp.toPx(), Offset(cx, size.height*.45f), style = Stroke(3.dp.toPx()))
        }
        ShotTechnique.TRACKING -> {
            drawLine(color.copy(alpha=.8f), Offset(size.width*.2f, cy), Offset(size.width*.8f, cy), 5.dp.toPx(), cap=StrokeCap.Round)
            drawCircle(color, 8.dp.toPx(), Offset(size.width*.8f, cy), style = Stroke(3.dp.toPx()))
        }
        ShotTechnique.ORBIT -> {
            drawArc(color.copy(alpha=.85f), 205f, 250f, false,
                topLeft=Offset(size.width*.23f, size.height*.35f),
                size=androidx.compose.ui.geometry.Size(size.width*.54f, size.width*.54f),
                style=Stroke(4.dp.toPx(), cap=StrokeCap.Round))
        }
        else -> {
            val targetW = when (technique) { ShotTechnique.WIDE -> .62f; ShotTechnique.CLOSE_UP -> .34f; else -> .46f }
            val targetH = when (technique) { ShotTechnique.WIDE -> .60f; ShotTechnique.CLOSE_UP -> .34f; else -> .50f }
            drawRect(color.copy(alpha=.65f),
                topLeft=Offset(size.width*(.5f-targetW/2f), size.height*(.48f-targetH/2f)),
                size=androidx.compose.ui.geometry.Size(size.width*targetW,size.height*targetH),
                style=Stroke(2.dp.toPx()))
        }
    }
}

private fun isShotReady(technique: ShotTechnique?, rect: NormRect?, horizon: Float, smoothness: Float): Boolean {
    if (technique == null || rect == null) return false
    if (abs(horizon) > 3.0f || smoothness < 60f) return false
    return when (technique) {
        ShotTechnique.CLOSE_UP -> rect.height > 0.32f && rect.centerX in 0.34f..0.66f
        ShotTechnique.WIDE -> rect.height in 0.18f..0.55f
        ShotTechnique.MEDIUM, ShotTechnique.HERO -> rect.height in 0.32f..0.78f && rect.centerX in 0.25f..0.75f
        ShotTechnique.TRACKING, ShotTechnique.DOLLY, ShotTechnique.ORBIT -> rect.centerX in 0.24f..0.76f
    }
}
