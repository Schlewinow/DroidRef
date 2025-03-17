package xyz.ruin.droidref

import android.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.fondesa.kpermissions.extension.permissionsBuilder
import com.fondesa.kpermissions.isDenied
import com.fondesa.kpermissions.isGranted
import com.fondesa.kpermissions.isPermanentlyDenied
import com.xiaopo.flying.sticker.DrawableSticker
import com.xiaopo.flying.sticker.StickerViewModel
import com.xiaopo.flying.sticker.StickerViewSerializer
import timber.log.Timber
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import kotlin.math.sqrt

/**
 * Helper class managing file operations.
 */
object RefBoardLoadSaveManager {
    const val SAVE_FILE_EXTENSION: String = "ref"

    private const val MAX_SCALE_PER_STEP_ON_IMAGE_IMPORT = 0.6f

    /**
     * Add a new image to the reference board.
     * @param file The image to be added to the reference board.
     * @param stickerViewModel View model managing the reference board.
     * @param context Local app context, usually current activity.
     */
    fun addSticker(file: Uri, stickerViewModel: StickerViewModel, context: Context) {
        val imageStream = context.contentResolver.openInputStream(file)
        val bitmap = BitmapFactory.decodeStream(imageStream)
        addSticker(bitmap, stickerViewModel, context)
    }

    /**
     * Add a new image to the reference board.
     * @param bitmap The image to be added to the reference board.
     * @param stickerViewModel View model managing the reference board.
     * @param context Local app context, usually current activity.
     */
    fun addSticker(bitmap: Bitmap?, stickerViewModel: StickerViewModel, context: Context) {
        if (bitmap == null) {
            Toast.makeText(context, "Could not decode image", Toast.LENGTH_SHORT).show()
        }
        else {
            var newBitmap: Bitmap = bitmap

            // Resize images above resolution threshold (if setting is activated).
            val bitmapResolution = bitmap.width * bitmap.height
            if (SettingsStorage.getActivateDownscaleOnImport()
                && bitmapResolution > SettingsStorage.getMaxResolutionOnImport()) {
                val shrinkFactor = sqrt(SettingsStorage.getMaxResolutionOnImport().toDouble() / bitmapResolution.toDouble()).toFloat()
                newBitmap = createScaledBitmap(bitmap, shrinkFactor, MAX_SCALE_PER_STEP_ON_IMAGE_IMPORT)
            }

            val drawable = BitmapDrawable(context.resources, newBitmap)
            stickerViewModel.addSticker(DrawableSticker(drawable))
        }
    }

    /**
     * Create a (down)scaled version of an image to improve general app performance.
     * @param sourceBitmap The image to be scaled down.
     * @param scaleFactor The new scale factor to be applied to the source image.
     * @param maxScalePerStep How much scaling is applied per step at max. Fixes an issue of jagged lines in downscaled images because of poor sampling. Should be chosen between 0.5 and 1.0.
     * @return The scaled image.
     */
    private fun createScaledBitmap(sourceBitmap: Bitmap, scaleFactor: Float, maxScalePerStep: Float) : Bitmap {
        var scaledBitmap = sourceBitmap
        var remainingScaleFactor = scaleFactor
        var continueScale = true

        // Scaling PNGs (and maybe other formats, too) down will create jagged lines if the scaling is too drastic.
        // To avoid these artifacts, scale the image in multiple steps, with each step having a scale factor above 0,5.
        // This increases the image loading time, but improves the resulting image quality significantly.
        while(continueScale) {
            var currentScaleFactor = remainingScaleFactor

            if (SettingsStorage.getActivateMultiStepScaleOnImport()
                && remainingScaleFactor < maxScalePerStep) {
                remainingScaleFactor *= 1f / maxScalePerStep
                currentScaleFactor = maxScalePerStep
            }
            else {
                continueScale = false
            }

            val oldScaledBitmap = scaledBitmap
            val targetWidth = (oldScaledBitmap.width.toFloat() * currentScaleFactor).toInt()
            val targetHeight = (oldScaledBitmap.height.toFloat() * currentScaleFactor).toInt()
            scaledBitmap = Bitmap.createScaledBitmap(oldScaledBitmap, targetWidth, targetHeight, true)
            oldScaledBitmap.recycle()
        }

        return scaledBitmap
    }

    /**
     * Write the reference board data to a file on the device's storage.
     * @param fileName Name of the file to store (without path, yet including extension).
     * @param stickerViewModel The reference board view model to be serialized.
     * @param activity Current activity.
     */
    fun saveRefBoard(fileName: String, stickerViewModel: StickerViewModel, activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            val permissionRequest = activity.permissionsBuilder(Manifest.permission.WRITE_EXTERNAL_STORAGE).build()
            permissionRequest.addListener { results ->
                val result = results[0]
                if (result.isGranted()) {
                    saveRefBoardLegacy(fileName, getGlobalDocumentsDirectory(activity), stickerViewModel, activity)
                }
                else if (result.isPermanentlyDenied()) {
                    Toast.makeText(activity, R.string.main_info_ref_board_save_permission_denied_permanently, Toast.LENGTH_LONG).show()
                }
                else if (result.isDenied()) {
                    Toast.makeText(activity, R.string.main_info_ref_board_save_permission_denied, Toast.LENGTH_LONG).show()
                }
            }
            permissionRequest.send()
        }
        else {
            saveRefBoardMediaStore(fileName, stickerViewModel, activity)
        }
    }

    /**
     * Save reference board using media storage API.
     * @param fileName Name of the file to store (without path, yet including extension).
     * @param stickerViewModel The reference board view model to be serialized.
     * @param context Local app context, usually current activity.
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun saveRefBoardMediaStore(fileName: String, stickerViewModel: StickerViewModel, context: Context) {
        val contentResolver = context.contentResolver
        val contentValues = ContentValues()

        // Only files with MIME-type images/* are allowed to be stored in images folder.
        // So use "Documents" (or potentially "Downloads") as base directory instead.
        contentValues.put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
        contentValues.put(MediaStore.Files.FileColumns.MIME_TYPE, "application/octet-stream")
        val relativePath = Environment.DIRECTORY_DOCUMENTS + File.separator + context.resources.getString(R.string.app_name)
        contentValues.put(MediaStore.Files.FileColumns.RELATIVE_PATH, relativePath)

        try {
            val uri = contentResolver.insert(MediaStore.Files.getContentUri("external"), contentValues)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use {
                        outputStream -> StickerViewSerializer().serialize(stickerViewModel, BufferedOutputStream(outputStream))
                }
                Toast.makeText(
                    context,
                    context.getString(R.string.main_info_ref_board_saved, relativePath + File.separator + fileName),
                    Toast.LENGTH_SHORT).show()
            }
        }
        catch (ex: Exception) {
            Timber.e(ex, "Error writing %s", fileName)
            Toast.makeText(context, context.getString(R.string.main_info_ref_board_save_error, fileName), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Save reference board using the legacy approach.
     * @param fileName Name of the file to store (without path, yet including extension).
     * @param saveDir Directory at which so store the file. Depending on the directory, permissions may be required.
     * @param stickerViewModel The reference board view model to be serialized.
     * @param context Local app context, usually current activity.
     */
    private fun saveRefBoardLegacy(fileName: String, saveDir: File, stickerViewModel: StickerViewModel, context: Context) {
        val file = File(saveDir, fileName)

        try {
            saveDir.mkdirs()
            StickerViewSerializer().serialize(stickerViewModel, file)
            stickerViewModel.currentFileName = fileName
            Toast.makeText(context, context.getString(R.string.main_info_ref_board_saved, file.toString()), Toast.LENGTH_SHORT).show()
        }
        catch (e: IOException) {
            Timber.e(e, "Error writing %s", file)
            Toast.makeText(context, context.getString(R.string.main_info_ref_board_save_error, file.toString()), Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Getter device documents storage directory.
     * Requires permissions and/or special access.
     * @param context Local app context, usually current activity.
     */
    private fun getGlobalDocumentsDirectory(context: Context): File {
        // Older DroidRef versions used the "Pictures" directory, but that is not possible in the new API.
        // To keep file access consistent, reference boards are now stored in "Documents".
        return File(
            listOf(
                Environment.getExternalStorageDirectory().absolutePath,
                Environment.DIRECTORY_DOCUMENTS,
                context.resources.getString(R.string.app_name)
            ).joinToString(File.separator)
        )
    }

    /**
     * Load the contents of a reference board file back into the app.
     * @param file The file containing the reference board data.
     * @param stickerViewModel The reference board view model to deserialize the file contents into.
     * @param context Local app context, usually current activity.
     */
    fun loadRefBoard(file: Uri, stickerViewModel: StickerViewModel, context: Context) {
        val fileName = getFileNameOfUri(file, context.contentResolver)
        val extension = File(fileName).extension
        if (extension != SAVE_FILE_EXTENSION) {
            Toast.makeText(
                context,
                "File does not have '.$SAVE_FILE_EXTENSION' extension",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val stream = context.contentResolver.openInputStream(file)!!
            StickerViewSerializer().deserialize(stickerViewModel, stream, context.resources)
            stickerViewModel.currentFileName = fileName
        }
        catch (e: IOException) {
            // Unable to create file, likely because external storage is not currently mounted.
            Timber.e(e, "Error writing %s", file)
            Toast.makeText(context, "Error reading $file", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Internal helper to get the file name of a Uri.
     */
    private fun getFileNameOfUri(uri: Uri, contentResolver: ContentResolver): String {
        var result: String? = null

        if (uri.scheme.equals("content")) {
            val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    var cursorColumnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (cursorColumnIndex < 0) {
                        cursorColumnIndex = 0
                    }
                    result = cursor.getString(cursorColumnIndex)
                }
            }
            finally {
                cursor?.close()
            }
        }

        if (result == null) {
            result = uri.path
            val cut = result!!.lastIndexOf('/')
            if (cut != -1) {
                result = result.substring(cut + 1)
            }
        }

        return result
    }
}