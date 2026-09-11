package app.chronota.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import app.chronota.R
import app.chronota.data.entity.Category
import app.chronota.ui.theme.*

@Composable fun CategoryTile(category: Category, modifier: Modifier = Modifier, selected: Boolean = false, click: () -> Unit) {
    Column(modifier.height(Metrics.categoryTile).clip(MaterialTheme.shapes.small)
        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).clickable(onClick = click),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CategoryMark(category)
        Spacer(Modifier.height(Space.xxs))
        Text(category.name, Modifier.fillMaxWidth(), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
@Composable fun CategoryPicker(categories: List<Category>, selected: Long?, select: (Long?) -> Unit, dismiss: () -> Unit) {
    SelectionDialog(stringResource(R.string.categories), dismiss, clear = { select(null) }) {
        categories.filter { it.parentId == null }.forEach { parent ->
            var expanded by remember(parent.id) { mutableStateOf(true) }
            OptionGroup {
                OptionRow(parent.name, if (expanded) AppIcons.Down else AppIcons.Next, { expanded = !expanded }, arrow = false)
                if (expanded) categories.filter { it.parentId == parent.id }.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Space.xxs)) {
                        row.forEach { category -> CategoryTile(category, Modifier.weight(1f), category.id == selected) { select(category.id) } }
                        repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        if (categories.none { it.parentId != null }) Text(stringResource(R.string.uncategorized), Modifier.padding(Space.md))
    }
}
