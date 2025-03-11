package com.xiaopo.flying.sticker

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.annotation.IntDef
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.xiaopo.flying.sticker.StickerView.Flip
import com.xiaopo.flying.sticker.StickerView.OnStickerAreaTouchListener
import timber.log.Timber
import kotlin.math.*

open class StickerViewModel :
    ViewModel() {
    var isLocked: MutableLiveData<Boolean> = MutableLiveData(false)
    var mustLockToPan: MutableLiveData<Boolean> = MutableLiveData(false)
    var isCropActive: MutableLiveData<Boolean> = MutableLiveData(false)
    var rotationEnabled: MutableLiveData<Boolean> = MutableLiveData(false)
    var constrained: MutableLiveData<Boolean> = MutableLiveData(false)
    var bringToFrontCurrentSticker = MutableLiveData(true)

    var canvasMatrix: CustomMutableLiveData<ObservableMatrix> = CustomMutableLiveData(ObservableMatrix())

    var stickers: MutableLiveData<ArrayList<Sticker>> = MutableLiveData(ArrayList())
    var icons: MutableLiveData<ArrayList<BitmapStickerIcon>> = MutableLiveData(ArrayList())
    var rotateIcons: MutableLiveData<ArrayList<BitmapStickerIcon>> = MutableLiveData(ArrayList())
    var cropIcons: MutableLiveData<ArrayList<BitmapStickerIcon>> = MutableLiveData(ArrayList())
    var activeIcons: MutableLiveData<List<BitmapStickerIcon>> = MutableLiveData(ArrayList(4))
    var handlingSticker: MutableLiveData<Sticker?> = MutableLiveData(null)

    var gestureListener: MutableLiveData<GestureListener> = MutableLiveData()

    init {
        gestureListener.value = GestureListener(this)
    }

    var currentFileName: String? = null

    private val onStickerAreaTouchListener: OnStickerAreaTouchListener? = null

    lateinit var stickerOperationListener: StickerView.OnStickerOperationListener

    private val stickerWorldTransform = StickerTransform(null)
    private val stickerWorldMatrix = Matrix()
    private val moveMatrix = Matrix()
    private val point = FloatArray(2)
    private val currentCenterPoint = PointF()
    private val tmp = FloatArray(2)
    private var pointerId = -1
    private var midPoint = PointF()

    private val DEFAULT_MIN_CLICK_DELAY_TIME = 200
    private var minClickDelayTime = DEFAULT_MIN_CLICK_DELAY_TIME

    //the first point down position
    private var downX = 0f
    private var downY = 0f
    private var downXScaled = 0f
    private var downYScaled = 0f

    private var oldDistance = 0f
    private var oldRotation = 0f

    private var lastClickTime: Long = 0

    @IntDef(
        ActionMode.NONE,
        ActionMode.DRAG,
        ActionMode.ZOOM_WITH_TWO_FINGER,
        ActionMode.ICON,
        ActionMode.CLICK,
        ActionMode.CANVAS_DRAG,
        ActionMode.CANVAS_ZOOM_WITH_TWO_FINGER
    )
    @kotlin.annotation.Retention(AnnotationRetention.SOURCE)
    annotation class ActionMode {
        companion object {
            const val NONE = 0
            const val DRAG = 1
            const val ZOOM_WITH_TWO_FINGER = 2
            const val ICON = 3
            const val CLICK = 4
            const val CANVAS_DRAG = 5
            const val CANVAS_ZOOM_WITH_TWO_FINGER = 6
        }
    }

    var currentMode = MutableLiveData(ActionMode.NONE)

    var currentIcon: MutableLiveData<BitmapStickerIcon?> = MutableLiveData(null)

    @SuppressLint("ClickableViewAccessibility")
    val onTouchListener = View.OnTouchListener { v, event -> onTouchEvent(v as StickerView, event) }

    fun addSticker(sticker: Sticker) {
        addSticker(sticker, Sticker.Position.CENTER)
    }

    fun addSticker(sticker: Sticker, position: Int) {
        sticker.setCanvasMatrix(canvasMatrix.value!!.getMatrix())
        stickers.value!!.add(sticker)
        handlingSticker.value = sticker
        stickerOperationListener.onStickerAdded(sticker, position)
    }

    fun resetView() {
        canvasMatrix.value!!.setMatrix(Matrix())
        updateCanvasMatrix()
    }

    fun updateCanvasMatrix() {
        for (i in stickers.value!!.indices) {
            val sticker: Sticker = stickers.value!![i]
            sticker.setCanvasMatrix(canvasMatrix.value!!.getMatrix())
        }
        stickerOperationListener.onInvalidateView()
    }

    fun removeCurrentSticker(): Boolean {
        if (handlingSticker.value != null) {
            return removeSticker(handlingSticker.value!!)
        } else {
            return false
        }
    }

    fun removeSticker(sticker: Sticker): Boolean {
        if (stickers.value!!.contains(sticker)) {
            stickers.value!!.remove(sticker)
            stickerOperationListener.onStickerDeleted(sticker)
            if (handlingSticker.value == sticker) {
                handlingSticker.value = null
            }
            stickerOperationListener.onInvalidateView()
            return true
        } else {
            Timber.d("remove: the sticker is not in this StickerView")
            return false
        }
    }

    fun removeAllStickers() {
        stickers.value?.clear()
        if (handlingSticker.value != null) {
            handlingSticker.value!!.release()
            handlingSticker.value = null
        }
        currentIcon.value = null
        stickerOperationListener.onInvalidateView()
    }

    fun resetCurrentStickerCropping() {
        handlingSticker.value?.let(this::resetStickerCropping)
    }

    private fun resetStickerCropping(sticker: Sticker) {
        if (isLocked.value != true) {
            sticker.setCroppedBounds(RectF(sticker.getRealBounds()))
            stickerOperationListener.onInvalidateView()
        }
    }

    fun resetCurrentStickerZoom() {
        handlingSticker.value?.let(this::resetStickerZoom)
    }

    private fun resetStickerZoom(sticker: Sticker) {
        if (isLocked.value != true) {
            sticker.transform.setScaling(1f, 1f)
            stickerOperationListener.onInvalidateView()
        }
    }

    fun resetCurrentStickerRotation() {
        handlingSticker.value?.let(this::resetStickerRotation)
    }

    private fun resetStickerRotation(sticker: Sticker) {
        if (isLocked.value != true) {
            sticker.transform.rotation = 0f
            stickerOperationListener.onInvalidateView()
        }
    }

    private fun handleCanvasMotion(view: StickerView, event: MotionEvent): Boolean {
        handlingSticker.value = null
        currentIcon.value = null
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!onTouchDownCanvas(event)) {
                    return false
                }
                pointerId = event.getPointerId(0)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                for (i in 0 until event.pointerCount) {
                    if (event.getPointerId(i) != pointerId) {
                        calculateDown(event)
                        pointerId = event.getPointerId(i)
                        break
                    }
                }
                oldDistance = StickerMath.calculateDistance(event)
                oldRotation = StickerMath.calculateAngle(event)
                midPoint = calculateMidPoint(event)
                stickerWorldMatrix.set(canvasMatrix.value!!.getMatrix())
                currentMode.value = ActionMode.CANVAS_ZOOM_WITH_TWO_FINGER
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    if (event.getPointerId(i) != pointerId) {
                        calculateDown(event)
                        pointerId = event.getPointerId(i)
                        break
                    }
                }
                handleMoveActionCanvas(event)
            }
            MotionEvent.ACTION_UP -> {
                onTouchUpCanvas(event)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (currentMode.value == ActionMode.CANVAS_ZOOM_WITH_TWO_FINGER) {
                    pointerId = -1
                    if (!onTouchDownCanvas(event)) {
                        return false
                    }
                } else {
                    onTouchUpCanvas(event)
                }
            }
        }
        stickerOperationListener.onInvalidateView()
        return true
    }

    /**
     * @param event MotionEvent received from [)][.onTouchEvent]
     */
    protected fun onTouchDownCanvas(event: MotionEvent): Boolean {
        currentMode.value = ActionMode.CANVAS_DRAG
        calculateDown(event)
        midPoint = calculateMidPoint(handlingSticker.value)
        oldDistance = StickerMath.calculateDistance(midPoint.x, midPoint.y, downX, downY)
        oldRotation = StickerMath.calculateAngle(midPoint.x, midPoint.y, downX, downY)
        stickerWorldMatrix.set(canvasMatrix.value!!.getMatrix())
        return true
    }

    protected fun onTouchUpCanvas(event: MotionEvent) {
        val currentTime = SystemClock.uptimeMillis()
        pointerId = -1
        currentMode.value = ActionMode.NONE
        lastClickTime = currentTime
    }

    protected fun handleMoveActionCanvas(event: MotionEvent) {
        when (currentMode.value) {
            ActionMode.CANVAS_DRAG -> {
                moveMatrix.set(stickerWorldMatrix)
                moveMatrix.postTranslate(event.x - downX, event.y - downY)
                canvasMatrix.value!!.setMatrix(moveMatrix)
                updateCanvasMatrix()
            }
            ActionMode.CANVAS_ZOOM_WITH_TWO_FINGER -> {
                val newDistance = StickerMath.calculateDistance(event)
                moveMatrix.set(stickerWorldMatrix)
                moveMatrix.postScale(
                    newDistance / oldDistance, newDistance / oldDistance, midPoint.x,
                    midPoint.y
                )
                canvasMatrix.value!!.setMatrix(moveMatrix)
                updateCanvasMatrix()
            }
        }
    }

    private fun isMovingCanvas(): Boolean {
        return currentMode.value == ActionMode.CANVAS_DRAG || currentMode.value == ActionMode.CANVAS_ZOOM_WITH_TWO_FINGER
    }

    @SuppressLint("ClickableViewAccessibility")
    fun onTouchEvent(view: StickerView, event: MotionEvent): Boolean {
        if (isLocked.value == true || isMovingCanvas()) {
            return handleCanvasMotion(view, event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                //                Timber.d("MotionEvent.ACTION_DOWN event__: %s", event.toString());
                onStickerAreaTouchListener?.onStickerAreaTouch()
                if (!onTouchDown(view, event)) {
                    return if (mustLockToPan.value != true) {
                        handleCanvasMotion(view, event)
                    } else false
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
            }
            MotionEvent.ACTION_MOVE -> {
                handleMoveAction(view, event)
            }
            MotionEvent.ACTION_UP -> onTouchUp(view, event)
            MotionEvent.ACTION_POINTER_UP -> if (currentMode.value == ActionMode.ZOOM_WITH_TWO_FINGER && handlingSticker.value != null) {
                stickerOperationListener.onStickerZoomFinished(handlingSticker.value!!)
                if (!onTouchDown(view, event)) {
                    return if (mustLockToPan.value != true) {
                        handleCanvasMotion(view, event)
                    } else false
                }
                currentMode.value = ActionMode.DRAG
            } else {
                currentMode.value = ActionMode.NONE
            }
        }
        stickerOperationListener.onInvalidateView()
        return true
    }

    /**
     * @param event MotionEvent received from [)][.onTouchEvent]
     */
    protected fun onTouchDown(view: StickerView, event: MotionEvent): Boolean {
        currentMode.value = ActionMode.DRAG
        calculateDown(event)
        midPoint = StickerMath.calculateMidPoint(handlingSticker.value)
        oldDistance = StickerMath.calculateDistance(midPoint.x, midPoint.y, downX, downY)
        oldRotation = StickerMath.calculateAngle(midPoint.x, midPoint.y, downXScaled, downYScaled)
        currentIcon.value = findCurrentIconTouched()

        // HACK if application logic is really meant to be handled in the ViewModel, how do you
        // communicate from the View to the ViewModel? There can't be a GestureDetector in the
        // ViewModel because it retains the activity's context.
        view.detectIconGesture(event)

        if (currentIcon.value != null) {
            currentMode.value = ActionMode.ICON
            currentIcon.value!!.onActionDown(view, this, event)
            // Timber.d("current_icon: %s", currentIcon.getDrawableName());
        } else {
            handlingSticker.value = findHandlingSticker()
        }

       handlingSticker.value?.let {
            stickerWorldMatrix.set(it.transform.matrix)
            stickerWorldTransform.copyFrom(it.transform)

            if (bringToFrontCurrentSticker.value == true) {
                stickers.value!!.remove(it)
                stickers.value!!.add(it)
            }

            stickerOperationListener.onStickerTouchedDown(it)
        }
        return currentIcon.value != null || handlingSticker.value != null
    }

    protected fun onTouchUp(view: StickerView, event: MotionEvent) {
        val touchSlop = ViewConfiguration.get(view.context).scaledTouchSlop

        val currentTime = SystemClock.uptimeMillis()
        if (currentMode.value == ActionMode.ICON && currentIcon.value != null && handlingSticker.value != null) {
            currentIcon.value!!.onActionUp(view, this, event)
            // Timber.d("current_icon: %s", currentIcon.getDrawableName());
        }
        if (currentMode.value == ActionMode.DRAG && Math.abs(event.x - downX) < touchSlop && Math.abs(
                event.y - downY
            ) < touchSlop && handlingSticker.value != null
        ) {
            currentMode.value = ActionMode.CLICK
            stickerOperationListener.onStickerClicked(handlingSticker.value!!)
            if (currentTime - lastClickTime < minClickDelayTime) {
                stickerOperationListener.onStickerDoubleTapped(handlingSticker.value!!)
            }
        }
        if (currentMode.value == ActionMode.DRAG && handlingSticker.value != null) {
              stickerOperationListener.onStickerDragFinished(handlingSticker.value!!)
        }
        currentMode.value = ActionMode.NONE
        lastClickTime = currentTime
    }

    private fun screenToWorld(vx: Float, vy: Float): PointF {
        val vec = floatArrayOf(vx, vy)
        val a = Matrix()
        canvasMatrix.value!!.invert(a)
        a.mapVectors(vec)
        return PointF(vec[0], vec[1])
    }

    private fun handleMoveAction(view: StickerView, event: MotionEvent) {
        when (currentMode.value) {
            ActionMode.NONE, ActionMode.CLICK -> {
            }

            ActionMode.DRAG -> if (handlingSticker.value != null) {
                val vec = screenToWorld(event.x - downX, event.y - downY)
                val newPosX = stickerWorldTransform.position.x + vec.x
                val newPosY = stickerWorldTransform.position.y + vec.y
                handlingSticker.value!!.transform.setPosition(newPosX, newPosY)

                if (constrained.value == true) {
                    constrainSticker(view, handlingSticker.value!!)
                }
                stickerOperationListener.onStickerMoved(handlingSticker.value!!)
            }

            // ToDo: couldn't get into this case so far, so no idea if it works after the refactoring
            ActionMode.ZOOM_WITH_TWO_FINGER -> if (handlingSticker.value != null) {
                val newDistance = StickerMath.calculateDistanceScaled(event, canvasMatrix.value!!.getMatrix())
                val newRotation = StickerMath.calculateAngle(event)
                moveMatrix.set(stickerWorldMatrix)

                if (rotationEnabled.value == false) {
                    val newScaleX = stickerWorldTransform.scaling.x * (newDistance / oldDistance)
                    val newScaleY = stickerWorldTransform.scaling.y * (newDistance / oldDistance)
                    handlingSticker.value!!.transform.setScaling(newScaleX, newScaleY)
                }
                else {
                    val deltaRotation = newRotation - oldRotation
                    handlingSticker.value!!.transform.rotation = stickerWorldTransform.rotation + deltaRotation
                }
            }

            ActionMode.ICON -> if (handlingSticker.value != null && currentIcon.value != null) {
                currentIcon.value!!.onActionMove(view, this, event)
            }
        }
    }

    fun zoomAndRotateCurrentSticker(event: MotionEvent, horizontalScale: Boolean, verticalScale: Boolean) {
        zoomAndRotateSticker(handlingSticker.value, event, horizontalScale, verticalScale)
    }

    private fun zoomAndRotateSticker(sticker: Sticker?, event: MotionEvent, horizontalScale: Boolean, verticalScale: Boolean) {
        if (sticker != null) {
            val temp = floatArrayOf(event.x, event.y)
            val invertCanvasMatrix = Matrix()
            canvasMatrix.value!!.invert(invertCanvasMatrix)
            invertCanvasMatrix.mapPoints(temp)
            val touchPoint = PointF(temp[0], temp[1])

            midPoint = calculateMidPoint(sticker)

            val oldDistance = StickerMath.calculateDistance(midPoint.x, midPoint.y, downXScaled, downYScaled)
            val newDistance = StickerMath.calculateDistance(midPoint.x, midPoint.y, touchPoint.x, touchPoint.y)
            val newRotation = StickerMath.calculateAngle(midPoint.x, midPoint.y, touchPoint.x, touchPoint.y)

            if (rotationEnabled.value == false) {
                var newScaleX = stickerWorldTransform.scaling.x
                if (horizontalScale) {
                    newScaleX *= newDistance / oldDistance
                }
                var newScaleY = stickerWorldTransform.scaling.y
                if (verticalScale) {
                    newScaleY *= newDistance / oldDistance
                }
                handlingSticker.value!!.transform.setScaling(newScaleX, newScaleY)
            }
            else {
                val deltaRotation = newRotation - oldRotation
                handlingSticker.value!!.transform.rotation = stickerWorldTransform.rotation + deltaRotation
            }
        }
    }

    private fun constrainSticker(view: StickerView, sticker: Sticker) {
        var moveX = 0f
        var moveY = 0f
        val width: Int = view.width
        val height: Int = view.height
        sticker.getMappedCenterPoint(currentCenterPoint, point, tmp)
        if (currentCenterPoint.x < 0) {
            moveX = -currentCenterPoint.x
        }
        if (currentCenterPoint.x > width) {
            moveX = width - currentCenterPoint.x
        }
        if (currentCenterPoint.y < 0) {
            moveY = -currentCenterPoint.y
        }
        if (currentCenterPoint.y > height) {
            moveY = height - currentCenterPoint.y
        }
        sticker.transform.translate(moveX, moveY)
    }

    fun cropCurrentSticker(event: MotionEvent, gravity: Int) {
        cropSticker(handlingSticker.value, event, gravity)
    }

    private fun convertFlippedGravity(sticker: Sticker, gravity: Int): Int =
        if (sticker.isFlippedHorizontally) {
            if (sticker.isFlippedVertically) {
                when (gravity) {
                    BitmapStickerIcon.LEFT_TOP -> BitmapStickerIcon.RIGHT_BOTTOM
                    BitmapStickerIcon.LEFT_BOTTOM -> BitmapStickerIcon.RIGHT_TOP
                    BitmapStickerIcon.RIGHT_TOP -> BitmapStickerIcon.LEFT_BOTTOM
                    BitmapStickerIcon.RIGHT_BOTTOM -> BitmapStickerIcon.LEFT_TOP
                    BitmapStickerIcon.LEFT_CENTER -> BitmapStickerIcon.RIGHT_CENTER
                    BitmapStickerIcon.RIGHT_CENTER -> BitmapStickerIcon.LEFT_CENTER
                    BitmapStickerIcon.TOP_CENTER -> BitmapStickerIcon.BOTTOM_CENTER
                    BitmapStickerIcon.BOTTOM_CENTER -> BitmapStickerIcon.TOP_CENTER
                    else -> gravity
                }
            } else {
                when (gravity) {
                    BitmapStickerIcon.LEFT_TOP -> BitmapStickerIcon.RIGHT_TOP
                    BitmapStickerIcon.LEFT_BOTTOM -> BitmapStickerIcon.RIGHT_BOTTOM
                    BitmapStickerIcon.RIGHT_TOP -> BitmapStickerIcon.LEFT_TOP
                    BitmapStickerIcon.RIGHT_BOTTOM -> BitmapStickerIcon.LEFT_BOTTOM
                    BitmapStickerIcon.LEFT_CENTER -> BitmapStickerIcon.RIGHT_CENTER
                    BitmapStickerIcon.RIGHT_CENTER -> BitmapStickerIcon.LEFT_CENTER
                    else -> gravity
                }
            }
        } else {
            if (sticker.isFlippedVertically) {
                when (gravity) {
                    BitmapStickerIcon.LEFT_TOP -> BitmapStickerIcon.LEFT_BOTTOM
                    BitmapStickerIcon.LEFT_BOTTOM -> BitmapStickerIcon.LEFT_TOP
                    BitmapStickerIcon.RIGHT_TOP -> BitmapStickerIcon.RIGHT_BOTTOM
                    BitmapStickerIcon.RIGHT_BOTTOM -> BitmapStickerIcon.RIGHT_TOP
                    BitmapStickerIcon.TOP_CENTER -> BitmapStickerIcon.BOTTOM_CENTER
                    BitmapStickerIcon.BOTTOM_CENTER -> BitmapStickerIcon.TOP_CENTER
                    else -> gravity
                }
            } else {
                gravity
            }
        }


    private fun cropSticker(sticker: Sticker?, event: MotionEvent, gravity: Int) {
        if (sticker == null) {
            return
        }
        val dx = event.x
        val dy = event.y
        val inv = Matrix()
        sticker.canvasMatrix.invert(inv)
        val inv2 = Matrix()
        sticker.transform.matrix.invert(inv2)
        val temp = floatArrayOf(dx, dy)
        inv.mapPoints(temp)
        inv2.mapPoints(temp)
        val cropped = RectF(sticker.getCroppedBounds())
        val px = temp[0].toInt()
        val py = temp[1].toInt()

        val flippedGravity = convertFlippedGravity(sticker, gravity)
        when (flippedGravity) {
            BitmapStickerIcon.LEFT_TOP -> {
                cropped.left = Math.min(px.toFloat(), cropped.right)
                cropped.top = Math.min(py.toFloat(), cropped.bottom)
            }
            BitmapStickerIcon.RIGHT_TOP -> {
                cropped.right = Math.max(px.toFloat(), cropped.left)
                cropped.top = Math.min(py.toFloat(), cropped.bottom)
            }
            BitmapStickerIcon.LEFT_BOTTOM -> {
                cropped.left = Math.min(px.toFloat(), cropped.right)
                cropped.bottom = Math.max(py.toFloat(), cropped.top)
            }
            BitmapStickerIcon.RIGHT_BOTTOM -> {
                cropped.right = Math.max(px.toFloat(), cropped.left)
                cropped.bottom = Math.max(py.toFloat(), cropped.top)
            }
            BitmapStickerIcon.LEFT_CENTER -> {
                cropped.left = Math.min(px.toFloat(), cropped.right)
            }
            BitmapStickerIcon.RIGHT_CENTER -> {
                cropped.right = Math.max(px.toFloat(), cropped.left)
            }
            BitmapStickerIcon.TOP_CENTER -> {
                cropped.top = Math.min(py.toFloat(), cropped.bottom)
            }
            BitmapStickerIcon.BOTTOM_CENTER -> {
                cropped.bottom = Math.max(py.toFloat(), cropped.top)
            }
        }
        sticker.setCroppedBounds(cropped)
    }

    fun duplicateCurrentSticker() {
        handlingSticker.value?.let { duplicateSticker(it) }
    }

    fun duplicateSticker(sticker: Sticker) {
        val newSticker = DrawableSticker(sticker as DrawableSticker)
        addSticker(newSticker)
    }

    private fun findCurrentIconTouched(): BitmapStickerIcon? {
        for (icon in activeIcons.value!!) {
            val x: Float = icon.x + icon.iconRadius - downXScaled
            val y: Float = icon.y + icon.iconRadius - downYScaled
            val distancePow2 = x * x + y * y
            if (distancePow2 <= ((icon.iconRadius + icon.iconRadius) * 1.2f.toDouble()).pow(2.0)
            ) {
                return icon
            }
        }
        return null
    }

    /**
     * find the touched Sticker
     */
    private fun findHandlingSticker(): Sticker? {
        stickers.value?.let {
            for (index in it.indices.reversed()) {
                val sticker = it[index]
                if (sticker.isEditable && isInStickerAreaCropped(sticker, downX, downY)) {
                    return sticker
                }
            }
        }
        return null
    }

    private fun isInStickerAreaCropped(sticker: Sticker, downX: Float, downY: Float): Boolean {
        tmp[0] = downX
        tmp[1] = downY
        return sticker.containsCropped(tmp)
    }

    private fun calculateMidPoint(event: MotionEvent?): PointF {
        val midPoint = PointF()
        if (event == null || event.pointerCount < 2) {
            midPoint.set(0f, 0f)
            return midPoint
        }

        val pts = floatArrayOf(event.getX(0), event.getY(0), event.getX(1), event.getY(1))
        //canvasMatrix.mapPoints(pts);
        val x = (pts[0] + pts[2]) / 2
        val y = (pts[1] + pts[3]) / 2
        midPoint.set(x, y)
        return midPoint
    }

    private fun calculateMidPoint(sticker: Sticker?): PointF {
        val midPoint = PointF()
        if (sticker == null) {
            midPoint.set(0f, 0f)
            return midPoint
        }

        sticker.getMappedCenterPointCropped(midPoint, point, tmp)
        return midPoint
    }

    private fun calculateDown(event: MotionEvent?) {
        if (event == null || event.pointerCount < 1) {
            downX = 0f
            downY = 0f
            downXScaled = 0f
            downYScaled = 0f
            return
        }
        val pts = floatArrayOf(event.getX(0), event.getY(0))
        downX = pts[0]
        downY = pts[1]
        val a = Matrix()
        canvasMatrix.value!!.invert(a)
        a.mapPoints(pts)
        downXScaled = pts[0]
        downYScaled = pts[1]
    }

    fun flipCurrentSticker(direction: Int) {
        handlingSticker.value?.let { flip(it, direction) }
    }

    private fun flip(sticker: Sticker, @Flip direction: Int) {
        sticker.getCenterPoint(midPoint)
        if (direction and StickerView.FLIP_HORIZONTALLY > 0) {
            sticker.isFlippedHorizontally = !sticker.isFlippedHorizontally
        }
        if (direction and StickerView.FLIP_VERTICALLY > 0) {
            sticker.isFlippedVertically = !sticker.isFlippedVertically
        }
        stickerOperationListener.onStickerFlipped(sticker)
    }
}
