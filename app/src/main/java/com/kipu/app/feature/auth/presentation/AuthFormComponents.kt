package com.kipu.app.feature.auth.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import com.kipu.app.R
import com.kipu.app.ui.theme.CalmEmeraldColors
import com.kipu.app.ui.theme.LightStatusBarIcons
import com.kipu.app.ui.theme.rememberCalmEmeraldColors

/** Rounded bottom edge of the header: the same wide ellipse the design uses (`ellipse(140% 100% at 50% 0%)`). */
private object HeroShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            addOval(Rect(-0.9f * size.width, -size.height, 1.9f * size.width, size.height))
        }
        return Outline.Generic(path)
    }
}

/** Lighter end of the brand button and of the selector's indicator. */
private fun CalmEmeraldColors.primaryGradient(): Brush =
    Brush.linearGradient(listOf(primaryDeep, lerp(primaryDeep, Color.White, if (isDark) 0.08f else 0.12f)))

/** Teal header with the swaying quipu cords, the real Kipu logo and a headline that changes with the mode. */
@Composable
internal fun AuthHero(register: Boolean, clock: SceneClock, minHeight: Dp, durationMillis: Int, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    LightStatusBarIcons()
    Box(
        modifier.fillMaxWidth().clip(HeroShape)
            .drawBehind { drawRect(celebrationBrush(colors.isDark, size.width, size.height)) },
    ) {
        Canvas(Modifier.matchParentSize()) { drawHeroCords(clock()) }
        CappedFontScale {
            Column(
                Modifier.fillMaxWidth().heightIn(min = minHeight).statusBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 52.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(Modifier.sceneReveal(clock, 0.05f, 0.8f, dy = 0.dp), verticalAlignment = Alignment.CenterVertically) {
                    val logoShape = RoundedCornerShape(12.dp)
                    Image(
                        painterResource(R.drawable.kipu_logo), null,
                        Modifier.size(40.dp).testTag("auth-form-logo").clip(logoShape)
                            .border(2.dp, Color.White.copy(alpha = 0.92f), logoShape),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("Kipu", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge)
                }
                AnimatedContent(
                    targetState = register,
                    modifier = Modifier.padding(end = 96.dp),
                    contentAlignment = Alignment.BottomStart,
                    transitionSpec = {
                        (fadeIn(tween(durationMillis)) + slideInVertically(tween(durationMillis)) { it / 4 }) togetherWith
                            (fadeOut(tween(durationMillis)) + slideOutVertically(tween(durationMillis)) { -it / 4 })
                    },
                    label = "auth-headline",
                ) { isRegister ->
                    Column {
                        Text(
                            if (isRegister) "Crea tu cuenta" else "Bienvenido\nde nuevo",
                            Modifier.semantics { heading() },
                            color = Color.White, fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold,
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isRegister) "Un correo y una contraseña." else "Ingresa con tu correo y contraseña.",
                            color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
    }
}

/** Floating pill that switches between signing in and registering; the indicator slides under the labels. */
@Composable
internal fun AuthModeTabs(
    register: Boolean,
    enabled: Boolean,
    durationMillis: Int,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val outer = RoundedCornerShape(20.dp)
    BoxWithConstraints(
        modifier.fillMaxWidth().height(58.dp)
            .shadow(10.dp, outer, ambientColor = Color(0x330F172A), spotColor = Color(0x330F172A))
            .background(colors.surfaceCard, outer).padding(5.dp),
    ) {
        val half = maxWidth / 2
        val offset by animateDpAsState(if (register) half else 0.dp, tween(durationMillis), label = "auth-tab-indicator")
        Box(
            Modifier.offset(x = offset).width(half).fillMaxHeight()
                .shadow(4.dp, RoundedCornerShape(15.dp), ambientColor = colors.primaryDeep, spotColor = colors.primaryDeep)
                .clip(RoundedCornerShape(15.dp)).background(colors.primaryGradient()),
        )
        Row(Modifier.fillMaxSize().selectableGroup()) {
            listOf(false to "Iniciar Sesión", true to "Registrarse").forEach { (mode, label) ->
                val selected = register == mode
                val textColor by animateColorAsState(
                    if (selected) colors.onPrimaryDeep else colors.secondaryMuted, tween(durationMillis), label = "auth-tab-text",
                )
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(15.dp))
                        .selectable(selected, enabled = enabled, role = Role.Tab, onClick = { onSelect(mode) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = textColor, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 8.dp))
                }
            }
        }
    }
}

/**
 * Filled field with a floating label, a leading icon, a focus halo and, for passwords, a visibility toggle.
 * The label doubles as the accessibility name so assistive tech announces the same text it sees.
 */
@Composable
internal fun AuthFloatingField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    error: String? = null,
    enabled: Boolean = true,
    password: Boolean = false,
    keyboardType: KeyboardType = if (password) KeyboardType.Password else KeyboardType.Email,
    imeAction: ImeAction = ImeAction.Next,
    onDone: () -> Unit = {},
) {
    val colors = rememberCalmEmeraldColors()
    val focus = LocalFocusManager.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    var visible by rememberSaveable { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)
    val errorColor = MaterialTheme.colorScheme.error
    val ringColor = if (error != null) errorColor else colors.primaryDeep
    val borderColor by animateColorAsState(
        when {
            error != null -> errorColor
            focused -> colors.primaryDeep
            else -> colors.borderSubtle
        }, tween(180), label = "auth-field-border",
    )
    val ring by animateFloatAsState(if (focused || error != null) 1f else 0f, tween(180), label = "auth-field-ring")
    val labelFocused = if (colors.isDark) colors.primaryDeep else Color(0xFF0B5F58)
    Column(modifier.fillMaxWidth()) {
        TextField(
            value = value, onValueChange = onValueChange, enabled = enabled, singleLine = true, isError = error != null,
            label = { Text(label) },
            leadingIcon = { Icon(leadingIcon, null) },
            trailingIcon = if (password) {
                {
                    IconButton(onClick = { visible = !visible }, enabled = enabled) {
                        Icon(
                            if (visible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            if (visible) "Ocultar $label" else "Mostrar $label",
                        )
                    }
                }
            } else null,
            visualTransformation = if (password && !visible) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onNext = { focus.moveFocus(androidx.compose.ui.focus.FocusDirection.Down) },
                onDone = { focus.clearFocus(); if (enabled) onDone() },
            ),
            interactionSource = interaction,
            shape = shape,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceCard, unfocusedContainerColor = colors.surfaceCard,
                disabledContainerColor = colors.surfaceCard, errorContainerColor = colors.surfaceCard,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
                focusedTextColor = colors.primaryText, unfocusedTextColor = colors.primaryText,
                disabledTextColor = colors.secondaryMuted, errorTextColor = colors.primaryText,
                focusedLabelColor = labelFocused, unfocusedLabelColor = colors.secondaryMuted,
                disabledLabelColor = colors.secondaryMuted, errorLabelColor = errorColor,
                cursorColor = colors.primaryDeep, errorCursorColor = errorColor,
                focusedLeadingIconColor = colors.primaryDeep, unfocusedLeadingIconColor = colors.secondaryMuted,
                disabledLeadingIconColor = colors.secondaryMuted, errorLeadingIconColor = errorColor,
                focusedTrailingIconColor = colors.secondaryMuted, unfocusedTrailingIconColor = colors.secondaryMuted,
                disabledTrailingIconColor = colors.secondaryMuted, errorTrailingIconColor = errorColor,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp)
                .drawBehind {
                    if (ring > 0f) {
                        val grow = 4.dp.toPx()
                        drawRoundRect(
                            ringColor.copy(alpha = 0.14f * ring), Offset(-grow, -grow),
                            Size(size.width + 2 * grow, size.height + 2 * grow), CornerRadius(20.dp.toPx()),
                        )
                    }
                }
                .border(1.5.dp, borderColor, shape)
                .semantics { contentDescription = label },
        )
        AuthErrorMessage(error)
    }
}

/** Three live requirement chips: each fills in as the password satisfies it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AuthRequirementChips(password: String, showErrors: Boolean, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RequirementChip("8–72 caracteres", "Entre 8 y 72 caracteres", password.length in 8..72, showErrors)
        RequirementChip("Una letra", "Al menos una letra", com.kipu.app.feature.auth.domain.PasswordValidator.hasLetter(password), showErrors)
        RequirementChip("Un número", "Al menos un número", com.kipu.app.feature.auth.domain.PasswordValidator.hasNumber(password), showErrors)
    }
}

@Composable
private fun RequirementChip(text: String, description: String, satisfied: Boolean, showErrors: Boolean) {
    val colors = rememberCalmEmeraldColors()
    val error = MaterialTheme.colorScheme.error
    val missed = !satisfied && showErrors
    val container by animateColorAsState(if (satisfied) colors.primaryDeep else colors.surfaceCard, tween(250), label = "chip-bg")
    val content by animateColorAsState(
        when {
            satisfied -> colors.onPrimaryDeep
            missed -> error
            else -> colors.secondaryMuted
        }, tween(250), label = "chip-content",
    )
    val border by animateColorAsState(
        when {
            satisfied -> colors.primaryDeep
            missed -> error
            else -> colors.borderSubtle
        }, tween(250), label = "chip-border",
    )
    val tick by animateFloatAsState(if (satisfied) 1f else 0f, tween(250), label = "chip-tick")
    Row(
        Modifier.height(28.dp).clip(CircleShape).background(container, CircleShape).border(1.dp, border, CircleShape)
            .padding(start = 7.dp, end = 10.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = description
                stateDescription = if (satisfied) "Cumplido" else if (showErrors) "No cumplido" else "Pendiente"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(14.dp).border(2.dp, if (satisfied) colors.onPrimaryDeep else content, CircleShape)
                .background(if (satisfied) colors.onPrimaryDeep else Color.Transparent, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, Modifier.size(9.dp).alpha(tick), tint = colors.primaryDeep)
        }
        Spacer(Modifier.width(5.dp))
        Text(text, color = content, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

/** Error / cooldown notice shown above the fields. */
@Composable
internal fun AuthBanner(message: String?, modifier: Modifier = Modifier) {
    val colors = rememberCalmEmeraldColors()
    AnimatedVisibility(message != null, modifier, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp)
                .background(colors.expenseBg, RoundedCornerShape(14.dp))
                .border(1.dp, colors.expenseBorder, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.WarningAmber, null, Modifier.size(20.dp), tint = colors.expenseText)
            Spacer(Modifier.width(10.dp))
            Text(message.orEmpty(), color = colors.expenseText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Brand button with a soft glow; swaps its label for a spinner while the request is in flight. */
@Composable
internal fun AuthCtaButton(
    text: String,
    loadingText: String,
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberCalmEmeraldColors()
    val focus = LocalFocusManager.current
    val shape = RoundedCornerShape(18.dp)
    val active = enabled && !loading
    Box(
        modifier.fillMaxWidth().heightIn(min = 58.dp)
            .alpha(if (enabled || loading) 1f else 0.5f)
            .shadow(if (active) 10.dp else 3.dp, shape, ambientColor = colors.primaryDeep, spotColor = colors.primaryDeep)
            .clip(shape).background(colors.primaryGradient())
            .clickable(enabled = active, role = Role.Button) { focus.clearFocus(); onClick() },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(loading, transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) }, label = "auth-cta") { busy ->
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), color = colors.onPrimaryDeep, strokeWidth = 2.5.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(loadingText, color = colors.onPrimaryDeep, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                } else {
                    Text(text, color = colors.onPrimaryDeep, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(10.dp))
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(20.dp), tint = colors.onPrimaryDeep)
                }
            }
        }
    }
}
