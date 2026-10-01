package com.saeid.aidirectorcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saeid.aidirectorcamera.director.StoryboardPlanner
import com.saeid.aidirectorcamera.director.StoryboardShot

@Composable
fun StoryboardScreen(onOpenCoach: (String) -> Unit) {
    var scene by remember { mutableStateOf("A man walks toward his car, opens the door, gets inside and drives away.") }
    var shots by remember { mutableStateOf<List<StoryboardShot>>(emptyList()) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("STORYBOARD ASSISTANT", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("Create a practical offline shot list from the scene description.")
        OutlinedTextField(
            value = scene,
            onValueChange = { scene = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Scene") }
        )
        Button(onClick = { shots = StoryboardPlanner.create(scene) }, modifier = Modifier.fillMaxWidth()) {
            Text("Generate Shot List")
        }
        shots.forEach { shot ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .62f))
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("SHOT %02d".format(shot.number), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    Text(shot.name, style = MaterialTheme.typography.titleMedium)
                    Text(shot.intent, style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { onOpenCoach(shot.name) }) { Text("Coach this shot") }
                }
            }
        }
    }
}
