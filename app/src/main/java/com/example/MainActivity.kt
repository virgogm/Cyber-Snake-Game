package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.StageRecord
import com.example.ui.*
import com.example.ui.theme.*
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = CyberBlack
                ) {
                    CyberSnakeApp()
                }
            }
        }
    }
}

@Composable
fun CyberSnakeApp(viewModel: GameViewModel = viewModel()) {
    val screen by viewModel.screen.collectAsState()
    val themeState by viewModel.theme.collectAsState()

    // Map themes to background aesthetics and canvas grid colors
    val themeColorTokens = remember(themeState) {
        when (themeState) {
            GameTheme.CYBERPUNK -> Triple(NeonCyan, NeonPink, NeonGreen)
            GameTheme.MATRIX -> Triple(Color(0xFF00FF00), Color(0xFF005500), Color(0xFF00FF33))
            GameTheme.SUNSET -> Triple(Color(0xFFFF8C00), Color(0xFFFF007F), Color(0xFFFFF200))
            GameTheme.OBSIDIAN -> Triple(Color.White, Color(0xFF444444), Color(0xFFCCCCCC))
        }
    }

    val primaryColor = themeColorTokens.first
    val secondaryColor = themeColorTokens.second
    val accentColor = themeColorTokens.third

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(CyberDark, CyberBlack)
                )
            )
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant HUD Screen block
            HUDBar(viewModel = viewModel, primaryColor = primaryColor, secondaryColor = secondaryColor)

            Spacer(modifier = Modifier.height(12.dp))

            // Main Screens Controller
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (screen) {
                    GameScreen.START_TRIGGER -> {
                        StartScreen(
                            onStartClick = { viewModel.navigateToModesMenu() },
                            primaryColor = primaryColor
                        )
                    }
                    GameScreen.MODES_MENU -> {
                        ModesMenuScreen(
                            viewModel = viewModel,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            accentColor = accentColor
                        )
                    }
                    GameScreen.SUB_MENU -> {
                        SubMenuScreen(
                            viewModel = viewModel,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor
                        )
                    }
                    GameScreen.IN_GAME, GameScreen.PAUSED -> {
                        GamePlayArea(
                            viewModel = viewModel,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor,
                            accentColor = accentColor
                        )
                    }
                    GameScreen.STAGE_WIN -> {
                        StageWinScreen(
                            viewModel = viewModel,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor
                        )
                    }
                    GameScreen.GAME_OVER -> {
                        GameOverScreen(
                            viewModel = viewModel,
                            primaryColor = primaryColor,
                            secondaryColor = secondaryColor
                        )
                    }
                }
            }

            // Standard Mobile Direction control pad if in match
            if (screen == GameScreen.IN_GAME || screen == GameScreen.PAUSED) {
                ControlPad(
                    viewModel = viewModel,
                    primaryColor = primaryColor,
                    secondaryColor = secondaryColor
                )
            }
        }
    }
}

@Composable
fun HUDBar(viewModel: GameViewModel, primaryColor: Color, secondaryColor: Color) {
    val score by viewModel.score.collectAsState()
    val targetScore by viewModel.targetScore.collectAsState()
    val health by viewModel.health.collectAsState()
    val highScore by viewModel.highScore.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val stageId by viewModel.selectedStageId.collectAsState()
    val currentScreen by viewModel.screen.collectAsState()

    val stageLabel = remember(currentScreen, difficulty, stageId) {
        if (currentScreen == GameScreen.START_TRIGGER || currentScreen == GameScreen.MODES_MENU || currentScreen == GameScreen.SUB_MENU) {
            "MATRIX"
        } else if (difficulty == GameDifficulty.FREE_RUN) {
            "FREE-M: ${stageId.toString().padStart(2, '0')}"
        } else {
            "${difficulty.name}: STG ${stageId.toString().padStart(2, '0')}"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .border(1.dp, primaryColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = CyberDark.copy(alpha = 0.85f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Token status
                Column {
                    Text(
                        text = "MODE MATRIX",
                        color = primaryColor.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = stageLabel,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Health Bar status
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "HEALTH RESILIENCE",
                        color = secondaryColor.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row {
                        if (difficulty == GameDifficulty.FREE_RUN && currentScreen != GameScreen.START_TRIGGER && currentScreen != GameScreen.MODES_MENU) {
                            Text(
                                text = "PRACTICE MODE",
                                color = secondaryColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                        } else {
                            repeat(5) { index ->
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Health heart",
                                    tint = if (index < health) secondaryColor else Color.DarkGray.copy(alpha = 0.5f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Divider(
                color = primaryColor.copy(alpha = 0.2f),
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Stage Targets
                Column {
                    Text(
                        text = "STAGE PROGRESS",
                        color = primaryColor.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    val targetText = if (difficulty == GameDifficulty.FREE_RUN) "INF" else targetScore.toString().padStart(3, '0')
                    Text(
                        text = "${score.toString().padStart(3, '0')} / $targetText",
                        color = primaryColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // High score indicator
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "STAGE HIGHEST SCORE",
                        color = primaryColor.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = highScore.toString().padStart(3, '0'),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun StartScreen(onStartClick: () -> Unit, primaryColor: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Decorative Logo Border Frame
        Box(
            modifier = Modifier
                .size(160.dp)
                .border(2.dp, primaryColor, CircleShape)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            // Launcher Icon placed inside start banner
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(CyberBlack)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier
                        .size(90.dp)
                        .align(Alignment.Center)
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "CYBERSNAKE PRO",
            color = primaryColor,
            fontSize = 32.sp,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "ARCHITECT STAGE MATRIX V16",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(64.dp))

        // Large Cyber start button
        Button(
            onClick = onStartClick,
            modifier = Modifier
                .width(260.dp)
                .height(56.dp)
                .testTag("trigger_start_btn")
                .border(2.dp, primaryColor, RoundedCornerShape(12.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor.copy(alpha = 0.15f),
                contentColor = primaryColor
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "INITIALIZE CORE RUN",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun ModesMenuScreen(
    viewModel: GameViewModel,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color
) {
    val challengeBy by viewModel.challengePlus.collectAsState()
    val themeState by viewModel.theme.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "SELECT REBOOT MATRIX MODE",
            color = primaryColor,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // Custom list item buttons
        GameModeButton(
            label = "EASY REBOOTS (1-10)",
            description = "Fewer obstacles. Tempo: 180ms. Target score: 10.",
            onClick = { viewModel.setDifficulty(GameDifficulty.EASY) },
            borderColor = primaryColor
        )

        GameModeButton(
            label = "MEDIUM INTEGRITIES (11-20)",
            description = "More portals. Tempo: 140ms. Target score: 20.",
            onClick = { viewModel.setDifficulty(GameDifficulty.MEDIUM) },
            borderColor = secondaryColor
        )

        GameModeButton(
            label = "HARD HAZARDS (21-30)",
            description = "Tight blockades. Tempo: 100ms. Target score: 35.",
            onClick = { viewModel.setDifficulty(GameDifficulty.HARD) },
            borderColor = accentColor
        )

        GameModeButton(
            label = "FREE RUN SANDBOX",
            description = "Practice matrix. Speed accelerates. Infinite trials.",
            onClick = { viewModel.setDifficulty(GameDifficulty.FREE_RUN) },
            borderColor = Color.White
        )

        // Dynamic Challenge + Mode Toggle
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    1.dp,
                    if (challengeBy) secondaryColor else Color.Gray.copy(alpha = 0.3f),
                    RoundedCornerShape(8.dp)
                )
                .clickable { viewModel.toggleChallengePlus() },
            colors = CardDefaults.cardColors(
                containerColor = if (challengeBy) secondaryColor.copy(alpha = 0.08f) else CyberDark
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CHALLENGE+ VELOCITY",
                        color = if (challengeBy) secondaryColor else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Dynamically scales tick rates as score grows",
                        color = Color.LightGray.copy(alpha = 0.7f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Switch(
                    checked = challengeBy,
                    onCheckedChange = { viewModel.toggleChallengePlus() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = secondaryColor,
                        checkedTrackColor = secondaryColor.copy(alpha = 0.4f)
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Theme choices selection
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "SYSTEM GRAPHICS INTERACTION",
                color = primaryColor.copy(alpha = 0.8f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GameThemeItem(
                    label = "CYBER",
                    selected = themeState == GameTheme.CYBERPUNK,
                    color = NeonCyan,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTheme(GameTheme.CYBERPUNK) }
                )
                GameThemeItem(
                    label = "MATRIX",
                    selected = themeState == GameTheme.MATRIX,
                    color = Color.Green,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTheme(GameTheme.MATRIX) }
                )
                GameThemeItem(
                    label = "SUNSET",
                    selected = themeState == GameTheme.SUNSET,
                    color = Color(0xFFFF8C00),
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTheme(GameTheme.SUNSET) }
                )
                GameThemeItem(
                    label = "OBSIDIAN",
                    selected = themeState == GameTheme.OBSIDIAN,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setTheme(GameTheme.OBSIDIAN) }
                )
            }
        }
    }
}

@Composable
fun GameThemeItem(
    label: String,
    selected: Boolean,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) color else Color.Gray.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            )
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (selected) color.copy(alpha = 0.1f) else CyberDark)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) color else Color.LightGray.copy(alpha = 0.6f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun GameModeButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    borderColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CyberDark)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    color = borderColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = borderColor,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun SubMenuScreen(
    viewModel: GameViewModel,
    primaryColor: Color,
    secondaryColor: Color
) {
    val difficulty by viewModel.difficulty.collectAsState()
    val records by viewModel.dbStagesFlow.collectAsState()

    // Determine numerical bounds based on difficulty selection
    val baseIndex = remember(difficulty) {
        when (difficulty) {
            GameDifficulty.EASY -> 1
            GameDifficulty.MEDIUM -> 11
            GameDifficulty.HARD -> 21
            GameDifficulty.FREE_RUN -> 1
        }
    }

    val listItems = remember(baseIndex) {
        (baseIndex until baseIndex + 10).toList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${difficulty.name} SECTORS",
                color = primaryColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )

            Button(
                onClick = { viewModel.backToMainMenu() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray.copy(alpha = 0.6f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("RESET", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, fontFamily = FontFamily.Monospace)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(listItems) { stageNum ->
                val record = records.find { it.stageId == stageNum }
                val isCleared = record?.isCompleted ?: false
                val score = record?.highScore ?: 0

                StageGridCell(
                    stageNum = stageNum,
                    isCompleted = isCleared,
                    highScore = score,
                    difficulty = difficulty,
                    borderColor = if (isCleared) NeonGreen else primaryColor,
                    onClick = { viewModel.selectStageAndStart(stageNum) }
                )
            }
        }
    }
}

@Composable
fun StageGridCell(
    stageNum: Int,
    isCompleted: Boolean,
    highScore: Int,
    difficulty: GameDifficulty,
    borderColor: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = CyberDark)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (difficulty == GameDifficulty.FREE_RUN) "RUN ${stageNum.toString().padStart(2, '0')}" else "STAGE ${stageNum.toString().padStart(2, '0')}",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )

                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Cleared",
                        tint = NeonGreen,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column {
                Text(
                    text = "RECORD HIGH",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = highScore.toString().padStart(3, '0'),
                    color = borderColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun GamePlayArea(
    viewModel: GameViewModel,
    primaryColor: Color,
    secondaryColor: Color,
    accentColor: Color
) {
    val screen by viewModel.screen.collectAsState()
    val snake by viewModel.snake.collectAsState()
    val food by viewModel.food.collectAsState()
    val activePowerUp by viewModel.activePowerUp.collectAsState()

    val activeShield by viewModel.activeShield.collectAsState()
    val activeFreeze by viewModel.activeFreeze.collectAsState()
    val activeDoublePoints by viewModel.activeDoublePoints.collectAsState()
    val powerUpBanner by viewModel.powerUpBanner.collectAsState()

    val particles by viewModel.particles.collectAsState()
    val rebootCount by viewModel.rebootCountdown.collectAsState()
    val difficulty by viewModel.difficulty.collectAsState()
    val stageId by viewModel.selectedStageId.collectAsState()

    val haptic = LocalHapticFeedback.current

    // Observe portals based on stages
    val activePortalCount = remember(difficulty, stageId) {
        when {
            difficulty == GameDifficulty.FREE_RUN -> {
                val sc = ((stageId - 1) % 10) + 1
                if (sc <= 3) 1 else if (sc <= 7) 2 else 3
            }
            stageId <= 10 -> 1
            stageId <= 20 -> 2
            else -> 3
        }
    }

    // Portal continuous spinning angle state
    var portalAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(screen, rebootCount) {
        if (screen == GameScreen.IN_GAME && rebootCount == null) {
            while (true) {
                delay(24L)
                portalAngle += 10f
                if (portalAngle >= 360f) portalAngle = 0f
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Core Visual Game Board Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .background(Color.Black)
                .border(2.dp, primaryColor, RoundedCornerShape(8.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            val threshold = 18f
                            if (abs(dragAmount.x) > abs(dragAmount.y)) {
                                if (dragAmount.x > threshold) {
                                    viewModel.changeDirection(1, 0)
                                } else if (dragAmount.x < -threshold) {
                                    viewModel.changeDirection(-1, 0)
                                }
                            } else {
                                if (dragAmount.y > threshold) {
                                    viewModel.changeDirection(0, 1)
                                } else if (dragAmount.y < -threshold) {
                                    viewModel.changeDirection(0, -1)
                                }
                            }
                        }
                    )
                }
        ) {
            // Main Render Drawing Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cw = size.width / viewModel.GRID_COUNT
                val ch = size.height / viewModel.GRID_COUNT

                // 1. Draw grid cyber matrix lines
                val gridBgColor = primaryColor.copy(alpha = 0.05f)
                for (i in 0..viewModel.GRID_COUNT) {
                    drawLine(gridBgColor, Offset(i * cw, 0f), Offset(i * cw, size.height), 1f)
                    drawLine(gridBgColor, Offset(0f, i * ch), Offset(size.width, i * ch), 1f)
                }

                // 2. Draw active structural blocking maze walls
                val currentWalls = viewModel.getActiveWalls()
                currentWalls.forEach { wallPt ->
                    drawRect(
                        color = Color(0xFF333344),
                        topLeft = Offset(wallPt.x * cw + 1f, wallPt.y * ch + 1f),
                        size = Size(cw - 2f, ch - 2f)
                    )
                    drawRect(
                        color = primaryColor.copy(alpha = 0.8f),
                        topLeft = Offset(wallPt.x * cw + 1f, wallPt.y * ch + 1f),
                        size = Size(cw - 2f, ch - 2f),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // 3. Draw active warp portals
                for (pIndex in 0 until activePortalCount) {
                    val portal = viewModel.DYNAMIC_PORTALS[pIndex]
                    drawWarpVortex(portal.first, portal.colorHex, portalAngle, cw, ch)
                    drawWarpVortex(portal.second, portal.colorHex, portalAngle, cw, ch)
                }

                // 4. Draw active power-ups (if spawned)
                val powerUp = activePowerUp
                if (powerUp != null) {
                    val pcx = powerUp.x * cw + cw / 2f
                    val pcy = powerUp.y * ch + ch / 2f
                    val color = when (powerUp.type) {
                        PowerUpType.SHIELD -> Color(0xFF00F3FF) // Cyan
                        PowerUpType.FREEZE -> Color(0xFF1F00FF) // Neon Blue
                        PowerUpType.DOUBLE_POINTS -> Color(0xFFFF007F) // Pink
                    }
                    // Pulsating circle
                    drawCircle(
                        color = color.copy(alpha = 0.25f),
                        center = Offset(pcx, pcy),
                        radius = cw * 0.75f
                    )
                    drawCircle(
                        color = color,
                        center = Offset(pcx, pcy),
                        radius = cw * 0.35f
                    )
                }

                // 5. Draw Target Food
                val fPoint = food
                drawRoundRect(
                    color = NeonYellow,
                    topLeft = Offset(fPoint.x * cw + 3f, fPoint.y * ch + 3f),
                    size = Size(cw - 6f, ch - 6f),
                    cornerRadius = CornerRadius(cw * 0.3f, ch * 0.3f)
                )

                // 6. Draw glowing neon Snake
                snake.forEachIndexed { idx, cell ->
                    val snakeBodyColor = if (idx == 0) Color.White else accentColor
                    drawRoundRect(
                        color = snakeBodyColor,
                        topLeft = Offset(cell.x * cw + 1.5f, cell.y * ch + 1.5f),
                        size = Size(cw - 3f, ch - 3f),
                        cornerRadius = CornerRadius(cw * 0.15f, ch * 0.15f)
                    )
                }

                // Draw protective shield aura centered on head
                if (activeShield && snake.isNotEmpty()) {
                    val headPt = snake.first()
                    drawCircle(
                        color = Color(0xFF00F3FF).copy(alpha = 0.5f),
                        center = Offset(headPt.x * cw + cw / 2f, headPt.y * ch + ch / 2f),
                        radius = cw * 1.5f,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // 7. Draw crash explosive debris particles
                particles.forEach { p ->
                    drawRect(
                        color = Color(android.graphics.Color.parseColor(p.color)).copy(alpha = p.alpha),
                        topLeft = Offset(p.x - p.size / 2, p.y - p.size / 2),
                        size = Size(p.size, p.size)
                    )
                }
            }

            // Power-up HUD Banner overlaid on top of game frame
            if (powerUpBanner != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Color.Black.copy(alpha = 0.85f))
                        .border(1.dp, secondaryColor.copy(alpha = 0.4f), RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                        .padding(vertical = 4.dp, horizontal = 12.dp)
                ) {
                    Text(
                        text = powerUpBanner!!,
                        color = secondaryColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3-sec system reboot count overlays
            if (rebootCount != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "SYSTEM CRASHED",
                            color = secondaryColor,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "COMPILING RUN REBOOT IN ${rebootCount}S",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Game PAUSED overlay
            if (screen == GameScreen.PAUSED) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = "SYSTEM MATCH SUSPENDED",
                            color = secondaryColor,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )

                        Button(
                            onClick = { viewModel.togglePause() },
                            modifier = Modifier.width(180.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("RESUME CORE", color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = { viewModel.retryCurrentStage() },
                            modifier = Modifier.width(180.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("RESTART", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }

                        Button(
                            onClick = { viewModel.quitToMenu() },
                            modifier = Modifier.width(180.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("QUIT MATCH", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
}

fun DrawScope.drawWarpVortex(pt: Point2D, hexColor: String, baseAngle: Float, cw: Float, ch: Float) {
    val cx = pt.x * cw + cw / 2f
    val cy = pt.y * ch + ch / 2f
    val maxRadius = minOf(cw, ch) / 1.7f
    val color = Color(android.graphics.Color.parseColor(hexColor))

    // Vortex translucent circle backdrops
    drawCircle(
        color = color.copy(alpha = 0.12f),
        center = Offset(cx, cy),
        radius = maxRadius
    )

    // Spiral spin lines
    val steps = 3
    for (i in 1..steps) {
        val radius = maxRadius * (i.toFloat() / steps)
        val angleShift = (baseAngle + (i * 30f)) % 360f
        drawArc(
            color = color,
            startAngle = angleShift,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - radius, cy - radius),
            size = Size(radius * 2f, radius * 2f),
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}

@Composable
fun StageWinScreen(viewModel: GameViewModel, primaryColor: Color, secondaryColor: Color) {
    val stageId by viewModel.selectedStageId.collectAsState()
    val score by viewModel.score.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Done,
            contentDescription = null,
            tint = NeonGreen,
            modifier = Modifier
                .size(72.dp)
                .border(2.dp, NeonGreen, CircleShape)
                .padding(12.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "STAGE $stageId SECURED",
            color = NeonGreen,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, primaryColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
            colors = CardDefaults.cardColors(containerColor = CyberDark)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = ">> REBOOT RUN COMPILER:",
                    color = primaryColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• SECTOR SECURED: STAGE $stageId",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "• INTEGRITY CREDITS: $score POWER UNITS",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "• COMPILER METRIC STATE: OPTIMAL",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.advanceToNextStage() },
            modifier = Modifier
                .width(220.dp)
                .height(50.dp)
                .border(2.dp, NeonGreen, RoundedCornerShape(8.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonGreen.copy(alpha = 0.15f),
                contentColor = NeonGreen
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("ADVANCE SECTOR", fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { viewModel.quitToMenu() },
            modifier = Modifier.width(220.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
        ) {
            Text("QUIT GAME", fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun GameOverScreen(viewModel: GameViewModel, primaryColor: Color, secondaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = secondaryColor,
            modifier = Modifier.size(64.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "SYSTEM GAME OVER",
            color = secondaryColor,
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.Monospace
        )

        Text(
            text = "ALL HARD RESET LIVES EXTINCT",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = { viewModel.retryCurrentStage() },
            modifier = Modifier
                .width(220.dp)
                .height(50.dp)
                .border(2.dp, secondaryColor, RoundedCornerShape(8.dp)),
            colors = ButtonDefaults.buttonColors(
                containerColor = secondaryColor.copy(alpha = 0.15f),
                contentColor = secondaryColor
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text("RETRY CORE RUN", fontWeight = FontWeight.Bold, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { viewModel.quitToMenu() },
            modifier = Modifier.width(220.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.LightGray)
        ) {
            Text("QUIT MATRIX", fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ControlPad(viewModel: GameViewModel, primaryColor: Color, secondaryColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pause trigger
        IconButton(
            onClick = { viewModel.togglePause() },
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, secondaryColor.copy(alpha = 0.5f), CircleShape)
                .background(CyberDark, CircleShape)
        ) {
            Icon(Icons.Default.Refresh, contentDescription = "Pause", tint = secondaryColor)
        }

        // Direction controller DPad block
        Box(
            modifier = Modifier
                .size(140.dp)
                .background(CyberDark.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .border(1.dp, primaryColor.copy(alpha = 0.15f), RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            // Top/Bottom/Left/Right arrow triggers arranger
            DirectionButton(
                icon = Icons.Default.KeyboardArrowUp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp),
                onClick = { viewModel.changeDirection(0, -1) },
                tint = primaryColor
            )

            DirectionButton(
                icon = Icons.Default.KeyboardArrowDown,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 4.dp),
                onClick = { viewModel.changeDirection(0, 1) },
                tint = primaryColor
            )

            DirectionButton(
                icon = Icons.Default.KeyboardArrowLeft,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
                onClick = { viewModel.changeDirection(-1, 0) },
                tint = primaryColor
            )

            DirectionButton(
                icon = Icons.Default.KeyboardArrowRight,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
                onClick = { viewModel.changeDirection(1, 0) },
                tint = primaryColor
            )

            // Center status core core cell
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(primaryColor.copy(alpha = 0.15f), CircleShape)
                    .border(1.dp, primaryColor, CircleShape)
            )
        }

        // Reset stage trigger
        IconButton(
            onClick = { viewModel.retryCurrentStage() },
            modifier = Modifier
                .size(48.dp)
                .border(1.dp, primaryColor.copy(alpha = 0.5f), CircleShape)
                .background(CyberDark, CircleShape)
        ) {
            Icon(Icons.Default.Home, contentDescription = "Restart Stage", tint = primaryColor)
        }
    }
}

@Composable
fun DirectionButton(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.ArrowBack, // default
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
    tint: Color
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(42.dp)
            .background(Color.Black, RoundedCornerShape(8.dp))
            .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
    ) {
        Icon(icon, contentDescription = "Direction Control", tint = tint, modifier = Modifier.size(24.dp))
    }
}
