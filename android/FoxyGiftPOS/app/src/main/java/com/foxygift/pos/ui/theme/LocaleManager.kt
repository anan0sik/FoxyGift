package com.foxygift.pos.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Persists the selected UI language in SharedPreferences. */
@Singleton
class LocaleManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("foxygift_prefs", Context.MODE_PRIVATE)

    fun getLanguage(): AppLanguage =
        AppLanguage.fromCode(prefs.getString(KEY_LANG, AppLanguage.EN.code) ?: AppLanguage.EN.code)

    fun setLanguage(lang: AppLanguage) {
        prefs.edit { putString(KEY_LANG, lang.code) }
    }

    companion object {
        private const val KEY_LANG = "selected_language"
    }
}
