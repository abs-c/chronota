package app.chronota.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Spacing scale. Every gap, padding and margin in the app comes from here so the
 * whole layout sits on one 4dp grid (4 / 8 / 12 / 16 / 24 / 32).
 */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
}

/**
 * Shared metrics.
 *
 * Elevation policy: shadows are reserved for surfaces that float above the page
 * (glass dock, action orb, radial menu) and stay at [floatingElevation]. Central
 * panels, cards, rows and chips are flat: they separate with a container fill and
 * an [outline] hairline border instead of a shadow. Do not add ad-hoc elevations.
 *
 * Touch policy: anything the user can tap or drag is at least [touchTarget] high.
 */
object Metrics {
    // Controls.
    val controlHeight = 48.dp
    val touchTarget = controlHeight
    val tabHeight = 40.dp
    val chipHeight = 28.dp
    val segmentHeight = 36.dp
    /** Chip label line height: fits the compact filter chip without clipping. */
    val chipLineHeight = 14.sp
    val optionValueWidth = 148.dp
    val multilineHeight = 84.dp
    val check = 24.dp
    val switchWidth = 44.dp

    // Panels and dialogs.
    val panelMargin = Space.lg
    val panelWidth = 480.dp
    val panelHeight = 680.dp
    val panelHeader = controlHeight
    val panelElevation = 0.dp
    val dialogElevation = 6.dp
    val floatingElevation = 2.dp
    /** What a white sheet lifts off the grouped page by: a lift, not a drop shadow. */
    val cardElevation = 1.dp
    const val switchScale = .7f

    // Strokes: hairline for floating glass edges, outline for flat panel/card borders.
    val hairline = .5.dp
    val outline = 1.dp
    val divider = 1.dp

    /** Alpha for timeline, calendar and chart guide lines. */
    const val gridAlpha = .3f

    // Icons and marks.
    val icon = 20.dp
    val iconSmall = 16.dp
    val iconLarge = 28.dp
    val emptyIcon = iconLarge
    val colorDot = 8.dp
    val dotSmall = 6.dp
    val starCompact = 14.dp
    val starLarge = 40.dp

    // Navigation and floating surfaces.
    val dockHeight = 56.dp
    val dockClearance = 112.dp
    val orb = dockHeight
    val wheelRadius = 176.dp
    val wheelSize = 268.dp

    // Timeline and calendars.
    /** Wide enough for a clock time plus the raised "+1" that marks the next calendar date. */
    val timelineGutter = 50.dp
    val timelineIcon = iconSmall
    /**
     * One hour in the day, week and today grids. Fixed rather than derived from the window so the
     * same entry always looks the same, and roomy enough for a row of text plus its category mark.
     */
    val hourHeight = 60.dp
    /**
     * A block is never drawn shorter than this many minutes — the height of a third of an hour, which
     * fits one line of text — so a one-minute entry stays visible instead of becoming a sliver.
     */
    const val eventMinMinutes = 20
    /**
     * Inset a block keeps around its label: [Space.xxs] by half an hour and above, tightening evenly
     * down to this at [eventMinMinutes] so the text still fits when the block is at its shortest.
     */
    val blockInsetMin = 1.dp
    /** Nothing is ever drawn thinner than this, however little room a squeeze leaves it. */
    const val eventFloorMinutes = 10
    val compactLabelSize = 9.sp
    val compactLabelLineHeight = 11.sp
    /** Room the compact label needs; a block below it keeps only its colour. */
    val compactLabelHeight = 11.dp
    /**
     * Blocks lower than this cannot fit the base label at all, so they drop to the compact one; just
     * above it the label still fits once its inset is given up. Blocks always lose the inset first.
     */
    val compactLabelThreshold = 16.dp
    /** Inset drawn around every calendar block, so neighbouring blocks never touch. */
    val eventGap = 1.dp
    /** Fill of a record's block: it is what happened, so it carries the weight. */
    const val recordFillAlpha = .16f
    /** A plan is still only an intention: a barely-there fill with a thin outline instead. */
    const val planFillAlpha = .05f
    const val planOutlineAlpha = .5f
    /** The running timer: a record's fill, heavier, so the one still going stands out. */
    const val liveFillAlpha = .32f
    /** Both styles dim by this when the block's time has already passed. */
    const val pastBlockAlpha = .5f
    /**
     * Side inset of a block inside its lane. Two of these separate blocks drawn side by side, which
     * is wider than [eventGap] because the day and today lanes are wide enough to afford it.
     */
    val blockSideGap = 2.dp
    /** Wide enough for a clock time plus the "+1" that marks the next calendar date. */
    val agendaTime = 50.dp
    /** Gutter between the times and the card, with the node dot on its centre. */
    val agendaAxis = 12.dp
    /**
     * Vertical centre of a card's leading icon — card vertical padding [Space.sm] plus the icon's
     * own top inset [Space.xxs] plus half of [icon]. The node dot and the times line up with it.
     */
    val agendaNodeCenter = Space.sm + Space.xxs + icon / 2
    /**
     * Height of the time column. The start/end pair is centred in it, so the node dot lands on the
     * same line either way: halfway between two times, or on the single time of a moment.
     */
    val agendaTimeHeight = agendaNodeCenter * 2
    /** Gap between a start and an end time, kept tight: they read as one range, not two rows. */
    val agendaTimeGap = 2.dp
    /**
     * The week's hour gutter. Its labels are hour numbers rather than clock times, which is what lets
     * it stay this narrow and hand the width to the seven day columns — but it still has to hold the
     * marked form ("04" plus the raised marker).
     */
    val weekGutter = Space.xl
    val calendarCell = 44.dp
    val calendarDay = 36.dp
    val monthDay = 44.dp
    val monthCell = 52.dp
    val wheelItem = 32.dp
    val categoryTile = 64.dp

    // Charts.
    val chartRing = 192.dp
    val chartRingFrame = 216.dp
    val chartRingStroke = 22.dp
    val chartTrend = 150.dp
    val chartActivityCell = 40.dp
    val shareBar = 4.dp
    val percentColumn = 44.dp
    val heatmapLabel = 34.dp
    val heatmapCell = 14.dp
    val heatmapCellLarge = 18.dp
    val chartStacked = 120.dp
    val dailyChart = 88.dp
    val binChart = 64.dp
    val barWidth = 16.dp
    val barGap = 2.dp
    val barRadius = 3.dp

    /** Visible gap between rounded ring segments, in degrees. */
    const val chartGapDegrees = 2f

    // Gestures, fades and text thresholds.
    /** How far a horizontal swipe travels before it turns the page. */
    val swipeThreshold = 72.dp
    /** The height over which a scrolling area fades out at its top edge. */
    val fadeHeight = 12.dp
    /** Above this block height a timeline block prints its label on more than one line. */
    val blockLabelTall = 40.dp

    // Content bounds.
    val pageHeader = 56.dp
    val contentMaxWidth = 720.dp
    val emptyMaxWidth = 300.dp
}

object Radii {
    val control = 14.dp
    val panel = 24.dp
    val event = 8.dp
    val chart = 4.dp
}

private val smallRadius = RoundedCornerShape(Radii.control)
private val mediumRadius = RoundedCornerShape(Radii.control)
private val largeRadius = RoundedCornerShape(Radii.panel)

val ChronotaShapes = Shapes(
    extraSmall = smallRadius,
    small = smallRadius,
    medium = mediumRadius,
    large = largeRadius,
    extraLarge = largeRadius,
)

/**
 * The white sheet that carries a page's main body — the calendar, the timeline. It runs the full
 * width from the header band down past the bottom edge, with square corners: the gray above it and
 * the 1dp lift at the seam are what say the page ends and its content begins.
 */
val SheetShape = RectangleShape

/**
 * Digits keep one width — the "tnum" feature of whatever system font is running — so a column of
 * times or counts lines up and a running clock does not jitter. This is a font feature, not a
 * monospaced family: the glyphs stay the system ones.
 */
private const val TabularFigures = "tnum"

val ChronotaTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularFigures),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = TabularFigures),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularFigures),
    bodyLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TabularFigures),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontFeatureSettings = TabularFigures),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularFigures),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularFigures),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularFigures),
)
