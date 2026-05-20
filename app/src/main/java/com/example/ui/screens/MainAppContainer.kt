package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.MemoryItem
import com.example.ui.theme.AppleDarkBg
import com.example.ui.theme.AppleLightBg
import com.example.ui.theme.AmberWarning
import com.example.ui.viewmodel.BrainViewModel
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MainAppContainer(
    viewModel: BrainViewModel = viewModel(),
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    onToggleTheme: () -> Unit = {}
) {
    val context = LocalContext.current
    val testTagPrefix = "app_"

    // Navigation setup: 0 = Dashboard, 1 = Capture/Dump, 2 = Search Brain, 3 = Follow-ups
    var selectedTab by remember { mutableStateOf(0) }
    var expandedMemoryId by remember { mutableStateOf<Int?>(null) }

    val memories by viewModel.memories.collectAsStateWithLifecycle()
    val activeReminders by viewModel.activeReminders.collectAsStateWithLifecycle()
    val isAnalyzing by viewModel.isAnalyzing.collectAsStateWithLifecycle()
    val showToastMsg by viewModel.showToastMsg.collectAsStateWithLifecycle()

    LaunchedEffect(showToastMsg) {
        showToastMsg?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearToast()
        }
    }

    Scaffold(
        bottomBar = {
            AppleBottomBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                remindersCount = activeReminders.size
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Background ambient gradients
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = if (isDarkTheme) {
                                listOf(AppleDarkBg, Color(0xFF13131D), AppleDarkBg)
                            } else {
                                listOf(AppleLightBg, Color(0xFFE9E9F0), AppleLightBg)
                            }
                        )
                    )
            )

            // Content Screens Switcher
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "screen_switch"
            ) { tab ->
                when (tab) {
                    0 -> DashboardTab(
                        memories = memories,
                        onOpenDetail = { expandedMemoryId = it },
                        onToggleTheme = onToggleTheme,
                        isDarkTheme = isDarkTheme,
                        onGotoCapture = { selectedTab = 1 }
                    )
                    1 -> CaptureTab(
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme
                    )
                    2 -> SearchTab(
                        viewModel = viewModel,
                        memories = memories,
                        onOpenDetail = { expandedMemoryId = it }
                    )
                    3 -> FollowUpsTab(
                        activeReminders = activeReminders,
                        viewModel = viewModel,
                        onOpenDetail = { expandedMemoryId = it }
                    )
                }
            }

            // Global analyzing state overlay modal
            if (isAnalyzing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .padding(32.dp)
                            .shadow(24.dp, RoundedCornerShape(20.dp)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 5.dp,
                                modifier = Modifier
                                    .size(60.dp)
                                    .testTag("analyzer_progress")
                            )
                            Spacer(modifier = Modifier.height(18.dp))
                            Text(
                                "AI Brain Processing...",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Calling Native Gemini-3.5-Flash OCR, Summarization & Meta Analysis",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Beautiful Memory Detail Bottom Sheet Dialog
            expandedMemoryId?.let { id ->
                val memory = memories.find { it.id == id }
                if (memory != null) {
                    MemoryDetailSheet(
                        memory = memory,
                        onDismiss = { expandedMemoryId = null },
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme
                    )
                } else {
                    expandedMemoryId = null
                }
            }
        }
    }
}

// ==========================================
// SCREEN 1: DASHBOARD
// ==========================================
@Composable
fun DashboardTab(
    memories: List<MemoryItem>,
    onOpenDetail: (Int) -> Unit,
    onToggleTheme: () -> Unit,
    isDarkTheme: Boolean,
    onGotoCapture: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_root"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // App Premium Header Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BRAIN DOCK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Your Second Brain",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(
                    onClick = onToggleTheme,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .testTag("theme_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Switch Theme",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Second Brain Storage Statistics & Beautiful Ring-Chart
        item {
            AppleStatsCard(memories = memories)
        }

        // Recent Intakes Subheading
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Captured Dumps",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "${memories.size} logs",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (memories.isEmpty()) {
            item {
                AppleEmptyState(
                    title = "Memory Bank is Empty",
                    description = "Dump slides, boarding passes, notes, or hold mic to record context voicenotes. Let Gemini organize your mind.",
                    icon = Icons.Default.Lightbulb,
                    actionText = "Dump screenshot & voice now",
                    onActionClick = onGotoCapture
                )
            }
        } else {
            items(memories, key = { it.id }) { memory ->
                MemoryItemCard(
                    memory = memory,
                    onClick = { onOpenDetail(memory.id) }
                )
            }
        }
    }
}

// ==========================================
// SCREEN 2: CAPTURE & DUMP SCREEN
// ==========================================
@Composable
fun CaptureTab(
    viewModel: BrainViewModel,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordSeconds by viewModel.recordingSeconds.collectAsStateWithLifecycle()
    val amplitude by viewModel.currentAmplitude.collectAsStateWithLifecycle()

    // File Picker launchers
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                    @Suppress("DEPRECATION")
                    MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                } else {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    ImageDecoder.decodeBitmap(source)
                }
                viewModel.ingestCustomScreenshot(bitmap)
            } catch (e: Exception) {
                Toast.makeText(context, "Image load fail: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val recordPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.isMicPermissionOn.value = isGranted
        if (isGranted) {
            viewModel.startVoiceRecording(context)
        } else {
            Toast.makeText(context, "Microphone access is required to capture audio notes.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("capture_tab_root"),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Title block
        Column {
            Text(
                text = "INGEST ENGINE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Capture Your Mind",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Auto screenshot permission simulation controller
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoCamera,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    Column {
                        Text(
                            text = "Auto Screenshot Monitor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Listen for system capture shortcuts and automatically dump into brain.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                            lineHeight = 14.sp
                        )
                    }
                }

                Switch(
                    checked = viewModel.isAutoDetectPermissionOn.value,
                    onCheckedChange = {
                        viewModel.isAutoDetectPermissionOn.value = it
                        if (it) {
                            Toast.makeText(
                                context,
                                "Screenshot Listener Activated (Simulating background notification scans)",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.testTag("screenshot_permission_switch")
                )
            }
        }

        // Real Screenshot upload or High fidelity simulated Ingest
        Text(
            text = "Dump Screenshots",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Upload Button
            Button(
                onClick = { imagePickerLauncher.launch("image/*") },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("upload_screenshot_btn")
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Select Screenshot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }

        // Simulated screenshot templates carousel! (Incredibly convenient for immediate testing)
        Text(
            text = "Select a Simulation Template",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 6.dp)
        ) {
            items(1) {
                MockTemplatePill(
                    index = 1,
                    title = "Board Strategy Slide",
                    subtitle = "Q4 launch meeting Chicago",
                    category = "Work",
                    color = MaterialTheme.colorScheme.primary,
                    icon = Icons.Default.Article,
                    onClick = {
                        viewModel.ingestMockScreenshotTemplate(
                            1,
                            "Board Strategy Meeting Chicago Slide",
                            "Second Brain Q4 launch plan presentation. Event timeline set for Chicago conference center. Milestones: June 30th launch and hire 4 developers. Key metrics: increase traction by 35%."
                        )
                    }
                )
                
                Spacer(modifier = Modifier.width(12.dp))

                MockTemplatePill(
                    index = 2,
                    title = "Flight E-Ticket",
                    subtitle = "Paris AF015 itinerary",
                    category = "Travel",
                    color = Color(0xFF5856D6),
                    icon = Icons.Default.Map,
                    onClick = {
                        viewModel.ingestMockScreenshotTemplate(
                            2,
                            "Air France Flight E-Ticket Details",
                            "Confirmed Travel flight seat 12B, flight AF015, travel destination: Charles de Gaulle Paris airport CDG. Departure scheduled on October 12th, 2026 at 8:30 PM from JFK airport New York. Contact: Air France services."
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                MockTemplatePill(
                    index = 3,
                    title = "Keto Recipe Card",
                    subtitle = "Healthy Avocado prep",
                    category = "Health",
                    color = Color(0xFF34C759),
                    icon = Icons.Default.Favorite,
                    onClick = {
                        viewModel.ingestMockScreenshotTemplate(
                            3,
                            "Keto Meal Plan Avocado Bowl Recipe",
                            "Healthy Ketogenic meal details. Prep Avocado salmon green salad bowl on Monday June 1st, 2026. Ingredients checklist: avocados, wild organic salmon, lemon splash, fresh herb seasoning. Location: Kitchen."
                        )
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                MockTemplatePill(
                    index = 4,
                    title = "AWS Cloud Bill Invoice",
                    subtitle = "Charge due virtual Seattle",
                    category = "Finance",
                    color = AmberWarning,
                    icon = Icons.Default.ShoppingBag,
                    onClick = {
                        viewModel.ingestMockScreenshotTemplate(
                            4,
                            "AWS Cloud Bill Invoice",
                            "Amazon AWS invoice summary for monthly web hosting services. Subscriptions count: 12. Total billing amount due: \$148.50. Scheduled automatic charge processing date: June 5th, 2026. Location: Virtual billing panel, Seattle HQ."
                        )
                    }
                )
            }
        }

        Divider(
            color = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA),
            thickness = 1.dp,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        // Capture voice notes / meetings section
        Text(
            text = "Record Audio Voicenotes",
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(
                width = 1.dp,
                color = if (isDarkTheme) Color(0xFF2C2C2E) else Color(0xFFE5E5EA)
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Recording visual states
                if (isRecording) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "LISTENING & ANALYZING VOICE...",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberWarning,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = String.format("%02d:%02d", recordSeconds / 60, recordSeconds % 60),
                            fontSize = 32.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Sound wave form animation
                    Row(
                        modifier = Modifier
                            .height(50.dp)
                            .fillMaxWidth(0.75f),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val amplitudes = remember { List(10) { (20..90).random() } }
                        for (i in 0 until 10) {
                            val pulseAnim by rememberInfiniteTransition(label = "").animateFloat(
                                initialValue = 10f,
                                targetValue = amplitudes[i] * (amplitude + 0.1f),
                                animationSpec = infiniteRepeatable(
                                    animation = tween((300..600).random(), easing = LinearEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = ""
                            )
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(pulseAnim.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    )
                            )
                        }
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                    Text(
                        text = "Talk freely about meetings, task updates, calendar schedules, and more.",
                        textAlign = TextAlign.Center,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }

                // Microphone toggle button
                Button(
                    onClick = {
                        if (!isRecording) {
                            // Request permission
                            recordPermissionsLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        } else {
                            viewModel.stopVoiceRecording(context, isMockTemplate = false, mockText = null)
                        }
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFFF453A) else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .size(68.dp)
                        .shadow(8.dp, CircleShape)
                        .testTag("microphone_record_toggle")
                ) {
                    Icon(
                        imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = "Microphone Button",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Simulating high quality speech notes
        Text(
            text = "Tap to Simulate Voice Input",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 1.sp
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    viewModel.startVoiceRecording(context)
                    viewModel.stopVoiceRecording(
                        context,
                        isMockTemplate = true,
                        mockText = "Met with Sarah and Lucas at Starbucks Seattle to outline our wilderness Yosemite hike plan for September 18th. Let's arrange Yosemite booking."
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🗣️ Hike Sync Review", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }

            Button(
                onClick = {
                    viewModel.startVoiceRecording(context)
                    viewModel.stopVoiceRecording(
                        context,
                        isMockTemplate = true,
                        mockText = "Doctor appointment allergy notes checklist. Take allergy dose every breakfast time and join blood check at Boston Hospital clinic main branch on July 20th."
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("🗣️ Doctor Clinic recap", color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp)
            }
        }
    }
}

// ==========================================
// SCREEN 3: SEARCH TAB SCREEN
// ==========================================
@Composable
fun SearchTab(
    viewModel: BrainViewModel,
    memories: List<MemoryItem>,
    onOpenDetail: (Int) -> Unit
) {
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var selectedCategoryFilter by remember { mutableStateOf("All") }

    val categories = listOf("All", "Work", "Personal", "Travel", "Health", "Finance", "Ideas")

    val filteredMemories = remember(memories, selectedCategoryFilter) {
        if (selectedCategoryFilter == "All") {
            memories
        } else {
            memories.filter { it.category.equals(selectedCategoryFilter, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("search_tab_root"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column {
            Text(
                text = "NEURAL RETRIEVAL",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp
            )
            Text(
                text = "Search Second Brain",
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Modern search input field with rounded bounds
        TextField(
            value = searchQuery,
            onValueChange = { viewModel.updateSearchQuery(it) },
            placeholder = { Text("Search keywords, locations, OCR, dates or categories...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("brain_search_field")
        )

        // Selectable Horiz category filters chips
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(categories) { cat ->
                val isSelected = cat == selectedCategoryFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                        .clickable { selectedCategoryFilter = cat }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = cat,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Live Feed matching results
        Text(
            text = "Matches found (${filteredMemories.size})",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            fontWeight = FontWeight.Medium
        )

        if (filteredMemories.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                        modifier = Modifier.size(54.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "No matching neural memories",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Try checking keywords or change categorization filtering",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(filteredMemories, key = { it.id }) { memory ->
                    MemoryItemCard(
                        memory = memory,
                        onClick = { onOpenDetail(memory.id) }
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4: FOLLOW-UPS & REMINDER TIMELINE
// ==========================================
@Composable
fun FollowUpsTab(
    activeReminders: List<MemoryItem>,
    viewModel: BrainViewModel,
    onOpenDetail: (Int) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("reminders_tab_root"),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "FUTURE PATHS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = "Follow-up Reminders",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (activeReminders.isEmpty()) {
            item {
                AppleEmptyState(
                    title = "All Caught Up!",
                    description = "Splendid job. You have no pending follow-up triggers or brain reminders scheduled.",
                    icon = Icons.Default.TaskAlt,
                    actionText = null,
                    onActionClick = {}
                )
            }
        } else {
            items(activeReminders, key = { it.id }) { item ->
                ReminderItemCard(
                    item = item,
                    onToggleComplete = { viewModel.toggleReminderCompleted(item) },
                    onContentClick = { onOpenDetail(item.id) }
                )
            }
        }
    }
}

// ==========================================
// COMPONENT: MOCK SCREENSHOT PILL
// ==========================================
@Composable
fun MockTemplatePill(
    index: Int,
    title: String,
    subtitle: String,
    category: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(180.dp)
            .clickable(onClick = onClick)
            .testTag("screenshot_template_$index"),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.08f)
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, color.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.2f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = category,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==========================================
// COMPONENT: MEMORY ITEM CARD
// ==========================================
@Composable
fun MemoryItemCard(
    memory: MemoryItem,
    onClick: () -> Unit
) {
    val dateText = remember(memory.timestamp) {
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(memory.timestamp))
    }

    val tags = remember(memory.keywords) {
        memory.keywords.split(",").filter { it.isNotBlank() }
    }

    // Material 3 premium soft shadow card
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .testTag("memory_card_${memory.id}"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (memory.type == "SCREENSHOT") Icons.Default.Image else Icons.Default.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )

                    Text(
                        text = memory.type,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.5.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(getCategoryColor(memory.category).copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = memory.category,
                        color = getCategoryColor(memory.category),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }

            // Title and Summary body
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = memory.title,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = memory.summary,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Pin row markers
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pin location / Pins source
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = memory.locationExtracted,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Text(
                    text = dateText,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            // Keywords horizontal tags flow
            if (tags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(tags) { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENT: REMINDER ITEM CARD
// ==========================================
@Composable
fun ReminderItemCard(
    item: MemoryItem,
    onToggleComplete: () -> Unit,
    onContentClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("MMM d, yyyy 'at' hh:mm a", Locale.getDefault()) }
    val timeLabel = remember(item.reminderTime) {
        item.reminderTime?.let { formatter.format(Date(it)) } ?: "No reminder time"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onContentClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.2.dp, AmberWarning.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            IconButton(
                onClick = onToggleComplete,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(AmberWarning.copy(alpha = 0.15f))
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Complete action task",
                    tint = AmberWarning,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Timer,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Trigger at $timeLabel",
                        fontSize = 11.sp,
                        color = AmberWarning,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// ==========================================
// COMPONENT: ACCESSIBLE COPED EMPTY STATE
// ==========================================
@Composable
fun AppleEmptyState(
    title: String,
    description: String,
    icon: ImageVector,
    actionText: String?,
    onActionClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )

            if (actionText != null) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(actionText, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ==========================================
// COMPONENT: STATS OVERVIEW CARDS WITH SLEEK Canvas Chart
// ==========================================
@Composable
fun AppleStatsCard(
    memories: List<MemoryItem>
) {
    val totalCount = memories.size
    val workCount = memories.count { it.category.equals("Work", ignoreCase = true) }
    val ideasCount = memories.count { it.category.equals("Ideas", ignoreCase = true) }
    val travelCount = memories.count { it.category.equals("Travel", ignoreCase = true) }
    val healthCount = memories.count { it.category.equals("Health", ignoreCase = true) }
    val financeCount = memories.count { it.category.equals("Finance", ignoreCase = true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(4.dp, RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "NEURAL INDEX STORAGE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = "Total Brain Memory",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "$totalCount items",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Canvas drawing: Beautiful modern segmented bar representation of categories ratio
            if (totalCount > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                    ) {
                        val workFraction = workCount.toFloat() / totalCount
                        val ideasFraction = ideasCount.toFloat() / totalCount
                        val travelFraction = travelCount.toFloat() / totalCount
                        val healthFraction = healthCount.toFloat() / totalCount
                        val financeFraction = financeCount.toFloat() / totalCount
                        val othersFraction = (totalCount - (workCount + ideasCount + travelCount + healthCount + financeCount)).toFloat() / totalCount

                        if (workFraction > 0f) Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(workFraction).background(MaterialTheme.colorScheme.primary))
                        if (ideasFraction > 0f) Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(ideasFraction).background(MaterialTheme.colorScheme.secondary))
                        if (travelFraction > 0f) Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(travelFraction).background(Color(0xFF5856D6)))
                        if (healthFraction > 0f) Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(healthFraction).background(Color(0xFF34C759)))
                        if (financeFraction > 0f) Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(financeFraction).background(AmberWarning))
                        if (othersFraction > 0f) Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(othersFraction).background(Color.Gray))
                    }

                    // Stat legend details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        LegendPill("Work", workCount, MaterialTheme.colorScheme.primary)
                        LegendPill("Ideas", ideasCount, MaterialTheme.colorScheme.secondary)
                        LegendPill("Travel", travelCount, Color(0xFF5856D6))
                        LegendPill("Health", healthCount, Color(0xFF34C759))
                    }
                }
            } else {
                Text(
                    text = "Awaiting mental intake to draw distribution metrics...",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
fun LegendPill(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label ($count)",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
    }
}

// ==========================================
// COMPONENT: DETAILED OVERLAY POPUP SCREEN
// ==========================================
@Composable
fun MemoryDetailSheet(
    memory: MemoryItem,
    onDismiss: () -> Unit,
    viewModel: BrainViewModel,
    isDarkTheme: Boolean
) {
    val context = LocalContext.current
    var selectedFollowUpDelay by remember { mutableStateOf(1) } // Default 1 minute for prompt follow-up simulation inspection

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clickable(enabled = false) {}
                .shadow(24.dp, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Drag handle element
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .align(Alignment.CenterHorizontally)
                )

                // Top Actions Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(getCategoryColor(memory.category).copy(alpha = 0.12f))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = memory.category.uppercase(),
                            color = getCategoryColor(memory.category),
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp,
                            letterSpacing = 0.5.sp
                        )
                    }

                    IconButton(
                        onClick = {
                            viewModel.deleteMemory(memory)
                            onDismiss()
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color(0xFFFF453A).copy(alpha = 0.1f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = Color(0xFFFF453A))
                    }
                }

                // Title
                Text(
                    text = memory.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Metadata list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetadataRow(icon = Icons.Default.CalendarToday, title = "Extracted Date", value = memory.dateExtracted)
                    MetadataRow(icon = Icons.Default.LocationOn, title = "Source / Location", value = memory.locationExtracted)
                    MetadataRow(icon = Icons.Default.Image, title = "Memory Ingest Type", value = memory.type)
                }

                // AI Summary Section
                Text(
                    text = "🧠 AI Core Synthesis",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = memory.summary,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    lineHeight = 22.sp
                )

                // Raw Extracted transcript OCR section
                Text(
                    text = "🔍 Ingested Context Details",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(14.dp)
                ) {
                    Text(
                        text = memory.rawContent.ifBlank { "No textual logs extracted from screenshot or voice recording." },
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Serif,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                    )
                }

                // REMINDER CONFIGURATOR
                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                Text(
                    text = "⏰ Future Action Calendar Follow-up",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                Text(
                    text = "Arrange automated system notification reminders to prompt action regarding this Memory.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                )

                if (memory.reminderTime != null && !memory.reminderCompleted) {
                    val dateStr = remember(memory.reminderTime) {
                        SimpleDateFormat("EEE, MMM d 'at' hh:mm a", Locale.getDefault()).format(Date(memory.reminderTime))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AmberWarning.copy(alpha = 0.1f))
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Alarm, contentDescription = null, tint = AmberWarning)
                            Column {
                                Text("Active follow-up scheduled", fontWeight = FontWeight.Bold, color = AmberWarning, fontSize = 13.sp)
                                Text(dateStr, color = AmberWarning.copy(alpha = 0.8f), fontSize = 12.sp)
                            }
                        }

                        Button(
                            onClick = { viewModel.toggleReminderCompleted(memory) },
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Complete Now", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(1, 5, 15, 60).forEach { mins ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selectedFollowUpDelay == mins) AmberWarning else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { selectedFollowUpDelay = mins }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (mins < 60) "${mins}m" else "1h",
                                    color = if (selectedFollowUpDelay == mins) Color.White else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                viewModel.setReminderTime(memory, selectedFollowUpDelay)
                                onDismiss()
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmberWarning)
                        ) {
                            Text("Set", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun MetadataRow(icon: ImageVector, title: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
        Text(value, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ==========================================
// COMPONENT: PREMIUM APPLE NAVIGATION BAR
// ==========================================
@Composable
fun AppleBottomBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    remindersCount: Int
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        modifier = Modifier.testTag("apple_bar_navigator")
    ) {
        NavigationBarItem(
            selected = selectedTab == 0,
            onClick = { onTabSelected(0) },
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Brain", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_dashboard")
        )

        NavigationBarItem(
            selected = selectedTab == 1,
            onClick = { onTabSelected(1) },
            icon = { Icon(Icons.Default.Mic, contentDescription = null) },
            label = { Text("Ingest", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_capture")
        )

        NavigationBarItem(
            selected = selectedTab == 2,
            onClick = { onTabSelected(2) },
            icon = { Icon(Icons.Default.Search, contentDescription = null) },
            label = { Text("Recall", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_search")
        )

        NavigationBarItem(
            selected = selectedTab == 3,
            onClick = { onTabSelected(3) },
            icon = {
                BadgedBox(
                    badge = {
                        if (remindersCount > 0) {
                            Badge(containerColor = AmberWarning) {
                                Text(
                                    text = remindersCount.toString(),
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Alarm, contentDescription = null)
                }
            },
            label = { Text("Triggers", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = MaterialTheme.colorScheme.primary,
                selectedTextColor = MaterialTheme.colorScheme.primary,
                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            ),
            modifier = Modifier.testTag("nav_reminders")
        )
    }
}

// Helper to style dynamic category pills with beautiful distinct colors
fun getCategoryColor(cat: String): Color {
    return when (cat.lowercase()) {
        "work" -> Color(0xFF0A84FF)
        "ideas" -> Color(0xFFBF5AF2)
        "travel" -> Color(0xFF5E5CE6)
        "health" -> Color(0xFF30D158)
        "finance" -> Color(0xFFED9D12)
        else -> Color(0xFF8E8E93)
    }
}
