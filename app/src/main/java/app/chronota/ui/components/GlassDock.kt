package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
    // The highlight slides from one page to the next instead of jumping, so the light reads as one
    // thing moving rather than two blinking on and off.
    val lighting = glassLighting()
    val light by animateFloatAsState(
        Destination.entries.indexOfFirst { it.route == route }.coerceAtLeast(0).toFloat(),
        spring(dampingRatio = .78f, stiffness = 320f),
        label = "dockLight",
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.lg), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
        val density = LocalDensity.current
        var dockWidth by remember { mutableStateOf(0) }
        // Where the light is, in the dock's own coordinates, worked out before the drawing so the rim
        // can be given the same place: the pane's pool and the rim's light have to agree, or the edge
        // reads as a separate, fixed highlight left over from another light.
        val insetPx = with(density) { Space.xxs.toPx() }
        val itemPx = if (dockWidth > 0) (dockWidth - insetPx * 2) / Destination.entries.size else 0f
        val lightCentre = if (itemPx > 0f) Offset(insetPx + (light + .5f) * itemPx, with(density) { Metrics.dockHeight.toPx() } / 2f) else null
        Box(Modifier.weight(1f).requiredHeight(Metrics.dockHeight).testTag("glass_dock")
            .graphicsLayer { alpha = if (wheelOpen) 0f else 1f }
            .onSizeChanged { dockWidth = it.width }
            .clip(CircleShape)
            // One step off the paper, whichever paper it is: `surfaceContainerHigh` is a shade darker
            // than the sheet in the light and a shade lighter than it in the dark, so the pane has a
            // body of its own and the rim's light has something to land on. A white wash on white, and
            // a black wash on black, are both nothing at all.
            .glassSurface(backdrop, backdropOrigin, MaterialTheme.colorScheme.surfaceContainerHigh, tintAlpha = .22f)
            .drawWithContent {
                drawContent()
                // One light on the dock, and it comes from the page you are on: a pool that spreads out
                // of that item to both sides and slides along when you change pages. Everything else on
                // the dock is material — the body, the rim — so there is no second light to disagree
                // with this one.
                val centre = lightCentre ?: return@drawWithContent
                val item = itemPx
                val lit = lighting.light
                drawRect(Brush.radialGradient(
                    listOf(Color.White.copy(alpha = .14f * lit), Color.White.copy(alpha = .04f * lit), Color.Transparent),
                    center = centre,
                    radius = item * 1.6f,
                ))
                // And the shade that light leaves: the pane falls away from it, so the ends of the dock
                // are its darkest part. A pool of light with no shade under it reads as a sticker; the
                // shade is what makes it sit on something with a thickness.
                drawRect(Brush.radialGradient(
                    listOf(Color.Transparent, Color.Transparent, Color.Black.copy(alpha = .04f * lighting.shade)),
                    center = centre,
                    radius = item * 2.6f,
                ))
            }
            .glassRing(lightCentre = lightCentre, lightReach = itemPx * 2.2f)) {
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
