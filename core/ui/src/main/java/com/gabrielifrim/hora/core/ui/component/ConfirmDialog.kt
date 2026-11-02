package com.gabrielifrim.hora.core.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gabrielifrim.hora.core.ui.R
import com.gabrielifrim.hora.core.ui.theme.HoraTheme

/**
 * Confirmation avant une action irréversible (un événement, un agenda et ses
 * événements). [message] dit ce qui part, plutôt qu'un « Êtes-vous sûr ? ».
 */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    HoraDialog(
        title = title,
        onDismissRequest = onDismiss,
        actions = {
            HoraButton(stringResource(R.string.action_cancel), onClick = onDismiss, style = ButtonStyle.Text)
            HoraButton(confirmLabel, onClick = onConfirm, style = ButtonStyle.Line, contentColor = HoraTheme.colors.error)
        },
    ) {
        HoraText(message, style = HoraTheme.type.body, color = HoraTheme.colors.ink2)
    }
}
