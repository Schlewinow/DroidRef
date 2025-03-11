package com.xiaopo.flying.sticker;

import android.graphics.Matrix;
import android.graphics.PointF;

import androidx.annotation.NonNull;

public class StickerTransform {
    private PointF position = new PointF(0f, 0f);

    private float rotation = 0.0f;

    private PointF scaling = new PointF(1f, 1f);

    private Sticker sticker = null;

    public StickerTransform(Sticker parentSticker) {
        this.sticker = parentSticker;
    }

    public void copyFrom(StickerTransform transformToCopy) {
        this.position = new PointF(transformToCopy.position.x, transformToCopy.position.y);
        this.rotation = transformToCopy.rotation;
        this.scaling = new PointF(transformToCopy.scaling.x, transformToCopy.scaling.y);
        this.sticker = transformToCopy.sticker;
    }

    public PointF getPosition() {
        return new PointF(position.x, position.y);
    }

    public void setPosition(PointF position) {
        this.position.x = position.x;
        this.position.y = position.y;
    }

    public void setPosition(float x, float y) {
        position.x = x;
        position.y = y;
    }

    public void translate(float x, float y) {
        position.x += x;
        position.y += y;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotationAngle) {
        rotation = rotationAngle;
    }

    public void rotate(float rotationAngle) {
        rotation += rotationAngle;
    }

    public PointF getScaling() {
        return new PointF(scaling.x, scaling.y);
    }

    public void setScaling(PointF scale) {
        scaling.x = scale.x;
        scaling.y = scale.y;
    }

    public void setScaling(float scaleX, float scaleY) {
        scaling.x = scaleX;
        scaling.y = scaleY;
    }

    public void scale(float scaleX, float scaleY) {
        scaling.x *= scaleX;
        scaling.y *= scaleY;
    }

    @NonNull
    public Matrix getMatrix() {
        Matrix transformMatrix = new Matrix();
        transformMatrix.postScale(scaling.x, scaling.y, sticker.getCenterPoint().x, sticker.getCenterPoint().y);
        transformMatrix.postRotate(rotation, sticker.getCenterPoint().x, sticker.getCenterPoint().y);
        transformMatrix.postTranslate(position.x, position.y);
        return transformMatrix;
    }

    /**
     * Resets the internal transformation values to an identity matrix.
     */
    public void reset() {
        position.x = 0f;
        position.y = 0f;
        rotation = 0f;
        scaling.x = 1f;
        scaling.y = 1f;
    }
}
