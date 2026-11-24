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
        Category.TASK -> Color(0xFF0A72E8)
        Category.WORK -> Color(0xFF5E5CE6)
        Category.MEDICINE -> Color(0xFFE5483F)
        Category.HEALTH -> Color(0xFF2FB24C)
        Category.HABIT -> Color(0xFFF08C00)
        Category.BILL -> Color(0xFF8E5BD8)
        Category.BIRTHDAY -> Color(0xFFE5487F)
        Category.EVENT -> Color(0xFF17A2B8)
        Category.DOCUMENT -> Color(0xFF7D8590)
        Category.VEHICLE -> Color(0xFF2C8FD6)
        Category.HOME -> Color(0xFF8A6D3B)
        Category.PET -> Color(0xFFD9822B)
        Category.SHOPPING -> Color(0xFF1FA58B)
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
        Priority.HIGH -> Color(0xFFE5483F)
        Priority.MEDIUM -> Color(0xFFF08C00)
        Priority.LOW -> Color(0xFF0A72E8)
        Priority.NONE -> null
    }

val Priority.label: String
    get() = when (this) {
        Priority.HIGH -> "High"
        Priority.MEDIUM -> "Medium"
        Priority.LOW -> "Low"
        Priority.NONE -> "None"
    }
