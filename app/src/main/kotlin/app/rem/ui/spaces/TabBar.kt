package app.rem.ui.spaces

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.rem.ui.motion.rememberHaptics
import app.rem.ui.theme.Rem

enum class Tab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.CalendarToday),
    SHOPPING("Shopping", Icons.Rounded.ShoppingBasket),
    WISHLIST("Wishlist", Icons.Rounded.CardGiftcard),
    WORK("Work", Icons.Rounded.WorkOutline),
}

/** How much space the tab bar takes, so screens can keep their bottom controls above it. */
val LocalBottomInset = compositionLocalOf { 0.dp }
val TAB_BAR_HEIGHT: Dp = 64.dp

@Composable
fun TabBar(selected: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    val c = Rem.colors
    val haptics = rememberHaptics()
    Column(modifier.fillMaxWidth().background(c.canvas)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(c.separator))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(TAB_BAR_HEIGHT).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Tab.entries.forEach { t ->
                val on = t == selected
                val tint by animateColorAsState(if (on) c.gold else c.secondaryLabel, label = "tab")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Tab) { if (!on) haptics.tick(); onSelect(t) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(t.icon, null, tint = tint, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, style = MaterialTheme.typography.labelMedium, color = tint)
                }
            }
        }
    }
}
