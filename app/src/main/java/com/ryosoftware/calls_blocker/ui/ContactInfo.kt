package com.ryosoftware.calls_blocker.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ryosoftware.calls_blocker.Main.Companion.hasReadContactsPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactInfo(
    val photo: Bitmap? = null,
    val name: String? = null,
)

fun lookupContact(phoneNumber: String, context: Context): ContactInfo {
    if (phoneNumber.isEmpty() || ! context.hasReadContactsPermission()) return ContactInfo()

    return try {
        val lookupUri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val cursor = context.contentResolver.query(
            lookupUri,
            arrayOf(
                ContactsContract.PhoneLookup._ID,
                ContactsContract.PhoneLookup.DISPLAY_NAME
            ),
            null, null, null
        )
        cursor?.use {
            if (it.moveToFirst()) {
                val name = it.getString(
                    it.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                )
                val contactId = it.getLong(
                    it.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)
                )
                val contactUri = ContentUris.withAppendedId(
                    ContactsContract.Contacts.CONTENT_URI, contactId
                )
                val inputStream = ContactsContract.Contacts
                    .openContactPhotoInputStream(context.contentResolver, contactUri)
                val photo = inputStream?.use { BitmapFactory.decodeStream(it) }
                ContactInfo(photo = photo, name = name)
            } else {
                ContactInfo()
            }
        } ?: ContactInfo()
    } catch (_: Exception) {
        ContactInfo()
    }
}

@Composable
fun rememberContactInfo(phoneNumber: String, context: Context): ContactInfo {
    var contactInfo by remember(phoneNumber) { mutableStateOf(ContactInfo()) }

    LaunchedEffect(phoneNumber) {
        contactInfo = withContext(Dispatchers.IO) {
            lookupContact(phoneNumber, context)
        }
    }

    return contactInfo
}
