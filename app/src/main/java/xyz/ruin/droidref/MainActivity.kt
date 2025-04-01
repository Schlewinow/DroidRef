package xyz.ruin.droidref

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Parcelable
import android.text.InputType
import android.util.Patterns
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.github.kittinunf.fuel.core.FuelManager
import com.github.kittinunf.fuel.core.Headers
import com.xiaopo.flying.sticker.*
import com.xiaopo.flying.sticker.iconEvents.CropIconEvent
import com.xiaopo.flying.sticker.iconEvents.DeleteIconEvent
import com.xiaopo.flying.sticker.iconEvents.FlipHorizontallyEvent
import com.xiaopo.flying.sticker.iconEvents.FlipVerticallyEvent
import com.xiaopo.flying.sticker.iconEvents.RotateLeftEvent
import com.xiaopo.flying.sticker.iconEvents.RotateRightEvent
import com.xiaopo.flying.sticker.iconEvents.ZoomIconEvent
import timber.log.Timber
import xyz.ruin.droidref.databinding.ActivityMainBinding
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity() {
    private lateinit var stickerViewModel: StickerViewModel
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.plant(Timber.DebugTree())
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // While reference board is showing, keep the screen turned on.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main)

        stickerViewModel = ViewModelProvider(this).get(StickerViewModel::class.java)
        stickerViewModel.stickerOperationListener = RefBoardStickerOperationListener(binding)
        binding.viewModel = stickerViewModel
        binding.executePendingBindings()
        binding.lifecycleOwner = this

        SettingsStorage.restoreSettings(this)

        setupScaleStickerIcons()
        setupRotateStickerIcons()
        setupCropStickerIcons()
        setupTopButtons()
        setupBottomButtons()

        // When the app starts, set locked edit mode as default.
        lockEditMode()
        addCenterMarker()

        // Some data are overridden by the view model, though with a seemingly async timing.
        // This code is executed after the view model has updated the UI.
        Handler(mainLooper).post {
            // Might have been in hidden UI mode before changing device orientation.
            setUIVisibility(binding.buttonHideShowUI.isChecked)
        }

        handleIntent(intent)
        intent.type = null // Don't run again if rotated/etc.
    }

    /**
     * Used whenever the reference board is cleared,
     * e.g. when starting a new board or before loading an existing board.
     */
    private fun resetReferenceBoard() {
        stickerViewModel.removeAllStickers()
        stickerViewModel.resetView()
        stickerViewModel.currentFileName = null
        lockEditMode()
    }

    /**
     * Marker at the center (0,0) of the coordinate system for better user navigation experience.
     */
    private fun addCenterMarker() {
        // This prevents having multiple markers after the device changes orientation.
        stickerViewModel.removeAllSystemStickers()

        val centerMarkerSticker = DrawableSticker(
            ResourcesCompat.getDrawable(resources, R.drawable.marker_center, null))
        centerMarkerSticker.setAlpha(64)
        stickerViewModel.addSystemSticker(centerMarkerSticker)
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
                }
                else if (intent.type == "text/plain") {
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
        try {
            val imageUri: Uri = (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM)) as Uri
            RefBoardLoadSaveManager.addSticker(imageUri, stickerViewModel, this)
        }
        catch (ex: Exception) {
            Toast.makeText(this, R.string.error_load_image_general, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleSendMultipleImages(intent: Intent) {
        try {
            val imagesParc = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            for (imageParc in imagesParc!!) {
                val imageUri = imageParc as Uri
                RefBoardLoadSaveManager.addSticker(imageUri, stickerViewModel, this)
            }
        }
        catch (ex: Exception) {
            Toast.makeText(this, R.string.error_load_image_general, Toast.LENGTH_LONG).show()
        }
    }

    private fun handleSendLink(intent: Intent) {
        // Technically, requires Manifest.permission.INTERNET as well as Manifest.permission.ACCESS_NETWORK_STATE.
        // Both are "normal" permissions though, which means these only have to be mentioned in the manifest to work.
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        if (!isValidUrl(text)) {
            Toast.makeText(this, R.string.error_load_image_link, Toast.LENGTH_LONG).show()
        }

        FetchImageFromLinkTask(text, this).execute()
    }

    private fun isValidUrl(text: String) = Patterns.WEB_URL.matcher(text).matches()

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
                .setTitle(getText(R.string.main_dialog_new_board_title))
                .setMessage(getText(R.string.main_dialog_new_board_content))
                .setPositiveButton(getText(R.string.main_dialog_accept)) { _, _ -> resetReferenceBoard() }
                .setNegativeButton(getText(R.string.main_dialog_decline), null)
                .show()
        }

        binding.buttonCropAll.setOnClickListener {
            AlertDialog.Builder(this)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(getText(R.string.main_dialog_apply_crop_all_title))
                .setMessage(getText(R.string.main_dialog_apply_crop_all_content))
                .setPositiveButton(getText(R.string.main_dialog_accept)) { _, _ -> cropAll() }
                .setNegativeButton(getText(R.string.main_dialog_decline), null)
                .show()
        }

        binding.buttonHideShowUI.setOnClickListener() {
            setUIVisibility(binding.buttonHideShowUI.isChecked)
        }

        binding.buttonSettings.setOnClickListener {
            val openSettingsIntent = Intent(this, SettingsActivity::class.java)
            startActivity(openSettingsIntent)
        }
    }

    private fun setupBottomButtons() {
        binding.buttonAdd.setOnClickListener {
            addStickerFromFileChooser()
        }

        binding.buttonReset.setOnClickListener {
            stickerViewModel.resetView()
        }

        binding.buttonDuplicate.setOnClickListener {
            stickerViewModel.duplicateCurrentSticker()
        }

        binding.buttonLock.setOnClickListener {
            if (binding.buttonLock.isChecked) {
                lockEditMode()
            }
            else {
                // The lock button does not un-toggle itself, as it is the default mode.
                // It is instead un-toggled once one of the edit modes is activated.
                binding.buttonLock.isChecked = true
            }
        }

        binding.buttonScale.setOnClickListener {
            val checked = binding.buttonScale.isChecked
            lockEditMode()
            if (checked) {
                setScaleMode()
            }
        }

        binding.buttonResetScale.setOnClickListener {
            stickerViewModel.resetCurrentStickerZoom()
        }

        binding.buttonCrop.setOnClickListener {
            val checked = binding.buttonCrop.isChecked
            lockEditMode()
            if (checked) {
                setCropEditMode()
            }
        }

        binding.buttonResetCrop.setOnClickListener {
            stickerViewModel.resetCurrentStickerCropping()
        }

        binding.buttonRotate.setOnClickListener {
            val checked = binding.buttonRotate.isChecked
            lockEditMode()
            if (checked) {
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
            RefBoardLoadSaveManager.saveRefBoard(stickerViewModel.currentFileName!!, stickerViewModel, this)
        }
        else {
            saveAs()
        }
    }

    private fun saveAs() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(getString(R.string.main_dialog_save_ref_board))

        val formatter = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)
        val defaultFileName: String =
            formatter.format(Calendar.getInstance().time) + "." + RefBoardLoadSaveManager.SAVE_FILE_EXTENSION

        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        input.setText(defaultFileName, TextView.BufferType.EDITABLE)
        builder.setView(input)

        builder.setPositiveButton(getString(R.string.main_dialog_save_ref_board_accept))
            { _, _ -> RefBoardLoadSaveManager.saveRefBoard(input.text.toString(), stickerViewModel, this) }
        builder.setNegativeButton(getString(R.string.main_dialog_save_ref_board_decline))
            { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    private fun load() {
        val intent = Intent()
        intent.type = "*/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(
            Intent.createChooser(intent, getString(R.string.main_dialog_open_ref_board)),
            INTENT_PICK_SAVED_FILE
        )
    }

    private fun cropAll() {
        stickerViewModel.stickers.value!!.forEach {
            (it as? DrawableSticker)?.cropDestructively(resources)
        }
        binding.stickerView.invalidate()
        Toast.makeText(this, "Cropped all images.", Toast.LENGTH_SHORT).show()
    }

    private fun addStickerFromFileChooser() {
        val intent = Intent()
        intent.type = "image/*"
        intent.action = Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Image"), INTENT_PICK_IMAGE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                INTENT_PICK_IMAGE -> {
                    val selectedImage = data!!.data!!
                    RefBoardLoadSaveManager.addSticker(selectedImage, stickerViewModel, this)
                }
                INTENT_PICK_SAVED_FILE -> {
                    val selectedFile = data!!.data!!
                    resetReferenceBoard()
                    RefBoardLoadSaveManager.loadRefBoard(selectedFile, stickerViewModel, this)
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

    internal class FetchImageFromLinkTask(val text: String, val mainActivity: MainActivity) : AsyncTask<Void, Void, Void>() {
        @SuppressLint("StaticFieldLeak")
        val progressBarHolder: View = mainActivity.findViewById(R.id.progressBarHolder)

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
                            Toast.makeText(mainActivity, "Link is not an image", Toast.LENGTH_LONG)
                                .show()
                            progressBarHolder.visibility = View.GONE
                            return@response
                        }

                        fuel.get(text)
                            .response { _, _, body ->
                                body.fold({
                                    val bitmap = BitmapFactory.decodeByteArray(it, 0, it.size)
                                    RefBoardLoadSaveManager.addSticker(bitmap, mainActivity.stickerViewModel, mainActivity)
                                    progressBarHolder.visibility =
                                        View.GONE
                                }, {
                                    Toast.makeText(
                                        mainActivity,
                                        "Failed to download image: $it",
                                        Toast.LENGTH_LONG
                                    )
                                        .show()
                                    progressBarHolder.visibility =
                                        View.GONE
                                    Timber.e(it)
                                })
                            }
                    },
                    {
                        progressBarHolder.visibility = View.GONE
                        Toast.makeText(mainActivity, R.string.error_load_image_general, Toast.LENGTH_LONG)
                            .show()
                        Timber.e(it)
                    })
                }
            }
            catch (e: Exception) {
                Timber.e(e)
                Toast.makeText(mainActivity, R.string.error_load_image_link, Toast.LENGTH_LONG).show()
                progressBarHolder.visibility = View.GONE
            }
            return null
        }
    }

    companion object {
        const val INTENT_PICK_IMAGE = 1
        const val INTENT_PICK_SAVED_FILE = 2
    }
}