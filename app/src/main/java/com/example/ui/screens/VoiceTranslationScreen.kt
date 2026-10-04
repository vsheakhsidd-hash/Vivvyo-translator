package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SupportedLanguages
import com.example.ui.components.LanguageSelectorDialog
import com.example.ui.theme.VivvyoEmerald
import com.example.ui.viewmodel.TranslatorViewModel
import com.example.ui.viewmodel.VoiceTranslationState

@Composable
fun VoiceTranslationScreen(
    viewModel: TranslatorViewModel,
    voiceState: VoiceTranslationState,
    installedCodes: Set<String>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.startVoiceListening(voiceState.sourceLang)
        }
    }

    var showSourceSelector by remember { mutableStateOf(false) }
    var showTargetSelector by remember { mutableStateOf(false) }

    val sourceItem = SupportedLanguages.findByCode(voiceState.sourceLang)
    val targetItem = SupportedLanguages.findByCode(voiceState.targetLang)

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (voiceState.isListening) 1.25f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    if (voiceState.isMirrorMode) {
        // Face-to-Face Split Screen Mirror Mode (Top half rotated 180 degrees)
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Partner's Half (Rotated 180 degrees)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer(rotationZ = 180f)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f))
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${targetItem?.name ?: voiceState.targetLang} (Partner)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(text = targetItem?.flagEmoji ?: "🌐", fontSize = 24.sp)
                    }

                    Text(
                        text = if (voiceState.translation.isNotBlank()) voiceState.translation else "Waiting for translation…",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (voiceState.translation.isNotBlank()) {
                            IconButton(onClick = { viewModel.speakText(voiceState.translation, voiceState.targetLang) }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak")
                            }
                        }
                    }
                }
            }

            // Middle Divider & Controls
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { viewModel.toggleMirrorMode() },
                    modifier = Modifier.testTag("exit_mirror_mode")
                ) {
                    Icon(imageVector = Icons.Default.Flip, contentDescription = "Exit Mirror Mode", tint = MaterialTheme.colorScheme.primary)
                }

                // Center Mic Button in Mirror Mode
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(if (voiceState.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                        .clickable {
                            if (voiceState.isListening) {
                                viewModel.stopVoiceListening()
                            } else {
                                if (hasAudioPermission) viewModel.startVoiceListening(voiceState.sourceLang)
                                else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Mic",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                IconButton(onClick = { viewModel.swapVoiceLanguages() }) {
                    Icon(imageVector = Icons.Default.SwapHoriz, contentDescription = "Swap")
                }
            }

            // User's Half
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${sourceItem?.name ?: voiceState.sourceLang} (You)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(text = sourceItem?.flagEmoji ?: "🌐", fontSize = 24.sp)
                    }

                    Text(
                        text = if (voiceState.transcript.isNotBlank()) voiceState.transcript else "Tap microphone to speak…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (voiceState.transcript.isNotBlank()) {
                            IconButton(onClick = { viewModel.speakText(voiceState.transcript, voiceState.sourceLang) }) {
                                Icon(imageVector = Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Speak")
                            }
                        }
                    }
                }
            }
        }
        return
    }

    // Standard Single-View Voice Tab
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Privacy and Mode Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = VivvyoEmerald.copy(alpha = 0.15f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = VivvyoEmerald,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% Offline V2V",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VivvyoEmerald
                    )
                }
            }

            // Mirror Mode Button
            OutlinedButton(
                onClick = { viewModel.toggleMirrorMode() },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("toggle_mirror_mode")
            ) {
                Icon(
                    imageVector = Icons.Default.Flip,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mirror Mode", fontSize = 12.sp)
            }
        }

        // Language Selectors Bar
        LanguageSelectionBar(
            sourceName = sourceItem?.name ?: voiceState.sourceLang,
            sourceFlag = sourceItem?.flagEmoji ?: "🌐",
            targetName = targetItem?.name ?: voiceState.targetLang,
            targetFlag = targetItem?.flagEmoji ?: "🌐",
            onSourceClick = { showSourceSelector = true },
            onTargetClick = { showTargetSelector = true },
            onSwapClick = { viewModel.swapVoiceLanguages() }
        )

        // Continuous Listening Switch Row
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Continuous Conversation",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "VAD auto-segments & translates speech hands-free",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = voiceState.isContinuousMode,
                    onCheckedChange = { viewModel.toggleContinuousListening() },
                    modifier = Modifier.testTag("continuous_voice_switch")
                )
            }
        }

        // Conversation Cards Display
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("voice_transcript_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "You said (${sourceItem?.name ?: voiceState.sourceLang}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(text = sourceItem?.flagEmoji ?: "", fontSize = 16.sp)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (voiceState.transcript.isNotBlank()) voiceState.transcript else "Tap microphone below and speak naturally…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (voiceState.transcript.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    minLines = 2
                )
            }
        }

        // Translated Result Card
        if (voiceState.translation.isNotBlank()) {
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("voice_translation_card"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Translated (${targetItem?.name ?: voiceState.targetLang}):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                        IconButton(
                            onClick = {
                                if (isSpeaking) viewModel.stopSpeaking()
                                else viewModel.speakText(voiceState.translation, voiceState.targetLang)
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("voice_listen_button")
                        ) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Listen",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = voiceState.translation,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Big Mic Button with Pulsing Wave
        Box(
            modifier = Modifier.size(130.dp),
            contentAlignment = Alignment.Center
        ) {
            if (voiceState.isListening) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .scale(pulseScale)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                )
            }

            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(
                        if (voiceState.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                    .clickable {
                        if (voiceState.isListening) {
                            viewModel.stopVoiceListening()
                        } else {
                            if (hasAudioPermission) {
                                viewModel.startVoiceListening(voiceState.sourceLang)
                            } else {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    }
                    .testTag("voice_mic_button"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (voiceState.isListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = if (voiceState.isListening) "Stop Listening" else "Start Speaking",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Text(
            text = if (voiceState.isListening) "Listening on-device… speak now" else "Push to talk in ${sourceItem?.name ?: "English"}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (voiceState.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }

    if (showSourceSelector) {
        LanguageSelectorDialog(
            title = "Voice Input Language",
            selectedCode = voiceState.sourceLang,
            allowAutoDetect = false,
            installedCodes = installedCodes,
            onDismiss = { showSourceSelector = false },
            onLanguageSelected = { code ->
                viewModel.setVoiceLanguages(code, voiceState.targetLang)
            }
        )
    }

    if (showTargetSelector) {
        LanguageSelectorDialog(
            title = "Voice Translation Language",
            selectedCode = voiceState.targetLang,
            allowAutoDetect = false,
            installedCodes = installedCodes,
            onDismiss = { showTargetSelector = false },
            onLanguageSelected = { code ->
                viewModel.setVoiceLanguages(voiceState.sourceLang, code)
            }
        )
    }
}
