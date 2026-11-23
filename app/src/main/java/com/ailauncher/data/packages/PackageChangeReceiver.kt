package com.ailauncher.data.packages

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log

/**
 * Listens for ACTION_PACKAGE_ADDED / REMOVED / CHANGED / REPLACED.
 *
 * These are implicit broadcasts, which Android 8.0+ no longer delivers to manifest-declared
 * receivers, so this receiver is registered dynamically by the Application for the whole process
 * lifetime (the launcher process is long-lived). A full re-sync on start-up covers changes made
 * while the process was dead.
 */
class PackageChangeReceiver(
    private val onPackagesChanged: (action: String, packageName: String) -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val packageName = intent.data?.schemeSpecificPart ?: return
        // An update fires REMOVED(replacing) + ADDED(replacing) + REPLACED; the repository
        // debounces them into a single sync.
        Log.d(TAG, "$action $packageName replacing=${intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)}")
        onPackagesChanged(action, packageName)
    }

    companion object {
        private const val TAG = "PackageChangeReceiver"

        fun intentFilter() = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
    }
}
