package com.moneykeeper.feature.auth.domain

/**
 * Callback invoked by [UnlockController] after the database is successfully opened.
 *
 * **Contract:** implementations MUST be stateless singletons (bound via Hilt multibindings).
 * Any instance that holds a reference to a ViewModel, Activity, or Fragment will cause a
 * memory leak because [UnlockController] is a singleton that outlives those objects.
 * Use a SharedFlow or a repository method if you need to signal a lifecycle-aware component.
 */
fun interface PostUnlockCallback {
    fun onUnlocked()
}
