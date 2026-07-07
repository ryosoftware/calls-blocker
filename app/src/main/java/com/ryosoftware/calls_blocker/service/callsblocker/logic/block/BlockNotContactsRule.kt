package com.ryosoftware.calls_blocker.service.callsblocker.logic.block

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission
import com.ryosoftware.calls_blocker.data.SettingsManager
import com.ryosoftware.calls_blocker.data.db.Reason
import com.ryosoftware.calls_blocker.service.callsblocker.logic.AbstractBlockRule
import dagger.hilt.android.qualifiers.ApplicationContext
import jakarta.inject.Inject

class BlockNotContactsRule @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
): AbstractBlockRule {
    private fun isUnknownNumber(normalizedPhoneNumber: String): Boolean {
        if (!context.hasReadContactsPermission()) return false

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalizedPhoneNumber))

        val projection = arrayOf(ContactsContract.PhoneLookup._ID)

        context.contentResolver.query(uri, projection, null, null, null)?.use {
            return it.count == 0
        }

        return true
    }

    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockUnknown = settingsManager.blockUnknown

        if (blockUnknown) {
            if (isUnknownNumber(normalizedPhoneNumber)) {
                return Reason.NOT_A_CONTACT
            }
        }

        return Reason.NONE
    }
}