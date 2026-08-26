package com.noesis.app.ui.conversations

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noesis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    workspaceId: String?,
    workspaceName: String,
    onOpenConversation: (id: String, title: String) -> Unit,
    onBack: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var renameTarget by remember { mutableStateOf<ConversationItem?>(null) }
    var renameText by remember { mutableStateOf("") }

    LaunchedEffect(workspaceId) { viewModel.load(workspaceId) }

    Scaffold(
        containerColor = NoesisBg,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = NoesisTextMuted)
                    }
                },
                title = {
                    Column {
                        Text(workspaceName, fontWeight = FontWeight.Bold, color = NoesisText)
                        Text("Conversations", style = MaterialTheme.typography.labelSmall, color = NoesisTextMuted)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load(workspaceId) }) {
                        Icon(Icons.Rounded.Sync, "Backup / Sync", tint = NoesisPurple)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NoesisSurface),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.createConversation { id -> onOpenConversation(id, "New AI Chat") } },
                containerColor = NoesisPurple,
                contentColor = Color.White,
                icon = { Icon(Icons.Rounded.AutoAwesome, "Chat with AI") },
                text = { Text("Chat with AI", fontWeight = FontWeight.Bold) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
        ) {
            // Search bar
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::search,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                placeholder = { Text("Search conversations…", color = NoesisTextMuted) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = NoesisTextMuted) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = NoesisPurple,
                    unfocusedBorderColor = NoesisBorder,
                    cursorColor          = NoesisPurple,
                ),
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NoesisPurple)
                }
            } else if (state.filtered.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Chat, null, tint = NoesisTextMuted, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("No conversations yet", color = NoesisTextMuted)
                        Text("Tap + to start a new one", style = MaterialTheme.typography.bodySmall, color = NoesisTextMuted)
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 88.dp),
                ) {
                    items(state.filtered, key = { it.id }) { conv ->
                        ConversationTile(
                            item      = conv,
                            onClick   = { onOpenConversation(conv.id, conv.title) },
                            onRename  = { renameTarget = conv; renameText = conv.title },
                            onDelete  = { viewModel.delete(conv.id) },
                        )
                    }
                }
            }
        }
    }

    // Rename dialog
    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = NoesisSurface2,
            title = { Text("Rename", color = NoesisText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NoesisPurple,
                        focusedLabelColor  = NoesisPurple,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.rename(target.id, renameText)
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoesisPurple),
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) { Text("Cancel", color = NoesisTextMuted) }
            },
        )
    }
}

@Composable
private fun ConversationTile(
    item: ConversationItem,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = NoesisSurface2),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Chat, null, tint = NoesisPurpleLight, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(
                text = item.title,
                fontWeight = FontWeight.Medium,
                color = NoesisText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
            )
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, "Options", tint = NoesisTextMuted)
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    containerColor = NoesisSurface,
                ) {
                    DropdownMenuItem(
                        text = { Text("Rename", color = NoesisText) },
                        leadingIcon = { Icon(Icons.Default.Edit, null, tint = NoesisTextMuted) },
                        onClick = { showMenu = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showMenu = false; onDelete() },
                    )
                }
            }
        }
    }
}
