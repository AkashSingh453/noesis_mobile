package com.noesis.app.ui.auth

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.noesis.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onAuthenticated: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    var isSignUp by remember { mutableStateOf(false) }
    var emailField by remember { mutableStateOf("") }
    var passwordField by remember { mutableStateOf("") }
    var fullNameField by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusManager = LocalFocusManager.current

    // Ambient pulse animation for background orb
    val infiniteTransition = rememberInfiniteTransition(label = "bg_pulse")
    val orbScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_scale",
    )
    val orbAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb_alpha",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(NoesisBg),
    ) {
        // Ambient background gradient orb
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                    alpha = orbAlpha
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(NoesisPurple, NoesisBlue, Color.Transparent),
                        radius = 600f,
                        center = Offset(250f, 250f),
                    ),
                    shape = RoundedCornerShape(50),
                )
                .blur(80.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(top = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Logo / brand
            Text(
                text = "noesis",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    brush = Brush.linearGradient(
                        listOf(NoesisPurpleLight, NoesisTeal),
                    ),
                ),
            )
            Text(
                text = "Your intelligent workspace",
                style = MaterialTheme.typography.bodyMedium,
                color = NoesisTextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 40.dp),
            )

            // Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = NoesisSurface,
                tonalElevation = 0.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Tab switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(NoesisSurface2),
                    ) {
                        listOf("Sign In", "Sign Up").forEachIndexed { idx, label ->
                            val selected = (idx == 1) == isSignUp
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) NoesisPurple else Color.Transparent),
                                contentAlignment = Alignment.Center,
                            ) {
                                TextButton(
                                    onClick = {
                                        isSignUp = (idx == 1)
                                        errorMessage = null
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        label,
                                        color = if (selected) Color.White else NoesisTextMuted,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Full name (sign up only)
                    AnimatedVisibility(visible = isSignUp) {
                        OutlinedTextField(
                            value = fullNameField,
                            onValueChange = { fullNameField = it },
                            label = { Text("Full Name") },
                            leadingIcon = { Icon(Icons.Default.Person, null) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            shape = RoundedCornerShape(12.dp),
                            colors = noesisTextFieldColors(),
                            singleLine = true,
                        )
                    }

                    // Email
                    OutlinedTextField(
                        value = emailField,
                        onValueChange = { emailField = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, null) },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next,
                        ),
                        keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                        shape = RoundedCornerShape(12.dp),
                        colors = noesisTextFieldColors(),
                        singleLine = true,
                    )

                    // Password
                    OutlinedTextField(
                        value = passwordField,
                        onValueChange = { passwordField = it },
                        label = { Text("Password") },
                        leadingIcon = { Icon(Icons.Default.Lock, null) },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle password",
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        shape = RoundedCornerShape(12.dp),
                        colors = noesisTextFieldColors(),
                        singleLine = true,
                    )

                    // Error message
                    AnimatedVisibility(visible = errorMessage != null) {
                        errorMessage?.let {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                            ) {
                                Text(
                                    text = it,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    // Action button
                    Button(
                        onClick = {
                            scope.launch {
                                isLoading = true
                                errorMessage = null
                                try {
                                    if (isSignUp) {
                                        viewModel.signUp(emailField, passwordField, fullNameField)
                                    } else {
                                        viewModel.signIn(emailField, passwordField)
                                    }
                                    onAuthenticated()
                                } catch (e: Exception) {
                                    errorMessage = e.message ?: "Authentication failed"
                                } finally {
                                    isLoading = false
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        enabled = !isLoading && emailField.isNotBlank() && passwordField.isNotBlank(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = NoesisPurple),
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color.White,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text(
                                text = if (isSignUp) "Create Account" else "Sign In",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "By continuing, you agree to Noesis Terms of Service",
                style = MaterialTheme.typography.labelSmall,
                color = NoesisTextMuted,
            )
        }
    }
}

@Composable
private fun noesisTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = NoesisPurple,
    unfocusedBorderColor = NoesisBorder,
    focusedLabelColor = NoesisPurple,
    cursorColor = NoesisPurple,
    focusedTextColor = NoesisText,
    unfocusedTextColor = NoesisText,
    focusedLeadingIconColor = NoesisPurple,
    unfocusedLeadingIconColor = NoesisTextMuted,
)
