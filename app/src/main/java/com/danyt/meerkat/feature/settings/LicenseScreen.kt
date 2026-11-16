package com.danyt.meerkat.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.danyt.meerkat.R
import com.danyt.meerkat.core.design.AppScaffold
import com.danyt.meerkat.core.design.AppTheme
import com.danyt.meerkat.core.design.GroupedRow
import com.danyt.meerkat.core.design.GroupedSection

/** Shows the project license, read from [LICENSE_NAME], and a link to the source code. */
@Composable
fun LicenseScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AppScaffold(title = stringResource(R.string.license), onTitleClick = onBack) {
        item {
            GroupedSection(footer = stringResource(R.string.license_footer)) {
                GroupedRow(title = stringResource(R.string.license), value = LICENSE_NAME)
                GroupedRow(
                    title = stringResource(R.string.license_copyright),
                    value = stringResource(R.string.license_copyright_value, COPYRIGHT_HOLDER),
                )
            }
        }
        item {
            GroupedSection {
                GroupedRow(
                    title = stringResource(R.string.license_source),
                    value = stringResource(R.string.license_source_value),
                    chevron = true,
                    // Hands the link to the user's browser. Does nothing if no app can open it.
                    onClick = { runCatching { uriHandler.openUri(PROJECT_URL) } },
                )
            }
        }
    }
}

@Preview
@Composable
private fun LicenseScreenPreview() {
    AppTheme { LicenseScreen(onBack = {}) }
}
