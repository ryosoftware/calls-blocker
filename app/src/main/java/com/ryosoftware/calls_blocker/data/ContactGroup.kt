package com.ryosoftware.calls_blocker.data

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission

data class ContactGroup(
    val groupId: Long,
    val title: String,
    val accountName: String,
    val memberCount: Int = 0,
)

fun fetchContactGroups(context: Context, contentResolver: ContentResolver): List<ContactGroup> {
    if (!context.hasReadContactsPermission()) return emptyList()
    val groups = mutableListOf<ContactGroup>()
    val projection = arrayOf(
        ContactsContract.Groups._ID,
        ContactsContract.Groups.TITLE,
        ContactsContract.Groups.ACCOUNT_NAME,
    )
    val sortOrder = "${ContactsContract.Groups.TITLE} ASC"
    val selection = "${ContactsContract.Groups.DELETED} = 0"
    contentResolver.query(
        ContactsContract.Groups.CONTENT_URI,
        projection,
        selection,
        null,
        sortOrder
    )?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(ContactsContract.Groups._ID)
        val titleCol = cursor.getColumnIndexOrThrow(ContactsContract.Groups.TITLE)
        val accountCol = cursor.getColumnIndexOrThrow(ContactsContract.Groups.ACCOUNT_NAME)
        while (cursor.moveToNext()) {
            val title = cursor.getString(titleCol) ?: continue
            if (title.isBlank()) continue
            val groupId = cursor.getLong(idCol)
            groups.add(
                ContactGroup(
                    groupId = groupId,
                    title = title,
                    accountName = cursor.getString(accountCol) ?: "",
                    memberCount = countGroupMembers(contentResolver, groupId),
                )
            )
        }
    }
    return groups
}

private fun countGroupMembers(contentResolver: ContentResolver, groupId: Long): Int {
    val uri = ContactsContract.Data.CONTENT_URI
    val projection = arrayOf(ContactsContract.Data._ID)
    val selection = "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?"
    val selectionArgs = arrayOf(
        groupId.toString(),
        ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE
    )
    var count = 0
    contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
        count = cursor.count
    }
    return count
}
