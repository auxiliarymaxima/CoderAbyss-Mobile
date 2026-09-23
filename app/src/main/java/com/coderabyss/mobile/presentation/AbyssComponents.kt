package com.coderabyss.mobile.presentation

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

import com.coderabyss.mobile.*

internal val AbyssBlack = Color(0xFF020810)
internal val AbyssPanel = Color(0xFF061522)
internal val AbyssPanel2 = Color(0xFF081D2C)
internal val AbyssBlue = Color(0xFF00CFFF)
internal val AbyssBlue2 = Color(0xFF248DFF)
internal val AbyssGreen = Color(0xFF38F6B4)
internal val AbyssText = Color(0xFFEAF8FF)
internal val AbyssMuted = Color(0xFF86A9BB)
internal val AbyssDanger = Color(0xFFFF6D79)

internal enum class FeatureType { APP, VIDEO, RESEARCH, VISUALS }

@Composable
internal fun Header() {

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
                    if (VideoBackendSettings(LocalContext.current).localOnly) "Local Only" else "Hybrid AI",
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
internal fun LogoMark(
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
internal fun AssistantFace() {

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
internal fun MicrophoneButton(
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
internal fun StatusPill(
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
internal fun QuickActions(
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
internal fun QuickAction(
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
internal fun ModelsPanel(
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
internal fun CompactModelRow(
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
internal fun SettingCard(
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
internal fun ScreenHeader(
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
internal fun AbyssBottomBar(
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

            "AI Models" to
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
