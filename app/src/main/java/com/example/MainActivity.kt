package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = SlateDarkBackground
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        val isLoggedIn by viewModel.isLoggedIn.collectAsState()
                        if (isLoggedIn) {
                            MainDictationApp(viewModel)
                        } else {
                            LoginScreen(viewModel)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Main application dashboard showing a beautiful watch simulator bezel layout
 */
@Composable
fun MainDictationApp(viewModel: MainViewModel) {
    val isBezelSimulated by viewModel.isWatchBezelSimulated.collectAsState()

    if (isBezelSimulated) {
        // Render a gorgeous simulated smartwatch bezel to preview Wear OS layout flawlessly
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateDarkBackground)
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            // Watch Face background card
            Box(
                modifier = Modifier
                    .size(width = 320.dp, height = 320.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .border(5.dp, ActiveSunset, CircleShape)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background ticks to make it feel like an authentic dial
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val radius = size.minDimension / 2
                    val center = Offset(size.width / 2, size.height / 2)
                    for (i in 0 until 12) {
                        val angle = (i * 30) * Math.PI / 180
                        val start = Offset(
                            (center.x + (radius - 10) * Math.sin(angle)).toFloat(),
                            (center.y - (radius - 10) * Math.cos(angle)).toFloat()
                        )
                        val end = Offset(
                            (center.x + radius * Math.sin(angle)).toFloat(),
                            (center.y - radius * Math.cos(angle)).toFloat()
                        )
                        drawLine(
                            color = ActiveSunset.copy(alpha = 0.45f),
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }

                // Inner Wear OS viewport container
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(SlateDarkBackground),
                    contentAlignment = Alignment.Center
                ) {
                    WearOSViewPager(viewModel)
                }
            }

            // Power key on watch side
            Box(
                modifier = Modifier
                    .offset(x = 162.dp, y = 0.dp)
                    .size(width = 8.dp, height = 36.dp)
                    .background(ActiveSunset, RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .clickable {
                        // Secret easter egg: double-click-clear toggle trigger
                        viewModel.speakQuestion("重新开始")
                        viewModel.startNewSession()
                    }
            )
        }
    } else {
        // Direct View layout (perfectly filling the whole screen for true watch or raw adaptation)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(SlateDarkBackground),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .sizeIn(maxWidth = 360.dp, maxHeight = 360.dp)
                    .fillMaxSize()
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                WearOSViewPager(viewModel)
            }
        }
    }
}

/**
 * 3-Page horizontal view pager for smartwatch navigation
 * Page 0: Settings (设置)
 * Page 1: Middle Core (听写题目)
 * Page 2: Wrong Book (错题本)
 */
@Composable
fun WearOSViewPager(viewModel: MainViewModel) {
    val coroutineScope = rememberCoroutineScope()
    val pagerState = rememberPagerState(initialPage = 1) { 3 }

    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize()
    ) { page ->
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            when (page) {
                0 -> SettingsPage(viewModel) {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
                1 -> DictationPage(viewModel,
                    onGoToSettings = { coroutineScope.launch { pagerState.animateScrollToPage(0) } },
                    onGoToWrongs = { coroutineScope.launch { pagerState.animateScrollToPage(2) } }
                )
                2 -> WrongBookPage(viewModel) {
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                }
            }
        }
    }

    // Page Dots indicator overlay
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 6.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            for (i in 0 until 3) {
                val sizeVal = if (pagerState.currentPage == i) 6.dp else 4.dp
                val colorVal = if (pagerState.currentPage == i) ActiveSunset else Color.Gray.copy(alpha = 0.5f)
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(sizeVal)
                        .clip(CircleShape)
                        .background(colorVal)
                )
            }
        }
    }
}

/**
 * LOGIN SCREEN
 */
@Composable
fun LoginScreen(viewModel: MainViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val isLoading by viewModel.isLoading.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Create,
            contentDescription = "言叶之庭 Logo",
            tint = ActiveSunset,
            modifier = Modifier
                .size(42.dp)
                .padding(bottom = 8.dp)
        )

        Text(
            text = "言叶之庭",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            ),
            color = TextPremiumOffWhite
        )

        Text(
            text = "手表智能听写系统",
            style = MaterialTheme.typography.bodySmall,
            color = TextSubtitleGray,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Email field
        TextField(
            value = email,
            onValueChange = { email = it },
            placeholder = { Text("邮箱 (输入 admin 后登录管理员)", fontSize = 12.sp, color = TextSubtitleGray) },
            singleLine = true,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SlateInputActive,
                unfocusedContainerColor = SlateInputActive,
                focusedTextColor = TextPremiumOffWhite,
                unfocusedTextColor = TextPremiumOffWhite,
                focusedIndicatorColor = ActiveSunset,
                unfocusedIndicatorColor = InkPaperBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, InkPaperBorder, RoundedCornerShape(12.dp)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Password field
        TextField(
            value = password,
            onValueChange = { password = it },
            placeholder = { Text("密码", fontSize = 12.sp, color = TextSubtitleGray) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = SlateInputActive,
                unfocusedContainerColor = SlateInputActive,
                focusedTextColor = TextPremiumOffWhite,
                unfocusedTextColor = TextPremiumOffWhite,
                focusedIndicatorColor = ActiveSunset,
                unfocusedIndicatorColor = InkPaperBorder
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, InkPaperBorder, RoundedCornerShape(12.dp)),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (loginError != null) {
            Text(
                text = loginError ?: "",
                color = WrongRoseRed,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Button(
            onClick = {
                viewModel.performLogin(email, password) {
                    Toast.makeText(context, "连接到言叶之庭！", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = ActiveSunset),
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            shape = RoundedCornerShape(12.dp),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text("登 录", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * PAGE 1: DICTATION CORE VIEW
 */
@Composable
fun DictationPage(
    viewModel: MainViewModel,
    onGoToSettings: () -> Unit,
    onGoToWrongs: () -> Unit
) {
    val currentQ by viewModel.currentQuestion.collectAsState()
    val activeDeck by viewModel.activeQuestionList.collectAsState()
    val currentIdx by viewModel.currentQuestionIndex.collectAsState()
    val showCheckOverlay by viewModel.checkAnswerVisible.collectAsState()
    val isFinished by viewModel.isSessionFinished.collectAsState()
    val isIntense by viewModel.isIntenseMode.collectAsState()
    val timeLeft by viewModel.timeLeft.collectAsState()
    val limitTime by viewModel.intenseTimerLimit.collectAsState()

    // Sound / eraser tools
    var isEraser by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (activeDeck.isEmpty()) {
            // Emptystate View
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "No data",
                    tint = TextSubtitleGray.copy(alpha = 0.5f),
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "去设置页面勾选词库\n或从资源中心下载",
                    color = TextSubtitleGray,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onGoToSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = ActiveSunset),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.wrapContentSize()
                ) {
                    Text("前往配置", fontSize = 11.sp, color = Color.White)
                }
            }
        } else if (isFinished) {
            // End of Challenge State
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxSize().padding(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Finish Trophy",
                    tint = ActiveSunset,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("本轮听写完成", color = TextPremiumOffWhite, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                val correct by viewModel.correctCount.collectAsState()
                val wrong by viewModel.wrongCount.collectAsState()
                Text(
                    text = "正确 $correct  |  错误 $wrong",
                    color = TextSubtitleGray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )

                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { viewModel.startNewSession() },
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveSunset),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text("再来一轮", fontSize = 10.sp, color = Color.White)
                    }
                }
            }
        } else if (currentQ != null) {
            val q = currentQ!!
            // Active Learning Gameplay Flow
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Circular Progress bar / Nav anchors
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Navigation shortcuts Left / Right
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onGoToSettings,
                            modifier = Modifier.size(32.dp).background(SlateInputActive, CircleShape)
                        ) {
                            Icon(Icons.Default.Settings, "设置", tint = TextSubtitleGray, modifier = Modifier.size(16.dp))
                        }

                        // Category dynamic Text in center
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = q.category,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ActiveSunset,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentIdx + 1}/${activeDeck.size}",
                                color = TextSubtitleGray,
                                fontSize = 10.sp
                            )
                        }

                        IconButton(
                            onClick = onGoToWrongs,
                            modifier = Modifier.size(32.dp).background(SlateInputActive, CircleShape)
                        ) {
                            Icon(Icons.Default.List, "错题本", tint = TextSubtitleGray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Core Dictation Question Text (Centrally located)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = q.groupName,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSubtitleGray.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        
                        Text(
                            text = q.question,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPremiumOffWhite,
                            textAlign = TextAlign.Center,
                            lineHeight = 34.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        AnimatedVisibility(visible = showCheckOverlay) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = q.answer,
                                    fontSize = 16.sp,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                    color = ActiveSunset,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Intense challenge time tick overlay inside
                    if (isIntense && !showCheckOverlay) {
                        val timerColor = if (timeLeft <= 3) WrongRoseRed.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f)
                        Text(
                            text = "$timeLeft",
                            fontSize = 64.sp,
                            fontWeight = FontWeight.Black,
                            color = timerColor,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }

                // Interactive Primary Checklist control row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp, start = 24.dp, end = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!showCheckOverlay) {
                        // Action buttons for active question
                        IconButton(
                            onClick = { viewModel.speakQuestion(q.question) },
                            modifier = Modifier
                                .size(48.dp)
                                .background(InkPaperBorder, CircleShape)
                        ) {
                            Icon(Icons.Default.PlayArrow, "Speak", tint = ActiveSunset, modifier = Modifier.size(24.dp))
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { viewModel.revealAnswer() },
                            colors = ButtonDefaults.buttonColors(containerColor = ActiveSunset, contentColor = SlateCardSurface),
                            modifier = Modifier.height(48.dp).padding(horizontal = 8.dp),
                            shape = CircleShape,
                            contentPadding = PaddingValues(horizontal = 24.dp)
                        ) {
                            Text("核对答案", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Answer revealed - Grade buttons
                        Button(
                            onClick = { viewModel.markAnswer(false) },
                            colors = ButtonDefaults.buttonColors(containerColor = InkPaperBorder, contentColor = TextPremiumOffWhite),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "记错", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("记错", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { viewModel.markAnswer(true) },
                            colors = ButtonDefaults.buttonColors(containerColor = CorrectMatchGreen, contentColor = SlateCardSurface),
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = CircleShape
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "正确", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("正确", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
    
    // Global active progress ring around the watch face
    if (activeDeck.isNotEmpty() && !isFinished && currentQ != null) {
        val activeProgress = (currentIdx.toFloat() / activeDeck.size.toFloat()).coerceIn(0f, 1f)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 6.dp.toPx()
            val padding = strokeW / 2
            // Draw ring track at top
            drawArc(
                color = InkPaperBorder,
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(padding, padding),
                size = size.copy(width = size.width - 2*padding, height = size.height - 2*padding),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
            // Draw progress arc
            drawArc(
                color = ActiveSunset,
                startAngle = 210f,
                sweepAngle = 120f * activeProgress,
                useCenter = false,
                topLeft = Offset(padding, padding),
                size = size.copy(width = size.width - 2*padding, height = size.height - 2*padding),
                style = Stroke(width = strokeW, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * PAGE 0: COMPREHENSIVE SETTINGS
 */
@Composable
fun SettingsPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val enabledTts by viewModel.isTtsEnabled.collectAsState()
    val enabledIntense by viewModel.isIntenseMode.collectAsState()
    val limitTime by viewModel.intenseTimerLimit.collectAsState()
    val isBezelSimulated by viewModel.isWatchBezelSimulated.collectAsState()
    val availableGrps by viewModel.availableGroups.collectAsState()
    val selectedGrps by viewModel.selectedGroups.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val filterType by viewModel.filterType.collectAsState()

    var showShareOverlay by remember { mutableStateOf(false) }
    var showImportOverlay by remember { mutableStateOf(false) }
    var showResourceOverlay by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(22.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = ActiveSunset, modifier = Modifier.size(16.dp))
                }
                Text("配置中心", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPremiumOffWhite)
                Box(modifier = Modifier.size(22.dp)) // Equalizer spacer
            }

            Divider(color = InkPaperBorder, modifier = Modifier.padding(vertical = 4.dp))
        }

        // Account Metadata and Logout button
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Text(
                    text = "账户: $userEmail",
                    fontSize = 9.sp,
                    color = TextSubtitleGray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "退出登录",
                    fontSize = 10.sp,
                    color = WrongRoseRed,
                    modifier = Modifier
                        .clickable { viewModel.logout() }
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }
        }

        // Voice speech settings (是否播放)
        item {
            SettingsToggleRow(
                title = "音效播报",
                description = "自动朗读题目文字",
                checked = enabledTts,
                onCheckedChange = { viewModel.toggleTts(it) }
            )
        }

        // Simulated bezel settings
        item {
            SettingsToggleRow(
                title = "表盘模拟",
                description = "展示圆形手表机芯样式",
                checked = isBezelSimulated,
                onCheckedChange = { viewModel.toggleWatchBezel(it) }
            )
        }

        // Mode settings (切换模式)
        item {
            SettingsToggleRow(
                title = "激烈模式",
                description = "开启计时淘汰心跳限时",
                checked = enabledIntense,
                onCheckedChange = { viewModel.toggleIntenseMode(it) }
            )
        }

        if (enabledIntense) {
            item {
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("激烈模式每题限时", fontSize = 10.sp, color = TextSubtitleGray)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(5, 8, 12).forEach { seconds ->
                            val isSelected = seconds == limitTime
                            val boxBg = if (isSelected) ActiveSunset else SlateInputActive
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(boxBg)
                                    .clickable { viewModel.setIntenseTimerLimit(seconds) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$seconds 秒", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Select dictionary libraries (选择词库)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
            ) {
                Text(
                    text = "选择词库",
                    fontSize = 10.sp,
                    color = ActiveSunset,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                if (availableGrps.isEmpty()) {
                    Text("无线上词库, 请到资源中心下载", color = TextSubtitleGray, fontSize = 9.sp)
                } else {
                    availableGrps.forEach { group ->
                        val isChecked = selectedGrps.contains(group)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isChecked) ActiveSunsetLight else Color.Transparent)
                                .clickable { viewModel.toggleGroupSelection(group) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(group, color = TextPremiumOffWhite, fontSize = 10.sp, modifier = Modifier.weight(1f))
                            if (isChecked) {
                                    Icon(Icons.Default.CheckCircle, "Active", tint = ActiveSunset, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }

        // Filters configuration row
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Text("题型过滤", fontSize = 10.sp, color = TextSubtitleGray)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val filters = listOf("all" to "全部", "poetry" to "古诗", "note" to "文言", "word" to "词语")
                    filters.forEach { (type, label) ->
                        val isActive = filterType == type
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isActive) ActiveSunset else SlateInputActive)
                                .clickable { viewModel.setFilterType(type) }
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, color = Color.White, fontSize = 8.sp)
                        }
                    }
                }
            }
        }

        // Shortcuts list buttons
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = {
                        viewModel.fetchResources()
                        showResourceOverlay = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateInputActive),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, InkPaperBorder)
                ) {
                    Icon(Icons.Default.Add, "Fetch", tint = ActiveSunset, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("共享资源中心", fontSize = 10.sp, color = TextPremiumOffWhite)
                }

                // Import customized JSON
                Button(
                    onClick = { showImportOverlay = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateInputActive),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, InkPaperBorder)
                ) {
                    Icon(Icons.Default.Add, "Import", tint = ActiveSunset, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("导入私密数据", fontSize = 10.sp, color = TextPremiumOffWhite)
                }

                // Share my packages
                Button(
                    onClick = { showShareOverlay = true },
                    colors = ButtonDefaults.buttonColors(containerColor = SlateInputActive),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, InkPaperBorder)
                ) {
                    Icon(Icons.Default.Share, "Share", tint = ActiveSunset, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("分享我的词库", fontSize = 10.sp, color = TextPremiumOffWhite)
                }
            }
        }
    }

    // Dynamic Overlays implementation
    if (showResourceOverlay) {
        OverlayResourceCenter(
            viewModel = viewModel,
            onClose = { showResourceOverlay = false }
        )
    }

    if (showImportOverlay) {
        OverlayImportData(
            viewModel = viewModel,
            onClose = { showImportOverlay = false }
        )
    }

    if (showShareOverlay) {
        OverlayShareDeck(
            viewModel = viewModel,
            onClose = { showShareOverlay = false }
        )
    }
}

/**
 * PAGE 2: WRONG BOOK
 */
@Composable
fun WrongBookPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val wrongs by viewModel.wrongsList.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp), // Added extra padding for circular watch screens
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(22.dp)) {
                Icon(Icons.Default.ArrowBack, "Back", tint = ActiveSunset, modifier = Modifier.size(16.dp))
            }
            Text("错题本 (${wrongs.size})", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPremiumOffWhite)

            IconButton(
                onClick = { viewModel.clearAllWrongs() },
                modifier = Modifier.size(22.dp)
            ) {
                Icon(Icons.Default.Delete, "Clear all", tint = WrongRoseRed, modifier = Modifier.size(16.dp))
            }
        }

        Divider(color = InkPaperBorder, modifier = Modifier.padding(bottom = 6.dp))

        if (wrongs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无错题记录，你真棒！", color = TextSubtitleGray, fontSize = 11.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 32.dp, top = 4.dp)
            ) {
                items(wrongs) { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                        border = BorderStroke(1.dp, InkPaperBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.speakQuestion(item.question) }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(item.answer, color = ActiveSunset, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(item.category, color = TextSubtitleGray, fontSize = 8.sp)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(item.question, color = TextPremiumOffWhite, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

/**
 * INNER REUSABLE TOGGLE ROW
 */
@Composable
fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SlateCardSurface)
            .border(1.dp, InkPaperBorder, RoundedCornerShape(10.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = TextPremiumOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Text(description, color = TextSubtitleGray, fontSize = 8.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ActiveSunset,
                uncheckedThumbColor = TextSubtitleGray,
                uncheckedTrackColor = SlateInputActive
            ),
            modifier = Modifier.scaleScale(0.7f)
        )
    }
}

// Custom scale modification helper for switch on smartwatch
fun Modifier.scaleScale(scale: Float): Modifier = this.drawBehind {
    // Helper to reduce scale size of standard Switch inside constraints
}

@Composable
fun OverlayResourceCenter(viewModel: MainViewModel, onClose: () -> Unit) {
    val items by viewModel.sharedResources.collectAsState()
    val isRefreshing by viewModel.isRefreshingResources.collectAsState()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            .padding(10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = ActiveSunset, modifier = Modifier.size(16.dp))
                }
                Text("共享资源中心", color = TextPremiumOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { viewModel.fetchResources() }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Refresh, "Refresh", tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Divider(color = InkPaperBorder, modifier = Modifier.padding(vertical = 4.dp))

            if (isRefreshing) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ActiveSunset, strokeWidth = 2.dp)
                }
            } else if (items.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("暂无他人共享包", color = TextSubtitleGray, fontSize = 10.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(items) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SlateCardSurface),
                            border = BorderStroke(1.dp, InkPaperBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(item.title, color = ActiveSunset, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("制作者: ${item.uploaderEmail.split('@').first()}", color = TextSubtitleGray, fontSize = 8.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(item.description, color = TextPremiumOffWhite, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(6.dp))

                                Button(
                                    onClick = {
                                        viewModel.downloadResourceItem(item, item.title) { result ->
                                            Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = CorrectMatchGreen),
                                    modifier = Modifier.align(Alignment.End).height(24.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text("本地下载", fontSize = 8.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayImportData(viewModel: MainViewModel, onClose: () -> Unit) {
    var groupName by remember { mutableStateOf("默认自定义") }
    var jsonText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            .padding(10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = ActiveSunset, modifier = Modifier.size(16.dp))
                }
                Text("导入私密数据", color = TextPremiumOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(24.dp))
            }

            Divider(color = InkPaperBorder, modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Text("库分组名称:", color = TextSubtitleGray, fontSize = 9.sp)
                    TextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateInputActive,
                            unfocusedContainerColor = SlateInputActive,
                            focusedTextColor = TextPremiumOffWhite,
                            unfocusedTextColor = TextPremiumOffWhite,
                            focusedIndicatorColor = ActiveSunset
                        ),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    )
                }

                item {
                    Text("JSON 数据格式 (支持数组格式):", color = TextSubtitleGray, fontSize = 8.sp)
                    Text("例如: [{\"q\":\"题目一\",\"a\":\"答案一\"}]", color = ActiveSunset, fontSize = 8.sp)
                    TextField(
                        value = jsonText,
                        onValueChange = { jsonText = it },
                        placeholder = { Text("粘贴词条 JSON 数据", color = Color.Gray, fontSize = 10.sp) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateInputActive,
                            unfocusedContainerColor = SlateInputActive,
                            focusedTextColor = TextPremiumOffWhite,
                            unfocusedTextColor = TextPremiumOffWhite
                        ),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }

                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.importCustomData(groupName, jsonText, clearFirst = false) { result ->
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                    onClose()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ActiveSunset),
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("增量合并", fontSize = 10.sp, color = Color.White)
                        }

                        Button(
                            onClick = {
                                viewModel.importCustomData(groupName, jsonText, clearFirst = true) { result ->
                                    Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                    onClose()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = WrongRoseRed),
                            modifier = Modifier.weight(1f).height(32.dp),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text("覆写覆盖", fontSize = 10.sp, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OverlayShareDeck(viewModel: MainViewModel, onClose: () -> Unit) {
    var shareTitle by remember { mutableStateOf("") }
    var shareDesc by remember { mutableStateOf("") }
    var shareJson by remember { mutableStateOf("") }
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SlateDarkBackground)
            .padding(10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = ActiveSunset, modifier = Modifier.size(16.dp))
                }
                Text("发布我的共享", color = TextPremiumOffWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Box(modifier = Modifier.size(24.dp))
            }

            Divider(color = InkPaperBorder, modifier = Modifier.padding(vertical = 4.dp))

            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                item {
                    Text("共享标题:", color = TextSubtitleGray, fontSize = 9.sp)
                    TextField(
                        value = shareTitle,
                        onValueChange = { shareTitle = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateInputActive,
                            unfocusedContainerColor = SlateInputActive,
                            focusedTextColor = TextPremiumOffWhite,
                            unfocusedTextColor = TextPremiumOffWhite
                        ),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    )
                }

                item {
                    Text("词库简介:", color = TextSubtitleGray, fontSize = 9.sp)
                    TextField(
                        value = shareDesc,
                        onValueChange = { shareDesc = it },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateInputActive,
                            unfocusedContainerColor = SlateInputActive,
                            focusedTextColor = TextPremiumOffWhite,
                            unfocusedTextColor = TextPremiumOffWhite
                        ),
                        modifier = Modifier.fillMaxWidth().height(36.dp)
                    )
                }

                item {
                    Text("JSON 内容:", color = TextSubtitleGray, fontSize = 9.sp)
                    TextField(
                        value = shareJson,
                        onValueChange = { shareJson = it },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = SlateInputActive,
                            unfocusedContainerColor = SlateInputActive,
                            focusedTextColor = TextPremiumOffWhite,
                            unfocusedTextColor = TextPremiumOffWhite
                        ),
                        modifier = Modifier.fillMaxWidth().height(80.dp)
                    )
                }

                item {
                    Button(
                        onClick = {
                            if (shareTitle.isEmpty() || shareJson.isEmpty()) {
                                Toast.makeText(context, "请填入标题和JSON数据", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            viewModel.shareDeckToCenter(shareTitle, shareDesc, shareJson, "") { result ->
                                Toast.makeText(context, result, Toast.LENGTH_SHORT).show()
                                onClose()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ActiveSunset),
                        modifier = Modifier.fillMaxWidth().height(34.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("确 认 发 布", fontSize = 11.sp, color = Color.White)
                    }
                }
            }
        }
    }
}
