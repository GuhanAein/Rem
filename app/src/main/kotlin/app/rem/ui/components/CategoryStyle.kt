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
        Category.TASK -> Color(0xFFB9C3F5)
        Category.WORK -> Color(0xFFAEB8C8)
        Category.MEDICINE -> Color(0xFFF2A296)
        Category.HEALTH -> Color(0xFFA3D6AE)
        Category.HABIT -> Color(0xFFEBCB8E)
        Category.BILL -> Color(0xFFCBB8F0)
        Category.BIRTHDAY -> Color(0xFFF1B5CB)
        Category.EVENT -> Color(0xFF98D2DB)
        Category.DOCUMENT -> Color(0xFFC9C5BC)
        Category.VEHICLE -> Color(0xFFA5C8EB)
        Category.HOME -> Color(0xFFD8C4A2)
        Category.PET -> Color(0xFFF0BE95)
        Category.SHOPPING -> Color(0xFF9ED6C6)
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
        Priority.HIGH -> Color(0xFFEC7B6B)
        Priority.MEDIUM -> Color(0xFFE9B872)
        Priority.LOW -> Color(0xFF9CB4E0)
        Priority.NONE -> null
    }

val Priority.label: String
    get() = when (this) {
        Priority.HIGH -> "High"
        Priority.MEDIUM -> "Medium"
        Priority.LOW -> "Low"
        Priority.NONE -> "None"
    }
