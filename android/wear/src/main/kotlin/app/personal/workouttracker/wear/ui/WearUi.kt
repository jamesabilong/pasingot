package app.personal.workouttracker.wear.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text

private val MutedTextColor = Color(0xFFAAB9AE)

@Composable
fun PasingotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = Color(0xFF32D583),
            onPrimary = Color(0xFF052515),
            secondary = Color(0xFF32D583),
            surface = Color(0xFF18221C),
            onSurface = Color(0xFFF1F5F2),
            background = Color.Black,
            onBackground = Color(0xFFF1F5F2),
            error = Color(0xFFFFB4A9),
        ),
        content = content,
    )
}

@Composable
fun WatchPage(content: ScalingLazyListScope.() -> Unit) {
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberScalingLazyListState(initialCenterItemIndex = 0),
        autoCentering = null,
        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 30.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content,
    )
}

@Composable
fun WatchHeading(eyebrow: String, title: String, detail: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = eyebrow,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.primary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = title,
            style = MaterialTheme.typography.title3,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        if (detail != null) WatchNote(detail)
    }
}

@Composable
fun WatchNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.caption2,
        color = MutedTextColor,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun WatchAction(label: String, onClick: () -> Unit, primary: Boolean = false, enabled: Boolean = true) {
    Chip(
        onClick = onClick,
        enabled = enabled,
        label = {
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        },
        colors = if (primary) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
        // Allow long labels to wrap on small round watches and at larger font scales.
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
    )
}

@Composable
fun WatchAmbientPlaceholder(title: String, detail: String) {
    val presentation = LocalWatchPresentationPolicy.current
    val offset = if (presentation.burnInProtectionRequired) {
        ambientBurnInOffset(presentation.ambientUpdate)
    } else {
        0 to 0
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .offset(x = offset.first.dp, y = offset.second.dp)
            .padding(34.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "$title. $detail."
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("PASINGOT", style = MaterialTheme.typography.caption2, textAlign = TextAlign.Center)
        Text(title, style = MaterialTheme.typography.title3, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center)
        Text(detail, style = MaterialTheme.typography.caption1, textAlign = TextAlign.Center)
    }
}
