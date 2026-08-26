package com.noesis.app.ui.workspace

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noesis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    onSelectWorkspace: (id: String, name: String) -> Unit,
    onManageWorkspace: (id: String, name: String) -> Unit,
    onPersonalChat: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showJoinDialog   by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var inviteCodeField  by remember { mutableStateOf("") }
    var workspaceNameField by remember { mutableStateOf("") }
    var showInviteResult by remember { mutableStateOf<String?>(null) }

    // Show success/error snackbars
    LaunchedEffect(state.successMessage) {
        if (state.successMessage != null) kotlinx.coroutines.delay(3000)
        viewModel.clearMessages()
    }

    Scaffold(
        containerColor = NoesisBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(NoesisPurple, NoesisBlue)))
                        )
                        Column {
                            Text("noesis", fontWeight = FontWeight.Bold, color = NoesisText)
                            Text("workspaces", style = MaterialTheme.typography.labelSmall, color = NoesisTextMuted)
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.Logout, "Sign Out", tint = NoesisTextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NoesisSurface),
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallFloatingActionButton(
                    onClick = { showJoinDialog = true },
                    containerColor = NoesisSurface2,
                    contentColor = NoesisPurpleLight,
                ) {
                    Icon(Icons.Default.Link, "Join workspace")
                }
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = NoesisPurple,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, "Create workspace")
                }
            }
        },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Personal chat tile
            item {
                WorkspaceTile(
                    name     = "Personal",
                    subtitle = "Your private conversations",
                    roleTag  = state.userRole ?: "personal",
                    isPersonal = true,
                    onClick  = onPersonalChat,
                    onInvite = null,
                )
            }

            if (state.isLoading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = NoesisPurple)
                    }
                }
            }

            if (state.workspaces.isNotEmpty()) {
                item {
                    Text(
                        "WORKSPACES",
                        style = MaterialTheme.typography.labelSmall,
                        color = NoesisTextMuted,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(state.workspaces) { ws ->
                    WorkspaceTile(
                        name     = ws.name,
                        subtitle = "@${ws.slug}",
                        roleTag  = ws.role,
                        isPersonal = false,
                        onClick  = { onSelectWorkspace(ws.id, ws.name) },
                        onInvite = if (ws.role == "owner") {
                            { viewModel.generateInvite(ws.id) { code -> showInviteResult = code } }
                        } else null,
                        onManage = if (ws.role == "owner") {
                            { onManageWorkspace(ws.id, ws.name) }
                        } else null,
                    )
                }
            } else if (!state.isLoading) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Group, null, tint = NoesisTextMuted, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(12.dp))
                            Text("No workspaces yet", color = NoesisTextMuted)
                            Text("Join one with an invite code or create your own", style = MaterialTheme.typography.bodySmall, color = NoesisTextMuted)
                        }
                    }
                }
            }

            state.error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(err, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
            }
        }
    }

    // Join Workspace Dialog
    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false; inviteCodeField = "" },
            containerColor = NoesisSurface2,
            title = { Text("Join Workspace", color = NoesisText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the 8-character invite code from your workspace owner.", style = MaterialTheme.typography.bodySmall, color = NoesisTextMuted)
                    OutlinedTextField(
                        value = inviteCodeField,
                        onValueChange = { inviteCodeField = it.uppercase() },
                        label = { Text("Invite Code") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NoesisPurple,
                            focusedLabelColor  = NoesisPurple,
                            cursorColor        = NoesisPurple,
                        ),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showJoinDialog = false
                        viewModel.joinWorkspace(inviteCodeField)
                        inviteCodeField = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoesisPurple),
                ) { Text("Join") }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false; inviteCodeField = "" }) {
                    Text("Cancel", color = NoesisTextMuted)
                }
            },
        )
    }

    // Create Workspace Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; workspaceNameField = "" },
            containerColor = NoesisSurface2,
            title = { Text("Create Workspace", color = NoesisText, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = workspaceNameField,
                    onValueChange = { workspaceNameField = it },
                    label = { Text("Workspace Name") },
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
                        showCreateDialog = false
                        viewModel.createWorkspace(workspaceNameField)
                        workspaceNameField = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NoesisPurple),
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; workspaceNameField = "" }) {
                    Text("Cancel", color = NoesisTextMuted)
                }
            },
        )
    }

    // Show generated invite code
    showInviteResult?.let { code ->
        AlertDialog(
            onDismissRequest = { showInviteResult = null },
            containerColor = NoesisSurface2,
            title = { Text("Invite Code", color = NoesisText, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Share this code with your team member. It expires in 7 days.", style = MaterialTheme.typography.bodySmall, color = NoesisTextMuted)
                    Card(colors = CardDefaults.cardColors(containerColor = NoesisBg)) {
                        Text(
                            code,
                            modifier = Modifier.padding(16.dp),
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            color = NoesisPurpleLight,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showInviteResult = null }, colors = ButtonDefaults.buttonColors(containerColor = NoesisPurple)) {
                    Text("Done")
                }
            },
        )
    }
}

@Composable
private fun WorkspaceTile(
    name: String,
    subtitle: String,
    roleTag: String,
    isPersonal: Boolean,
    onClick: () -> Unit,
    onInvite: (() -> Unit)?,
    onManage: (() -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = NoesisSurface2),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Icon orb
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            if (isPersonal) listOf(NoesisBlue, NoesisTeal)
                            else listOf(NoesisPurple, NoesisPurpleDark)
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isPersonal) Icons.Default.Person else Icons.Default.Group,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.SemiBold, color = NoesisText)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = NoesisTextMuted)
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoleBadge(roleTag)
                Row {
                    if (onManage != null) {
                        IconButton(onClick = onManage, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Settings, "Manage members", tint = NoesisTextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (onInvite != null) {
                        IconButton(onClick = onInvite, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.PersonAdd, "Generate invite", tint = NoesisPurpleLight, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleBadge(role: String) {
    val (bg, fg) = when (role) {
        "owner"   -> GlacialBlue to Color.White
        "manager" -> WeatheredGranite to Color.White
        "member"  -> Color(0xFFE5E7EB) to Color(0xFF4B5563)
        else      -> Color(0xFFE5E7EB) to Color(0xFF4B5563)
    }
    Surface(shape = RoundedCornerShape(8.dp), color = bg) {
        Text(
            role.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
