package com.gymbuddy.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.gymbuddy.app.domain.BodyPart
import com.gymbuddy.app.ui.theme.GymBuddyTheme
import com.gymbuddy.app.ui.theme.Spacing

/** Big bold screen title with optional actions on the right, like iOS large titles. */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.xs, top = Spacing.md, bottom = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

/** Small uppercase-free section title above a group of cards. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action()
    }
}

/** Card colors of the app: white on the warm background in light mode, a lifted gray in dark mode. */
@Composable
fun gymCardColors(containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow): CardColors =
    CardDefaults.cardColors(containerColor = containerColor)

/** Material's card with the app's colors and corners; screens import this instead of material3's. */
@Composable
fun Card(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors = gymCardColors(),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(modifier = modifier, shape = shape, colors = colors, content = content)

@Composable
fun Card(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = MaterialTheme.shapes.large,
    colors: CardColors = gymCardColors(),
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Card(
    onClick = onClick,
    modifier = modifier,
    enabled = enabled,
    shape = shape,
    colors = colors,
    content = content,
)

/** A full-width card with padded content, the building block of most screens. */
@Composable
fun GymCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentPadding: Dp = Spacing.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(Spacing.xs), content = content)
    }
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier.fillMaxWidth(), colors = gymCardColors(color), content = inner)
    } else {
        Card(modifier = modifier.fillMaxWidth(), colors = gymCardColors(color), content = inner)
    }
}

/** Icon, title and hint for a screen with nothing in it yet. */
@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier, action: @Composable () -> Unit = {}) {
    Column(
        modifier.fillMaxWidth().padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action()
    }
}

/** Each body part keeps one color everywhere: avatars, pills and charts by body part. */
@Composable
fun BodyPart.color(): Color {
    val series = GymBuddyTheme.colors.chartSeries
    return when (this) {
        BodyPart.CHEST -> series[0]
        BodyPart.BACK -> series[2]
        BodyPart.LEGS -> series[1]
        BodyPart.SHOULDERS -> series[3]
        BodyPart.ARMS -> series[4]
        BodyPart.CORE -> GymBuddyTheme.colors.success
        BodyPart.CARDIO -> MaterialTheme.colorScheme.error
        BodyPart.OLYMPIC, BodyPart.FULL_BODY, BodyPart.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
