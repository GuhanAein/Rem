package app.rem.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import app.rem.core.model.Category
import app.rem.core.model.Priority

val Category.color: Color
    get() = when (this) {
        Category.TASK -> Color(0xFF5B4BFF)
        Category.WORK -> Color(0xFF3B82F6)
        Category.MEDICINE -> Color(0xFFEF4444)
        Category.HEALTH -> Color(0xFF10B981)
        Category.HABIT -> Color(0xFFF59E0B)
        Category.BILL -> Color(0xFF8B5CF6)
        Category.BIRTHDAY -> Color(0xFFEC4899)
        Category.EVENT -> Color(0xFF06B6D4)
        Category.DOCUMENT -> Color(0xFF64748B)
        Category.VEHICLE -> Color(0xFF0EA5E9)
        Category.HOME -> Color(0xFF65A30D)
        Category.PET -> Color(0xFFF97316)
        Category.SHOPPING -> Color(0xFF14B8A6)
    }

val Category.icon: ImageVector
    get() = when (this) {
        Category.TASK -> Icons.Rounded.TaskAlt
        Category.WORK -> Icons.Rounded.WorkOutline
        Category.MEDICINE -> Icons.Rounded.Medication
        Category.HEALTH -> Icons.Rounded.Favorite
        Category.HABIT -> Icons.Rounded.SelfImprovement
        Category.BILL -> Icons.AutoMirrored.Rounded.ReceiptLong
        Category.BIRTHDAY -> Icons.Rounded.Cake
        Category.EVENT -> Icons.Rounded.Celebration
        Category.DOCUMENT -> Icons.Rounded.Description
        Category.VEHICLE -> Icons.Rounded.DirectionsCar
        Category.HOME -> Icons.Rounded.Home
        Category.PET -> Icons.Rounded.Pets
        Category.SHOPPING -> Icons.Rounded.ShoppingBag
    }

fun categoryColorArgb(category: Category): Int = category.color.toArgb()

val Priority.color: Color?
    get() = when (this) {
        Priority.HIGH -> Color(0xFFEF4444)
        Priority.MEDIUM -> Color(0xFFF59E0B)
        Priority.LOW -> Color(0xFF3B82F6)
        Priority.NONE -> null
    }

val Priority.label: String
    get() = when (this) {
        Priority.HIGH -> "High"
        Priority.MEDIUM -> "Medium"
        Priority.LOW -> "Low"
        Priority.NONE -> "None"
    }
