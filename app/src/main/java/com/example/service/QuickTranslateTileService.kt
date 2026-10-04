package com.example.service

import android.content.Intent
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.MainActivity

class QuickTranslateTileService : TileService() {

    private var isVoiceActive = false

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = if (isVoiceActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Vivvyo Translate"
            subtitle = if (isVoiceActive) "Voice Active" else "Tap to Speak"
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        isVoiceActive = !isVoiceActive
        qsTile?.apply {
            state = if (isVoiceActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = if (isVoiceActive) "Voice Active" else "Tap to Speak"
            updateTile()
        }

        // Tapping the tile toggles Voice Translation
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAV_TAB", 1) // Voice Tab
            putExtra("AUTO_START_VOICE", isVoiceActive)
        }
        startActivityAndCollapse(intent)
    }
}
