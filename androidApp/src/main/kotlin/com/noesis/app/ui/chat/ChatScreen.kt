package com.noesis.app.ui.chat

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import com.noesis.app.ui.theme.*
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// ChatScreen — main composable
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    workspaceId: String?,
    conversationId: String?,
    workspaceName: String,
    onBack: () -> Unit,
    chatViewModel: ChatViewModel = hiltViewModel(),
) {
    val uiState by chatViewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Auto-scroll to bottom as tokens arrive
    LaunchedEffect(uiState.messages.size, uiState.messages.lastOrNull()?.content) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    LaunchedEffect(workspaceId, conversationId) {
        chatViewModel.init(workspaceId, conversationId)
    }

    Scaffold(
        containerColor = NoesisBg,
        topBar = {
            NoesisTopBar(
                workspaceName = workspaceName,
                onBack = onBack,
                availableModels = uiState.availableModels,
                selectedModel = uiState.selectedModel,
                canChooseModel = uiState.canChooseModel,
                onModelSelected = chatViewModel::onModelSelected,
            )
        },
        bottomBar = {
            ChatInputBar(
                inputText = uiState.inputText,
                isStreaming = uiState.isStreaming || uiState.isThinking,
                canAttachFiles = uiState.canAttachFiles,
                onTextChange = chatViewModel::onInputChange,
                onSend = { chatViewModel.sendMessage(workspaceId, conversationId) },
                onStop = chatViewModel::stopGenerating,
            )
        },
        snackbarHost = {
            uiState.errorMessage?.let { err ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        TextButton(onClick = chatViewModel::clearError) {
                            Text("Dismiss", color = NoesisPurpleLight)
                        }
                    },
                    containerColor = NoesisSurface2,
                ) {
                    Text(err, color = MaterialTheme.colorScheme.error)
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (uiState.messages.isEmpty() && !uiState.isThinking) {
                // Empty state
                EmptyStateView()
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(uiState.messages, key = { it.id }) { message ->
                        ChatBubble(
                            message = message,
                            userEmail = uiState.userEmail,
                            userRole = uiState.userRole,
                        )
                    }

                    // Thinking Orb (shown while waiting for first token)
                    if (uiState.isThinking) {
                        item(key = "thinking_orb") {
                            ThinkingOrb()
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Top App Bar
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoesisTopBar(
    workspaceName: String,
    onBack: () -> Unit,
    availableModels: List<ModelOption>,
    selectedModel: ModelOption?,
    canChooseModel: Boolean,
    onModelSelected: (ModelOption) -> Unit,
) {
    var showModelMenu by remember { mutableStateOf(false) }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Back", tint = NoesisTextMuted)
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Atlas AI / Workspace Avatar
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = GlacialBlue,
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    // Online indicator dot
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.BottomEnd)
                            .offset(x = 2.dp, y = 2.dp)
                            .background(Color(0xFF10B981), CircleShape)
                            .border(1.5.dp, Color.White, CircleShape)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        workspaceName.ifBlank { "Atlas AI" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF111111),
                        maxLines = 1,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            selectedModel?.name ?: "Workspace Model",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF6B7280),
                        )
                        if (canChooseModel && availableModels.size > 1) {
                            Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFF6B7280), modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        actions = {
            // Manage Roles Pill Button
            Surface(
                color = BadgeBg,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .clickable { /* Manage Roles Action */ }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Rounded.Shield, "Manage Roles", tint = Color(0xFF4B5563), modifier = Modifier.size(14.dp))
                    Text("Manage Roles", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF4B5563))
                }
            }

            IconButton(onClick = { showModelMenu = true }) {
                Icon(Icons.Default.MoreVert, "Menu", tint = Color(0xFF4B5563))
            }
            if (canChooseModel && availableModels.size > 1) {
                DropdownMenu(
                    expanded = showModelMenu,
                    onDismissRequest = { showModelMenu = false },
                    containerColor = Color.White,
                ) {
                    availableModels.forEach { m ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    m.name,
                                    color = if (m.id == selectedModel?.id) GlacialBlue else Color(0xFF111111)
                                )
                            },
                            onClick = {
                                showModelMenu = false
                                onModelSelected(m)
                            }
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White),
    )
    HorizontalDivider(color = NoesisBorder, thickness = 1.dp)
}

// ---------------------------------------------------------------------------
// Chat Input Bar
// ---------------------------------------------------------------------------

@Composable
private fun ChatInputBar(
    inputText: String,
    isStreaming: Boolean,
    canAttachFiles: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Stop Generating Button (shown above input)
        AnimatedVisibility(
            visible = isStreaming,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
        ) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = CopperBrown),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(14.dp).background(Color.White, RoundedCornerShape(2.dp)))
                    Text("Stop Generating", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Input Pill
        Surface(
            color = Color(0xFFF3F4F6), // Light gray background for pill
            shape = RoundedCornerShape(32.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Attach File
                if (canAttachFiles) {
                    IconButton(onClick = { /* gated feature placeholder */ }) {
                        Icon(Icons.Default.AttachFile, "Attach file", tint = Color(0xFF6B7280))
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }

                // Text Field
                TextField(
                    value = inputText,
                    onValueChange = onTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Atlas AI...", color = Color(0xFF9CA3AF)) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (!isStreaming && inputText.isNotBlank()) onSend() }),
                    maxLines = 5,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = GlacialBlue,
                        focusedTextColor = Color(0xFF111111),
                        unfocusedTextColor = Color(0xFF111111),
                    )
                )

                // Mic Icon
                IconButton(onClick = { /* Voice input placeholder */ }) {
                    Icon(Icons.Rounded.Mic, "Voice Input", tint = Color(0xFF6B7280))
                }

                // Send Button
                Box(
                    modifier = Modifier
                        .padding(end = 4.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (inputText.isNotBlank()) GlacialBlue else Color(0xFFD1D5DB))
                        .clickable(enabled = inputText.isNotBlank()) { if (!isStreaming) onSend() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.ArrowUpward, "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }

        // Footer Text
        Text(
            text = "AI responses may contain errors. Verify important info.",
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.sp,
            color = Color(0xFF9CA3AF),
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
        )
    }
}

// ---------------------------------------------------------------------------
// Chat Message Bubble
// ---------------------------------------------------------------------------

@Composable
private fun ChatBubble(message: ChatMessage, userEmail: String?, userRole: String?) {
    val isUser = message.role == MessageRole.USER
    val userName = userEmail?.substringBefore("@") ?: "Jordan Lee" // Mock or fallback
    val roleDisplay = userRole?.replaceFirstChar { it.uppercase() } ?: "Owner"

    // The timestamp could be from message.createdAt in a real app, mocking here for UI consistency
    val timestamp = "2:38 PM" 

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Header for User / AI
        if (isUser) {
            Row(
                modifier = Modifier.padding(bottom = 4.dp, end = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Badge
                Surface(
                    color = GlacialBlue,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Rounded.Shield, null, tint = Color.White, modifier = Modifier.size(12.dp))
                        Text(roleDisplay, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text(userName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF111111))
            }
        } else {
            Text(
                "Atlas AI",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111111),
                modifier = Modifier.padding(bottom = 4.dp, start = 40.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom // Align avatars to bottom of bubble
        ) {
            if (!isUser) {
                // Assistant avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color = GlacialBlue, shape = CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
            }

            Column(
                modifier = Modifier.widthIn(max = 300.dp),
                horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            ) {
                Surface(
                    shape = RoundedCornerShape(
                        topStart = 18.dp,
                        topEnd = 18.dp,
                        bottomStart = if (isUser) 18.dp else 4.dp,
                        bottomEnd = if (isUser) 4.dp else 18.dp,
                    ),
                    color = if (isUser) GlacialBlue else SurfaceLight,
                ) {
                    Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        if (isUser) {
                            Text(
                                text = message.content,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        } else {
                            Markdown(
                                content = message.content.ifBlank { " " }
                            )
                        }
                    }
                }

                // Streaming cursor indicator
                if (message.isStreaming) {
                    StreamingCursor()
                }
            }

            if (isUser) {
                Spacer(Modifier.width(8.dp))
                // User avatar
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(SurfaceLight, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, null, tint = Color(0xFF9CA3AF), modifier = Modifier.size(20.dp))
                }
            }
        }
        
        // Timestamp
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9CA3AF),
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp).padding(
                start = if (isUser) 0.dp else 40.dp,
                end = if (isUser) 40.dp else 0.dp
            )
        )
    }
}

// ---------------------------------------------------------------------------
// Streaming cursor (blinking caret after last token)
// ---------------------------------------------------------------------------

@Composable
private fun StreamingCursor() {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "cursor_alpha",
    )
    Box(
        modifier = Modifier
            .padding(start = 4.dp, top = 4.dp)
            .size(width = 2.dp, height = 14.dp)
            .graphicsLayer { this.alpha = alpha }
            .background(NoesisPurple, RoundedCornerShape(1.dp)),
    )
}

// ---------------------------------------------------------------------------
// Thinking Orb — shown while waiting for the first streaming token
// ---------------------------------------------------------------------------

@Composable
fun ThinkingOrb() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking_orb")

    // Slow rhythmic breathing scale
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_scale",
    )

    // Slow breathing inner glow alpha
    val innerAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "inner_alpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(NoesisPurpleLight, NoesisPurple))),
            contentAlignment = Alignment.Center,
        ) {
            Text("N", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Spacer(Modifier.width(8.dp))

        // The pulsing orb itself
        Box(
            modifier = Modifier
                .size(40.dp) // Slightly smaller for a calmer feel
                .scale(scale),
            contentAlignment = Alignment.Center,
        ) {
            // Outer soft glow ring
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.radialGradient(
                            listOf(
                                NoesisPurple.copy(alpha = 0.3f),
                                Color.Transparent
                            ),
                        ),
                        shape = CircleShape,
                    ),
            )
            // Inner core
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer { alpha = innerAlpha }
                    .background(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.9f), NoesisPurple),
                        ),
                        shape = CircleShape,
                    ),
            )
        }

        Spacer(Modifier.width(10.dp))
        Text(
            "Thinking…",
            style = MaterialTheme.typography.bodySmall,
            color = NoesisTextMuted,
        )
    }
}

// ---------------------------------------------------------------------------
// Empty State
// ---------------------------------------------------------------------------

@Composable
private fun EmptyStateView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "idle_orb")
        val orbScale by infiniteTransition.animateFloat(
            initialValue = 0.9f, targetValue = 1.05f,
            animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "idle_scale",
        )

        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(orbScale)
                .background(
                    brush = Brush.radialGradient(
                        listOf(NoesisPurple.copy(0.4f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("N", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(24.dp))

        Text(
            "What can I help you with?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = NoesisText,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Start a conversation with Noesis, your AI workspace companion.",
            style = MaterialTheme.typography.bodyMedium,
            color = NoesisTextMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        Spacer(Modifier.height(32.dp))

        // Suggestion chips
        val suggestions = listOf("Explain quantum computing", "Write a Kotlin coroutine example", "Plan my week")
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { /* populate input */ },
                label = { Text(suggestion, color = NoesisTextMuted) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, NoesisBorder),
                colors = SuggestionChipDefaults.suggestionChipColors(containerColor = NoesisSurface2),
            )
        }
    }
}
