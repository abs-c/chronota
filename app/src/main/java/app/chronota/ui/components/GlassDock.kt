package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.annotation.StringRes
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import android.os.Build
import kotlin.math.roundToInt
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.chronota.R
import app.chronota.data.entity.TimerSession
import app.chronota.domain.OrbAction
import app.chronota.feature.today.FloatingOrb
import app.chronota.ui.theme.*

/**
 * The four primary pages, in dock order. Both the dock and the navigation graph read this one table,
 * so a page cannot end up with one label and icon in the dock and another in the route.
 */
enum class Destination(val route: String, @param:StringRes @get:StringRes val label: Int, val icon: ImageVector) {
    TODAY("today", R.string.today, AppIcons.Today),
    TODO("todo", R.string.plans_tab, AppIcons.Plan),
    RECORDS("records", R.string.records_tab, AppIcons.Record),
    MINE("settings", R.string.mine, AppIcons.Person),
}

@Composable
fun GlassDock(route: String, navigate: (String) -> Unit, timer: TimerSession?, defaultAction: OrbAction,
    action: (OrbAction) -> Unit, openTimer: () -> Unit, backdrop: GraphicsLayer, backdropOrigin: Offset, onWheel: (Boolean) -> Unit = {}, overrideAction: OrbAction? = null) {
    val pageAction = overrideAction ?: when (route) { "todo" -> OrbAction.PLAN; "records" -> OrbAction.RECORD; else -> defaultAction }
    val routes = Destination.entries.map { it.route }
    val labels = Destination.entries.map { it.label }
    val icons = Destination.entries.map { it.icon }
    var wheelOpen by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        Box(Modifier.weight(1f).requiredHeight(Metrics.dockHeight).testTag("glass_dock")
            .graphicsLayer { alpha = if (wheelOpen) 0f else 1f }
            .shadow(Metrics.floatingElevation, CircleShape, ambientColor = Color.Black.copy(alpha = .08f), spotColor = Color.Black.copy(alpha = .12f)).clip(CircleShape)
            // Nearly opaque, like the chrome of a system bar: what is behind a dock is text and grid
            // lines, and at a low alpha they frost into patches of grey that read as a split pane.
            .glassSurface(backdrop, backdropOrigin, MaterialTheme.colorScheme.surface, tintAlpha = .18f)
            .drawWithContent {
                drawContent()
                // The light falling on the top of the glass, and the shade gathering at its bottom.
                drawRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = .18f), Color.Transparent), endY = size.height * .58f))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .05f)), startY = size.height * .62f))
            }
            .glassRing()) {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface.copy(alpha = .08f)).padding(Space.xxs).selectableGroup()) {
                routes.forEachIndexed { index, destination ->
                    val selected = route == destination
                    val tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    // The selected page is told by its icon and label, not by a fill behind them: a
                    // second tone inside the bar is what made its glass look split in two.
                    Column(Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .testTag("nav_$destination").selectable(selected, enabled = !wheelOpen, role = Role.Tab, onClick = { navigate(destination) }),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(icons[index], null, Modifier.size(Metrics.icon), tint = tint)
                        Text(stringResource(labels[index]), style = MaterialTheme.typography.labelMedium, color = tint, maxLines = 1)
                    }
                }
            }
        }
        FloatingOrb(timer, pageAction, action, openTimer, { wheelOpen = it; onWheel(it) }, backdrop, backdropOrigin, fixedAction = route in listOf("todo", "records"))
    }
}
