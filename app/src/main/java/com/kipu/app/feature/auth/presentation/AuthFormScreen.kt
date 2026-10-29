package com.kipu.app.feature.auth.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kipu.app.R
import com.kipu.app.feature.auth.domain.PasswordValidator
import com.kipu.app.ui.motion.rememberReducedMotionEnabled
import com.kipu.app.ui.theme.KipuMotionTokens
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

@Composable
internal fun AuthFormScreen(
    uiState: AuthUiState,
    onEmailChanged: (String) -> Unit,
    onPasswordChanged: (String) -> Unit,
    onLoginClick: () -> Unit,
    onRegisterClick: () -> Unit,
    onNavigateToRecovery: () -> Unit,
    onDismissExistingAccountDialog: () -> Unit,
    onDismissConfirmationDialog: () -> Unit,
    initialRegisterMode: Boolean,
    modifier: Modifier = Modifier,
    onNavigateToRegister: (() -> Unit)? = null,
    onNavigateToLogin: (() -> Unit)? = null,
) {
    var register by rememberSaveable { mutableStateOf(initialRegisterMode) }
    // Passwords deliberately never enter rememberSaveable or SavedStateHandle.
    var confirmation by remember { mutableStateOf("") }
    var confirmationError by remember { mutableStateOf<String?>(null) }
    var registrationAttempted by remember { mutableStateOf(false) }
    var legalDocument by rememberSaveable { mutableStateOf<String?>(null) }
    val colors = rememberCalmEmeraldColors()
    val reducedMotion = rememberReducedMotionEnabled()
    val duration = if (reducedMotion) 0 else KipuMotionTokens.FastMillis
    val canAct = !uiState.isLoading && uiState.cooldownSeconds == 0
    val focus = LocalFocusManager.current
    val clock = rememberSceneClock(active = true)

    LaunchedEffect(uiState.password) {
        if (uiState.password.isEmpty()) confirmation = ""
    }
    DisposableEffect(Unit) { onDispose { onPasswordChanged("") } }

    fun changeMode(toRegister: Boolean) {
        if (register == toRegister || !canAct) return
        focus.clearFocus()
        confirmation = ""
        confirmationError = null
        registrationAttempted = false
        onPasswordChanged("")
        if (toRegister && onNavigateToRegister != null) onNavigateToRegister()
        else if (!toRegister && onNavigateToLogin != null) onNavigateToLogin()
        else register = toRegister
    }
    BackHandler(register && !initialRegisterMode && onNavigateToRegister == null) { changeMode(false) }
    fun submit() {
        if (!canAct) return
        if (register) {
            registrationAttempted = true
            val validation = PasswordValidator.validatePasswordConfirmation(uiState.password, confirmation)
            if (validation is PasswordValidator.ValidationResult.Invalid) {
                confirmationError = validation.reason
                return
            }
            confirmation = ""
            onRegisterClick()
        } else onLoginClick()
    }

    Box(modifier.fillMaxSize().background(colors.background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            // The header is decoration: it gives way on short windows so the fields keep their size.
            val loginHeader = (viewport * 0.34f).coerceIn(228.dp, 300.dp)
            val registerHeader = (viewport * 0.26f).coerceIn(184.dp, 236.dp)
            val headerTarget by animateFloatAsState(
                if (register) registerHeader.value else loginHeader.value, tween(duration + 150), label = "auth-header-height",
            )
            val scroll = rememberScrollState()
            val sectionGap = if (viewport < 700.dp) 10.dp else 12.dp
            Column(Modifier.fillMaxSize().verticalScroll(scroll)) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = viewport),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth()) {
                            AuthHero(register, clock, headerTarget.dp + statusTop, duration + 150)
                            AuthModeTabs(
                                register, canAct, duration + 100, ::changeMode,
                                Modifier.align(Alignment.BottomCenter).offset(y = 29.dp).widthIn(max = 480.dp)
                                    .padding(horizontal = 24.dp).sceneReveal(clock, 0.15f, 0.6f, dy = 16.dp),
                            )
                        }
                        Column(
                            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 49.dp),
                        ) {
                            AuthBanner(uiState.generalError)
                            AuthBanner(if (uiState.cooldownSeconds > 0)
                                stringResource(R.string.auth_cooldown_message, uiState.cooldownSeconds) else null)
                            AuthFloatingField(uiState.email, onEmailChanged, "Correo electrónico", Icons.Outlined.Email,
                                Modifier.sceneReveal(clock, 0.25f, 0.6f, dy = 16.dp),
                                error = uiState.emailError, enabled = canAct)
                            Spacer(Modifier.height(sectionGap))
                            AuthFloatingField(uiState.password, onPasswordChanged, "Contraseña", Icons.Outlined.Lock,
                                Modifier.sceneReveal(clock, 0.33f, 0.6f, dy = 16.dp),
                                error = uiState.passwordError, enabled = canAct, password = true,
                                imeAction = if (register) ImeAction.Next else ImeAction.Done, onDone = ::submit)
                            AnimatedVisibility(register,
                                enter = expandVertically(tween(duration), expandFrom = Alignment.Top) + fadeIn(tween(duration)),
                                exit = shrinkVertically(tween(duration), shrinkTowards = Alignment.Top) + fadeOut(tween(duration))) {
                                Column(Modifier.fillMaxWidth()) {
                                    Spacer(Modifier.height(10.dp))
                                    AuthRequirementChips(uiState.password, registrationAttempted)
                                    Spacer(Modifier.height(sectionGap + 2.dp))
                                    AuthFloatingField(confirmation, { confirmation = it; confirmationError = null },
                                        "Confirmar contraseña", Icons.Outlined.Lock,
                                        error = confirmationError, enabled = canAct, password = true, onDone = ::submit)
                                    if (confirmation.isNotEmpty() && uiState.password.isNotEmpty()) {
                                        val match = PasswordValidator.passwordsMatch(uiState.password, confirmation)
                                        Text(if (match) "Las contraseñas coinciden" else "Las contraseñas no coinciden",
                                            color = if (match) colors.incomeText else MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(top = 6.dp))
                                    }
                                }
                            }
                            AnimatedVisibility(!register,
                                enter = expandVertically(tween(duration)) + fadeIn(tween(duration)),
                                exit = shrinkVertically(tween(duration)) + fadeOut(tween(duration))) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                                    TextButton(onNavigateToRecovery, enabled = canAct) {
                                        Text("¿Olvidaste tu contraseña?", color = colors.primaryDeep, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Spacer(Modifier.height(if (register) 14.dp else 4.dp))
                            AuthCtaButton(
                                text = if (register) "Crear cuenta" else "Ingresar",
                                loadingText = if (register) "Creando cuenta…" else "Ingresando…",
                                loading = uiState.isLoading, enabled = canAct, onClick = ::submit,
                                modifier = Modifier.sceneReveal(clock, 0.41f, 0.6f, dy = 16.dp),
                            )
                            Box(Modifier.fillMaxWidth().sceneReveal(clock, 0.48f, 0.6f, dy = 16.dp), contentAlignment = Alignment.Center) {
                                TextButton({ changeMode(!register) }, enabled = canAct) {
                                    val question = if (register) "¿Ya tienes una cuenta?" else "¿No tienes cuenta?"
                                    val action = if (register) "Iniciar sesión" else "Regístrate"
                                    Text(buildAnnotatedString {
                                        append("$question ")
                                        withStyle(SpanStyle(color = colors.primaryDeep, fontWeight = FontWeight.Bold)) { append(action) }
                                    }, color = colors.secondaryMuted, textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    Text(buildAnnotatedString {
                        append(if (register) "Al registrarte aceptas los " else "Al ingresar aceptas los ")
                        val styles = TextLinkStyles(SpanStyle(color = colors.primaryDeep, fontWeight = FontWeight.SemiBold,
                            textDecoration = TextDecoration.Underline))
                        withLink(LinkAnnotation.Clickable("terms", styles) { legalDocument = "Términos y Condiciones" }) {
                            append("Términos y Condiciones")
                        }
                        append(" y ")
                        withLink(LinkAnnotation.Clickable("privacy", styles) { legalDocument = "Políticas de privacidad" }) {
                            append("Políticas de privacidad")
                        }
                        append(" de Kipu.")
                    }, style = MaterialTheme.typography.bodySmall, color = colors.secondaryMuted, textAlign = TextAlign.Center,
                        modifier = Modifier.widthIn(max = 480.dp).padding(horizontal = 32.dp, vertical = 16.dp)
                            .sceneReveal(clock, 0.55f, 0.6f, dy = 12.dp))
                }
            }
            // Keeps the status bar icons readable once the teal header has scrolled away.
            val scrimAlpha by animateFloatAsState(if (scroll.value > 0) 1f else 0f, tween(150), label = "auth-status-scrim")
            Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(Color(0xFF0A5A54).copy(alpha = scrimAlpha)))
        }
    }
    if (uiState.showExistingAccountDialog) AlertDialog(
        onDismissRequest = onDismissExistingAccountDialog,
        title = { Text(stringResource(R.string.auth_existing_account_dialog_title)) },
        text = { Text(stringResource(R.string.auth_existing_account_dialog_message)) },
        confirmButton = { TextButton({ onDismissExistingAccountDialog(); changeMode(false) }) { Text("Iniciar Sesión") } },
        dismissButton = { TextButton(onDismissExistingAccountDialog) { Text("Cancelar") } })
    if (uiState.confirmationRequiredEmail != null) AlertDialog(
        onDismissRequest = onDismissConfirmationDialog,
        title = { Text("Verifica tu cuenta") },
        text = { Text(stringResource(R.string.auth_confirmation_required_message)) },
        confirmButton = { TextButton({ onDismissConfirmationDialog(); changeMode(false) }) { Text("Entendido") } })
    // No approved legal URLs/documents exist in the repository: do not invent their contents.
    if (legalDocument != null) AlertDialog(onDismissRequest = { legalDocument = null },
        title = { Text(legalDocument.orEmpty()) },
        text = { Text("Este documento aún no está disponible en la aplicación.") },
        confirmButton = { TextButton({ legalDocument = null }) { Text("Cerrar") } })
}
