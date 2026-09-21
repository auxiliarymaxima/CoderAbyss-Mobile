package com.coderabyss.mobile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AbyssBlack = Color(0xFF020810)
private val AbyssPanel = Color(0xFF061522)
private val AbyssPanel2 = Color(0xFF081D2C)
private val AbyssBlue = Color(0xFF00CFFF)
private val AbyssBlue2 = Color(0xFF248DFF)
private val AbyssGreen = Color(0xFF38F6B4)
private val AbyssText = Color(0xFFEAF8FF)
private val AbyssMuted = Color(0xFF86A9BB)
private val AbyssDanger = Color(0xFFFF6D79)

private enum class AppPage {
    HOME,
    PROJECTS,
    LIBRARY,
    SETTINGS,
    MODELS,
    FEATURE
}

private enum class FeatureType(
    val label: String,
    val description: String
) {

    APP(
        "Build an app",
        "Plan and generate software using your selected local coding model."
    ),

    VIDEO(
        "Create video",
        "Prepare scripts, scenes and prompts locally. Video model runtime comes next."
    ),

    RESEARCH(
        "Research paper",
        "Draft, structure and analyze documents using a local text model."
    ),

    VISUALS(
        "Create visuals",
        "Prepare image prompts and projects. Local image runtime comes next."
    )
}

class MainActivity :
    ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)

        setContent {

            MaterialTheme(
                colorScheme =
                    darkColorScheme(
                        primary = AbyssBlue,
                        background = AbyssBlack,
                        surface = AbyssPanel
                    )
            ) {
                CoderAbyssApp()
            }
        }
    }
}

@Composable
private fun CoderAbyssApp() {

    val context =
        LocalContext.current

    val modelManager =
        remember {
            OfflineModelManager(
                context.applicationContext
            )
        }

    var page by remember {
        mutableStateOf(
            AppPage.HOME
        )
    }

    var feature by remember {
        mutableStateOf(
            FeatureType.APP
        )
    }

    val selectedBottom =
        when (page) {

            AppPage.HOME -> 0
            AppPage.PROJECTS -> 1
            AppPage.LIBRARY -> 2
            AppPage.SETTINGS -> 3
            else -> -1
        }

    Scaffold(
        containerColor = AbyssBlack,

        bottomBar = {

            AbyssBottomBar(
                selected =
                    selectedBottom,

                onSelected = {

                    page =
                        when (it) {

                            0 -> AppPage.HOME
                            1 -> AppPage.PROJECTS
                            2 -> AppPage.LIBRARY

                            else ->
                                AppPage.SETTINGS
                        }
                }
            )
        }
    ) { padding ->

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF02070D),
                                Color(0xFF02101B),
                                Color(0xFF020810)
                            )
                        )
                    )
        ) {

            when (page) {

                AppPage.HOME ->

                    HomeScreen(
                        modelManager =
                            modelManager,

                        onModels = {
                            page =
                                AppPage.MODELS
                        },

                        onFeature = {
                            feature = it
                            page =
                                AppPage.FEATURE
                        }
                    )

                AppPage.MODELS ->

                    ModelManagerScreen(
                        manager =
                            modelManager,

                        onBack = {
                            page =
                                AppPage.HOME
                        }
                    )

                AppPage.FEATURE ->

                    FeatureWorkspace(
                        feature =
                            feature,

                        manager =
                            modelManager,

                        onBack = {
                            page =
                                AppPage.HOME
                        },

                        onModels = {
                            page =
                                AppPage.MODELS
                        }
                    )

                AppPage.PROJECTS ->

                    PlaceholderScreen(
                        title = "Projects",
                        icon =
                            Icons.Rounded.Folder,
                        subtitle =
                            "Your local Coder Abyss projects will live here."
                    )

                AppPage.LIBRARY ->

                    LibraryScreen(
                        modelManager
                    )

                AppPage.SETTINGS ->

                    SettingsScreen(
                        manager = modelManager,
                        onModels = {
                            page = AppPage.MODELS
                        }
                    )
            }
        }
    }
}


@Composable
private fun HomeScreen(
    modelManager: OfflineModelManager,
    onModels: () -> Unit,
    onFeature: (FeatureType) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val whisperEngine =
        remember {
            WhisperVoiceEngine()
        }

    var recording by remember {
        mutableStateOf(false)
    }

    var transcribing by remember {
        mutableStateOf(false)
    }

    var transcript by remember {
        mutableStateOf("")
    }

    var voiceError by remember {
        mutableStateOf<String?>(null)
    }

    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val microphoneLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            microphoneGranted = granted
        }

    val whisperModel =
        ModelCatalog.models.firstOrNull {
            it.kind == ModelKind.SPEECH &&
            modelManager.isInstalled(it)
        }

    val installedCount =
        modelManager.installedModels().size

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(horizontal = 18.dp)
                .padding(
                    top = 18.dp,
                    bottom = 28.dp
                ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Header()

        Spacer(
            Modifier.height(26.dp)
        )

        AssistantFace()

        Spacer(
            Modifier.height(18.dp)
        )

        MicrophoneButton(
            ready = whisperModel != null,
            onClick = {

                voiceError = null

                if (whisperModel == null) {

                    onModels()

                } else if (!microphoneGranted) {

                    microphoneLauncher.launch(
                        Manifest.permission.RECORD_AUDIO
                    )

                } else if (!recording) {

                    if (
                        whisperEngine.startRecording()
                    ) {
                        recording = true
                        transcript = ""
                    } else {
                        voiceError =
                            "Could not start microphone."
                    }

                } else {

                    recording = false
                    transcribing = true

                    val audio =
                        whisperEngine.stopRecording()

                    scope.launch {

                        try {

                            transcript =
                                whisperEngine.transcribe(
                                    modelManager
                                        .modelFile(
                                            whisperModel
                                        ),
                                    audio
                                )

                        } catch (e: Exception) {

                            voiceError =
                                e.message
                                    ?: "Whisper transcription failed."

                        } finally {

                            transcribing =
                                false
                        }
                    }
                }
            }
        )

        Spacer(
            Modifier.height(8.dp)
        )

        Text(
            text =
                when {
                    recording ->
                        "Listening — tap to stop"

                    transcribing ->
                        "Whisper is transcribing..."

                    whisperModel != null ->
                        "Tap to speak"

                    else ->
                        "Install Whisper"
                },
            color = AbyssText,
            fontWeight =
                FontWeight.SemiBold,
            fontSize = 18.sp
        )

        Spacer(
            Modifier.height(10.dp)
        )

        StatusPill(
            "$installedCount offline model" +
                if (installedCount == 1)
                    " installed"
                else
                    "s installed"
        )

        if (
            transcript.isNotBlank()
        ) {

            Spacer(
                Modifier.height(14.dp)
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(16.dp),
                color =
                    AbyssPanel
            ) {

                Text(
                    transcript,
                    modifier =
                        Modifier.padding(15.dp),
                    color =
                        AbyssText
                )
            }
        }

        voiceError?.let {

            Spacer(
                Modifier.height(10.dp)
            )

            Text(
                it,
                color = AbyssDanger,
                fontSize = 12.sp
            )
        }

        Spacer(
            Modifier.height(28.dp)
        )

        QuickActions(
            onFeature
        )

        Spacer(
            Modifier.height(18.dp)
        )

        ModelsPanel(
            manager = modelManager,
            onManage = onModels
        )

        Spacer(
            Modifier.height(18.dp)
        )

        LocalFirstPanel()

        Spacer(
            Modifier.height(22.dp)
        )
    }
}

@Composable
private fun Header() {

    Row(
        modifier =
            Modifier.fillMaxWidth(),

        verticalAlignment =
            Alignment.CenterVertically
    ) {

        LogoMark(
            Modifier.size(48.dp)
        )

        Spacer(
            Modifier.width(12.dp)
        )

        Column(
            modifier =
                Modifier.weight(1f)
        ) {

            Row {

                Text(
                    "Coder ",
                    color = AbyssText,
                    fontSize = 25.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "Abyss",
                    color = AbyssBlue,
                    fontSize = 25.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Text(
                "YOUR AI COMPANION ON YOUR PHONE",
                color = AbyssMuted,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp
            )
        }

        Surface(
            shape =
                RoundedCornerShape(50),

            color =
                Color(0xFF052232),

            border =
                androidx.compose
                    .foundation
                    .BorderStroke(
                        1.dp,
                        Color(0xFF07688A)
                    )
        ) {

            Row(
                modifier =
                    Modifier.padding(
                        horizontal = 10.dp,
                        vertical = 7.dp
                    ),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Rounded.Lock,
                    null,
                    tint = AbyssBlue,
                    modifier =
                        Modifier.size(15.dp)
                )

                Spacer(
                    Modifier.width(5.dp)
                )

                Text(
                    "Local Only",
                    color =
                        Color(0xFF9EF9DE),
                    fontWeight =
                        FontWeight.Bold,
                    fontSize = 10.sp
                )

                Spacer(
                    Modifier.width(7.dp)
                )

                Box(
                    Modifier
                        .size(7.dp)
                        .background(
                            AbyssGreen,
                            CircleShape
                        )
                )
            }
        }
    }
}

@Composable
private fun LogoMark(
    modifier: Modifier
) {

    Canvas(modifier) {

        drawCircle(
            brush =
                Brush.sweepGradient(
                    listOf(
                        AbyssBlue,
                        AbyssBlue2,
                        AbyssBlue
                    )
                ),

            style =
                Stroke(
                    width =
                        5.dp.toPx(),
                    cap =
                        StrokeCap.Round
                )
        )
    }
}

@Composable
private fun AssistantFace() {

    Box(
        modifier =
            Modifier
                .size(190.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            Color(0xFF063A58),
                            Color(0xFF031421),
                            Color(0xFF01070B)
                        )
                    )
                )
                .border(
                    2.dp,
                    AbyssBlue,
                    CircleShape
                ),

        contentAlignment =
            Alignment.Center
    ) {

        Canvas(
            Modifier.size(130.dp)
        ) {

            val eye =
                34.dp.toPx()

            val stroke =
                7.dp.toPx()

            drawArc(
                color = AbyssBlue,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft =
                    Offset(
                        size.width * .16f,
                        size.height * .26f
                    ),
                size =
                    Size(
                        eye,
                        eye
                    ),
                style =
                    Stroke(
                        stroke,
                        cap =
                            StrokeCap.Round
                    )
            )

            drawArc(
                color = AbyssBlue,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft =
                    Offset(
                        size.width * .58f,
                        size.height * .26f
                    ),
                size =
                    Size(
                        eye,
                        eye
                    ),
                style =
                    Stroke(
                        stroke,
                        cap =
                            StrokeCap.Round
                    )
            )

            drawArc(
                color =
                    Color(0xFF68A8FF),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft =
                    Offset(
                        size.width * .34f,
                        size.height * .57f
                    ),
                size =
                    Size(
                        size.width * .32f,
                        size.height * .18f
                    ),
                style =
                    Stroke(
                        5.dp.toPx(),
                        cap =
                            StrokeCap.Round
                    )
            )
        }
    }
}

@Composable
private fun MicrophoneButton(
    ready: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier =
            Modifier
                .size(118.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            if (ready)
                                Color(0xFF0877A9)
                            else
                                Color(0xFF182B35),

                            Color(0xFF062137)
                        )
                    )
                )
                .border(
                    3.dp,
                    if (ready)
                        AbyssBlue
                    else
                        AbyssMuted,
                    CircleShape
                )
                .clickable {
                    onClick()
                },

        contentAlignment =
            Alignment.Center
    ) {

        Icon(
            Icons.Rounded.Mic,
            "Microphone",
            tint =
                if (ready)
                    Color(0xFF62E8FF)
                else
                    AbyssMuted,
            modifier =
                Modifier.size(53.dp)
        )
    }
}

@Composable
private fun StatusPill(
    text: String
) {

    Surface(
        shape =
            RoundedCornerShape(50),
        color =
            Color(0xFF07283A),
        border =
            androidx.compose.foundation
                .BorderStroke(
                    1.dp,
                    Color(0xFF0B6282)
                )
    ) {

        Row(
            modifier =
                Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 7.dp
                ),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Rounded.Memory,
                null,
                tint = AbyssBlue,
                modifier =
                    Modifier.size(18.dp)
            )

            Spacer(
                Modifier.width(8.dp)
            )

            Text(
                text,
                color =
                    Color(0xFFB8DCE9),
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun QuickActions(
    onClick:
        (FeatureType) -> Unit
) {

    Row(
        modifier =
            Modifier.fillMaxWidth(),

        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        QuickAction(
            Modifier.weight(1f),
            "Build\nan app",
            Icons.Rounded.Code
        ) {
            onClick(
                FeatureType.APP
            )
        }

        QuickAction(
            Modifier.weight(1f),
            "Create\nvideo",
            Icons.Rounded.PlayArrow
        ) {
            onClick(
                FeatureType.VIDEO
            )
        }

        QuickAction(
            Modifier.weight(1f),
            "Research\npaper",
            Icons.Rounded.Description
        ) {
            onClick(
                FeatureType.RESEARCH
            )
        }

        QuickAction(
            Modifier.weight(1f),
            "Create\nvisuals",
            Icons.Rounded.Image
        ) {
            onClick(
                FeatureType.VISUALS
            )
        }
    }
}

@Composable
private fun QuickAction(
    modifier: Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit
) {

    Surface(
        modifier =
            modifier
                .height(108.dp)
                .clickable {
                    onClick()
                },

        shape =
            RoundedCornerShape(17.dp),

        color = AbyssPanel,

        border =
            androidx.compose.foundation
                .BorderStroke(
                    1.dp,
                    Color(0xFF0A587A)
                )
    ) {

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(8.dp),

            horizontalAlignment =
                Alignment.CenterHorizontally,

            verticalArrangement =
                Arrangement.Center
        ) {

            Icon(
                icon,
                title,
                tint = AbyssBlue,
                modifier =
                    Modifier.size(28.dp)
            )

            Spacer(
                Modifier.height(9.dp)
            )

            Text(
                title,
                color = AbyssText,
                textAlign =
                    TextAlign.Center,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight =
                    FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ModelsPanel(
    manager: OfflineModelManager,
    onManage: () -> Unit
) {

    val installed =
        manager.installedModels()

    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(20.dp),

        color = AbyssPanel,

        border =
            androidx.compose.foundation
                .BorderStroke(
                    1.dp,
                    Color(0xFF096283)
                )
    ) {

        Column(
            Modifier.padding(16.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Rounded.ViewInAr,
                    null,
                    tint = AbyssBlue
                )

                Spacer(
                    Modifier.width(10.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        "On-device Models",
                        color = AbyssText,
                        fontWeight =
                            FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        "Download once. Use locally afterwards.",
                        color = AbyssMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    "${installed.size} INSTALLED",
                    color = AbyssBlue,
                    fontSize = 10.sp,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Spacer(
                Modifier.height(14.dp)
            )

            if (installed.isEmpty()) {

                Text(
                    "No offline models installed yet.",
                    color = AbyssMuted,
                    fontSize = 12.sp
                )

            } else {

                installed
                    .take(3)
                    .forEach {

                        CompactModelRow(it)

                        Spacer(
                            Modifier.height(8.dp)
                        )
                    }
            }

            OutlinedButton(
                onClick = onManage,
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(50),
                border =
                    androidx.compose.foundation
                        .BorderStroke(
                            1.dp,
                            AbyssBlue
                        )
            ) {

                Icon(
                    Icons.Rounded.Download,
                    null,
                    tint = AbyssBlue
                )

                Spacer(
                    Modifier.width(8.dp)
                )

                Text(
                    "Manage Models",
                    color = AbyssText
                )
            }
        }
    }
}

@Composable
private fun CompactModelRow(
    model: OfflineModel
) {

    Surface(
        shape =
            RoundedCornerShape(14.dp),

        color = AbyssPanel2
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                if (
                    model.kind ==
                    ModelKind.SPEECH
                )
                    Icons.Rounded.GraphicEq
                else
                    Icons.Rounded.Memory,

                null,
                tint = AbyssBlue
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column(
                Modifier.weight(1f)
            ) {

                Text(
                    model.name,
                    color = AbyssText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    "${model.quant} • ${model.sizeLabel}",
                    color = AbyssMuted,
                    fontSize = 11.sp
                )
            }

            Box(
                Modifier
                    .size(8.dp)
                    .background(
                        AbyssGreen,
                        CircleShape
                    )
            )
        }
    }
}

@Composable
private fun ModelManagerScreen(
    manager: OfflineModelManager,
    onBack: () -> Unit
) {

    var wifiOnly by remember {
        mutableStateOf(
            manager.wifiOnly()
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(18.dp)
    ) {

        ScreenHeader(
            "Model Manager",
            "Install, select and remove local AI models.",
            onBack
        )

        Spacer(
            Modifier.height(18.dp)
        )

        Surface(
            shape =
                RoundedCornerShape(16.dp),
            color = AbyssPanel
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),

                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Rounded.Wifi,
                    null,
                    tint = AbyssBlue
                )

                Spacer(
                    Modifier.width(12.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        "Wi-Fi only downloads",
                        color = AbyssText,
                        fontWeight =
                            FontWeight.SemiBold
                    )

                    Text(
                        "Recommended for multi-gigabyte models.",
                        color = AbyssMuted,
                        fontSize = 11.sp
                    )
                }

                Switch(
                    checked = wifiOnly,

                    onCheckedChange = {

                        wifiOnly = it

                        manager
                            .setWifiOnly(it)
                    }
                )
            }
        }

        Spacer(
            Modifier.height(18.dp)
        )

        Text(
            "AVAILABLE MODELS",
            color = AbyssBlue,
            fontWeight =
                FontWeight.Bold,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )

        Spacer(
            Modifier.height(10.dp)
        )

        ModelCatalog.models
            .forEach { model ->

                ModelCard(
                    manager,
                    model
                )

                Spacer(
                    Modifier.height(12.dp)
                )
            }

        Spacer(
            Modifier.height(12.dp)
        )

        Text(
            "Models are stored in the app's private external-files area. Removing Coder Abyss removes those downloaded files too.",
            color = AbyssMuted,
            fontSize = 11.sp
        )

        Spacer(
            Modifier.height(30.dp)
        )
    }
}

@Composable
private fun ModelCard(
    manager: OfflineModelManager,
    model: OfflineModel
) {

    val transfer by
        produceState(
            initialValue =
                manager.state(model),
            key1 = model.id
        ) {

            while (true) {

                value =
                    manager.state(model)

                delay(1000)
            }
        }

    var selectedId by remember {
        mutableStateOf(
            manager.selectedModelId()
        )
    }

    Surface(
        shape =
            RoundedCornerShape(18.dp),

        color = AbyssPanel,

        border =
            androidx.compose.foundation
                .BorderStroke(
                    1.dp,
                    if (
                        selectedId ==
                        model.id
                    )
                        AbyssGreen
                    else
                        Color(0xFF17425A)
                )
    ) {

        Column(
            Modifier.padding(15.dp)
        ) {

            Row(
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    when (
                        model.kind
                    ) {

                        ModelKind.SPEECH ->
                            Icons.Rounded.GraphicEq

                        ModelKind.IMAGE ->
                            Icons.Rounded.Image

                        ModelKind.VIDEO ->
                            Icons.Rounded.Movie

                        else ->
                            Icons.Rounded.Memory
                    },

                    null,
                    tint = AbyssBlue
                )

                Spacer(
                    Modifier.width(12.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        model.name,
                        color = AbyssText,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        model.purpose,
                        color = AbyssMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    model.sizeLabel,
                    color = AbyssBlue,
                    fontSize = 11.sp
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                "${model.family} • ${model.quant}",
                color = AbyssMuted,
                fontSize = 11.sp
            )

            if (
                transfer.status ==
                    TransferStatus.DOWNLOADING ||
                transfer.status ==
                    TransferStatus.QUEUED ||
                transfer.status ==
                    TransferStatus.PAUSED
            ) {

                Spacer(
                    Modifier.height(12.dp)
                )

                LinearProgressIndicator(
                    progress = {
                        transfer.progress /
                            100f
                    },
                    modifier =
                        Modifier.fillMaxWidth()
                )

                Spacer(
                    Modifier.height(5.dp)
                )

                Text(
                    if (
                        transfer.status ==
                        TransferStatus.QUEUED
                    )
                        "Queued..."
                    else
                        "${transfer.progress}% downloaded",

                    color = AbyssBlue,
                    fontSize = 11.sp
                )
            }

            if (
                transfer.status ==
                    TransferStatus.FAILED
            ) {

                Spacer(
                    Modifier.height(8.dp)
                )

                Text(
                    "Download failed. Tap Retry.",
                    color = AbyssDanger,
                    fontSize = 11.sp
                )
            }

            Spacer(
                Modifier.height(12.dp)
            )

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                when (
                    transfer.status
                ) {

                    TransferStatus.INSTALLED -> {

                        if (
                            model.kind ==
                                ModelKind.TEXT
                        ) {

                            Button(
                                onClick = {

                                    manager
                                        .select(model)

                                    selectedId =
                                        model.id
                                },

                                modifier =
                                    Modifier.weight(1f)
                            ) {

                                Text(
                                    if (
                                        selectedId ==
                                        model.id
                                    )
                                        "Selected"
                                    else
                                        "Use Model"
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = {

                                manager
                                    .remove(model)

                                selectedId =
                                    manager
                                        .selectedModelId()
                            },

                            modifier =
                                Modifier.weight(1f)
                        ) {

                            Text(
                                "Remove"
                            )
                        }
                    }

                    TransferStatus.DOWNLOADING,
                    TransferStatus.QUEUED,
                    TransferStatus.PAUSED -> {

                        OutlinedButton(
                            onClick = {
                                manager
                                    .cancelDownload(
                                        model
                                    )
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Text(
                                "Cancel"
                            )
                        }
                    }

                    else -> {

                        Button(
                            onClick = {

                                manager
                                    .startDownload(
                                        model
                                    )
                            },

                            modifier =
                                Modifier.fillMaxWidth()
                        ) {

                            Icon(
                                Icons.Rounded.Download,
                                null
                            )

                            Spacer(
                                Modifier.width(8.dp)
                            )

                            Text(
                                if (
                                    transfer.status ==
                                    TransferStatus.FAILED
                                )
                                    "Retry"
                                else
                                    "Download"
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun FeatureWorkspace(
    feature: FeatureType,
    manager: OfflineModelManager,
    onBack: () -> Unit,
    onModels: () -> Unit
) {

    if (feature == FeatureType.VIDEO) {

        WanVideoWorkspace(
            onBack = onBack
        )

        return
    }

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val engine =
        remember {
            LocalLlmEngine(
                context.applicationContext
            )
        }

    val selected =
        manager.selectedTextModel()

    var prompt by remember {
        mutableStateOf("")
    }

    var output by remember {
        mutableStateOf("")
    }

    var running by remember {
        mutableStateOf(false)
    }

    var error by remember {
        mutableStateOf<String?>(null)
    }

    var generationJob by remember {
        mutableStateOf<Job?>(null)
    }

    val systemPrompt =
        when (feature) {

            FeatureType.APP ->
                """
                You are Coder Abyss, a senior software engineer running locally
                on an Android phone.

                Turn the user's request into practical software.

                Give:
                1. architecture
                2. files
                3. complete code where practical
                4. build/run instructions
                5. testing notes

                Prefer concise, functional solutions.
                """.trimIndent()

            FeatureType.RESEARCH ->
                """
                You are Coder Abyss local research assistant.
                Produce well structured research, clearly separate facts,
                assumptions and conclusions, and do not invent citations.
                """.trimIndent()

            FeatureType.VISUALS ->
                """
                You are Coder Abyss local visual design assistant.
                Turn the request into detailed professional image-generation
                prompts, layouts and visual specifications.
                """.trimIndent()

            else ->
                "You are Coder Abyss."
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(18.dp)
    ) {

        ScreenHeader(
            feature.label,
            feature.description,
            onBack
        )

        Spacer(
            Modifier.height(20.dp)
        )

        Surface(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(18.dp),
            color =
                AbyssPanel
        ) {

            Column(
                Modifier.padding(16.dp)
            ) {

                Text(
                    selected?.name
                        ?: "No local model selected",
                    color =
                        AbyssText,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    Modifier.height(4.dp)
                )

                Text(
                    selected?.let {
                        "${it.quant} • ${it.sizeLabel} • OFFLINE"
                    } ?: "Choose an installed GGUF model.",
                    color =
                        AbyssMuted,
                    fontSize =
                        11.sp
                )

                Spacer(
                    Modifier.height(10.dp)
                )

                OutlinedButton(
                    onClick =
                        onModels
                ) {

                    Text(
                        "Change Model"
                    )
                }
            }
        }

        Spacer(
            Modifier.height(18.dp)
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(190.dp),
            label = {
                Text(
                    when (feature) {
                        FeatureType.APP ->
                            "Describe the app you want"

                        FeatureType.RESEARCH ->
                            "Enter your research request"

                        FeatureType.VISUALS ->
                            "Describe the visual"

                        else ->
                            "Prompt"
                    }
                )
            }
        )

        Spacer(
            Modifier.height(14.dp)
        )

        if (!running) {

            Button(
                onClick = {

                    if (selected == null) {
                        onModels()
                        return@Button
                    }

                    if (prompt.isBlank()) {
                        error =
                            "Enter a prompt first."
                        return@Button
                    }

                    error = null
                    output = ""
                    running = true

                    generationJob =
                        scope.launch {

                            try {

                                engine.generate(
                                    modelPath =
                                        manager
                                            .modelFile(
                                                selected
                                            )
                                            .absolutePath,
                                    systemPrompt =
                                        systemPrompt,
                                    prompt =
                                        prompt
                                ) { token ->

                                    output += token
                                }

                            } catch (
                                e: Exception
                            ) {

                                error =
                                    e.message
                                        ?: "Local generation failed."

                            } finally {

                                running =
                                    false
                            }
                        }
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Icon(
                    Icons.Rounded.PlayArrow,
                    null
                )

                Spacer(
                    Modifier.width(8.dp)
                )

                Text(
                    if (selected == null)
                        "Select Offline Model"
                    else
                        "Run Prompt Offline"
                )
            }

        } else {

            Button(
                onClick = {

                    generationJob?.cancel()
                    running = false
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Icon(
                    Icons.Rounded.Stop,
                    null
                )

                Spacer(
                    Modifier.width(8.dp)
                )

                Text(
                    "Stop Generation"
                )
            }
        }

        if (running) {

            Spacer(
                Modifier.height(10.dp)
            )

            LinearProgressIndicator(
                modifier =
                    Modifier.fillMaxWidth()
            )
        }

        error?.let {

            Spacer(
                Modifier.height(12.dp)
            )

            Text(
                it,
                color =
                    AbyssDanger
            )
        }

        if (
            output.isNotBlank()
        ) {

            Spacer(
                Modifier.height(18.dp)
            )

            Text(
                "OUTPUT",
                color =
                    AbyssBlue,
                fontWeight =
                    FontWeight.Bold,
                fontSize =
                    12.sp
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Surface(
                modifier =
                    Modifier.fillMaxWidth(),
                shape =
                    RoundedCornerShape(16.dp),
                color =
                    AbyssPanel
            ) {

                Text(
                    output,
                    modifier =
                        Modifier.padding(16.dp),
                    color =
                        AbyssText
                )
            }

            Spacer(
                Modifier.height(8.dp)
            )

            OutlinedButton(
                onClick = {
                    output = ""
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    "Clear Output"
                )
            }
        }

        Spacer(
            Modifier.height(30.dp)
        )
    }
}

@Composable
private fun WanVideoWorkspace(
    onBack: () -> Unit
) {

    val context =
        LocalContext.current

    val scope =
        rememberCoroutineScope()

    val client =
        remember {
            WanVideoClient(
                context.applicationContext
            )
        }

    var prompt by remember {
        mutableStateOf("")
    }

    var hfToken by remember {
        mutableStateOf("")
    }

    var status by remember {
        mutableStateOf(
            "Ready"
        )
    }

    var generating by remember {
        mutableStateOf(false)
    }

    var videoUri by remember {
        mutableStateOf<android.net.Uri?>(null)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(18.dp)
    ) {

        ScreenHeader(
            "Create video",
            "Official Wan-AI 2.1 through Hugging Face Inference Providers.",
            onBack
        )

        Spacer(
            Modifier.height(20.dp)
        )

        Surface(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(16.dp),
            color =
                AbyssPanel
        ) {

            Column(
                Modifier.padding(15.dp)
            ) {

                Text(
                    "Wan-AI / Wan2.1 T2V 1.3B",
                    color =
                        AbyssText,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    "Hosted by Hugging Face Inference Providers / Fal AI",
                    color =
                        AbyssMuted,
                    fontSize =
                        11.sp
                )
            }
        }

        Spacer(
            Modifier.height(16.dp)
        )

        OutlinedTextField(
            value =
                hfToken,
            onValueChange = {
                hfToken = it.trim()
            },
            modifier =
                Modifier.fillMaxWidth(),
            label = {
                Text(
                    "Hugging Face token"
                )
            },
            visualTransformation =
                PasswordVisualTransformation(),
            singleLine = true
        )

        Spacer(
            Modifier.height(14.dp)
        )

        OutlinedTextField(
            value = prompt,
            onValueChange = {
                prompt = it
            },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            label = {
                Text(
                    "Describe your video"
                )
            }
        )

        Spacer(
            Modifier.height(14.dp)
        )

        Button(
            enabled =
                !generating &&
                hfToken.isNotBlank() &&
                prompt.isNotBlank(),
            onClick = {

                generating = true
                videoUri = null

                scope.launch {

                    try {

                        videoUri =
                            client.generate(
                                hfToken =
                                    hfToken,
                                prompt =
                                    prompt
                            ) {
                                status = it
                            }

                        status =
                            "Saved to Movies/CoderAbyss"

                    } catch (
                        e: Exception
                    ) {

                        status =
                            e.message
                                ?: "Video generation failed."

                    } finally {

                        generating =
                            false
                    }
                }
            },
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                if (generating)
                    "Generating..."
                else
                    "Generate with Wan"
            )
        }

        Spacer(
            Modifier.height(12.dp)
        )

        Text(
            status,
            color =
                if (
                    status.contains(
                        "failed",
                        true
                    ) ||
                    status.contains(
                        "error",
                        true
                    )
                )
                    AbyssDanger
                else
                    AbyssMuted
        )

        videoUri?.let { uri ->

            Spacer(
                Modifier.height(14.dp)
            )

            Button(
                onClick = {

                    val intent =
                        Intent(
                            Intent.ACTION_VIEW
                        ).apply {

                            setDataAndType(
                                uri,
                                "video/mp4"
                            )

                            addFlags(
                                Intent.FLAG_GRANT_READ_URI_PERMISSION
                            )
                        }

                    context.startActivity(
                        intent
                    )
                },
                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    "Open Generated Video"
                )
            }
        }

        Spacer(
            Modifier.height(30.dp)
        )
    }
}

@Composable
private fun LibraryScreen(
    manager: OfflineModelManager
) {

    val models =
        manager.installedModels()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(18.dp)
    ) {

        Text(
            "Library",
            color = AbyssText,
            fontSize = 27.sp,
            fontWeight =
                FontWeight.Bold
        )

        Text(
            "Your downloaded local AI assets.",
            color = AbyssMuted
        )

        Spacer(
            Modifier.height(22.dp)
        )

        if (models.isEmpty()) {

            Text(
                "Your offline library is empty.",
                color = AbyssMuted
            )

        } else {

            models.forEach {

                CompactModelRow(it)

                Spacer(
                    Modifier.height(10.dp)
                )
            }
        }
    }
}


@Composable
private fun SettingsScreen(
    manager: OfflineModelManager,
    onModels: () -> Unit
) {

    var wifiOnly by remember {
        mutableStateOf(
            manager.wifiOnly()
        )
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(
                    rememberScrollState()
                )
                .padding(18.dp)
    ) {

        Text(
            "Settings",
            color =
                AbyssText,
            fontSize =
                27.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(20.dp)
        )

        Button(
            onClick =
                onModels,
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Icon(
                Icons.Rounded.Memory,
                null
            )

            Spacer(
                Modifier.width(8.dp)
            )

            Text(
                "Model Manager"
            )
        }

        Spacer(
            Modifier.height(12.dp)
        )

        SettingCard(
            icon =
                Icons.Rounded.Security,
            title =
                "Local First",
            subtitle =
                "Downloaded GGUF and Whisper models remain on this device."
        )

        Spacer(
            Modifier.height(12.dp)
        )

        Surface(
            shape =
                RoundedCornerShape(16.dp),
            color =
                AbyssPanel
        ) {

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(15.dp),
                verticalAlignment =
                    Alignment.CenterVertically
            ) {

                Icon(
                    Icons.Rounded.Wifi,
                    null,
                    tint =
                        AbyssBlue
                )

                Spacer(
                    Modifier.width(12.dp)
                )

                Column(
                    Modifier.weight(1f)
                ) {

                    Text(
                        "Wi-Fi only model downloads",
                        color =
                            AbyssText
                    )

                    Text(
                        "Avoid multi-gigabyte model downloads over mobile data.",
                        color =
                            AbyssMuted,
                        fontSize =
                            11.sp
                    )
                }

                Switch(
                    checked =
                        wifiOnly,
                    onCheckedChange = {

                        wifiOnly = it
                        manager.setWifiOnly(
                            it
                        )
                    }
                )
            }
        }

        Spacer(
            Modifier.height(12.dp)
        )

        SettingCard(
            icon =
                Icons.Rounded.Cloud,
            title =
                "Online AI Providers",
            subtitle =
                "Wan video uses Hugging Face only when requested. GPT, Claude and other API providers can be added later."
        )

        Spacer(
            Modifier.height(20.dp)
        )

        Text(
            "Model storage",
            color =
                AbyssBlue,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            Modifier.height(5.dp)
        )

        Text(
            manager
                .modelDirectory
                .absolutePath,
            color =
                AbyssMuted,
            fontSize =
                11.sp
        )
    }
}

@Composable
private fun SettingCard(
    icon: ImageVector,
    title: String,
    subtitle: String
) {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(16.dp),
        color = AbyssPanel
    ) {

        Row(
            Modifier.padding(15.dp)
        ) {

            Icon(
                icon,
                null,
                tint = AbyssBlue
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column {

                Text(
                    title,
                    color = AbyssText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    subtitle,
                    color = AbyssMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun LocalFirstPanel() {

    Surface(
        modifier =
            Modifier.fillMaxWidth(),

        shape =
            RoundedCornerShape(17.dp),

        color = AbyssPanel
    ) {

        Row(
            modifier =
                Modifier.padding(15.dp),

            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                Icons.Rounded.Lock,
                null,
                tint = AbyssBlue,
                modifier =
                    Modifier.size(27.dp)
            )

            Spacer(
                Modifier.width(12.dp)
            )

            Column {

                Text(
                    "Local First",
                    color = AbyssText,
                    fontWeight =
                        FontWeight.SemiBold
                )

                Text(
                    "Internet is needed for model downloads only. Local inference is the primary design.",
                    color = AbyssMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun ScreenHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {

    Row(
        verticalAlignment =
            Alignment.CenterVertically
    ) {

        IconButton(
            onClick = onBack
        ) {

            Icon(
                Icons.Rounded.ArrowBack,
                "Back",
                tint = AbyssBlue
            )
        }

        Spacer(
            Modifier.width(5.dp)
        )

        Column {

            Text(
                title,
                color = AbyssText,
                fontSize = 24.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Text(
                subtitle,
                color = AbyssMuted,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun AbyssBottomBar(
    selected: Int,
    onSelected:
        (Int) -> Unit
) {

    val items =
        listOf(
            "Home" to
                Icons.Rounded.Home,

            "Projects" to
                Icons.Rounded.Folder,

            "Library" to
                Icons.Rounded.Description,

            "Settings" to
                Icons.Rounded.Settings
        )

    NavigationBar(
        containerColor =
            Color(0xFF020A10)
    ) {

        items
            .forEachIndexed {
                    index,
                    item ->

                NavigationBarItem(
                    selected =
                        selected ==
                            index,

                    onClick = {
                        onSelected(
                            index
                        )
                    },

                    icon = {

                        Icon(
                            item.second,
                            item.first
                        )
                    },

                    label = {

                        Text(
                            item.first,
                            fontSize = 10.sp
                        )
                    },

                    colors =
                        NavigationBarItemDefaults
                            .colors(
                                selectedIconColor =
                                    AbyssBlue,

                                selectedTextColor =
                                    AbyssBlue,

                                unselectedIconColor =
                                    AbyssMuted,

                                unselectedTextColor =
                                    AbyssMuted,

                                indicatorColor =
                                    Color(
                                        0xFF062538
                                    )
                            )
                )
            }
    }
}

@Composable
private fun PlaceholderScreen(
    title: String,
    icon: ImageVector,
    subtitle: String
) {

    Box(
        Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                icon,
                null,
                tint = AbyssBlue,
                modifier =
                    Modifier.size(60.dp)
            )

            Spacer(
                Modifier.height(14.dp)
            )

            Text(
                title,
                color = AbyssText,
                fontSize = 24.sp,
                fontWeight =
                    FontWeight.Bold
            )

            Spacer(
                Modifier.height(8.dp)
            )

            Text(
                subtitle,
                color = AbyssMuted,
                textAlign =
                    TextAlign.Center,
                modifier =
                    Modifier.padding(
                        horizontal = 30.dp
                    )
            )
        }
    }
}
