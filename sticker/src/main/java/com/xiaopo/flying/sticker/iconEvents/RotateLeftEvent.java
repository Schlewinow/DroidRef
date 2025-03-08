package com.xiaopo.flying.sticker.iconEvents;

import android.view.MotionEvent;

import com.xiaopo.flying.sticker.StickerIconEvent;
import com.xiaopo.flying.sticker.StickerView;
import com.xiaopo.flying.sticker.StickerViewModel;

public class RotateLeftEvent implements StickerIconEvent {
    @Override
    public void onActionDown(StickerView stickerView, StickerViewModel viewModel, MotionEvent event) {
        stickerView.setRotation(stickerView.getRotation() - 90.0f);
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
