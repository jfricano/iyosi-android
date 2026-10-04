package com.orcasolutions.iyosi.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import com.orcasolutions.iyosi.data.AdvisoryStatus
import com.orcasolutions.iyosi.data.PlaceKind

/** The city-guide palette shared with the approved landing page, with adaptive dark values. */
@Immutable
data class GuidePalette(
    val paper: Color,
    val surface: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val teal: Color,
    val tealInk: Color,
    val tealPale: Color,
    val amber: Color,
    val amberInk: Color,
    val amberPale: Color,
    val warning: Color,
    val warningPale: Color,
)

val LightGuide = GuidePalette(
    paper = Color(0xFFF6F1E8), surface = Color(0xFFFFFEFA), ink = Color(0xFF192B35), muted = Color(0xFF626E73),
    line = Color(0xFFD8D6CD), teal = Color(0xFF147D79), tealInk = Color(0xFF116F6B), tealPale = Color(0xFFD9EEEA),
    amber = Color(0xFFAE651E), amberInk = Color(0xFF965C1E), amberPale = Color(0xFFF7E8CF),
    warning = Color(0xFFAA483D), warningPale = Color(0xFFF7E2DC),
)

val DarkGuide = GuidePalette(
    paper = Color(0xFF142630), surface = Color(0xFF20343E), ink = Color(0xFFF6F1E8), muted = Color(0xFFB7C9C9),
    line = Color(0xFF49606A), teal = Color(0xFFA7D6C9), tealInk = Color(0xFFA7D6C9), tealPale = Color(0xFF294A48),
    amber = Color(0xFFEDC86B), amberInk = Color(0xFFEDC86B), amberPale = Color(0xFF493B27),
    warning = Color(0xFFFFADA0), warningPale = Color(0xFF4A302E),
)

val LocalGuide = staticCompositionLocalOf { LightGuide }

object Guide {
    val colors: GuidePalette @Composable get() = LocalGuide.current

    fun heading(size: TextUnit, tracking: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontWeight = FontWeight.Bold, fontSize = size, letterSpacing = tracking, lineHeight = size * 1.2f)

    fun editorial(size: TextUnit) = TextStyle(fontFamily = FontFamily.Serif, fontSize = size, lineHeight = size * 1.2f)

    fun body(size: TextUnit = 16.sp) = TextStyle(fontSize = size, lineHeight = size * 1.4f)
}

@Composable
fun GuideTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val palette = if (dark) DarkGuide else LightGuide
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.tealInk, onPrimary = palette.paper, background = palette.paper, onBackground = palette.ink,
            surface = palette.surface, onSurface = palette.ink, surfaceVariant = palette.surface,
            onSurfaceVariant = palette.muted, outline = palette.line, error = palette.warning,
            surfaceContainerLow = palette.surface, surfaceContainer = palette.surface, surfaceContainerHigh = palette.surface,
        )
    } else {
        lightColorScheme(
            primary = palette.tealInk, onPrimary = palette.paper, background = palette.paper, onBackground = palette.ink,
            surface = palette.surface, onSurface = palette.ink, surfaceVariant = palette.surface,
            onSurfaceVariant = palette.muted, outline = palette.line, error = palette.warning,
            surfaceContainerLow = palette.surface, surfaceContainer = palette.surface, surfaceContainerHigh = palette.surface,
        )
    }
    CompositionLocalProvider(LocalGuide provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

val PlaceKind.icon: ImageVector
    get() = when (this) {
        PlaceKind.PublicDesignated -> Icons.Filled.Verified
        PlaceKind.VenueDesignated -> Icons.Filled.Business
        PlaceKind.InformalOutdoor -> Icons.Filled.Park
        PlaceKind.VenueClaim -> Icons.Filled.QuestionMark
    }

val AdvisoryStatus.icon: ImageVector
    get() = when (this) {
        AdvisoryStatus.VerifiedDesignated -> Icons.Filled.Check
        AdvisoryStatus.CommunityReported -> Icons.Filled.Diamond
        AdvisoryStatus.Disputed -> Icons.Filled.Warning
        AdvisoryStatus.Prohibited -> Icons.Filled.Block
        AdvisoryStatus.Closed -> Icons.Filled.Close
    }

@Composable
fun AdvisoryStatus.color(): Color = when (this) {
    AdvisoryStatus.VerifiedDesignated -> Guide.colors.teal
    AdvisoryStatus.CommunityReported -> Guide.colors.amber
    else -> Guide.colors.warning
}

@Composable
fun AdvisoryStatus.background(): Color = when (this) {
    AdvisoryStatus.VerifiedDesignated -> Guide.colors.tealPale
    AdvisoryStatus.CommunityReported -> Guide.colors.amberPale
    else -> Guide.colors.warningPale
}

@Composable
fun AdvisoryStatus.textColor(): Color = when (this) {
    AdvisoryStatus.VerifiedDesignated -> Guide.colors.tealInk
    AdvisoryStatus.CommunityReported -> Guide.colors.amberInk
    else -> Guide.colors.warning
}

/** Native drawing of the landing page favicon pin, including its open center. */
fun guidePinPath(width: Float, height: Float): Path {
    val sx = width / 64f
    val sy = height / 64f
    return Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(32 * sx, 8 * sy)
        cubicTo(19 * sx, 8 * sy, 9 * sx, 18 * sy, 9 * sx, 31 * sy)
        cubicTo(9 * sx, 49 * sy, 32 * sx, 59 * sy, 32 * sx, 59 * sy)
        cubicTo(32 * sx, 59 * sy, 55 * sx, 49 * sy, 55 * sx, 31 * sy)
        cubicTo(55 * sx, 18 * sy, 45 * sx, 8 * sy, 32 * sx, 8 * sy)
        close()
        addOval(androidx.compose.ui.geometry.Rect(Offset(23 * sx, 21 * sy), androidx.compose.ui.geometry.Size(18 * sx, 18 * sy)))
    }
}

@Composable
fun GuideWordmark(size: TextUnit = 30.sp, showPin: Boolean = true) {
    val colors = Guide.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.clearAndSetSemantics { contentDescription = "iYosi" },
    ) {
        if (showPin) {
            val height = (size.value).dp
            Canvas(Modifier.size(width = height * 0.8f, height = height)) {
                drawPath(guidePinPath(this.size.width, this.size.height), colors.teal)
            }
        }
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = colors.ink)) { append("iYosi") }
                withStyle(SpanStyle(color = colors.teal)) { append(".") }
            },
            style = Guide.heading(size, tracking = (-0.04).em),
        )
    }
}

@Composable
fun GuideEyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = Guide.heading(11.sp, tracking = 0.14.em),
        color = Guide.colors.tealInk,
        modifier = modifier,
    )
}

@Composable
fun GuideStatusBadge(status: AdvisoryStatus) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .background(status.background(), RoundedCornerShape(5.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(status.icon, contentDescription = null, tint = status.textColor(), modifier = Modifier.size(16.dp))
        Text(status.title, style = Guide.heading(13.sp), color = status.textColor())
    }
}

@Composable
fun GuideFact(title: String, value: String, icon: ImageVector) {
    val colors = Guide.colors
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.semantics(mergeDescendants = true) {},
    ) {
        Box(
            Modifier.size(34.dp).background(colors.tealPale, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.teal, modifier = Modifier.size(18.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title.uppercase(), style = Guide.heading(10.sp, tracking = 0.12.em), color = colors.muted)
            Text(value, style = Guide.body(15.sp), color = colors.ink)
        }
    }
}

@Composable
fun GuideNotice(text: String, icon: ImageVector = Icons.Outlined.Info, warning: Boolean = false, modifier: Modifier = Modifier) {
    val colors = Guide.colors
    val tint = if (warning) colors.warning else colors.muted
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .background(if (warning) colors.warningPale else colors.surface, RoundedCornerShape(8.dp))
            .padding(14.dp)
            .semantics(mergeDescendants = true) {},
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(text, style = Guide.body(14.sp), color = tint)
    }
}

@Composable
fun GuideButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondary: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val colors = Guide.colors
    val shape = RoundedCornerShape(7.dp)
    val padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    if (secondary) {
        OutlinedButton(
            onClick = onClick, enabled = enabled, shape = shape, contentPadding = padding,
            border = BorderStroke(1.dp, colors.line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = colors.surface, contentColor = colors.ink),
            modifier = modifier.heightIn(min = 48.dp),
        ) { ProvideTextStyleRow(content) }
    } else {
        Button(
            onClick = onClick, enabled = enabled, shape = shape, contentPadding = padding,
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.ink, contentColor = colors.paper,
                disabledContainerColor = colors.ink.copy(alpha = 0.45f), disabledContentColor = colors.paper,
            ),
            modifier = modifier.heightIn(min = 48.dp),
        ) { ProvideTextStyleRow(content) }
    }
}

@Composable
private fun RowScope.ProvideTextStyleRow(content: @Composable RowScope.() -> Unit) {
    androidx.compose.material3.ProvideTextStyle(Guide.heading(15.sp)) { content() }
}

fun Modifier.guideCard(surface: Color, line: Color, padding: Dp = 18.dp) = this
    .fillMaxWidth()
    .background(surface, RoundedCornerShape(12.dp))
    .border(1.dp, line, RoundedCornerShape(12.dp))
    .padding(padding)

@Composable
fun GuideCard(modifier: Modifier = Modifier, spacing: Dp = 14.dp, content: @Composable () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(spacing),
        modifier = modifier.guideCard(Guide.colors.surface, Guide.colors.line),
    ) { content() }
}
