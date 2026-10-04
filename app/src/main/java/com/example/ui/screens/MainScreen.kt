package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SupportedLanguages
import com.example.ui.theme.VivvyoEmerald
import com.example.ui.viewmodel.TranslatorViewModel

enum class NavScreen {
    MAIN, PACKS, SETTINGS, APP_TARGETING, PRIVACY_DASHBOARD
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: TranslatorViewModel,
    initialTab: Int = 0,
    initialNavTarget: String? = null
) {
    var currentScreen by rememberSaveable {
        mutableStateOf(
            when (initialNavTarget) {
                "SETTINGS" -> NavScreen.SETTINGS
                "PACKS" -> NavScreen.PACKS
                "APP_TARGETING" -> NavScreen.APP_TARGETING
                else -> NavScreen.MAIN
            }
        )
    }
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }

    LaunchedEffect(initialTab) {
        selectedTab = initialTab
    }

    val textState by viewModel.textState.collectAsStateWithLifecycle()
    val voiceState by viewModel.voiceState.collectAsStateWithLifecycle()
    val imageState by viewModel.imageState.collectAsStateWithLifecycle()
    val installedPacks by viewModel.installedPacks.collectAsStateWithLifecycle()
    val primaryLanguage by viewModel.primaryLanguage.collectAsStateWithLifecycle()
    val isSimpleMode by viewModel.isSimpleModeEnabled.collectAsStateWithLifecycle()

    val installedCodes = remember(installedPacks) {
        installedPacks.map { it.code }.toSet()
    }

    val isRtl = remember(primaryLanguage) {
        SupportedLanguages.RTL_LANG_CODES.contains(primaryLanguage)
    }

    val layoutDirection = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        when (currentScreen) {
            NavScreen.PACKS -> {
                BackHandler { currentScreen = NavScreen.MAIN }
                LanguagePacksScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = NavScreen.MAIN }
                )
            }
            NavScreen.APP_TARGETING -> {
                BackHandler { currentScreen = NavScreen.SETTINGS }
                AppTargetingScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = NavScreen.SETTINGS }
                )
            }
            NavScreen.PRIVACY_DASHBOARD -> {
                BackHandler { currentScreen = NavScreen.SETTINGS }
                PrivacyDashboardScreen(
                    viewModel = viewModel,
                    onBack = { currentScreen = NavScreen.SETTINGS }
                )
            }
            NavScreen.SETTINGS -> {
                BackHandler { currentScreen = NavScreen.MAIN }
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToPacks = { currentScreen = NavScreen.PACKS },
                    onNavigateToAppTargeting = { currentScreen = NavScreen.APP_TARGETING },
                    onNavigateToPrivacyDashboard = { currentScreen = NavScreen.PRIVACY_DASHBOARD },
                    onBack = { currentScreen = NavScreen.MAIN }
                )
            }
            NavScreen.MAIN -> {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = "Vivvyo Translator",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Text(
                                        text = if (isSimpleMode) "simple offline mode" else "vivvyo translator",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                        fontSize = 11.sp
                                    )
                                }
                            },
                            actions = {
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .testTag("offline_status_badge"),
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = RoundedCornerShape(20.dp),
                                    onClick = { currentScreen = NavScreen.PACKS }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(VivvyoEmerald)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "${installedPacks.size} Packs • Offline",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = { currentScreen = NavScreen.SETTINGS },
                                    modifier = Modifier.testTag("open_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                    },
                    bottomBar = {
                        // In Simple Mode, bottom tabs are hidden to provide a distraction-free focused translator
                        if (!isSimpleMode) {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                tonalElevation = 8.dp
                            ) {
                                NavigationBarItem(
                                    selected = selectedTab == 0,
                                    onClick = { selectedTab = 0 },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Translate,
                                            contentDescription = "Text Translation"
                                        )
                                    },
                                    label = { Text("Text") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("tab_text")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 1,
                                    onClick = { selectedTab = 1 },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice Translation"
                                        )
                                    },
                                    label = { Text("Voice") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("tab_voice")
                                )

                                NavigationBarItem(
                                    selected = selectedTab == 2,
                                    onClick = { selectedTab = 2 },
                                    icon = {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Image Translation"
                                        )
                                    },
                                    label = { Text("Image") },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.testTag("tab_image")
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val tabToRender = if (isSimpleMode) 0 else selectedTab
                    when (tabToRender) {
                        0 -> TextTranslationScreen(
                            viewModel = viewModel,
                            textState = textState,
                            installedCodes = installedCodes,
                            onOpenPacks = { currentScreen = NavScreen.PACKS },
                            modifier = Modifier.padding(innerPadding)
                        )
                        1 -> VoiceTranslationScreen(
                            viewModel = viewModel,
                            voiceState = voiceState,
                            installedCodes = installedCodes,
                            modifier = Modifier.padding(innerPadding)
                        )
                        2 -> ImageTranslationScreen(
                            viewModel = viewModel,
                            imageState = imageState,
                            installedCodes = installedCodes,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}
