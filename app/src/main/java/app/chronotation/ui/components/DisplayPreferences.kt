package app.chronotation.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import app.chronotation.R
import app.chronotation.data.entity.Category
import app.chronotation.data.repository.AppPreferences
import app.chronotation.domain.displayName

val LocalDisplayPreferences = staticCompositionLocalOf { AppPreferences() }

@Composable fun itemName(title: String, category: Category?): String = displayName(title, category?.name,
    LocalDisplayPreferences.current.preferCategoryName, stringResource(R.string.uncategorized))
