package app.chronota

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Build
import java.util.Locale

fun localizedContext(context: Context, language: String): Context {
    val locale = if (language.isNotEmpty()) Locale.forLanguageTag(language)
        else if (Build.VERSION.SDK_INT >= 33) context.getSystemService(LocaleManager::class.java).systemLocales[0]
            ?: Resources.getSystem().configuration.locales[0]
        else Resources.getSystem().configuration.locales[0]
    return context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(locale) })
}
