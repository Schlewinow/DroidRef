package com.xiaopo.flying.sticker.iconEvents;

import android.view.MotionEvent;

import com.xiaopo.flying.sticker.Sticker;
import com.xiaopo.flying.sticker.StickerIconEvent;
import com.xiaopo.flying.sticker.StickerView;
import com.xiaopo.flying.sticker.StickerViewModel;

public class RotateLeftEvent implements StickerIconEvent {
    @Override
    public void onActionDown(StickerView stickerView, StickerViewModel viewModel, MotionEvent event) {
        Sticker activeSticker = stickerView.getCurrentSticker();
        if (activeSticker != null) {
            activeSticker.getTransform().rotate(-90.0f);
        }
    }

    @Override
    public void onActionMove(StickerView stickerView, StickerViewModel viewModel, MotionEvent event) {
        // No implementation required.
    }

    @Override
    public void onActionUp(StickerView stickerView, StickerViewModel viewModel, MotionEvent event) {
        // No implementation required.
    }
}
