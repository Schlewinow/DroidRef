package xyz.ruin.droidref

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.xiaopo.flying.sticker.*
import com.xiaopo.flying.sticker.StickerView.OnStickerOperationListener
import com.xiaopo.flying.sticker.iconEvents.CropIconEvent
import com.xiaopo.flying.sticker.iconEvents.DeleteIconEvent
import com.xiaopo.flying.sticker.iconEvents.FlipHorizontallyEvent
import com.xiaopo.flying.sticker.iconEvents.FlipVerticallyEvent
import com.xiaopo.flying.sticker.iconEvents.RotateLeftEvent
import com.xiaopo.flying.sticker.iconEvents.RotateRightEvent
import com.xiaopo.flying.sticker.iconEvents.ZoomIconEvent
import timber.log.Timber
import xyz.ruin.droidref.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var stickerViewModel: StickerViewModel
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.plant(Timber.DebugTree())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        stickerViewModel = ViewModelProvider(this).get(StickerViewModel::class.java)
        stickerViewModel.stickerOperationListener = MyStickerOperationListener(binding)
        binding.viewModel = stickerViewModel
        binding.executePendingBindings()
        binding.lifecycleOwner = this

        setupScaleStickerIcons()
        setupRotateStickerIcons()
        setupCropStickerIcons()
        setupTopButtons()
        setupBottomButtons()

        if (stickerViewModel.stickers.value!!.isEmpty()) {
            // Marker at the center (0,0) of the coordinate system for better user navigation experience.
            val centerMarkerSticker = DrawableSticker(
                ResourcesCompat.getDrawable(resources, R.drawable.marker_center, null))
            centerMarkerSticker.setAlpha(64)
            centerMarkerSticker.isEditable = false
            stickerViewModel.addSticker(centerMarkerSticker)
        }

        // When the app starts, set locked edit mode as default.
        lockEditMode()

        handleIntent(intent)
        intent.type = null // Don't run again if rotated/etc.
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        when {
            intent?.action == Intent.ACTION_SEND -> {
                if (intent.type?.startsWith("image/") == true) {
                    handleSendImage(intent)
                } else if (intent.type == "text/plain") {
                    handleSendLink(intent)
                }
            }
            intent?.action == Intent.ACTION_SEND_MULTIPLE
                    && intent.type?.startsWith("image/") == true -> {
                handleSendMultipleImages(intent)
            }
        }
    }

    private fun handleSendImage(intent: Intent) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let(this@MainActivity::doAddSticker)
    }

    private fun handleSendMultipleImages(intent: Intent) {
        intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.let { images ->
            images.forEach {
                (it as? Uri)?.let(this@MainActivity::doAddSticker)
            }
        }
    }

    private fun isValidUrl(text: String) = Patterns.WEB_URL.matcher(text).matches()

    private fun handleSendLink(intent: Intent) {
        requestPermission(Manifest.permission.INTERNET)
        requestPermission(Manifest.permission.ACCESS_NETWORK_STATE)

        if (hasPermission(Manifest.permission.INTERNET)
            && hasPermission(Manifest.permission.ACCESS_NETWORK_STATE))
         {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)!!
            if (!isValidUrl(text)) {
                Toast.makeText(this, "Invalid link", Toast.LENGTH_LONG).show()
            }

            FetchImageFromLinkTask(text, this).execute()
        }
    }

    /**
     * It's possible to override the sticker action icons.
     * Create custom actions for the scale sticker icons.
     */
    private fun setupScaleStickerIcons() {
        val deleteIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.sticker_ic_close_white_18dp,
            BitmapStickerIcon.LEFT_TOP,
            DeleteIconEvent())

        val zoomIcon = setupStickerIcon(
            R.drawable.icon_scale,
            BitmapStickerIcon.RIGHT_BOTTOM,
            ZoomIconEvent())

        val flipIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.sticker_ic_flip_white_18dp,
            BitmapStickerIcon.RIGHT_TOP,
            FlipHorizontallyEvent())

        val flipVerticallyIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.sticker_ic_flip_vert_white_18dp,
            BitmapStickerIcon.LEFT_BOTTOM,
            FlipVerticallyEvent())

        val scaleVerticalIcon = setupStickerIcon(
            R.drawable.icon_scale_vertical,
            BitmapStickerIcon.BOTTOM_CENTER,
            ZoomIconEvent(false, true))

        val scaleHorizontalIcon = setupStickerIcon(
            R.drawable.icon_scale_horizontal,
            BitmapStickerIcon.RIGHT_CENTER,
            ZoomIconEvent(true, false))

        stickerViewModel.icons.value = arrayListOf(
            deleteIcon,
            zoomIcon,
            flipIcon,
            flipVerticallyIcon,
            scaleVerticalIcon,
            scaleHorizontalIcon
        )
        stickerViewModel.activeIcons.value = stickerViewModel.icons.value
    }

    private fun setupRotateStickerIcons() {
        val deleteIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.sticker_ic_close_white_18dp,
            BitmapStickerIcon.LEFT_TOP,
            DeleteIconEvent())

        val rotateIcon = setupStickerIcon(
            R.drawable.icon_rotate_circle,
            BitmapStickerIcon.RIGHT_BOTTOM,
            ZoomIconEvent())

        val rotateLeftIcon = setupStickerIcon(
            R.drawable.icon_rotate_left,
            BitmapStickerIcon.LEFT_BOTTOM,
            RotateLeftEvent())

        val rotateRightIcon = setupStickerIcon(
            R.drawable.icon_rotate_right,
            BitmapStickerIcon.RIGHT_TOP,
            RotateRightEvent())

        stickerViewModel.rotateIcons.value = arrayListOf(
            deleteIcon,
            rotateIcon,
            rotateLeftIcon,
            rotateRightIcon
        )
    }

    private fun setupCropStickerIcons() {
        val cropDiagonalLeftTopIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.scale_1,
            BitmapStickerIcon.LEFT_TOP,
            CropIconEvent(BitmapStickerIcon.LEFT_TOP))

        val cropDiagonalRightTopIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.scale_2,
            BitmapStickerIcon.RIGHT_TOP,
            CropIconEvent(BitmapStickerIcon.RIGHT_TOP))

        val cropDiagonalLeftBottomIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.scale_2,
            BitmapStickerIcon.LEFT_BOTTOM,
            CropIconEvent(BitmapStickerIcon.LEFT_BOTTOM))

        val cropDiagonalRightBottomIcon = setupStickerIcon(
            com.xiaopo.flying.sticker.R.drawable.scale_1,
            BitmapStickerIcon.RIGHT_BOTTOM,
            CropIconEvent(BitmapStickerIcon.RIGHT_BOTTOM))

        val cropVerticalTopIcon = setupStickerIcon(
            R.drawable.icon_crop_vertical,
            BitmapStickerIcon.TOP_CENTER,
            CropIconEvent(BitmapStickerIcon.TOP_CENTER))

        val cropVerticalBottomIcon = setupStickerIcon(
            R.drawable.icon_crop_vertical,
            BitmapStickerIcon.BOTTOM_CENTER,
            CropIconEvent(BitmapStickerIcon.BOTTOM_CENTER))

        val cropHorizontalLeftIcon = setupStickerIcon(
            R.drawable.icon_crop_horizontal,
            BitmapStickerIcon.LEFT_CENTER,
            CropIconEvent(BitmapStickerIcon.LEFT_CENTER))

        val cropHorizontalRightIcon = setupStickerIcon(
            R.drawable.icon_crop_horizontal,
            BitmapStickerIcon.RIGHT_CENTER,
            CropIconEvent(BitmapStickerIcon.RIGHT_CENTER))

        stickerViewModel.cropIcons.value = arrayListOf(
            cropDiagonalLeftTopIcon,
            cropDiagonalRightTopIcon,
            cropDiagonalLeftBottomIcon,
            cropDiagonalRightBottomIcon,
            cropVerticalTopIcon,
            cropVerticalBottomIcon,
            cropHorizontalLeftIcon,
            cropHorizontalRightIcon
        )
    }

    private fun setupStickerIcon(drawableID: Int, gravity: Int, event: StickerIconEvent): BitmapStickerIcon {
        val stickerIcon = BitmapStickerIcon(
            ContextCompat.getDrawable(
                this,
                drawableID
            ),
            gravity
        )
        stickerIcon.iconEvent = event
        return stickerIcon
    }

    private fun setupTopButtons() {
        binding.buttonOpen.setOnClickListener { load() }

        binding.buttonSave.setOnClickListener { save() }

        binding.buttonSaveAs.setOnClickListener { saveAs() }

        binding.buttonNew.setOnClickListener {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Confirm")
                .setMessage("Are you sure you want to create a new board?")
                .setPositiveButton("Yes") { _, _ -> newBoard() }
                .setNegativeButton("No", null)
                .show()
        }

        binding.buttonCropAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle("Confirm")
                .setMessage("Are you sure you want to crop all images? This will permanently modify the images to match their cropped areas, but it can save  space and improve performance.")
                .setPositiveButton("Yes") { _, _ -> cropAll() }
                .setNegativeButton("No", null)
                .show()
        }

        binding.buttonHideShowUI.setOnCheckedChangeListener { _, isToggled ->
            setUIVisibility(isToggled)
        }
    }

    private fun setupBottomButtons() {
        binding.buttonAdd.setOnClickListener {
            addSticker()
        }

        binding.buttonReset.setOnClickListener {
            stickerViewModel.resetView()
        }

        binding.buttonDuplicate.setOnClickListener {
            stickerViewModel.duplicateCurrentSticker()
        }

        binding.buttonLock.setOnCheckedChangeListener { _, isToggled ->
            if (isToggled) {
                // The change listener is also called if another button is clicked.
                // So activate this only if the button actually changed its toggle.
                lockEditMode()
            }
            else {
                // The lock button does not un-toggle itself, as it is the default mode.
                // It is instead un-toggled once one of the edit modes is activated.
                if (!binding.buttonScale.isChecked
                    && !binding.buttonCrop.isChecked
                    && !binding.buttonRotate.isChecked) {
                    binding.buttonLock.isChecked = true
                }
            }
        }

        binding.buttonScale.setOnCheckedChangeListener { _, isToggled ->
            lockEditMode()
            if (isToggled) {
                setScaleMode()
            }
        }

        binding.buttonResetScale.setOnClickListener {
            stickerViewModel.resetCurrentStickerZoom()
        }

        binding.buttonCrop.setOnCheckedChangeListener { _, isToggled ->
            lockEditMode()
            if (isToggled) {
                setCropEditMode()
            }
        }

        binding.buttonResetCrop.setOnClickListener {
            stickerViewModel.resetCurrentStickerCropping()
        }

        binding.buttonRotate.setOnCheckedChangeListener { _, isToggled ->
            lockEditMode()
            if (isToggled) {
                setRotationEditMode()
            }
        }

        binding.buttonResetRotation.setOnClickListener {
            stickerViewModel.resetCurrentStickerRotation()
        }
    }

    private fun lockEditMode() {
        // If no edit mode is active, reset to locked mode as default.
        binding.buttonLock.isChecked = true
        stickerViewModel.isLocked.value = true

        binding.buttonScale.isChecked = false

        binding.buttonCrop.isChecked = false
        stickerViewModel.isCropActive.value = false

        binding.buttonRotate.isChecked = false
        stickerViewModel.rotationEnabled.value = false
    }

    private fun setScaleMode() {
        // Scale mode is the default edit mode, so there is no special flag.
        // It's achieved by just being in edit mode without being locked.
        binding.buttonScale.isChecked = true

        binding.buttonLock.isChecked = false
        stickerViewModel.isLocked.value = false
    }

    private fun setCropEditMode() {
        binding.buttonCrop.isChecked = true
        stickerViewModel.isCropActive.value = true

        binding.buttonLock.isChecked = false
        stickerViewModel.isLocked.value = false
    }

    private fun setRotationEditMode() {
        binding.buttonRotate.isChecked = true
        stickerViewModel.rotationEnabled.value = true

        binding.buttonLock.isChecked = false
        stickerViewModel.isLocked.value = false
    }

    private fun setUIVisibility(isToggled: Boolean) {
        if (isToggled) {
            binding.toolbarLayout.visibility = View.GONE
            val top = ContextCompat.getDrawable(this, R.drawable.ic_baseline_visibility_off_24)
            binding.buttonHideShowUI.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null)
        } else {
            binding.toolbarLayout.visibility = View.VISIBLE
            val top = ContextCompat.getDrawable(this, R.drawable.ic_baseline_visibility_24)
            binding.buttonHideShowUI.setCompoundDrawablesWithIntrinsicBounds(null, top, null, null)
        }
    }

    private fun save() {
        if (stickerViewModel.currentFileName != null) {
            doSave(stickerViewModel.currentFileName!!)
        } else {
            saveAs()
        }
    }

    private fun saveAs() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Choose File Name")

        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val defaultFileName: String =
            formatter.format(Calendar.getInstance().time) + "." + SAVE_FILE_EXTENSION

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(defaultFileName, TextView.BufferType.EDITABLE)
        builder.setView(input)

        builder.setPositiveButton(
            "OK"
        ) { dialog, which -> doSave(input.text.toString()) }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }

        builder.show()
    }

    private fun getSaveDirectory() =
        File(
            listOf(
                Environment.getExternalStorageDirectory().absolutePath,
                Environment.DIRECTORY_PICTURES,
                resources.getString(R.string.app_name)
            ).joinToString(File.separator)
        )

    private fun doSave(fileName: String) {
        requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            val saveDir = getSaveDirectory()
            val file = File(saveDir, fileName)

            try {
                saveDir.mkdirs()
                StickerViewSerializer().serialize(stickerViewModel, file)
                stickerViewModel.currentFileName = fileName
                Toast.makeText(this, "Saved to $file", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Timber.e(e, "Error writing %s", file)
                Toast.makeText(this, "Error writing $file", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun load() {
        requestPermission(Manifest.permission.READ_EXTERNAL_STORAGE)

        if (hasPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
            val intent = Intent()
            intent.type = "*/*"
            intent.action = Intent.ACTION_GET_CONTENT
            startActivityForResult(
                Intent.createChooser(intent, "Open Saved File"),
                INTENT_PICK_SAVED_FILE
            )
        }
    }

    private fun getFileNameOfUri(uri: Uri): String {
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
            } finally {
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

    private fun doLoad(file: Uri) {
        val fileName = getFileNameOfUri(file)
        val extension = File(fileName).extension
        if (extension != SAVE_FILE_EXTENSION) {
            Toast.makeText(
                this,
                "File does not have '.$SAVE_FILE_EXTENSION' extension",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        try {
            val stream = contentResolver.openInputStream(file)!!
            StickerViewSerializer().deserialize(stickerViewModel, stream, resources)
            //Toast.makeText(this, "Loaded $fileName", Toast.LENGTH_SHORT).show()
            stickerViewModel.currentFileName = fileName
            stickerViewModel.isLocked.value = true
        } catch (e: IOException) {
            // Unable to create file, likely because external storage is
            // not currently mounted.
            Timber.e(e, "Error writing %s", file)
            Toast.makeText(this, "Error reading $file", Toast.LENGTH_LONG).show()
        }
    }

    private fun newBoard() {
        stickerViewModel.removeAllStickers()
        stickerViewModel.resetView()
        stickerViewModel.currentFileName = null
    }

    private fun cropAll() {
        stickerViewModel.stickers.value!!.forEach {
            (it as? DrawableSticker)?.cropDestructively(resources)
        }
        binding.stickerView.invalidate()
        Toast.makeText(this, "Cropped all images.", Toast.LENGTH_SHORT).show()
    }

    private fun addSticker() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Image"), INTENT_PICK_IMAGE)
    }

    private fun doAddSticker(file: Uri) {
        val imageStream = contentResolver.openInputStream(file)
        val bitmap = BitmapFactory.decodeStream(imageStream)
        doAddSticker(bitmap)
    }

    private fun doAddSticker(bitmap: Bitmap?) {
        if (bitmap == null) {
            Toast.makeText(this, "Could not decode image", Toast.LENGTH_SHORT).show()
        } else {
            // Resize absurdly large images
            val totalSize = bitmap.width * bitmap.height
            val newBitmap = if (totalSize > MAX_SIZE_PIXELS) {
                val scaleFactor: Float = MAX_SIZE_PIXELS.toFloat() / totalSize.toFloat()
                val scaled = Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scaleFactor).toInt(),
                    (bitmap.height * scaleFactor).toInt(),
                    false
                )
                Timber.w(
                    "Scaled huge bitmap, memory savings: %dMB",
                    (bitmap.allocationByteCount - scaled.allocationByteCount) / (1024 * 1024)
                )
                scaled
            } else {
                bitmap
            }

            val drawable = BitmapDrawable(resources, newBitmap)
            stickerViewModel.addSticker(DrawableSticker(drawable))
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                INTENT_PICK_IMAGE -> {
                    val selectedImage = data!!.data!!
                    doAddSticker(selectedImage)
                }
                INTENT_PICK_SAVED_FILE -> {
                    val selectedFile = data!!.data!!
                    doLoad(selectedFile)
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onBackPressed() {
        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setTitle("Confirm")
            .setMessage("Are you sure you want to quit?")
            .setPositiveButton("Yes") { _, _ -> finish() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun hasPermission(permission: String) =
        ActivityCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestPermission(permission: String) {
        if (!hasPermission(permission)) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(permission),
                PERM_RQST_CODE
            )
        }
    }

    internal class FetchImageFromLinkTask(val text: String, val context: MainActivity) : AsyncTask<Void, Void, Void>() {
        @SuppressLint("StaticFieldLeak")
        val progressBarHolder: View = context.findViewById(R.id.progressBarHolder)

        override fun onPreExecute() {
            super.onPreExecute()
            progressBarHolder.visibility = View.VISIBLE
        }

        override fun doInBackground(vararg params: Void?): Void? {
            try {
                val fuel = FuelManager()
                fuel.baseHeaders = mapOf(Headers.USER_AGENT to "Mozilla/5.0 (X11; Linux x86_64; rv:76.0) Gecko/20100101 Firefox/76.0")

                fuel.head(text).response { _, head, result ->
                    result.fold({
                        val contentType = head.headers[Headers.CONTENT_TYPE]
                        if (!contentType.any { it.startsWith("image/") }) {
                            Toast.makeText(context, "Link is not an image", Toast.LENGTH_LONG)
                                .show()
                            progressBarHolder.visibility = View.GONE
                            return@response
                        }

                        fuel.get(text)
                            .response { _, _, body ->
                                body.fold({
                                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                                    context.doAddSticker(bitmap)
                                    progressBarHolder.visibility =
                                        View.GONE
                                }, {
                                    Toast.makeText(
                                        context,
                                        "Failed to download image: $it",
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                    progressBarHolder.visibility =
                                        View.GONE
                                    Timber.e(it)
                                })
                            }
                    }, {
                        progressBarHolder.visibility = View.GONE
                        Toast.makeText(context, "Failed to download image", Toast.LENGTH_LONG)
                            .show()
                        Timber.e(it)
                    })
                }
            } catch (e: Exception) {
                Timber.e(e)
                Toast.makeText(context, "Invalid link", Toast.LENGTH_LONG).show()
                progressBarHolder.visibility = View.GONE
            }
            return null
        }
    }

    internal class MyStickerOperationListener(private val binding: ActivityMainBinding) :
        OnStickerOperationListener {
        override fun onStickerAdded(sticker: Sticker, direction: Int) {
            binding.stickerView.layoutSticker(sticker, direction)
            binding.stickerView.invalidate()
        }

        override fun onStickerClicked(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onStickerDeleted(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onStickerDragFinished(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onStickerTouchedDown(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onStickerZoomFinished(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onStickerFlipped(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onStickerDoubleTapped(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onStickerMoved(sticker: Sticker) {
            binding.stickerView.invalidate()
        }

        override fun onInvalidateView() {
            binding.stickerView.invalidate()
        }
    }

    companion object {
        const val PERM_RQST_CODE = 110
        const val SAVE_FILE_EXTENSION: String = "ref"

        const val INTENT_PICK_IMAGE = 1
        const val INTENT_PICK_SAVED_FILE = 2

        const val MAX_SIZE_PIXELS = 2000 * 2000
    }
}