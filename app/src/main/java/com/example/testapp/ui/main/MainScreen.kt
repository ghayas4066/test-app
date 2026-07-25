@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.example.testapp.ui.main

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.Alignment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.pm.PackageManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale

import android.graphics.BitmapFactory
import androidx.core.content.ContextCompat
import com.example.testapp.camera.FaceAnalyzer
import com.example.testapp.data.CameraSettings
import com.example.testapp.tts.TtsHelper
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class Feature(val title: String, val file: String)
data class Category(val categoryName: String, val features: List<Feature>)

@Composable
fun MainScreen() {
    val context = LocalContext.current
    
    var serverMessage by remember { mutableStateOf<Triple<String, String, Int>?>(null) }
    
    LaunchedEffect(Unit) {
        com.example.testapp.updater.MessageChecker.checkForMessage(context) { title, msg, id ->
            serverMessage = Triple(title, msg, id)
        }
    }

    serverMessage?.let { (title, msg, id) ->
        AlertDialog(
            onDismissRequest = { 
                serverMessage = null
                com.example.testapp.updater.MessageChecker.markMessageAsRead(context, id)
            },
            title = { Text(title) },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { 
                    serverMessage = null
                    com.example.testapp.updater.MessageChecker.markMessageAsRead(context, id)
                }) {
                    Text("OK")
                }
            }
        )
    }
    
    val permissions = remember {
        arrayOf(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO
        )
    }
    var hasPermissions by remember {
        mutableStateOf(
            permissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
    }

    if (!hasPermissions) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Camera and Microphone permissions are required to run the Talking Camera app.",
                color = Color.White,
                fontSize = 20.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )
            Button(
                onClick = { launcher.launch(permissions) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDD00))
            ) {
                Text("Grant Permissions", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    var currentScreen by remember { mutableStateOf("menu") } // "menu", "camera", "capture_result"
    var currentLevel by remember { mutableStateOf("categories") } // "categories", "features"
    var selectedCategoryIndex by remember { mutableStateOf(0) }

    val categories = remember {
        listOf(
            Category("Third Eye", listOf(
                Feature("1. Selfie Camera", "feature_20"),
                Feature("2. Jar & Spice Identifier", "feature_11")
            )),
            Category("Navigation & Mobility", listOf(
                Feature("1. Indoor Beacon Navigator", "feature_1"),
                Feature("2. Step Counter", "feature_2"),
                Feature("3. Pothole Detector", "feature_3"),
                Feature("4. Zebra Crossing Finder", "feature_4")
            )),
            Category("Daily Life Helpers", listOf(
                Feature("1. Liquid Pour Assistant", "feature_12"),
                Feature("2. Money Manager", "feature_16")
            ))
        )
    }

    val settings = remember { CameraSettings.load(context) }
    var ttsInitialized by remember { mutableStateOf(false) }
    
    val tts = remember {
        TtsHelper(context) {
            ttsInitialized = true
            // Initial greeting matching main.lua
            Handler(Looper.getMainLooper()).postDelayed({
                CameraSettings.load(context).let { s ->
                    val t = TtsHelper(context) {}
                    t.speak("Welcome to Vision Helper. Please select a category.")
                }
            }, 500)
        }
    }

    LaunchedEffect(ttsInitialized) {
        if (ttsInitialized) {
            tts.setSpeechRate(settings.speechRate)
            tts.setPitch(settings.speechPitch)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tts.shutdown()
        }
    }

    val speakText = { text: String ->
        if (settings.voiceGuide == "On") {
            tts.speak(text)
        }
    }

    BackHandler(enabled = currentScreen == "menu" && currentLevel == "features") {
        speakText("Going back to categories.")
        currentLevel = "categories"
    }

    when (currentScreen) {
        "menu" -> {
            MenuScreen(
                categories = categories,
                currentLevel = currentLevel,
                selectedCategoryIndex = selectedCategoryIndex,
                onCategoryClick = { index ->
                    selectedCategoryIndex = index
                    currentLevel = "features"
                    speakText(categories[index].categoryName + " selected. Choose a feature.")
                },
                onFeatureClick = { feature ->
                    if (feature.file == "feature_20") {
                        speakText("Loading " + feature.title)
                        currentScreen = "camera"
                    } else {
                        speakText("This feature is not available yet.")
                        Toast.makeText(context, "Not yet created: ${feature.file}", Toast.LENGTH_SHORT).show()
                    }
                },
                onAboutClick = {
                    speakText("About Vision Helper.")
                    currentScreen = "about"
                },
                speakText = speakText
            )
        }
        "about" -> {
            AboutScreen(
                onBack = {
                    speakText("Going back to menu.")
                    currentScreen = "menu"
                }
            )
        }
        "camera" -> {
            CameraScreen(
                settings = settings,
                tts = tts,
                onExit = {
                    speakText("Camera closed.")
                    currentScreen = "menu"
                },
                onMediaCaptured = { file, isVideo ->
                    if (settings.autoSave == "On") {
                        // Automatically save and restart camera
                        saveMediaToGallery(context, file, isVideo)
                        speakText(if (isVideo) "Video Auto-Saved." else "Photo Auto-Saved.")
                        Toast.makeText(context, "Saved to Gallery", Toast.LENGTH_SHORT).show()
                    } else {
                        // Show save/share/discard options screen
                        // We will store the temp file and navigate to capture_result
                        TempMediaHolder.file = file
                        TempMediaHolder.isVideo = isVideo
                        currentScreen = "capture_result"
                        speakText(if (isVideo) "Video captured. Select an option below." else "Photo captured. Select an option below.")
                    }
                }
            )
        }
        "capture_result" -> {
            CaptureResultScreen(
                settings = settings,
                tts = tts,
                onSave = {
                    TempMediaHolder.file?.let { file ->
                        saveMediaToGallery(context, file, TempMediaHolder.isVideo)
                        speakText("Saved to Gallery.")
                        Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show()
                    }
                    currentScreen = "camera"
                },
                onShare = {
                    TempMediaHolder.file?.let { file ->
                        speakText("Opening share menu.")
                        shareMedia(context, file, TempMediaHolder.isVideo)
                    }
                    currentScreen = "camera"
                },
                onDiscard = {
                    speakText("Discarded.")
                    TempMediaHolder.file?.delete()
                    currentScreen = "camera"
                }
            )
        }
    }
}

object TempMediaHolder {
    var file: File? = null
    var isVideo: Boolean = false
}

@Composable
fun MenuScreen(
    categories: List<Category>,
    currentLevel: String,
    selectedCategoryIndex: Int,
    onCategoryClick: (Int) -> Unit,
    onFeatureClick: (Feature) -> Unit,
    onAboutClick: () -> Unit,
    speakText: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Premium Header
        Surface(
            color = Color(0xFF1E1E1E),
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp, horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (currentLevel == "categories") "Vision Helper" else categories[selectedCategoryIndex].categoryName,
                    color = Color(0xFF00E5FF),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (currentLevel == "categories") "Select a Category" else "Select a Feature",
                    color = Color.LightGray,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(
                visible = currentLevel == "categories",
                enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally(),
                exit = fadeOut(animationSpec = tween(300)) + slideOutHorizontally()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    categories.forEachIndexed { index, cat ->
                        ListItemView(
                            title = cat.categoryName,
                            icon = Icons.Default.Category,
                            onClick = { onCategoryClick(index) }
                        )
                    }
                    ListItemView(
                        title = "About",
                        icon = Icons.Default.Info,
                        onClick = onAboutClick
                    )
                }
            }

            AnimatedVisibility(
                visible = currentLevel == "features",
                enter = fadeIn(animationSpec = tween(300)) + slideInHorizontally { it / 2 },
                exit = fadeOut(animationSpec = tween(300)) + slideOutHorizontally { it / 2 }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (currentLevel == "features") {
                        categories[selectedCategoryIndex].features.forEach { feature ->
                            val icon = if (feature.file == "feature_20") Icons.Default.CameraAlt else Icons.Default.Extension
                            ListItemView(
                                title = feature.title,
                                icon = icon,
                                onClick = { onFeatureClick(feature) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ListItemView(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF2A2A2A), shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFFFDD00),
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.width(20.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
fun CameraScreen(
    settings: CameraSettings,
    tts: TtsHelper,
    onExit: () -> Unit,
    onMediaCaptured: (File, Boolean) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var guidanceMessage by remember { mutableStateOf("Starting Camera...") }
    var flashMode by remember { mutableStateOf(settings.ratio) } // reused local state, actual flash configuration
    val flashModes = listOf("Off", "Auto", "On")
    var currentFlashIndex by remember { mutableStateOf(0) }

    var isFrontCamera by remember { mutableStateOf(settings.isFrontCamera) }
    var isPhotoMode by remember { mutableStateOf(settings.mode == "Photo") }
    var isRecording by remember { mutableStateOf(false) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    val vibrator = remember { context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    var hasPermissions by remember { 
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions[Manifest.permission.CAMERA] ?: false
        if (!hasPermissions) {
            onExit()
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermissions) {
            permissionLauncher.launch(
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
            )
        }
    }

    if (!hasPermissions) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF121212)), contentAlignment = Alignment.Center) {
            Text("Waiting for camera permission...", color = Color.White, fontSize = 20.sp)
        }
        return
    }

    val cameraController = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(
                CameraController.IMAGE_ANALYSIS or
                CameraController.IMAGE_CAPTURE or
                CameraController.VIDEO_CAPTURE
            )
        }
    }

    LaunchedEffect(cameraController, lifecycleOwner) {
        cameraController.bindToLifecycle(lifecycleOwner)
    }

    var lastSpeakTime by remember { mutableStateOf(0L) }
    var lastDynamicVibTime by remember { mutableStateOf(0L) }
    var isCountingDown by remember { mutableStateOf(false) }
    var isCapturing by remember { mutableStateOf(false) }

    val speakText = { text: String ->
        if (settings.voiceGuide == "On") {
            tts.speak(text)
        }
    }

    val triggerVibration = { vType: String ->
        if (settings.vibration != "Off" && vibrator != null && vibrator.hasVibrator()) {
            val duration = when (vType) {
                "center" -> {
                    when (settings.vibration) {
                        "Low" -> 200L
                        "Medium" -> 400L
                        "High" -> 800L
                        else -> 400L
                    }
                }
                "pulse" -> {
                    when (settings.vibration) {
                        "Low" -> 10L
                        "Medium" -> 25L
                        "High" -> 50L
                        else -> 25L
                    }
                }
                else -> 50L
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(duration)
            }
        }
    }

    // Apply flash mode
    val applyFlashSetting = { index: Int ->
        val modeStr = flashModes[index]
        when (modeStr) {
            "Off" -> cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_OFF
            "On" -> cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_ON
            "Auto" -> cameraController.imageCaptureFlashMode = ImageCapture.FLASH_MODE_AUTO
        }
    }

    // Set Initial camera state
    LaunchedEffect(isFrontCamera) {
        cameraController.cameraSelector = if (isFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        speakText(if (isFrontCamera) "Front Camera" else "Back Camera")
    }

    // Image analyzer binding
    LaunchedEffect(isFrontCamera, showSettingsDialog) {
        if (!showSettingsDialog) {
            cameraController.setImageAnalysisAnalyzer(
                cameraExecutor,
                FaceAnalyzer(settings) { result ->
                    if (isCapturing || isRecording) return@FaceAnalyzer

                    val currentTime = System.currentTimeMillis()
                    guidanceMessage = result.guidanceMsg

                    // Voice prompts matching Lua timing
                    if (currentTime - lastSpeakTime > settings.promptDelay) {
                        lastSpeakTime = currentTime
                        speakText(result.guidanceMsg)
                    }

                    // Vibrate dynamic pulse frequency based on distance to center
                    if (!result.isCentered && result.faceCount > 0) {
                        val distance = result.distanceToCenter
                        val vibInterval = 100L + (Math.min(distance, 1000f) / 1000f * 800L).toLong()
                        if (currentTime - lastDynamicVibTime > vibInterval) {
                            lastDynamicVibTime = currentTime
                            triggerVibration("pulse")
                        }
                    }

                    // Centered event
                    if (result.isCentered && !isCountingDown) {
                        isCountingDown = true
                        isCapturing = true
                        triggerVibration("center")

                        if (settings.autoCapture == "Off") {
                            speakText("Perfect! Tap screen to capture.")
                            isCountingDown = false
                        } else {
                            if (settings.timerDelay > 0) {
                                val secs = settings.timerDelay / 1000
                                speakText("Capturing in $secs seconds.")
                                Handler(Looper.getMainLooper()).postDelayed({
                                    if (isCountingDown) {
                                        // Take photo
                                        val photoFile = File(context.cacheDir, "Selfie_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                        cameraController.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                                    isCountingDown = false
                                                    isCapturing = false
                                                    onMediaCaptured(photoFile, false)
                                                }

                                                override fun onError(exception: ImageCaptureException) {
                                                    isCountingDown = false
                                                    isCapturing = false
                                                    speakText("Error: ${exception.message}")
                                                }
                                            }
                                        )
                                    }
                                }, settings.timerDelay)
                            } else {
                                speakText("Click!")
                                val photoFile = File(context.cacheDir, "Selfie_${System.currentTimeMillis()}.jpg")
                                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                cameraController.takePicture(
                                    outputOptions,
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                            isCountingDown = false
                                            isCapturing = false
                                            onMediaCaptured(photoFile, false)
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            isCountingDown = false
                                            isCapturing = false
                                            speakText("Error: ${exception.message}")
                                        }
                                    }
                                )
                            }
                        }
                    } else if (!result.isCentered && isCountingDown) {
                        isCountingDown = false
                        isCapturing = false
                        speakText("Timer canceled. You moved.")
                    }
                }
            )
        } else {
            cameraController.clearImageAnalysisAnalyzer()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Camera Preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    controller = cameraController
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                it.controller = cameraController
            }
        )

        // Shutter / Capture Trigger Overlay (when autoCapture is off)
        if (settings.autoCapture == "Off" && isCapturing && !isRecording) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        isCapturing = true
                        speakText("Click!")
                        val photoFile = File(context.cacheDir, "Selfie_${System.currentTimeMillis()}.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                        cameraController.takePicture(
                            outputOptions,
                            ContextCompat.getMainExecutor(context),
                            object : ImageCapture.OnImageSavedCallback {
                                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                    isCapturing = false
                                    onMediaCaptured(photoFile, false)
                                }

                                override fun onError(exception: ImageCaptureException) {
                                    isCapturing = false
                                    speakText("Error: ${exception.message}")
                                }
                            }
                        )
                    }
            )
        }

        // Top Navigation & Settings Row
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // Glassmorphic Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .background(Color(0x66000000), shape = RoundedCornerShape(24.dp))
                    .padding(vertical = 12.dp, horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onExit) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Exit", tint = Color.White)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = {
                            currentFlashIndex = (currentFlashIndex + 1) % 3
                            applyFlashSetting(currentFlashIndex)
                            speakText("Flash " + flashModes[currentFlashIndex])
                        },
                        modifier = Modifier.background(Color(0x44FFFFFF), shape = RoundedCornerShape(12.dp))
                    ) {
                        val flashIcon = when(flashModes[currentFlashIndex]) {
                            "On" -> Icons.Default.FlashOn
                            "Auto" -> Icons.Default.FlashAuto
                            else -> Icons.Default.FlashOff
                        }
                        Icon(flashIcon, contentDescription = "Flash", tint = Color(0xFFFFDD00))
                    }
                    
                    IconButton(
                        onClick = { isFrontCamera = !isFrontCamera },
                        modifier = Modifier.background(Color(0x44FFFFFF), shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera", tint = Color.White)
                    }
                    
                    IconButton(
                        onClick = {
                            showSettingsDialog = true
                            speakText("Settings opened.")
                        },
                        modifier = Modifier.background(Color(0x44FFFFFF), shape = RoundedCornerShape(12.dp))
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                }
            }

            // Guidance Text Display (Pill Shape)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                if (guidanceMessage.isNotEmpty()) {
                    Text(
                        text = guidanceMessage,
                        color = Color(0xFF121212),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .background(Color(0xFF00E5FF), shape = RoundedCornerShape(32.dp))
                            .padding(vertical = 16.dp, horizontal = 24.dp)
                    )
                }
            }

            // Bottom Shutter Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .background(Color(0x66000000), shape = RoundedCornerShape(32.dp))
                        .clip(RoundedCornerShape(32.dp))
                        .clickable {
                            isPhotoMode = !isPhotoMode
                            speakText(if (isPhotoMode) "Mode: Photo" else "Mode: Video")
                        }
                        .padding(vertical = 12.dp, horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isPhotoMode) Icons.Default.PhotoCamera else Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPhotoMode) "PHOTO" else "VIDEO",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            settings = settings,
            tts = tts,
            onDismiss = {
                settings.save(context)
                tts.setSpeechRate(settings.speechRate)
                tts.setPitch(settings.speechPitch)
                speakText("Settings Saved.")
                showSettingsDialog = false
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    settings: CameraSettings,
    tts: TtsHelper,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var framingMode by remember { mutableStateOf(settings.framingMode) }
    var sensitivity by remember { mutableStateOf(settings.sensitivity) }
    var intensity by remember { mutableStateOf(settings.intensity) }
    var promptDelay by remember { mutableStateOf(settings.promptDelay) }
    var quality by remember { mutableStateOf(settings.quality) }
    var ratio by remember { mutableStateOf(settings.ratio) }
    var autoCapture by remember { mutableStateOf(settings.autoCapture) }
    var timerDelay by remember { mutableStateOf(settings.timerDelay) }
    var autoSave by remember { mutableStateOf(settings.autoSave) }
    var lightGuide by remember { mutableStateOf(settings.lightGuide) }
    var voiceGuide by remember { mutableStateOf(settings.voiceGuide) }
    var saveLocation by remember { mutableStateOf(settings.saveLocation) }
    var vibration by remember { mutableStateOf(settings.vibration) }
    var speechRate by remember { mutableStateOf(settings.speechRate) }
    var speechPitch by remember { mutableStateOf(settings.speechPitch) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = {
            settings.framingMode = framingMode
            settings.sensitivity = sensitivity
            settings.intensity = intensity
            settings.promptDelay = promptDelay
            settings.quality = quality
            settings.ratio = ratio
            settings.autoCapture = autoCapture
            settings.timerDelay = timerDelay
            settings.autoSave = autoSave
            settings.lightGuide = lightGuide
            settings.voiceGuide = voiceGuide
            settings.saveLocation = saveLocation
            settings.vibration = vibration
            settings.speechRate = speechRate
            settings.speechPitch = speechPitch
            onDismiss()
        },
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E1E),
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "CAMERA SETTINGS",
                color = Color(0xFF00E5FF),
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Reusable setting row component
            Text("Shot Framing Mode", color = Color.Gray, fontSize = 14.sp)
            FramingModeSpinner(framingMode) { framingMode = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Centering Sensitivity", color = Color.Gray, fontSize = 14.sp)
            SensitivitySpinner(sensitivity) { sensitivity = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Guidance Intensity", color = Color.Gray, fontSize = 14.sp)
            DropdownSpinner(listOf("Low", "Medium", "High"), intensity) { intensity = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Auto-Capture", color = Color.Gray, fontSize = 14.sp)
            DropdownSpinner(listOf("On", "Off"), autoCapture) { autoCapture = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Capture Timer Delay", color = Color.Gray, fontSize = 14.sp)
            TimerDelaySpinner(timerDelay) { timerDelay = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Auto-save Image after Capture", color = Color.Gray, fontSize = 14.sp)
            DropdownSpinner(listOf("On", "Off"), autoSave) { autoSave = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Voice Guidance", color = Color.Gray, fontSize = 14.sp)
            DropdownSpinner(listOf("On", "Off"), voiceGuide) { voiceGuide = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Vibration Mode", color = Color.Gray, fontSize = 14.sp)
            DropdownSpinner(listOf("Off", "Low", "Medium", "High"), vibration) { vibration = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            HorizontalDivider(color = Color(0xFF333333), modifier = Modifier.padding(vertical = 16.dp))
            
            Text("SPEECH CONFIGURATION", color = Color(0xFFFFDD00), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Speech Rate (Speed)", color = Color.Gray, fontSize = 14.sp)
            FloatPercentageSpinner(speechRate) { speechRate = it }
            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Speech Pitch", color = Color.Gray, fontSize = 14.sp)
            FloatPercentageSpinner(speechPitch) { speechPitch = it }
            Spacer(modifier = Modifier.height(24.dp))
            
            Button(
                onClick = {
                    tts.setSpeechRate(speechRate)
                    tts.setPitch(speechPitch)
                    tts.speak("This is a test of your current voice configuration.")
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Test Voice Settings", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
            
            Button(
                onClick = {
                    com.example.testapp.updater.UpdateChecker.checkForUpdate(context, com.example.testapp.BuildConfig.VERSION_CODE, true)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1976D2)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Check for Updates (GitHub)", color = Color.White, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}

@Composable
fun DropdownSpinner(options: List<String>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF444444))
            .clickable { expanded = true }
            .padding(12.dp)
    ) {
        Text(selected, color = Color.White)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF444444))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.White) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun FramingModeSpinner(selected: String, onSelected: (String) -> Unit) {
    val options = listOf("Portrait", "Close-up Selfie", "Chest-Up Shot", "Half Body", "Full Body")
    DropdownSpinner(options, selected, onSelected)
}

@Composable
fun SensitivitySpinner(selected: Int, onSelected: (Int) -> Unit) {
    val map = mapOf("Strict (Center only)" to 70, "Normal" to 100, "Loose" to 150)
    val display = map.entries.firstOrNull { it.value == selected }?.key ?: "Normal"
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF444444))
            .clickable { expanded = true }
            .padding(12.dp)
    ) {
        Text(display, color = Color.White)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF444444))
        ) {
            map.forEach { (lbl, value) ->
                DropdownMenuItem(
                    text = { Text(lbl, color = Color.White) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun TimerDelaySpinner(selected: Long, onSelected: (Long) -> Unit) {
    val map = mapOf("Off (Instant)" to 0L, "3 Seconds" to 3000L, "5 Seconds" to 5000L)
    val display = map.entries.firstOrNull { it.value == selected }?.key ?: "Off (Instant)"
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF444444))
            .clickable { expanded = true }
            .padding(12.dp)
    ) {
        Text(display, color = Color.White)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF444444))
        ) {
            map.forEach { (lbl, value) ->
                DropdownMenuItem(
                    text = { Text(lbl, color = Color.White) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun FloatPercentageSpinner(selected: Float, onSelected: (Float) -> Unit) {
    val map = mapOf(
        "25%" to 0.25f, "50%" to 0.5f, "75%" to 0.75f, "100% (Default)" to 1.0f,
        "125%" to 1.25f, "150%" to 1.5f, "175%" to 1.75f, "200%" to 2.0f
    )
    val display = map.entries.firstOrNull { it.value == selected }?.key ?: "100% (Default)"
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF444444))
            .clickable { expanded = true }
            .padding(12.dp)
    ) {
        Text(display, color = Color.White)
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF444444))
        ) {
            map.forEach { (lbl, value) ->
                DropdownMenuItem(
                    text = { Text(lbl, color = Color.White) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun CaptureResultScreen(
    settings: CameraSettings,
    tts: TtsHelper,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDiscard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val file = TempMediaHolder.file
        if (file != null && file.exists()) {
            if (TempMediaHolder.isVideo) {
                AndroidView(
                    factory = { ctx ->
                        android.widget.VideoView(ctx).apply {
                            setVideoPath(file.absolutePath)
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                start()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .padding(bottom = 20.dp)
                )
            } else {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .padding(bottom = 20.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF00E5FF),
                modifier = Modifier.size(80.dp).padding(bottom = 16.dp)
            )
            Text(
                text = "Media Captured!",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 40.dp)
            )
        }

        Button(
            onClick = onSave,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(vertical = 5.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Icon(Icons.Default.Save, contentDescription = null, tint = Color(0xFF00E5FF))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Save to Gallery", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onShare,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(vertical = 5.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Icon(Icons.Default.Share, contentDescription = null, tint = Color(0xFFFFDD00))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Share", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onDiscard,
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(vertical = 5.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF5252))
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFF5252))
            Spacer(modifier = Modifier.width(12.dp))
            Text("Discard", color = Color(0xFFFF5252), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// Media saving logic
fun saveMediaToGallery(context: Context, file: File, isVideo: Boolean) {
    val resolver = context.contentResolver
    val contentValues = android.content.ContentValues().apply {
        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file.name)
        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/VisionHelper")
        }
    }
    val uri = resolver.insert(
        if (isVideo) android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI else android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        contentValues
    )
    if (uri != null) {
        resolver.openOutputStream(uri).use { outputStream ->
            file.inputStream().use { inputStream ->
                inputStream.copyTo(outputStream!!)
            }
        }
    }
}

// Media sharing logic
fun shareMedia(context: Context, file: File, isVideo: Boolean) {
    // Provide a file sharing Uri using FileProvider
    val authority = "${context.packageName}.fileprovider"
    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (isVideo) "video/mp4" else "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Media"))
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "About Vision Helper",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))
            
            // App Logo / Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(60.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Vision Helper App",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            
            Text(
                text = "Version 1.0",
                color = Color.Gray,
                fontSize = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            
            Spacer(modifier = Modifier.height(32.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        text = "This application is designed specifically for low-vision individuals to assist in daily activities using advanced ML and accessibility features.",
                        color = Color.LightGray,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
