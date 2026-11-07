package com.example.filemanager.utils

import android.content.Context
import android.content.Intent
import android.net.Uri

object SafManager {

    private const val PREFS_NAME = "file_manager_saf_prefs"
    private const val KEY_SAVED_URI = "saved_saf_uri"

    fun takePersistableUriPermission(context: Context, uri: Uri) {
        val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            saveSafUri(context, uri)
        } catch (e: Exception) {
            // Some URIs might not support persistable permissions
        }
    }

    fun saveSafUri(context: Context, uri: Uri) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_SAVED_URI, uri.toString()).apply()
    }

    fun getSavedSafUri(context: Context): Uri? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uriStr = prefs.getString(KEY_SAVED_URI, null) ?: return null
        return try {
            Uri.parse(uriStr)
        } catch (e: Exception) {
            null
        }
    }
}
