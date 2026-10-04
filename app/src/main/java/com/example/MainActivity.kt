package com.example

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.service.VivvyoForegroundService
import com.example.ui.screens.MainScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.TranslatorViewModel
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {

    private var volumeDownPressTime = 0L
    private var initialTab = 0
    private var initialNavTarget: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        VivvyoForegroundService.start(this)

        initialTab = intent.getIntExtra("NAV_TAB", 0)
        initialNavTarget = intent.getStringExtra("NAV_TARGET")

        val incomingText = handleIncomingIntent(intent)
        val imageUriString = intent.getStringExtra("CLIPBOARD_IMAGE_URI")

        setContent {
            MyApplicationTheme {
                val viewModel: TranslatorViewModel = viewModel()
                var showSplash by remember { mutableStateOf(incomingText == null && imageUriString == null && initialTab == 0) }

                LaunchedEffect(Unit) {
                    if (incomingText != null) {
                        viewModel.onInputTextChanged(incomingText)
                        viewModel.translateText()
                    }
                    if (imageUriString != null) {
                        try {
                            val uri = Uri.parse(imageUriString)
                            viewModel.onImageSelected(uri)
                        } catch (e: Exception) {}
                    }
                    if (intent.getBooleanExtra("AUTO_START_VOICE", false)) {
                        viewModel.startVoiceListening("en")
                    }
                    if (showSplash) {
                        delay(650)
                        showSplash = false
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (showSplash) {
                        SplashScreen()
                    } else {
                        MainScreen(
                            viewModel = viewModel,
                            initialTab = initialTab,
                            initialNavTarget = initialNavTarget
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        this.intent = intent
        initialTab = intent.getIntExtra("NAV_TAB", 0)
        initialNavTarget = intent.getStringExtra("NAV_TARGET")
        handleIncomingIntent(intent)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event?.repeatCount == 0) {
                volumeDownPressTime = System.currentTimeMillis()
            } else if (event != null && System.currentTimeMillis() - volumeDownPressTime > 1200) {
                // Hardware hotkey triggered: Volume-key long press triggers clipboard translation
                handleQuickHotkeyTrigger()
                volumeDownPressTime = Long.MAX_VALUE // Prevent duplicate trigger
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleQuickHotkeyTrigger() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrBlank()) {
            Toast.makeText(this, "Hotkey: Translating clipboard…", Toast.LENGTH_SHORT).show()
            VivvyoForegroundService.start(this)
        }
    }

    private fun handleIncomingIntent(intent: Intent?): String? {
        if (intent == null) return null

        if (intent.action == Intent.ACTION_PROCESS_TEXT) {
            val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
            if (!text.isNullOrBlank()) return text
        } else if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) return text
        }
        return null
    }
}

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Translate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = stringResource(R.string.wordmark),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.privacy_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}
