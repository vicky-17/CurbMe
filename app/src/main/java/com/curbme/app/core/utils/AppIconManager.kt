package com.curbme.app.core.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileOutputStream

object AppIconManager {

    private const val ICONS_DIR = "app_icons"

    /**
     * Saves a Drawable app icon as a PNG image in the application's internal files directory.
     * Returns the absolute path of the saved file, or null if saving fails.
     */
    fun saveAppIcon(context: Context, packageName: String, drawable: Drawable): String? {
        return try {
            val directory = File(context.filesDir, ICONS_DIR)
            if (!directory.exists()) {
                directory.mkdirs()
            }

            val file = File(directory, "$packageName.png")

            val bitmap = when (drawable) {
                is BitmapDrawable -> drawable.bitmap
                else -> {
                    val width = if (drawable.intrinsicWidth > 0) drawable.intrinsicWidth else 96
                    val height = if (drawable.intrinsicHeight > 0) drawable.intrinsicHeight else 96
                    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bmp
                }
            }

            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Returns the saved icon File for a package if it exists in internal storage, otherwise null.
     */
    fun getSavedIconFile(context: Context, packageName: String): File? {
        val file = File(context.filesDir, "$ICONS_DIR/$packageName.png")
        return if (file.exists()) file else null
    }

    /**
     * Deletes the saved icon file for a given packageName if it exists.
     */
    fun deleteSavedIcon(context: Context, packageName: String) {
        try {
            val file = File(context.filesDir, "$ICONS_DIR/$packageName.png")
            if (file.exists()) {
                file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
