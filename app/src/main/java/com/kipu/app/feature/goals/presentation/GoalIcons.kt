package com.kipu.app.feature.goals.presentation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal data class GoalIconOption(val key: String, val label: String, val image: ImageVector)

internal val goalIcons = listOf(
    GoalIconOption("savings", "Ahorro", Icons.Default.Savings),
    GoalIconOption("laptop", "Tecnología", Icons.Default.LaptopMac),
    GoalIconOption("shield", "Emergencias", Icons.Default.Shield),
    GoalIconOption("flight", "Viajes", Icons.Default.Flight),
    GoalIconOption("home", "Hogar", Icons.Default.Home),
    GoalIconOption("car", "Vehículo", Icons.Default.DirectionsCar),
    GoalIconOption("school", "Estudios", Icons.Default.School),
    GoalIconOption("smartphone", "Celular", Icons.Default.Smartphone),
)

internal fun goalIcon(key: String?): GoalIconOption = goalIcons.firstOrNull { it.key == key } ?: goalIcons.first()

@Composable
internal fun GoalIconBadge(key: String?, modifier: Modifier = Modifier) {
    val option = goalIcon(key)
    val colors = MaterialTheme.colorScheme
    val alternate = option.key in setOf("laptop", "school", "smartphone")
    Surface(
        modifier = modifier.size(48.dp), shape = RoundedCornerShape(14.dp),
        color = if (alternate) colors.secondaryContainer else colors.primaryContainer,
    ) {
        Icon(option.image, contentDescription = "Icono: ${option.label}",
            tint = if (alternate) colors.onSecondaryContainer else colors.onPrimaryContainer,
            modifier = Modifier.padding(11.dp))
    }
}
