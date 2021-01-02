package com.xiaopo.flying.sticker;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.IntRange;
import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;

/**
 * @author wupanjie
 */
public class DrawableSticker extends Sticker {
    private Drawable drawable;
    private byte[] bitmapCache = new byte[0];

    public DrawableSticker(Drawable drawable) {
        this.drawable = drawable;
        this.realBounds = new Rect(0, 0, getWidth(), getHeight());
        this.croppedBounds = new RectF(this.realBounds);
        cacheBitmap();
    }

    public DrawableSticker(Drawable drawable, byte[] bitmapCache) {
        this.drawable = drawable;
        this.realBounds = new Rect(0, 0, getWidth(), getHeight());
        this.croppedBounds = new RectF(this.realBounds);
        this.bitmapCache = bitmapCache;
    }

    public DrawableSticker(DrawableSticker other) {
        super(other);
        this.drawable = other.drawable.getConstantState().newDrawable().mutate();
    }

    private void cacheBitmap() {
        // This is extremely expensive, we'd only want to do it once preferably.
        Bitmap bitmap = StickerViewSerializer.Companion.drawableToBitmap(this.drawable);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream);
        this.bitmapCache = stream.toByteArray();
    }

    @NonNull
    @Override
    public Drawable getDrawable() {
        return drawable;
    }

    @Override
    public DrawableSticker setDrawable(@NonNull Drawable drawable) {
        this.drawable = drawable;
        return this;
    }
    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.save();
        canvas.concat(getFinalMatrix());
        if (true) {
            canvas.clipRect(croppedBounds);
            drawable.setBounds(realBounds);
            drawable.draw(canvas);
        }
        else {
            drawable.setBounds(realBounds);
            drawable.draw(canvas);
        }
        canvas.restore();
    }

    @NonNull
    @Override
    public DrawableSticker setAlpha(@IntRange(from = 0, to = 255) int alpha) {
        drawable.setAlpha(alpha);
        return this;
    }

    @Override
    public int getWidth() {
        return drawable.getIntrinsicWidth();
    }

    @Override
    public int getHeight() {
        return drawable.getIntrinsicHeight();
    }

    @Override
    public void release() {
        super.release();
        if (drawable != null) {
            drawable = null;
        }
    }

    public byte[] getBitmapCache() {
        return bitmapCache;
    }

    public void setBitmapCache(byte[] bitmapCache) {
        this.bitmapCache = bitmapCache;
    }
}
