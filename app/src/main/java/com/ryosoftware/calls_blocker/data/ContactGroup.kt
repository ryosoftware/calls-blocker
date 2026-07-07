package com.ryosoftware.calls_blocker.data

import android.content.ContentResolver
import android.content.Context
import android.provider.ContactsContract
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission

data class ContactGroup(
    val groupId: Long,
    val title: String,
    val memberCount: Int = 0,
)

fun fetchContactGroups(context: Context, contentResolver: ContentResolver): List<ContactGroup> {
    if (!context.hasReadContactsPermission()) return emptyList()
    val groups = mutableListOf<ContactGroup>()
    val projection = arrayOf(
        ContactsContract.Groups._ID,
        ContactsContract.Groups.TITLE,
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
        while (cursor.moveToNext()) {
            val title = cursor.getString(titleCol) ?: continue
            if (title.isBlank()) continue
            val groupId = cursor.getLong(idCol)
            groups.add(ContactGroup(groupId = groupId, title = title))
        }
    }
    if (groups.isEmpty()) return groups
    val counts = countGroupMembersBatch(contentResolver, groups.map { it.groupId })
    return groups.map { it.copy(memberCount = counts[it.groupId] ?: 0) }
}

private fun countGroupMembersBatch(contentResolver: ContentResolver, groupIds: List<Long>): Map<Long, Int> {
    val placeholders = groupIds.joinToString(",") { "?" }
    val uri = ContactsContract.Data.CONTENT_URI
    val projection = arrayOf(ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID)
    val selection = "${ContactsContract.CommonDataKinds.GroupMembership.GROUP_ROW_ID} IN ($placeholders) AND ${ContactsContract.Data.MIMETYPE} = ?"
    val selectionArgs = groupIds.map { it.toString() }.toTypedArray() +
        arrayOf(ContactsContract.CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
    val countsMap = mutableMapOf<Long, Int>()
    contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
        while (cursor.moveToNext()) {
            val groupId = cursor.getLong(0)
            countsMap[groupId] = (countsMap[groupId] ?: 0) + 1
        }
    }
    return countsMap
}
