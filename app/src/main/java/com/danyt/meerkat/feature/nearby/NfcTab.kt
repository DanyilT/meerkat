package com.danyt.meerkat.feature.nearby

import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.EmptyState
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.HeaderButton
import com.danyt.meerkat.core.design.Note
import com.danyt.meerkat.core.design.SecondaryButton
import com.danyt.meerkat.core.design.animated
import com.danyt.meerkat.core.design.rememberCopyText
import com.danyt.meerkat.core.nearby.CardProblem
import com.danyt.meerkat.core.nearby.NfcNote
import com.danyt.meerkat.core.nearby.NfcTag

internal class NfcActions(
    val onOpenSettings: (action: String) -> Unit,
    val onClear: () -> Unit,
    val onOpenToolSettings: () -> Unit,
    val onReread: (readAt: Long?) -> Unit,
)

/** The NFC tab: hold a tag to the device to see what it is and what it holds. Nothing is written to tags. */
internal fun LazyListScope.nfcItems(state: NfcTabState, animate: Boolean, actions: NfcActions) {
    if (!state.supported) {
        item(key = "nfc-none") {
            EmptyState(
                title = stringResource(R.string.nearby_nfc_unsupported),
                modifier = Modifier.animated(this, animate),
            )
        }
        return
    }
    if (!state.enabled) {
        item(key = "nfc-off") {
            SettingsPrompt(
                text = stringResource(R.string.nearby_nfc_off),
                button = stringResource(R.string.nearby_open_nfc_settings),
                onClick = { actions.onOpenSettings(Settings.ACTION_NFC_SETTINGS) },
                modifier = Modifier.padding(bottom = 22.dp).animated(this, animate),
            )
        }
    } else {
        item(key = "nfc-hint") {
            Column(
                modifier = NearbyInset.padding(bottom = 22.dp).animated(this, animate),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Note(stringResource(R.string.nearby_nfc_hold), icon = AppIcons.Nfc)
                Note(stringResource(R.string.nearby_nfc_payments))
            }
        }
    }
    items(state.tags, key = { tag -> "nfc-${tag.readAt}-${tag.id}" }) { tag ->
        val resources = LocalResources.current
        val copy = rememberCopyText()
        GroupedSection(
            modifier = Modifier.animated(this, animate),
            header = stringResource(R.string.nearby_nfc_read_at, clockTime(tag.readAt)),
        ) {
            // What the tag does when held to a phone, first: that is usually what one wants to know.
            GroupedRow(stringResource(R.string.nearby_nfc_does), subtitle = resources.tagPurpose(tag))
            // Notes first: the bank card one matters most.
            tag.notes.sorted().forEach { note ->
                val (title, text) = when (note) {
                    NfcNote.PaymentCard -> R.string.nearby_nfc_note_payment_title to if (state.cardDetailsAllowed) {
                        R.string.nearby_nfc_note_payment_shown
                    } else {
                        R.string.nearby_nfc_note_payment
                    }
                    NfcNote.LockedByKeys -> R.string.nearby_nfc_note_keys_title to R.string.nearby_nfc_note_keys
                    NfcNote.AppsLocked -> R.string.nearby_nfc_note_apps_title to R.string.nearby_nfc_note_apps
                }
                GroupedRow(
                    title = stringResource(title),
                    subtitle = stringResource(text),
                    icon = if (note == NfcNote.PaymentCard) AppIcons.Shield else AppIcons.Info,
                )
                if (note == NfcNote.PaymentCard) PaymentCardRows(tag, state, actions)
            }
            GroupedRow(stringResource(R.string.nearby_nfc_id), value = tag.id, onLongClick = { copy(tag.id) })
            GroupedRow(stringResource(R.string.nearby_nfc_techs), value = tag.techs.joinToString(", "))
            tag.facts.forEach { (fact, value) ->
                GroupedRow(resources.nfcFact(fact), value = value, onLongClick = { copy(value) })
            }
            tag.ndef?.let { ndef ->
                val writable = stringResource(if (ndef.writable) R.string.nearby_yes else R.string.nearby_no)
                GroupedRow(
                    title = stringResource(R.string.nearby_nfc_ndef),
                    subtitle = stringResource(
                        R.string.nearby_nfc_ndef_summary,
                        ndef.type,
                        resources.bytes(ndef.maxSize),
                        writable,
                    ),
                )
                if (ndef.records.isEmpty()) {
                    GroupedRow(
                        title = stringResource(R.string.nearby_nfc_ndef_empty),
                        titleColor = AppTheme.colors.secondaryLabel,
                    )
                }
                ndef.records.forEach { record ->
                    val (label, content) = resources.ndefRecord(record)
                    GroupedRow(label, subtitle = content, onLongClick = { copy(content) })
                }
            }
        }
    }
    if (state.tags.isNotEmpty()) {
        item(key = "nfc-clear") {
            SecondaryButton(
                text = stringResource(R.string.nearby_nfc_clear),
                onClick = actions.onClear,
                destructive = true,
                modifier = NearbyInset.fillMaxWidth().animated(this, animate),
            )
        }
    }
}

/**
 * The bank card's own rows: its number, expiry, and holder when the setting allows reading them, and a link to
 * read the card again (or to the setting, while it is off). Holding a detail copies it as sensitive.
 */
@Composable
private fun PaymentCardRows(tag: NfcTag, state: NfcTabState, actions: NfcActions) {
    val colors = AppTheme.colors
    val copySecret = rememberCopyText(sensitive = true)
    if (!state.cardDetailsAllowed) {
        GroupedRow(
            title = stringResource(R.string.nearby_nfc_show_details),
            titleColor = colors.accent,
            chevron = true,
            onClick = actions.onOpenToolSettings,
        )
        return
    }
    tag.card?.let { card ->
        card.formattedNumber?.let { number ->
            GroupedRow(
                title = stringResource(R.string.nearby_nfc_card_number),
                value = number,
                onLongClick = { copySecret(card.number.orEmpty()) },
            )
        }
        card.expiry?.let { expiry ->
            GroupedRow(
                title = stringResource(R.string.nearby_nfc_card_expiry),
                value = expiry,
                onLongClick = { copySecret(expiry) },
            )
        }
        card.holder?.let { GroupedRow(stringResource(R.string.nearby_nfc_card_holder), value = it) }
    }
    // Why the details are missing, so the user knows whether holding the card longer could help.
    if (tag.card?.number == null) {
        tag.cardProblem?.let { problem ->
            val reason = when (problem) {
                CardProblem.LEFT -> stringResource(R.string.nearby_nfc_problem_left)
                CardProblem.NO_APPLICATION -> stringResource(R.string.nearby_nfc_problem_no_app)
                CardProblem.NO_NUMBER -> stringResource(R.string.nearby_nfc_problem_no_number)
                else -> stringResource(R.string.nearby_nfc_problem_refused, problem)
            }
            GroupedRow(
                stringResource(R.string.nearby_nfc_problem),
                subtitle = reason,
                titleColor = colors.secondaryLabel,
            )
        }
    }
    if (state.rereading == tag.readAt) {
        GroupedRow(
            title = stringResource(R.string.nearby_nfc_hold_again),
            titleColor = colors.secondaryLabel,
            trailing = { HeaderButton(stringResource(R.string.action_cancel)) { actions.onReread(null) } },
        )
    } else {
        GroupedRow(
            title = stringResource(
                if (tag.card == null) R.string.nearby_nfc_read_details else R.string.nearby_nfc_read_again,
            ),
            titleColor = colors.accent,
            onClick = { actions.onReread(tag.readAt) },
        )
    }
}
