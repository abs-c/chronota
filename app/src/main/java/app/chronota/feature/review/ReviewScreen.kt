package app.chronota.feature.review

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.testTag
import app.chronota.R
import app.chronota.domain.*
import app.chronota.feature.WorkspaceState
import app.chronota.ui.components.*
import app.chronota.ui.theme.Space
import app.chronota.ui.theme.Metrics
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(onSettings: () -> Unit, state: WorkspaceState = WorkspaceState(), onRecord: (Long) -> Unit = {}, onPlan: (Long) -> Unit = {}, onAddRecord: (LocalDate?) -> Unit = { onRecord(0) }, onCreatePlan: (TimeSpan) -> Unit = {}, onCreateRecord: (TimeSpan) -> Unit = {}, onDeleteGoal: (Long) -> Unit = {}) {
    app.chronota.feature.browse.BrowseScreen(true, state, onPlan, onRecord, onAddRecord, onCreatePlan, onCreateRecord, statistics = {
        StatisticsView(state, onRecord, onDeleteGoal)
    })
}

