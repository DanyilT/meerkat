package com.danyt.meerkat.core.design

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.ui.Modifier

/** Animates a lazy list item appearing, disappearing, and moving, unless animations are turned off. */
fun Modifier.animated(scope: LazyItemScope, enabled: Boolean): Modifier =
    if (enabled) with(scope) { animateItem() } else this
