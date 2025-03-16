package xyz.ruin.droidref

import com.xiaopo.flying.sticker.Sticker
import com.xiaopo.flying.sticker.StickerView.OnStickerOperationListener
import xyz.ruin.droidref.databinding.ActivityMainBinding

class RefBoardStickerOperationListener(private val binding: ActivityMainBinding) : OnStickerOperationListener {

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