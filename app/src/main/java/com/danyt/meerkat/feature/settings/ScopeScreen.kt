package com.danyt.meerkat.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppIcons
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection
import com.danyt.meerkat.core.design.IconButton
import com.danyt.meerkat.core.design.SecondaryButton
import com.danyt.meerkat.core.design.TextField
import com.danyt.meerkat.core.net.formatAddressInput

/** The scope guard list: the ranges the user may scan without being asked first. */
@Composable
fun ScopeScreen(viewModel: ScopeViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.refreshNetworks()
        onPauseOrDispose {}
    }
    ScopeContent(state, onBack, viewModel::setInput, viewModel::add, viewModel::remove)
}

@Composable
private fun ScopeContent(
    state: ScopeUiState,
    onBack: () -> Unit,
    onInput: (String) -> Unit,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
) {
    AppScaffold(title = stringResource(R.string.scope_title), onTitleClick = onBack) {
        item {
            GroupedSection(
                header = stringResource(R.string.scope_header),
                footer = stringResource(R.string.scope_footer),
            ) {
                if (state.ranges.isEmpty()) GroupedRow(stringResource(R.string.scope_empty), enabled = false)
                state.ranges.forEach { range ->
                    GroupedRow(
                        title = range,
                        trailing = {
                            IconButton(
                                icon = AppIcons.Close,
                                contentDescription = stringResource(R.string.scope_remove, range),
                                onClick = { onRemove(range) },
                            )
                        },
                    )
                }
            }
        }
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextField(
                    value = state.input,
                    onValueChange = onInput,
                    label = stringResource(R.string.scope_add_label),
                    placeholder = stringResource(R.string.lan_target_placeholder),
                    error = stringResource(R.string.scope_add_error).takeIf { state.inputError },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done,
                        autoCorrectEnabled = false,
                    ),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    transformInput = { old, new -> formatAddressInput(old, new, allowRange = true) },
                    clearable = true,
                )
                SecondaryButton(stringResource(R.string.scope_add), onAdd, Modifier.fillMaxWidth())
            }
        }
        item {
            GroupedSection(
                header = stringResource(R.string.scope_own_header),
                footer = stringResource(R.string.scope_own_footer),
            ) {
                if (state.own.isEmpty()) GroupedRow(stringResource(R.string.scope_own_none), enabled = false)
                state.own.forEach { GroupedRow(it) }
            }
        }
    }
}

@Preview
@Composable
private fun ScopeScreenPreview() {
    AppTheme {
        ScopeContent(
            state = ScopeUiState(ranges = listOf("10.0.0.0/8", "172.16.5.1-50"), own = listOf("192.168.1.0/24")),
            onBack = {},
            onInput = {},
            onAdd = {},
            onRemove = {},
        )
    }
}
