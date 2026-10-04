package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.engine.MlKitTranslationService
import com.example.data.local.VivvyoDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VivvyoForegroundService : Service() {

    private val tag = "VivvyoFGS"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null
    private var floatingBubbleView: View? = null
    private var quickPopupView: View? = null
    private var isContinuousVoiceActive = false

    private lateinit var clipboardManager: ClipboardManager
    private var translationService: MlKitTranslationService? = null
    private var database: VivvyoDatabase? = null

    companion object {
        const val CHANNEL_ID = "vivvyo_translator_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.example.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.action.STOP_SERVICE"
        const val ACTION_TRANSLATE_CLIPBOARD = "com.example.action.TRANSLATE_CLIPBOARD"
        const val ACTION_OCR_IMAGE = "com.example.action.OCR_IMAGE"
        const val ACTION_START_VOICE = "com.example.action.START_VOICE"
        const val ACTION_OPEN_SETTINGS = "com.example.action.OPEN_SETTINGS"

        fun start(context: Context) {
            val intent = Intent(context, VivvyoForegroundService::class.java).apply {
                action = ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, VivvyoForegroundService::class.java).apply {
                action = ACTION_STOP_SERVICE
            }
            context.stopService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        translationService = MlKitTranslationService(this)
        database = VivvyoDatabase.getInstance(this)

        createNotificationChannel()
        setupClipboardListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START_SERVICE

        if (intent?.getBooleanExtra("EXTRA_VOICE_ACTIVE", false) == true) {
            isContinuousVoiceActive = true
        }

        when (action) {
            ACTION_STOP_SERVICE -> {
                stopForeground(true)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_TRANSLATE_CLIPBOARD -> {
                handleClipboardTranslation()
            }
            ACTION_OCR_IMAGE -> {
                openAppWithTab(2)
            }
            ACTION_START_VOICE -> {
                isContinuousVoiceActive = true
                openAppWithTab(1)
            }
            ACTION_OPEN_SETTINGS -> {
                val settingsIntent = Intent(this, MainActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("NAV_TARGET", "SETTINGS")
                }
                startActivity(settingsIntent)
            }
        }

        startForegroundWithNotification()

        if (Settings.canDrawOverlays(this)) {
            showFloatingBubble()
        }

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Vivvyo Translator Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps offline translation active and handles quick translation actions"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundWithNotification() {
        val openAppIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clipboardIntent = PendingIntent.getService(
            this, 2,
            Intent(this, VivvyoForegroundService::class.java).apply { action = ACTION_TRANSLATE_CLIPBOARD },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val ocrIntent = PendingIntent.getService(
            this, 3,
            Intent(this, VivvyoForegroundService::class.java).apply { action = ACTION_OCR_IMAGE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val voiceIntent = PendingIntent.getService(
            this, 4,
            Intent(this, VivvyoForegroundService::class.java).apply { action = ACTION_START_VOICE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settingsIntent = PendingIntent.getService(
            this, 5,
            Intent(this, VivvyoForegroundService::class.java).apply { action = ACTION_OPEN_SETTINGS },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Vivvyo Translator")
            .setContentText("100% On-Device Offline Engine Active")
            .setSmallIcon(R.drawable.ic_vivvyo_logo)
            .setOngoing(true)
            .setContentIntent(openAppIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_vivvyo_logo, "Translate Clip", clipboardIntent)
            .addAction(R.drawable.ic_vivvyo_logo, "OCR Image", ocrIntent)
            .addAction(R.drawable.ic_vivvyo_logo, "Voice", voiceIntent)
            .addAction(R.drawable.ic_vivvyo_logo, "Settings", settingsIntent)
            .build()

        val hasRecordAudioPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                var fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                if (hasRecordAudioPermission && isContinuousVoiceActive) {
                    fgsType = fgsType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (hasRecordAudioPermission && isContinuousVoiceActive) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(tag, "startForeground error: ${e.message}", e)
        }
    }

    private fun setupClipboardListener() {
        clipboardManager.addPrimaryClipChangedListener {
            // Background clipboard update notification or auto-processing when requested
            Log.d(tag, "Clipboard content updated")
        }
    }

    private fun handleClipboardTranslation() {
        val clip = clipboardManager.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }

        val item = clip.getItemAt(0)
        val text = item.text?.toString()

        if (!text.isNullOrBlank()) {
            scope.launch {
                val detected = translationService?.detectLanguage(text) ?: "en"
                val source = if (detected != "und") detected else "auto"
                val target = "en" // or primary language
                val result = translationService?.translateText(text, source, target)
                val translated = result?.getOrNull()?.first ?: text

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@VivvyoForegroundService, "Translation: $translated", Toast.LENGTH_LONG).show()
                    val newClip = ClipData.newPlainText("Vivvyo Translation", translated)
                    clipboardManager.setPrimaryClip(newClip)
                }
            }
        } else if (item.uri != null) {
            // Image in clipboard!
            openAppWithTab(2, item.uri)
        }
    }

    private fun openAppWithTab(tabIndex: Int, imageUri: Uri? = null) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("NAV_TAB", tabIndex)
            if (imageUri != null) {
                putExtra("CLIPBOARD_IMAGE_URI", imageUri.toString())
            }
        }
        startActivity(intent)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showFloatingBubble() {
        if (floatingBubbleView != null || !Settings.canDrawOverlays(this)) return

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 200
        }

        val bubble = ImageView(this).apply {
            setImageResource(R.drawable.ic_vivvyo_logo)
            val pad = 12
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(Color.parseColor("#0F172A"))
            elevation = 16f
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = true

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isClick = false
                    }
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager?.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        handleBubbleClick()
                    }
                    true
                }
                else -> false
            }
        }

        bubble.setOnLongClickListener {
            showQuickActionsPopup()
            true
        }

        try {
            windowManager?.addView(bubble, params)
            floatingBubbleView = bubble
        } catch (e: Exception) {
            Log.e(tag, "Failed to add floating bubble: ${e.message}", e)
        }
    }

    private fun handleBubbleClick() {
        val clip = clipboardManager.primaryClip
        val text = clip?.getItemAt(0)?.text?.toString()
        if (!text.isNullOrBlank()) {
            handleClipboardTranslation()
        } else {
            openAppWithTab(0)
        }
    }

    private fun showQuickActionsPopup() {
        Toast.makeText(this, "Vivvyo Quick: Translating clipboard…", Toast.LENGTH_SHORT).show()
        handleClipboardTranslation()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        if (floatingBubbleView != null) {
            try {
                windowManager?.removeView(floatingBubbleView)
            } catch (e: Exception) {
                Log.e(tag, "Error removing bubble", e)
            }
            floatingBubbleView = null
        }
        translationService?.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
