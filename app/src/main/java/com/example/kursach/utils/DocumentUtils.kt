package com.example.kursach.utils

import android.content.Context
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog

object DocumentUtils {

    fun showDocumentDialog(context: Context, @StringRes titleRes: Int, @RawRes contentRes: Int) {
        val content = context.resources.openRawResource(contentRes).bufferedReader().use { it.readText() }
        AlertDialog.Builder(context)
            .setTitle(titleRes)
            .setMessage(content)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }
}









