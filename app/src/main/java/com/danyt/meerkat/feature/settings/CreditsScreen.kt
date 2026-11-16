package com.danyt.meerkat.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection

/** Each library as a pair of string resources: its name, and one line about it with its license. */
private val Libraries = listOf(
    R.string.credits_compose to R.string.credits_compose_line,
    R.string.credits_androidx to R.string.credits_androidx_line,
    R.string.credits_kotlin to R.string.credits_kotlin_line,
    R.string.credits_haze to R.string.credits_haze_line,
)

/** Lists the libraries the app is built with, one line each. */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    AppScaffold(title = stringResource(R.string.credits), onTitleClick = onBack) {
        item {
            GroupedSection(header = stringResource(R.string.credits_libraries)) {
                Libraries.forEach { (name, line) ->
                    GroupedRow(title = stringResource(name), subtitle = stringResource(line))
                }
            }
        }
    }
}

@Preview
@Composable
private fun CreditsScreenPreview() {
    AppTheme { CreditsScreen(onBack = {}) }
}
