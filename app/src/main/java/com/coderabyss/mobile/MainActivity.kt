package com.coderabyss.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.core.content.ContextCompat

private val AbyssBlack = Color(0xFF020810)
private val AbyssPanel = Color(0xFF061522)
private val AbyssPanel2 = Color(0xFF081D2C)
private val AbyssBlue = Color(0xFF00CFFF)
private val AbyssBlue2 = Color(0xFF248DFF)
private val AbyssGreen = Color(0xFF38F6B4)
private val AbyssText = Color(0xFFEAF8FF)
private val AbyssMuted = Color(0xFF86A9BB)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
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
fun CoderAbyssApp() {

    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        containerColor = AbyssBlack,
        bottomBar = {
            AbyssBottomBar(
                selected = selectedTab,
                onSelected = { selectedTab = it }
            )
        }
    ) { padding ->

        Box(
            modifier = Modifier
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

            when (selectedTab) {
                0 -> HomeScreen()
                1 -> PlaceholderScreen(
                    title = "Projects",
                    icon = Icons.Rounded.Folder
                )
                2 -> PlaceholderScreen(
                    title = "Library",
                    icon = Icons.Rounded.Description
                )
                else -> PlaceholderScreen(
                    title = "Settings",
                    icon = Icons.Rounded.Settings
                )
            }
        }
    }
}

@Composable
fun HomeScreen() {

    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }

    val microphoneLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            listening = granted
        }

    fun microphonePressed() {

        val granted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            listening = !listening
        } else {
            microphoneLauncher.launch(
                Manifest.permission.RECORD_AUDIO
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Header()

        Spacer(Modifier.height(26.dp))

        AssistantFace()

        Spacer(Modifier.height(18.dp))

        MicrophoneButton(
            listening = listening,
            onClick = { microphonePressed() }
        )

        Spacer(Modifier.height(7.dp))

        Text(
            text =
                if (listening)
                    "Listening with Whisper..."
                else
                    "Tap to speak",
            color = AbyssText,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp
        )

        Spacer(Modifier.height(9.dp))

        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF07283A),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF0B6282)
            )
        ) {

            Row(
                modifier = Modifier.padding(
                    horizontal = 16.dp,
                    vertical = 7.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = AbyssBlue,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text =
                        if (listening)
                            "Whisper microphone active"
                        else
                            "Whisper ready on-device",
                    color = Color(0xFFB8DCE9),
                    fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        QuickActions()

        Spacer(Modifier.height(18.dp))

        ModelsPanel()

        Spacer(Modifier.height(16.dp))

        InternetPanel()

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Local mode is primary. Internet is optional.",
            color = AbyssMuted,
            fontSize = 11.sp
        )
    }
}

@Composable
fun Header() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {

        LogoMark(
            modifier = Modifier.size(46.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Row {

                Text(
                    text = "Coder ",
                    color = AbyssText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Abyss",
                    color = AbyssBlue,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "YOUR AI COMPANION ON YOUR PHONE",
                color = AbyssMuted,
                fontSize = 9.sp,
                letterSpacing = 1.5.sp
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = Color(0xFF052232),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF07688A)
            )
        ) {

            Row(
                modifier = Modifier.padding(
                    horizontal = 10.dp,
                    vertical = 7.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = AbyssBlue,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(Modifier.width(5.dp))

                Text(
                    text = "Local Only",
                    color = Color(0xFF9EF9DE),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )

                Spacer(Modifier.width(6.dp))

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
fun LogoMark(
    modifier: Modifier = Modifier
) {

    Canvas(modifier = modifier) {

        drawCircle(
            brush = Brush.sweepGradient(
                listOf(
                    AbyssBlue,
                    AbyssBlue2,
                    AbyssBlue
                )
            ),
            style = Stroke(
                width = 5.dp.toPx(),
                cap = StrokeCap.Round
            )
        )

        drawArc(
            color = AbyssBlue,
            startAngle = 205f,
            sweepAngle = 80f,
            useCenter = false,
            topLeft = Offset(
                size.width * 0.05f,
                size.height * 0.05f
            ),
            size = Size(
                size.width * 0.9f,
                size.height * 0.9f
            ),
            style = Stroke(
                width = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        )
    }
}

@Composable
fun AssistantFace() {

    Box(
        modifier = Modifier
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
        contentAlignment = Alignment.Center
    ) {

        Canvas(
            modifier = Modifier.size(130.dp)
        ) {

            val eyeWidth = 34.dp.toPx()
            val stroke = 7.dp.toPx()

            drawArc(
                color = AbyssBlue,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(
                    size.width * .16f,
                    size.height * .26f
                ),
                size = Size(
                    eyeWidth,
                    eyeWidth
                ),
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round
                )
            )

            drawArc(
                color = AbyssBlue,
                startAngle = 190f,
                sweepAngle = 160f,
                useCenter = false,
                topLeft = Offset(
                    size.width * .58f,
                    size.height * .26f
                ),
                size = Size(
                    eyeWidth,
                    eyeWidth
                ),
                style = Stroke(
                    width = stroke,
                    cap = StrokeCap.Round
                )
            )

            drawArc(
                color = Color(0xFF68A8FF),
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(
                    size.width * .34f,
                    size.height * .57f
                ),
                size = Size(
                    size.width * .32f,
                    size.height * .18f
                ),
                style = Stroke(
                    width = 5.dp.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

@Composable
fun MicrophoneButton(
    listening: Boolean,
    onClick: () -> Unit
) {

    Box(
        modifier = Modifier
            .size(118.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        if (listening)
                            Color(0xFF08775F)
                        else
                            Color(0xFF0877A9),
                        Color(0xFF062137)
                    )
                )
            )
            .border(
                3.dp,
                if (listening)
                    AbyssGreen
                else
                    AbyssBlue,
                CircleShape
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {

        Icon(
            imageVector =
                if (listening)
                    Icons.Rounded.GraphicEq
                else
                    Icons.Rounded.Mic,
            contentDescription = "Microphone",
            tint =
                if (listening)
                    AbyssGreen
                else
                    Color(0xFF62E8FF),
            modifier = Modifier.size(53.dp)
        )
    }
}

@Composable
fun QuickActions() {

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(8.dp)
    ) {

        QuickAction(
            modifier = Modifier.weight(1f),
            title = "Build\nan app",
            icon = Icons.Rounded.Code
        )

        QuickAction(
            modifier = Modifier.weight(1f),
            title = "Create\nvideo",
            icon = Icons.Rounded.PlayArrow
        )

        QuickAction(
            modifier = Modifier.weight(1f),
            title = "Research\npaper",
            icon = Icons.Rounded.Description
        )

        QuickAction(
            modifier = Modifier.weight(1f),
            title = "Create\nvisuals",
            icon = Icons.Rounded.Image
        )
    }
}

@Composable
fun QuickAction(
    modifier: Modifier,
    title: String,
    icon: ImageVector
) {

    Surface(
        modifier = modifier.height(108.dp),
        shape = RoundedCornerShape(17.dp),
        color = AbyssPanel,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF0A587A)
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = AbyssBlue,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.height(9.dp))

            Text(
                text = title,
                color = AbyssText,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun ModelsPanel() {

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = AbyssPanel,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            Color(0xFF096283)
        )
    ) {

        Column(
            modifier = Modifier.padding(16.dp)
        ) {

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {

                Icon(
                    imageVector = Icons.Rounded.ViewInAr,
                    contentDescription = null,
                    tint = AbyssBlue
                )

                Spacer(Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {

                    Text(
                        text = "On-device Models",
                        color = AbyssText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )

                    Text(
                        text = "AI models running locally on your phone.",
                        color = AbyssMuted,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = "3 READY",
                    color = AbyssBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }

            Spacer(Modifier.height(13.dp))

            ModelRow(
                icon = Icons.Rounded.Code,
                name = "Qwen 2.5 Coder",
                details = "7B  •  4.1 GB"
            )

            Spacer(Modifier.height(8.dp))

            ModelRow(
                icon = Icons.Rounded.GraphicEq,
                name = "Whisper",
                details = "Base  •  0.8 GB"
            )

            Spacer(Modifier.height(8.dp))

            ModelRow(
                icon = Icons.Rounded.Image,
                name = "SDXL Turbo",
                details = "Text-to-Image  •  2.6 GB"
            )

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                border =
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        AbyssBlue
                    )
            ) {

                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = AbyssBlue,
                    modifier = Modifier.size(18.dp)
                )

                Spacer(Modifier.width(8.dp))

                Text(
                    text = "Manage Models",
                    color = AbyssText
                )
            }
        }
    }
}

@Composable
fun ModelRow(
    icon: ImageVector,
    name: String,
    details: String
) {

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = AbyssPanel2,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF18364A)
            )
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AbyssBlue,
                modifier = Modifier.size(28.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = name,
                    color = AbyssText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )

                Text(
                    text = details,
                    color = AbyssMuted,
                    fontSize = 11.sp
                )
            }

            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        AbyssGreen,
                        CircleShape
                    )
            )

            Spacer(Modifier.width(7.dp))

            Text(
                text = "Ready",
                color = AbyssGreen,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun InternetPanel() {

    var enabled by remember {
        mutableStateOf(false)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(17.dp),
        color = AbyssPanel,
        border =
            androidx.compose.foundation.BorderStroke(
                1.dp,
                Color(0xFF153B50)
            )
    ) {

        Row(
            modifier = Modifier.padding(15.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Icon(
                imageVector = Icons.Rounded.Language,
                contentDescription = null,
                tint = AbyssBlue,
                modifier = Modifier.size(27.dp)
            )

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Internet-Enhanced Mode",
                    color = AbyssText,
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "Access online services only when allowed.",
                    color = AbyssMuted,
                    fontSize = 11.sp
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                }
            )
        }
    }
}

@Composable
fun AbyssBottomBar(
    selected: Int,
    onSelected: (Int) -> Unit
) {

    val items = listOf(
        Pair("Home", Icons.Rounded.Home),
        Pair("Projects", Icons.Rounded.Folder),
        Pair("Library", Icons.Rounded.Description),
        Pair("Settings", Icons.Rounded.Settings)
    )

    NavigationBar(
        containerColor = Color(0xFF020A10)
    ) {

        items.forEachIndexed {
                index,
                item ->

            NavigationBarItem(
                selected = selected == index,
                onClick = {
                    onSelected(index)
                },
                icon = {

                    Icon(
                        imageVector = item.second,
                        contentDescription = item.first
                    )
                },
                label = {

                    Text(
                        text = item.first,
                        fontSize = 10.sp
                    )
                },
                colors =
                    NavigationBarItemDefaults.colors(
                        selectedIconColor = AbyssBlue,
                        selectedTextColor = AbyssBlue,
                        unselectedIconColor = AbyssMuted,
                        unselectedTextColor = AbyssMuted,
                        indicatorColor =
                            Color(0xFF062538)
                    )
            )
        }
    }
}

@Composable
fun PlaceholderScreen(
    title: String,
    icon: ImageVector
) {

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        Column(
            horizontalAlignment =
                Alignment.CenterHorizontally
        ) {

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AbyssBlue,
                modifier = Modifier.size(60.dp)
            )

            Spacer(Modifier.height(14.dp))

            Text(
                text = title,
                color = AbyssText,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "Coder Abyss",
                color = AbyssMuted
            )
        }
    }
}
