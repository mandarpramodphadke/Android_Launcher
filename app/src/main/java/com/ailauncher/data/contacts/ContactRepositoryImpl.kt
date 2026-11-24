package com.ailauncher.data.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import com.ailauncher.domain.model.ContactMatch
import com.ailauncher.domain.repository.ContactRepository
import com.ailauncher.domain.search.FuzzyMatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Looks contacts up on the phone (READ_CONTACTS) for the contact_action tool; nothing is uploaded. */
@Singleton
class ContactRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : ContactRepository {

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    private data class PhoneRow(val name: String, val number: String, val type: Int, val label: String?, val primary: Boolean)

    override suspend fun search(name: String, limit: Int): List<ContactMatch> = withContext(Dispatchers.IO) {
        if (!hasPermission() || name.isBlank()) return@withContext emptyList()
        val rows = ArrayList<PhoneRow>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        )
        runCatching {
            context.contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
                while (c.moveToNext() && rows.size < MAX_ROWS) {
                    val displayName = c.getString(0) ?: continue
                    val number = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    rows += PhoneRow(displayName, number, c.getInt(2), c.getString(3), c.getInt(4) == 1)
                }
            }
        }
        val byName = rows.groupBy { it.name }
        FuzzyMatcher.rank(name.trim().removePrefix("my ").trim(), byName.keys.toList()) { listOf(it) }
            .filter { it.second >= FuzzyMatcher.CONFIDENT_MATCH * 0.8 }
            .take(limit)
            .map { (displayName, _) ->
                // Prefer the default number, then mobile, then whatever comes first.
                val numbers = byName.getValue(displayName)
                val best = numbers.firstOrNull { it.primary }
                    ?: numbers.firstOrNull { it.type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE }
                    ?: numbers.first()
                ContactMatch(
                    displayName = displayName,
                    phoneNumber = best.number,
                    phoneLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, best.type, best.label)?.toString(),
                )
            }
    }

    private companion object {
        const val MAX_ROWS = 20_000
    }
}
