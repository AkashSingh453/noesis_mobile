package com.noesis.app.ui.members

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noesis.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersScreen(
    workspaceId: String,
    workspaceName: String,
    onBack: () -> Unit,
    viewModel: MembersViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(workspaceId) {
        viewModel.load(workspaceId)
    }

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
                        Text("Manage Members", style = MaterialTheme.typography.labelSmall, color = NoesisTextMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NoesisSurface),
            )
        }
    ) { innerPadding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = NoesisPurple)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.members, key = { it.userId }) { member ->
                    MemberTile(
                        member = member,
                        onRoleChange = { newRole -> viewModel.updateRole(member.userId, newRole) },
                        onRemove = { viewModel.removeMember(member.userId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MemberTile(
    member: MemberItem,
    onRoleChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = NoesisSurface2),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(member.email, fontWeight = FontWeight.Medium, color = NoesisText)
                Text("Role: ${member.role}", style = MaterialTheme.typography.bodySmall, color = NoesisTextMuted)
            }
            if (member.role != "owner") {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "Options", tint = NoesisTextMuted)
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = NoesisSurface
                    ) {
                        val roles = listOf("member", "manager")
                        roles.filter { it != member.role }.forEach { r ->
                            DropdownMenuItem(
                                text = { Text("Make $r", color = NoesisText) },
                                onClick = { showMenu = false; onRoleChange(r) }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Remove", color = MaterialTheme.colorScheme.error) },
                            onClick = { showMenu = false; onRemove() }
                        )
                    }
                }
            }
        }
    }
}
