package app.chronota.ui.theme

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import app.chronota.feature.today.TodayScreen

@Preview(showBackground = true, locale = "en", widthDp = 360, heightDp = 800)
@Composable
private fun LightPreview() {
    ChronotaTheme(dark = false) {
        TodayScreen(onSettings = {})
    }
}

@Preview(showBackground = true, locale = "zh-rCN", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 360, heightDp = 800)
@Composable
private fun DarkPreview() {
    ChronotaTheme(dark = true) {
        TodayScreen(onSettings = {})
    }
}
