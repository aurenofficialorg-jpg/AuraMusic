package com.aura.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MenuAction(val label: String, val icon: ImageVector, val selected: Boolean = false, val onClick: () -> Unit)

/**
 * Lightweight in-window action sheet. Unlike DropdownMenu / AlertDialog it does not create a new
 * Android window, so opening it costs nothing; the whole animation is a single GPU layer
 * (translation + alpha driven by one spring), so it stays at the display's full refresh rate.
 */
@Composable
fun AuraMenu(
    visible: Boolean, onDismiss: () -> Unit, actions: List<MenuAction>,
    title: String? = null, dark: Boolean = isDarkTheme(), edgeToEdge: Boolean = false
) {
    val p = remember { Animatable(0f) }
    var lastActions by remember { mutableStateOf(actions) }
    var lastTitle by remember { mutableStateOf(title) }
    if (visible) { lastActions = actions; lastTitle = title }
    LaunchedEffect(visible) { p.animateTo(if (visible) 1f else 0f, spring(dampingRatio = 0.86f, stiffness = 520f)) }
    BackHandler(enabled = visible) { onDismiss() }
    if (!visible && p.value <= 0.002f) return

    val card = if (dark) Color(0xFF1E1E21) else Color.White
    val fg = if (dark) Color.White else Color.Black
    val sub = if (dark) Color(0xFF9A9AA0) else Color(0xFF6E6E73)
    val shape = RoundedCornerShape(28.dp)

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { alpha = p.value.coerceIn(0f, 1f) }
                .background(Color.Black.copy(alpha = 0.5f))
                .clickable(remember { MutableInteractionSource() }, null) { if (visible) onDismiss() }
        )
        Column(
            Modifier.align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = (1f - p.value) * (size.height + 32.dp.toPx())
                    alpha = (p.value * 1.6f).coerceIn(0f, 1f)
                }
                .then(if (edgeToEdge) Modifier.navigationBarsPadding() else Modifier)
                .padding(12.dp)
                .widthIn(max = 480.dp).fillMaxWidth()
                .clip(shape).background(card).border(1.dp, fg.copy(alpha = 0.08f), shape)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(top = 10.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.width(36.dp).height(4.dp).clip(CircleShape).background(fg.copy(alpha = 0.18f)))
            if (lastTitle != null) {
                Text(
                    lastTitle!!, color = sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            } else Spacer(Modifier.height(6.dp))
            lastActions.forEach { a ->
                val c = if (a.selected) Accent else fg
                Row(
                    Modifier.fillMaxWidth().height(54.dp)
                        .clickable { a.onClick(); onDismiss() }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(a.icon, null, tint = if (a.selected) Accent else sub, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(a.label, color = c, fontSize = 16.sp, fontWeight = if (a.selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                }
            }
        }
    }
}
