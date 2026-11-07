package com.frost.ears.utils

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

class IconProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val name = uri.lastPathSegment ?: return null
        if (name.isEmpty()) return null
        val file = resolve(name) ?: return null
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        } catch (e: Throwable) {
            null
        }
    }

    private fun resolve(name: String): File? {
        val dir = File(context?.filesDir, DIR_NAME)
        find(dir, name)?.let { return it }
        val alias = when {
            name.endsWith("_l") -> ALIAS_LEFT
            name.endsWith("_r") -> ALIAS_RIGHT
            else -> ALIAS_BOX
        }
        return find(dir, alias)
    }

    private fun find(dir: File, name: String): File? {
        for (ext in EXTENSIONS) {
            val file = File(dir, "$name.$ext")
            if (file.exists() && file.isFile) return file
        }
        return null
    }

    override fun getType(uri: Uri): String = "image/*"

    override fun query(
        uri: Uri, projection: Array<String>?, selection: String?,
        selectionArgs: Array<String>?, sortOrder: String?
    ): Cursor? = null

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int = 0

    override fun update(
        uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?
    ): Int = 0

    companion object {
        const val DIR_NAME = "icon"
        const val ALIAS_BOX = "box"
        const val ALIAS_LEFT = "left"
        const val ALIAS_RIGHT = "right"
        private val EXTENSIONS = listOf("png", "jpg", "jpeg", "webp", "bmp")
    }
}
