package com.xiaopo.flying.sticker;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.FrameLayout;

import androidx.annotation.ColorInt;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.databinding.ObservableField;

import com.xiaopo.flying.sticker.iconEvents.CropIconEvent;
import com.xiaopo.flying.sticker.iconEvents.DeleteIconEvent;
import com.xiaopo.flying.sticker.iconEvents.FlipHorizontallyEvent;
import com.xiaopo.flying.sticker.iconEvents.ZoomIconEvent;

import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Sticker View
 *
 * @author wupanjie
 */
public class StickerView extends FrameLayout {

    private GestureDetector gestureDetector;

    @IntDef(flag = true, value = {FLIP_HORIZONTALLY, FLIP_VERTICALLY})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Flip {
    }

    public static final int FLIP_HORIZONTALLY = 1;
    public static final int FLIP_VERTICALLY = 1 << 1;

    private List<Sticker> stickers = new ArrayList<>();
    private List<Sticker> systemStickers = new ArrayList<>();
    private final List<BitmapStickerIcon> icons = new ArrayList<>(4);
    private final List<BitmapStickerIcon> rotateIcons = new ArrayList<>(2);
    private final List<BitmapStickerIcon> cropIcons = new ArrayList<>(4);
    private final ObservableField<List<BitmapStickerIcon>> activeIcons = new ObservableField<>(new ArrayList<>(4));

    private final Paint borderPaint = new Paint();
    private final Paint iconPaint = new Paint();
    private final Paint auxiliaryLinePaint = new Paint();

    private final RectF stickerRect = new RectF();

    private final ObservableMatrix canvasMatrix = new ObservableMatrix();

    private final float[] bitmapPoints = new float[8];
    private final float[] bounds = new float[8];

    private Sticker handlingSticker;

    private boolean isLocked;
    private boolean constrained;
    private boolean rotationEnabled;
    private boolean mustLockToPan;

    private boolean isCropActive;

    @ColorInt
    private final int accentColor;

    @ColorInt
    private final int dashColor;

    @StickerViewModel.ActionMode
    private int currentMode;

    private BitmapStickerIcon currentIcon;

    public StickerView(Context context) {
        this(context, null);
    }

    public StickerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = null;
        try {
            a = context.obtainStyledAttributes(attrs, R.styleable.StickerView);
            accentColor = a.getColor(R.styleable.StickerView_accentColor, Color.GRAY);
            dashColor = a.getColor(R.styleable.StickerView_dashColor, Color.GREEN);

            borderPaint.setAntiAlias(true);
            borderPaint.setStrokeWidth(3);
            borderPaint.setColor(a.getColor(R.styleable.StickerView_borderColor, Color.BLACK));
            borderPaint.setAlpha(a.getInteger(R.styleable.StickerView_borderAlpha, 128));

            iconPaint.setAntiAlias(true);
            iconPaint.setStrokeWidth(3);
            iconPaint.setColor(a.getColor(R.styleable.StickerView_iconColor, Color.BLACK));
            iconPaint.setAlpha(a.getInteger(R.styleable.StickerView_borderAlpha, 128));

            auxiliaryLinePaint.setAntiAlias(true);
            auxiliaryLinePaint.setStrokeWidth(3);
            auxiliaryLinePaint.setColor(accentColor);
            auxiliaryLinePaint.setAlpha(a.getInteger(R.styleable.StickerView_borderAlpha, 128));

            configDefaultIcons();
        } finally {
            if (a != null) {
                a.recycle();
            }
        }

        updateIcons();
    }

    public void configDefaultIcons() {
        BitmapStickerIcon deleteIcon = new BitmapStickerIcon(getContext(), R.drawable.ic_close, BitmapStickerIcon.LEFT_TOP);
        deleteIcon.setIconEvent(new DeleteIconEvent());

        BitmapStickerIcon zoomIcon = new BitmapStickerIcon(getContext(), R.drawable.ic_scale, BitmapStickerIcon.RIGHT_BOTTOM);
        zoomIcon.setIconEvent(new ZoomIconEvent());

        BitmapStickerIcon flipIcon = new BitmapStickerIcon(getContext(), R.drawable.ic_flip, BitmapStickerIcon.RIGHT_TOP);
        flipIcon.setIconEvent(new FlipHorizontallyEvent());

        icons.clear();
        icons.add(deleteIcon);
        icons.add(zoomIcon);
        icons.add(flipIcon);

        BitmapStickerIcon rotateDeleteIcon = new BitmapStickerIcon(getContext(), R.drawable.ic_close, BitmapStickerIcon.LEFT_TOP);
        deleteIcon.setIconEvent(new DeleteIconEvent());

        BitmapStickerIcon rotateIcon = new BitmapStickerIcon(getContext(), R.drawable.ic_scale, BitmapStickerIcon.RIGHT_BOTTOM);
        zoomIcon.setIconEvent(new ZoomIconEvent());

        rotateIcons.clear();
        rotateIcons.add(rotateDeleteIcon);
        rotateIcons.add(rotateIcon);

        BitmapStickerIcon cropLeftTop = new BitmapStickerIcon(getContext(), R.drawable.scale_1, BitmapStickerIcon.LEFT_TOP);
        cropLeftTop.setIconEvent(new CropIconEvent(BitmapStickerIcon.LEFT_TOP));

        BitmapStickerIcon cropRightTop = new BitmapStickerIcon(getContext(), R.drawable.scale_2, BitmapStickerIcon.RIGHT_TOP);
        cropRightTop.setIconEvent(new CropIconEvent(BitmapStickerIcon.RIGHT_TOP));

        BitmapStickerIcon cropLeftBottom = new BitmapStickerIcon(getContext(), R.drawable.scale_2, BitmapStickerIcon.LEFT_BOTTOM);
        cropLeftBottom.setIconEvent(new CropIconEvent(BitmapStickerIcon.LEFT_BOTTOM));

        BitmapStickerIcon cropRightBottom = new BitmapStickerIcon(getContext(), R.drawable.scale_1, BitmapStickerIcon.RIGHT_BOTTOM);
        cropRightBottom.setIconEvent(new CropIconEvent(BitmapStickerIcon.RIGHT_BOTTOM));

        cropIcons.clear();
        cropIcons.add(cropLeftTop);
        cropIcons.add(cropRightTop);
        cropIcons.add(cropLeftBottom);
        cropIcons.add(cropRightBottom);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            stickerRect.left = left;
            stickerRect.top = top;
            stickerRect.right = right;
            stickerRect.bottom = bottom;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        drawStickers(canvas);
    }

    private void drawBorder(Canvas canvas, float[] bitmapPoints, int color, int width) {
        float x1 = bitmapPoints[0];
        float y1 = bitmapPoints[1];
        float x2 = bitmapPoints[2];
        float y2 = bitmapPoints[3];
        float x3 = bitmapPoints[4];
        float y3 = bitmapPoints[5];
        float x4 = bitmapPoints[6];
        float y4 = bitmapPoints[7];

        canvas.save();
        canvas.concat(canvasMatrix.getMatrix());
        canvas.drawLine(x1, y1, x2, y2, borderPaint);
        canvas.drawLine(x1, y1, x3, y3, borderPaint);
        canvas.drawLine(x2, y2, x4, y4, borderPaint);
        canvas.drawLine(x4, y4, x3, y3, borderPaint);

        canvas.restore();
    }

    protected void drawStickers(Canvas canvas) {
        for (int index = 0; index < systemStickers.size(); index++) {
            Sticker sticker = systemStickers.get(index);
            if (sticker != null) {
                sticker.draw(canvas);
            }
        }

        for (int index = 0; index < stickers.size(); index++) {
            Sticker sticker = stickers.get(index);
            if (sticker != null) {
                sticker.draw(canvas);
            }
        }

        drawStickerBorder(canvas);
        drawStickerActionIcons(canvas);
    }

    /**
     * Draw a border around the active sticker as visual indicator.
     * @param canvas Canvas to draw the visual border to.
     */
    private void drawStickerBorder(Canvas canvas) {
        if (handlingSticker != null && !isLocked) {
            getStickerPoints(handlingSticker, bitmapPoints);
            if (handlingSticker != null) {
                drawBorder(canvas, bitmapPoints, dashColor, 1);
            }
        }
    }

    /**
     * Draw the icons at the edge of the active sticker which allow editing operations.
     * @param canvas Canvas to draw the icons to.
     */
    private void drawStickerActionIcons(Canvas canvas) {
        if (handlingSticker != null && !isLocked) {
            List<BitmapStickerIcon> activeIconList = activeIcons.get();
            if (activeIconList == null) {
                return;
            }

            getStickerPointsCropped(handlingSticker, bitmapPoints);
            float x1 = bitmapPoints[0];
            float y1 = bitmapPoints[1];
            float x2 = bitmapPoints[2];
            float y2 = bitmapPoints[3];
            float x3 = bitmapPoints[4];
            float y3 = bitmapPoints[5];
            float x4 = bitmapPoints[6];
            float y4 = bitmapPoints[7];

            float rotation = StickerMath.calculateAngle(x4, y4, x3, y3);

            for (int i = 0; i < activeIconList.size(); i++) {
                BitmapStickerIcon icon = activeIconList.get(i);
                switch (icon.getPosition()) {
                    case BitmapStickerIcon.LEFT_TOP:
                        configIconMatrix(icon, x1, y1, rotation);
                        break;

                    case BitmapStickerIcon.RIGHT_TOP:
                        configIconMatrix(icon, x2, y2, rotation);
                        break;

                    case BitmapStickerIcon.LEFT_BOTTOM:
                        configIconMatrix(icon, x3, y3, rotation);
                        break;

                    case BitmapStickerIcon.RIGHT_BOTTOM:
                        configIconMatrix(icon, x4, y4, rotation);
                        break;

                    case BitmapStickerIcon.LEFT_CENTER:
                        configIconMatrix(icon, (x1 + x3) / 2, (y1 + y3) / 2, rotation);
                        break;

                    case BitmapStickerIcon.RIGHT_CENTER:
                        configIconMatrix(icon, (x2 + x4) / 2, (y2 + y4) / 2, rotation);
                        break;

                    case BitmapStickerIcon.TOP_CENTER:
                        configIconMatrix(icon, (x1 + x2) / 2, (y1 + y2) / 2, rotation);
                        break;

                    case BitmapStickerIcon.BOTTOM_CENTER:
                        configIconMatrix(icon, (x3 + x4) / 2, (y3 + y4) / 2, rotation);
                        break;
                }
                icon.draw(canvas, iconPaint);
            }
        }
    }

    protected void configIconMatrix(@NonNull BitmapStickerIcon icon, float x, float y, float rotation) {
        icon.setX(x);
        icon.setY(y);
        icon.getTransform().reset();

        icon.getTransform().rotate(rotation);
        icon.getTransform().translate(x - icon.getWidth() / 2f, y - icon.getHeight() / 2f);
        icon.setCanvasMatrix(canvasMatrix.getMatrix());
        Matrix a = new Matrix();
        canvasMatrix.invert(a);
        float radius = a.mapRadius(BitmapStickerIcon.DEFAULT_ICON_RADIUS);
        icon.setIconRadius(radius);
        icon.getTransform().scale(radius / BitmapStickerIcon.DEFAULT_ICON_RADIUS, radius / BitmapStickerIcon.DEFAULT_ICON_RADIUS);
    }

    public void detectIconGesture(@NotNull MotionEvent event) {
        gestureDetector.onTouchEvent(event);
    }

    @NonNull
    public StickerView layoutSticker(@NonNull Sticker sticker) {
        return layoutSticker(sticker, Sticker.Position.CENTER);
    }

    public StickerView layoutSticker(@NonNull final Sticker sticker,
                                     final @Sticker.Position int position) {
        if (ViewCompat.isLaidOut(this)) {
            layoutStickerImmediately(sticker, position);
        } else {
            post(() -> layoutStickerImmediately(sticker, position));
        }
        return this;
    }

    private PointF getScaledSize(Sticker sticker) {
        float[] temp = {sticker.getWidth(), sticker.getHeight()};
        sticker.getFinalMatrix().mapVectors(temp);
        float sw = temp[0];
        float sh = temp[1];
        return new PointF(sw, sh);
    }

    protected void layoutStickerImmediately(@NonNull Sticker sticker, @Sticker.Position int position) {
        sticker.getTransform().reset();

        float[] worldSize = { getWidth(), getHeight() };
        Matrix a = new Matrix();
        canvasMatrix.invert(a);
        a.mapVectors(worldSize);

        setStickerPosition(sticker, position);
        setHandlingSticker(sticker);
    }

    protected void setStickerPosition(@NonNull Sticker sticker, @Sticker.Position int position) {
        PointF scaled = getScaledSize(sticker);
        float screenX, screenY;

        if ((position & Sticker.Position.TOP) > 0) {
            screenY = 0f;
        } else if ((position & Sticker.Position.BOTTOM) > 0) {
            screenY = getHeight() - scaled.y;
        } else {
            screenY = (getHeight() / 2f) - (scaled.y / 2f);
        }
        if ((position & Sticker.Position.LEFT) > 0) {
            screenX = 0f;
        } else if ((position & Sticker.Position.RIGHT) > 0) {
            screenX = getWidth() - scaled.x;
        } else {
            screenX = (getWidth() / 2f) - (scaled.x / 2f);
        }

        float[] temp2 = {screenX, screenY};
        Matrix a = new Matrix();
        sticker.getFinalMatrix().invert(a);
        a.mapPoints(temp2);
        sticker.getTransform().setPosition(temp2[0], temp2[1]);
    }

    public Sticker getHandlingSticker() {
        return handlingSticker;
    }

    public void setHandlingSticker(Sticker sticker) {
        // Avoid system stickers to be set as active stickers.
        if (stickers.contains(sticker) || sticker == null) {
            handlingSticker = sticker;
        }
        invalidate();
    }

    @NonNull
    public float[] getStickerPoints(@Nullable Sticker sticker) {
        float[] points = new float[8];
        getStickerPoints(sticker, points);
        return points;
    }

    public void getStickerPoints(@Nullable Sticker sticker, @NonNull float[] dst) {
        if (sticker == null) {
            Arrays.fill(dst, 0);
            return;
        }
        sticker.getBoundPoints(bounds);
        sticker.getMappedPointsPre(dst, bounds);
    }

    public void getStickerPointsCropped(@Nullable Sticker sticker, @NonNull float[] dst) {
        if (sticker == null) {
            Arrays.fill(dst, 0);
            return;
        }
        sticker.getCroppedBoundPoints(bounds);
        sticker.getMappedPointsPre(dst, bounds);
    }

    public int getStickerCount() {
        return stickers.size();
    }

    public boolean isNoneSticker() {
        return getStickerCount() == 0;
    }

    public boolean getIsLocked() {
        return isLocked;
    }

    @NonNull
    public StickerView setIsLocked(boolean isLocked) {
        this.isLocked = isLocked;
        invalidate();
        return this;
    }

    public boolean isRotationEnabled() {
        return rotationEnabled;
    }

    @NonNull
    public StickerView setRotationEnabled(boolean rotationEnabled) {
        this.rotationEnabled = rotationEnabled;
        updateIcons();
        invalidate();
        return this;
    }

    public boolean isCropActive() {
        return isCropActive;
    }

    @NonNull
    public StickerView setCropActive(boolean cropActive) {
        this.isCropActive = cropActive;
        updateIcons();
        invalidate();
        return this;
    }

    public boolean isMustLockToPan() {
        return mustLockToPan;
    }

    @NonNull
    public StickerView setMustLockToPan(boolean mustLockToPan) {
        this.mustLockToPan = mustLockToPan;
        invalidate();
        return this;
    }

    public boolean isConstrained() {
        return constrained;
    }

    @NonNull
    public StickerView setConstrained(boolean constrained) {
        this.constrained = constrained;
        postInvalidate();
        return this;
    }

    @Nullable
    public Sticker getCurrentSticker() {
        return handlingSticker;
    }

    @NonNull
    public List<BitmapStickerIcon> getIcons() {
        return icons;
    }

    public void setIcons(@NonNull List<BitmapStickerIcon> icons) {
        this.icons.clear();
        this.icons.addAll(icons);
        updateIcons();
        invalidate();
    }

    @NonNull
    public List<BitmapStickerIcon> getRotateIcons() {
        return rotateIcons;
    }

    public void setRotateIcons(@NonNull List<BitmapStickerIcon> rotateIcons) {
        this.rotateIcons.clear();
        this.rotateIcons.addAll(rotateIcons);
        updateIcons();
        invalidate();
    }

    @NotNull
    public List<BitmapStickerIcon> getCropIcons() {
        return cropIcons;
    }

    public void setCropIcons(@NonNull List<BitmapStickerIcon> cropIcons) {
        this.cropIcons.clear();
        this.cropIcons.addAll(cropIcons);
        updateIcons();
        invalidate();
    }

    private void updateIcons() {
        this.currentIcon = null;
        if (isCropActive) {
            setActiveIcons(cropIcons);
        }
        else if (rotationEnabled) {
            setActiveIcons(rotateIcons);
        }
        else {
            setActiveIcons(icons);
        }
    }
    public interface OnStickerOperationListener {
        void onStickerAdded(@NonNull Sticker sticker, int position);

        void onStickerClicked(@NonNull Sticker sticker);

        void onStickerDeleted(@NonNull Sticker sticker);

        void onStickerDragFinished(@NonNull Sticker sticker);

        void onStickerTouchedDown(@NonNull Sticker sticker);

        void onStickerZoomFinished(@NonNull Sticker sticker);

        void onStickerFlipped(@NonNull Sticker sticker);

        void onStickerDoubleTapped(@NonNull Sticker sticker);

        void onStickerMoved(@NonNull Sticker sticker);

        void onInvalidateView();
    }

    public interface OnStickerAreaTouchListener {
        void onStickerAreaTouch();
    }

//    public void setOnStickerAreaTouchListener(OnStickerAreaTouchListener onStickerAreaTouchListener) {
//        this.onStickerAreaTouchListener = onStickerAreaTouchListener;
//    }

    public int getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(int currentMode) {
        this.currentMode = currentMode;
    }

    public BitmapStickerIcon getCurrentIcon() {
        return currentIcon;
    }

    public void setCurrentIcon(BitmapStickerIcon currentIcon) {
        this.currentIcon = currentIcon;
    }

    public List<Sticker> getStickers() {
        return stickers;
    }

    public void setStickers(List<Sticker> stickers) {
        this.stickers = stickers;
        this.handlingSticker = null;
    }

    public List<Sticker> getSystemStickers() {
        return systemStickers;
    }

    public void setSystemStickers(List<Sticker> systemStickers) {
        this.systemStickers = systemStickers;
    }

    public ObservableMatrix getCanvasMatrix() {
        return canvasMatrix;
    }
    public void setCanvasMatrix(Matrix matrix) {
        canvasMatrix.setMatrix(matrix);
    }

    public void setGestureDetector(GestureListener gestureListener) {
        // HACK can't figure out how to get around this, some of the icon events require both a
        // Context and the ViewModel logic to work, but the ViewModel can't be put in the View and
        // the ViewModel can't hold references to views or contexts.
        gestureListener.setStickerView(this);
        gestureDetector = new GestureDetector(getContext(), gestureListener);
    }

    public ObservableField<List<BitmapStickerIcon>> getActiveIcons() {
        return activeIcons;
    }

    public void setActiveIcons(List<BitmapStickerIcon> activeIcons) {
        this.activeIcons.set(activeIcons);
    }
}
