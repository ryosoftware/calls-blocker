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

class BlockGroupsOfContactsRule @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
): AbstractBlockRule {
    private fun isFromBlockedGroup(normalizedPhoneNumber: String): Boolean {
        if (!context.hasReadContactsPermission()) return false

        val blockedIds = settingsManager.blockedGroupIds.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { it.toLongOrNull() }
            .toSet()

        if (blockedIds.isEmpty()) return false

        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(normalizedPhoneNumber))
        val projection = arrayOf(ContactsContract.PhoneLookup.CONTACT_ID)
        val contactId =
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            } ?: return false

        val groupProjection = arrayOf(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
        val groupSelection = "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
        val groupArgs = arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)

        return runCatching {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                groupProjection,
                groupSelection,
                groupArgs,
                null
            )?.use { cursor ->
                generateSequence { if (cursor.moveToNext()) cursor else null }
                    .any { cursor.getLong(0) in blockedIds }
            } ?: false
        }.getOrElse { false }
    }

    override suspend fun evaluate(normalizedPhoneNumber: String, phoneNumber: String, normalizeToE164: (String?) -> String, isHiddenNumber: (String?) -> Boolean): Reason {
        val blockGroups = settingsManager.blockGroups

        if (blockGroups) {
            if (isFromBlockedGroup(normalizedPhoneNumber)) {
                return Reason.MEMBER_OF_BLOCKED_GROUP_OF_CONTACTS
            }
        }

        return Reason.NONE
    }
}