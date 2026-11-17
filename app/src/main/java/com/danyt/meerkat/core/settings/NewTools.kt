package com.danyt.meerkat.core.settings

/**
 * The result of [reconcileNewTools]: what to store.
 *
 * @property layout the dashboard layout, with the new tools added at the end. Null keeps "every tool".
 * @property known every working tool the user has now been offered.
 * @property fresh the tools that carry a "New" badge until the user opens them.
 */
data class NewToolsUpdate(val layout: List<String>?, val known: Set<String>, val fresh: Set<String>)

/**
 * Works out which tools became available since the user last opened the app, and adds them to the dashboard.
 *
 * A tool counts as new the first time it is working: just added to the app, or just finished after showing
 * "Soon". New tools go to the end of a customized dashboard and get a "New" badge. A tool the user removes
 * afterwards stays known, so it is never put back.
 *
 * @param built routes of the tools that work in this version, in catalog order.
 * @param layout the saved dashboard layout, or null when the user never changed it (then every tool shows).
 * @param known the tools offered before, or null when this has never run.
 * @param fresh the tools still marked "New".
 * @return what to store, or null when nothing changed.
 */
fun reconcileNewTools(
    built: List<String>,
    layout: List<String>?,
    known: Set<String>?,
    fresh: Set<String>,
): NewToolsUpdate? {
    // A first start of a new install: everything is new, so nothing needs pointing out.
    if (known == null && layout == null) return NewToolsUpdate(null, built.toSet(), fresh)
    // Updated from a version that did not track this: what the user already has on the dashboard is known.
    val before = known ?: layout.orEmpty().filter { it in built }.toSet()
    val added = built.filter { it !in before }
    if (added.isEmpty()) return if (known == null) NewToolsUpdate(layout, before, fresh) else null
    val newLayout = layout?.let { current -> current + added.filter { it !in current } }
    return NewToolsUpdate(newLayout, before + added, fresh + added)
}
