package com.easycodex.mobile

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal object EasyCodexDesign {
    val PanelShape = RoundedCornerShape(18.dp)
    val ComposerPanelShape = RoundedCornerShape(22.dp)
}

internal object EasyCodexMotion {
    const val FastEffects = 140
    const val NormalEffects = 200
    const val Spatial = 260
    const val Exit = 160

    val PressSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium,
    )

    val SelectionSpring = spring<Float>(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium,
    )

    fun <T> fastTween() = tween<T>(durationMillis = FastEffects, easing = FastOutSlowInEasing)
    fun <T> normalTween() = tween<T>(durationMillis = NormalEffects, easing = FastOutSlowInEasing)
    fun <T> spatialTween() = tween<T>(durationMillis = Spatial, easing = FastOutSlowInEasing)
    fun <T> exitTween() = tween<T>(durationMillis = Exit, easing = FastOutSlowInEasing)
}

internal fun easyCodexExpandVertically(
    expandFrom: Alignment.Vertical = Alignment.Top,
): EnterTransition = expandVertically(
    animationSpec = EasyCodexMotion.spatialTween(),
    expandFrom = expandFrom,
) + fadeIn(animationSpec = EasyCodexMotion.normalTween())

internal fun easyCodexShrinkVertically(
    shrinkTowards: Alignment.Vertical = Alignment.Top,
): ExitTransition = shrinkVertically(
    animationSpec = EasyCodexMotion.exitTween(),
    shrinkTowards = shrinkTowards,
) + fadeOut(animationSpec = EasyCodexMotion.fastTween())

@Composable
internal fun EasyCodexIconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier.size(40.dp),
    contentDescription: String? = null,
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.primary,
        modifier = modifier,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(21.dp))
        }
    }
}
