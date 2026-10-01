package com.saeid.aidirectorcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.saeid.aidirectorcamera.camera.CameraSessionManager
import com.saeid.aidirectorcamera.data.ProjectRepository
import com.saeid.aidirectorcamera.sensors.MotionAnalyzer
import com.saeid.aidirectorcamera.ui.theme.AiDirectorTheme

enum class MainMode(val label: String) {
    CAMERA("CAMERA"), DIRECTOR("AI DIRECTOR"), COACH("SHOT COACH"), STORYBOARD("STORYBOARD"), PROJECTS("PROJECTS")
}

enum class ShotTechnique(val label: String) {
    WIDE("Wide"), MEDIUM("Medium"), CLOSE_UP("Close-Up"), HERO("Hero"), TRACKING("Tracking"), DOLLY("Dolly"), ORBIT("Orbit")
}

@Composable
fun DirectorCameraApp(cameraPermissionGranted: Boolean, requestPermissions: () -> Unit) {
    AiDirectorTheme {
        val context = LocalContext.current.applicationContext
        val camera = remember { CameraSessionManager(context) }
        val motion = remember { MotionAnalyzer(context) }
        val repository = remember { ProjectRepository(context) }
        var mode by remember { mutableStateOf(MainMode.CAMERA) }
        var coach by remember { mutableStateOf(ShotTechnique.HERO) }
        var activeProjectId by remember { mutableLongStateOf(-1L) }

        DisposableEffect(Unit) {
            motion.start()
            onDispose {
                motion.stop()
                camera.close()
            }
        }

        if (!cameraPermissionGranted) {
            PermissionGate(requestPermissions)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .windowInsetsPadding(WindowInsets.safeDrawing)
            ) {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (mode) {
                        MainMode.CAMERA -> CameraScreen(camera, motion, repository, activeProjectId.takeIf { it > 0 }, null)
                        MainMode.COACH -> CameraScreen(camera, motion, repository, activeProjectId.takeIf { it > 0 }, coach) { coach = it }
                        MainMode.DIRECTOR -> DirectorScreen { preset ->
                            coach = techniqueFromPreset(preset)
                            mode = MainMode.COACH
                        }
                        MainMode.STORYBOARD -> StoryboardScreen { name ->
                            coach = techniqueFromPreset(name)
                            mode = MainMode.COACH
                        }
                        MainMode.PROJECTS -> ProjectsScreen(repository, activeProjectId.takeIf { it > 0 }) {
                            activeProjectId = it
                        }
                    }
                }
                ModeBar(mode) { mode = it }
            }
        }
    }
}

private fun techniqueFromPreset(name: String): ShotTechnique {
    val n = name.lowercase()
    return when {
        "orbit" in n || "arc" in n -> ShotTechnique.ORBIT
        "track" in n -> ShotTechnique.TRACKING
        "dolly" in n || "push" in n -> ShotTechnique.DOLLY
        "close" in n -> ShotTechnique.CLOSE_UP
        "wide" in n -> ShotTechnique.WIDE
        "medium" in n -> ShotTechnique.MEDIUM
        else -> ShotTechnique.HERO
    }
}

@Composable
private fun PermissionGate(requestPermissions: () -> Unit) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("AI Director Camera", style = MaterialTheme.typography.headlineMedium)
            Text("Camera permission is required for the real live camera feed.")
            Button(onClick = requestPermissions) { Text("Allow Camera + Microphone") }
        }
    }
}

@Composable
private fun ModeBar(selected: MainMode, onSelect: (MainMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        MainMode.entries.forEach { mode ->
            Button(
                onClick = { onSelect(mode) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (mode == selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                    contentColor = if (mode == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 10.dp)
            ) { Text(mode.label, style = MaterialTheme.typography.labelSmall, maxLines = 1) }
        }
    }
}
