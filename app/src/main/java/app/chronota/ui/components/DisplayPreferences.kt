package app.chronota.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.chronota.R
import app.chronota.data.entity.Category
import app.chronota.data.repository.AppPreferences
import app.chronota.domain.displayName

val LocalDisplayPreferences = staticCompositionLocalOf { AppPreferences() }

@Composable fun itemName(title: String, category: Category?): String = displayName(title, category?.name,
    LocalDisplayPreferences.current.preferCategoryName, stringResource(R.string.uncategorized))
