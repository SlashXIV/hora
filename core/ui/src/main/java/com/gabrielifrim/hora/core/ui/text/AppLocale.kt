package com.gabrielifrim.hora.core.ui.text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.gabrielifrim.hora.core.ui.R
import java.util.Locale

/**
 * Langue des dates et des jours : celle des textes de l'app, pas celle du
 * téléphone. Hora n'est qu'en français ; sur un téléphone en anglais, on aurait
 * sinon « Monday · semaine 45 ». Une traduction future fixe sa propre valeur.
 */
@Composable
fun rememberAppLocale(): Locale {
    val tag = stringResource(R.string.app_locale)
    return remember(tag) { Locale.forLanguageTag(tag) }
}
