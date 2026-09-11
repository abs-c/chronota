package app.chronotation

import app.chronotation.ui.components.CategoryIconLibrary
import app.chronotation.ui.components.propertyInputs
import app.chronotation.ui.theme.categoryColorRows
import app.chronotation.ui.theme.oklchColor
import app.chronotation.data.entity.*
import org.junit.Assert.*
import org.junit.Test

class DesignCatalogTest {
    @Test fun iconCatalogParsesEveryPathAndPreservesOldKeys() {
        val icons = CategoryIconLibrary.icons
        assertTrue(icons.size >= 100)
        assertEquals(icons.size, icons.map { it.first }.distinct().size)
        listOf("book", "code", "coffee", "star", "work", "music").forEach { key -> assertTrue(icons.any { it.first == key }) }
        icons.forEach { assertEquals(24f, it.second.viewportWidth) }
    }
    @Test fun paletteHasConsistentRowsAndAchromaticEndpoints() {
        assertEquals(14, categoryColorRows.size)
        assertTrue(categoryColorRows.all { it.size == 7 })
        assertEquals(0xFF000000, oklchColor(0.0, 0.0, 0.0))
        assertEquals(0xFFFFFFFF, oklchColor(1.0, 0.0, 0.0))
        assertTrue(categoryColorRows.flatten().all { it ushr 24 == 255L })
    }
    @Test fun explicitClearOverridesDefaultAndExistingRecordsDoNotAcquireDefaults() {
        val definition = PropertyDefinition(id = 1, categoryId = 2, name = "Format", type = PropertyType.SELECT, options = "Book\nPaper", defaultValue = "Book")
        assertEquals("Book", propertyInputs(listOf(definition), emptyMap())[1])
        assertEquals("", propertyInputs(listOf(definition), mapOf(1L to ""))[1])
        assertEquals("", propertyInputs(listOf(definition), emptyMap(), defaults = false)[1])
    }
}
