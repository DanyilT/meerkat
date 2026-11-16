package com.danyt.meerkat.core.design

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.danyt.meerkat.R

/**
 * Placeholder screen for a feature that is not built yet.
 *
 * @param title the feature name, shown as the screen title.
 * @param description one line about what the feature will do.
 */
@Composable
fun ComingSoonScreen(title: String, description: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    AppScaffold(title = title, modifier = modifier, onTitleClick = onBack) {
        item {
            EmptyState(
                title = stringResource(R.string.coming_soon),
                icon = AppIcons.Clock,
                message = description,
                modifier = Modifier.fillParentMaxHeight(0.7f),
            )
        }
    }
}

@Preview
@Composable
private fun ComingSoonScreenPreview() {
    AppTheme {
        ComingSoonScreen(title = "SSH", description = "Connect to a remote shell.", onBack = {})
    }
}
