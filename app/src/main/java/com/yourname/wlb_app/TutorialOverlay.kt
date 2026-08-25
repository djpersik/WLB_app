package com.yourname.wlb_app

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TutorialStep(
    val title: String,
    val description: String,
    val targetBounds: Rect? = null
)

@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    onFinish: () -> Unit
) {
    var currentStep by remember { mutableStateOf(0) }
    val step = steps[currentStep]

    Box(modifier = Modifier.fillMaxSize()) {
        // Затемнення з дірою
        Canvas(modifier = Modifier
            .fillMaxSize()
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) { }
        ) {
            // Темний фон
            drawRect(color = Color.Black.copy(alpha = 0.75f))

            // Підсвічений елемент — "діра" через BlendMode
            step.targetBounds?.let { bounds ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = Offset(bounds.left - 8f, bounds.top - 8f),
                    size = Size(bounds.width + 16f, bounds.height + 16f),
                    cornerRadius = CornerRadius(16f, 16f),
                    blendMode = BlendMode.Clear
                )
            }
        }

        // Підпис знизу
        Card(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .padding(bottom = 32.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = step.title,
                    fontSize = 18.sp,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = step.description,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${currentStep + 1} / ${steps.size}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (currentStep > 0) {
                            OutlinedButton(onClick = { currentStep-- }) {
                                Text("Назад")
                            }
                        }
                        Button(
                            onClick = {
                                if (currentStep < steps.size - 1) {
                                    currentStep++
                                } else {
                                    onFinish()
                                }
                            }
                        ) {
                            Text(if (currentStep < steps.size - 1) "Далі" else "Почати!")
                        }
                    }
                }
            }
        }
    }
}