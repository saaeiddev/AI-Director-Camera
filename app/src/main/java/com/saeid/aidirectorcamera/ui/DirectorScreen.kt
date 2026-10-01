package com.saeid.aidirectorcamera.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.saeid.aidirectorcamera.director.DirectorPlan
import com.saeid.aidirectorcamera.director.DirectorPlanner

@Composable
fun DirectorScreen(onOpenCoach: (String) -> Unit) {
    var prompt by remember { mutableStateOf("Create a dramatic hero shot") }
    var plan by remember { mutableStateOf<DirectorPlan?>(null) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("AI DIRECTOR", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Text("Describe the result you want. The offline planner converts it into practical shooting instructions; no footage is uploaded.")
        OutlinedTextField(
            value = prompt,
            onValueChange = { prompt = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            label = { Text("Creative direction") }
        )
        Button(onClick = { plan = DirectorPlanner.plan(prompt) }, modifier = Modifier.fillMaxWidth()) {
            Text("Build Shooting Plan")
        }
        plan?.let { p ->
            PlanCard("SHOT", p.shot)
            PlanCard("CAMERA HEIGHT", p.cameraHeight)
            PlanCard("SUBJECT", p.subject)
            PlanCard("MOVEMENT", p.movement)
            PlanCard("LENS", p.lens)
            PlanCard("LIGHT", p.light)
            Button(onClick = { onOpenCoach(p.coachPreset) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open Shot Coach")
            }
        }
    }
}

@Composable
private fun PlanCard(label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .6f))
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
