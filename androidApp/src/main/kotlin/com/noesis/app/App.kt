package com.noesis.app

import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.hilt.navigation.compose.hiltViewModel
import com.noesis.app.ui.auth.AuthScreen
import com.noesis.app.ui.chat.ChatScreen
import com.noesis.app.ui.conversations.ConversationListScreen
import com.noesis.app.ui.workspace.WorkspaceScreen
import com.noesis.app.ui.theme.NoesisTheme
import io.github.jan.supabase.auth.status.SessionStatus

sealed class Screen(val route: String) {
    object Auth         : Screen("auth")
    object Workspace    : Screen("workspace")
    object Conversations: Screen("conversations?workspaceId={workspaceId}&workspaceName={workspaceName}") {
        fun create(workspaceId: String?, workspaceName: String) =
            "conversations?workspaceId=${workspaceId ?: ""}&workspaceName=$workspaceName"
    }
    object Chat         : Screen("chat?workspaceId={workspaceId}&conversationId={conversationId}&workspaceName={workspaceName}") {
        fun create(workspaceId: String?, conversationId: String?, workspaceName: String = "Personal") =
            "chat?workspaceId=${workspaceId ?: ""}&conversationId=${conversationId ?: ""}&workspaceName=$workspaceName"
    }
    object Members      : Screen("members?workspaceId={workspaceId}&workspaceName={workspaceName}") {
        fun create(workspaceId: String, workspaceName: String) =
            "members?workspaceId=$workspaceId&workspaceName=$workspaceName"
    }
}

@Composable
fun App(viewModel: MainViewModel = hiltViewModel()) {
    NoesisTheme {
        val navController = rememberNavController()
        var startDestination by remember { mutableStateOf<String?>(null) }

        LaunchedEffect(Unit) {
            var isInitialState = true
            viewModel.sessionStatus.collect { status ->
                when (status) {
                    is SessionStatus.Authenticated -> {
                        if (isInitialState) {
                            startDestination = Screen.Workspace.route
                            isInitialState = false
                        } else {
                            if (navController.currentDestination?.route != Screen.Workspace.route) {
                                runCatching {
                                    navController.navigate(Screen.Workspace.route) {
                                        popUpTo(Screen.Auth.route) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                    is SessionStatus.NotAuthenticated -> {
                        if (isInitialState) {
                            startDestination = Screen.Auth.route
                            isInitialState = false
                        } else {
                            if (navController.currentDestination?.route != Screen.Auth.route) {
                                runCatching {
                                    navController.navigate(Screen.Auth.route) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }
        }

        if (startDestination == null) return@NoesisTheme

        NavHost(navController = navController, startDestination = startDestination!!) {

            // ── Auth ──────────────────────────────────────────────────────
            composable(Screen.Auth.route) {
                AuthScreen(onAuthenticated = {
                    navController.navigate(Screen.Workspace.route) {
                        popUpTo(Screen.Auth.route) { inclusive = true }
                    }
                })
            }

            // ── Workspace hub ─────────────────────────────────────────────
            composable(Screen.Workspace.route) {
                WorkspaceScreen(
                    onSelectWorkspace = { id, name ->
                        navController.navigate(Screen.Conversations.create(id, name))
                    },
                    onManageWorkspace = { id, name ->
                        navController.navigate(Screen.Members.create(id, name))
                    },
                    onPersonalChat = {
                        navController.navigate(Screen.Conversations.create(null, "Personal"))
                    },
                    onSignOut = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }

            // ── Conversation list ─────────────────────────────────────────
            composable(
                route = Screen.Conversations.route,
                arguments = listOf(
                    navArgument("workspaceId")   { type = NavType.StringType; defaultValue = "" },
                    navArgument("workspaceName") { type = NavType.StringType; defaultValue = "Personal" },
                ),
            ) { backStack ->
                val workspaceId   = backStack.arguments?.getString("workspaceId").takeIf { !it.isNullOrBlank() }
                val workspaceName = backStack.arguments?.getString("workspaceName") ?: "Personal"

                ConversationListScreen(
                    workspaceId   = workspaceId,
                    workspaceName = workspaceName,
                    onOpenConversation = { convId, _ ->
                        navController.navigate(Screen.Chat.create(workspaceId, convId, workspaceName))
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Chat ──────────────────────────────────────────────────────
            composable(
                route = Screen.Chat.route,
                arguments = listOf(
                    navArgument("workspaceId")    { type = NavType.StringType; defaultValue = "" },
                    navArgument("conversationId") { type = NavType.StringType; defaultValue = "" },
                    navArgument("workspaceName")  { type = NavType.StringType; defaultValue = "Personal" },
                ),
            ) { backStack ->
                val workspaceId    = backStack.arguments?.getString("workspaceId").takeIf { !it.isNullOrBlank() }
                val conversationId = backStack.arguments?.getString("conversationId").takeIf { !it.isNullOrBlank() }
                val workspaceName  = backStack.arguments?.getString("workspaceName") ?: "Personal"

                ChatScreen(
                    workspaceId    = workspaceId,
                    conversationId = conversationId,
                    workspaceName  = workspaceName,
                    onBack = { navController.popBackStack() },
                )
            }

            // ── Members ───────────────────────────────────────────────────
            composable(
                route = Screen.Members.route,
                arguments = listOf(
                    navArgument("workspaceId")   { type = NavType.StringType; defaultValue = "" },
                    navArgument("workspaceName") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { backStack ->
                val workspaceId   = backStack.arguments?.getString("workspaceId").takeIf { !it.isNullOrBlank() }
                val workspaceName = backStack.arguments?.getString("workspaceName") ?: ""

                if (workspaceId != null) {
                    com.noesis.app.ui.members.MembersScreen(
                        workspaceId   = workspaceId,
                        workspaceName = workspaceName,
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}