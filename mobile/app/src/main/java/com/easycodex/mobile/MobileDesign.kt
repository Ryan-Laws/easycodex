package com.easycodex.mobile

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
