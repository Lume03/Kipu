package com.kipu.app.feature.categories.presentation.components

import com.kipu.app.ui.theme.KipuModalSystemBars
import com.kipu.app.ui.theme.kipuBottomSheetProperties

import com.kipu.app.ui.theme.readableContentColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.Image
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import com.kipu.app.ui.theme.rememberKipuColors
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.kipu.app.feature.categories.domain.CategoryRules
import com.kipu.app.feature.categories.domain.model.MerchantCatalogEntry
import com.kipu.app.feature.categories.domain.model.CategoryId

// TAXONOMÍA DE INTERFAZ (UI TAXONOMY)
// Documentado en EP-CCO: Agrupación visual coherente para facilitar la búsqueda
// independiente de las subcategorías contables de la base de datos.
enum class UiMerchantGroup(
    val label: String,
    val canonicalNames: Set<String>,
    val canonicalIds: Set<String> = emptySet(),
) {
    RESTAURANTS(
        label = "Restaurantes y Delivery",
        canonicalNames = setOf("bembos", "burger king", "kfc", "mcdonalds", "pedidosya", "pizza hut", "rappi", "starbucks"),
        canonicalIds = setOf(
            "00000000-0000-0001-0000-000000000002", // Starbucks
            "00000000-0000-0000-0001-000000000010", // KFC
            "00000000-0000-0000-0001-000000000011", // McDonald's
            "00000000-0000-0000-0001-000000000012", // Bembos
            "00000000-0000-0000-0001-000000000013", // Burger King
            "00000000-0000-0000-0001-000000000014", // Pizza Hut
            "00000000-0000-0000-0001-000000000015", // Rappi
            "00000000-0000-0000-0001-000000000016", // PedidosYa
        ),
    ),
    ENTERTAINMENT(
        label = "Entretenimiento y Streaming",
        canonicalNames = setOf("cinemark", "cineplanet", "crunchyroll", "disney", "disney plus", "joinnus", "max", "netflix", "prime video", "spotify", "teleticket", "youtube premium"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000040", // Netflix
            "00000000-0000-0000-0001-000000000041", // Disney+
            "00000000-0000-0000-0001-000000000042", // Prime Video
            "00000000-0000-0000-0001-000000000043", // Spotify
            "00000000-0000-0000-0001-000000000046", // Max
            "00000000-0000-0000-0001-000000000047", // YouTube Premium
            "00000000-0000-0000-0001-000000000048", // Crunchyroll
            "00000000-0000-0000-0001-000000000060", // Cineplanet
            "00000000-0000-0000-0001-000000000061", // Cinemark
            "00000000-0000-0000-0001-000000000062", // Teleticket
            "00000000-0000-0000-0001-000000000063", // Joinnus
        ),
    ),
    SUPERMARKETS(
        label = "Supermercados y Tiendas",
        canonicalNames = setOf("listo", "mass", "metro", "oxxo", "plaza vea", "tambo", "tottus", "wong"),
        canonicalIds = setOf(
            "00000000-0000-0001-0000-000000000001", // Tambo
            "00000000-0000-0001-0000-000000000003", // Plaza Vea
            "00000000-0000-0000-0001-000000000004", // Tottus
            "00000000-0000-0000-0001-000000000005", // Metro
            "00000000-0000-0000-0001-000000000006", // Wong
            "00000000-0000-0000-0001-000000000007", // Mass
            "00000000-0000-0000-0001-000000000008", // Oxxo
            "00000000-0000-0000-0001-000000000009", // Listo!
        ),
    ),
    TRANSPORT(
        label = "Transporte y Viajes",
        canonicalNames = setOf("cabify", "didi", "indrive", "latam airlines", "latam", "lima expresa", "linea 1", "metropolitano", "rutas de lima", "uber"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000019", // Uber
            "00000000-0000-0000-0001-000000000020", // inDrive
            "00000000-0000-0000-0001-000000000021", // DiDi
            "00000000-0000-0000-0001-000000000022", // Cabify
            "00000000-0000-0000-0001-000000000026", // Metropolitano
            "00000000-0000-0000-0001-000000000027", // Línea 1
            "00000000-0000-0000-0001-000000000028", // Rutas de Lima
            "00000000-0000-0000-0001-000000000029", // Lima Expresa
            "00000000-0000-0000-0001-000000000030", // LATAM Airlines
        ),
    ),
    TELECOM(
        label = "Telecomunicaciones",
        canonicalNames = setOf("bitel", "claro", "entel", "movistar", "win"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000035", // Claro
            "00000000-0000-0000-0001-000000000036", // Movistar
            "00000000-0000-0000-0001-000000000037", // Entel
            "00000000-0000-0000-0001-000000000038", // Bitel
            "00000000-0000-0000-0001-000000000039", // Win
        ),
    ),
    TECH(
        label = "Productividad e IA",
        canonicalNames = setOf("adobe", "canva", "chatgpt", "claude", "google ai pro", "google one", "icloud", "icloud plus", "microsoft 365", "notion", "perplexity"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000044", // Google One
            "00000000-0000-0000-0001-000000000045", // iCloud+
            "00000000-0000-0000-0001-000000000049", // ChatGPT
            "00000000-0000-0000-0001-000000000050", // Claude
            "00000000-0000-0000-0001-000000000051", // Google AI Pro
            "00000000-0000-0000-0001-000000000052", // Perplexity
            "00000000-0000-0000-0001-000000000053", // Canva
            "00000000-0000-0000-0001-000000000054", // Microsoft 365
            "00000000-0000-0000-0001-000000000055", // Adobe
            "00000000-0000-0000-0001-000000000056", // Notion
        ),
    ),
    GAMING(
        label = "Videojuegos",
        canonicalNames = setOf("playstation", "steam", "xbox"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000057", // Steam
            "00000000-0000-0000-0001-000000000058", // PlayStation
            "00000000-0000-0000-0001-000000000059", // Xbox
        ),
    ),
    UTILITIES(
        label = "Servicios Básicos",
        canonicalNames = setOf("calidda", "luz del sur", "pluz energia", "sedapal"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000031", // Pluz Energía
            "00000000-0000-0000-0001-000000000032", // Luz del Sur
            "00000000-0000-0000-0001-000000000033", // Sedapal
            "00000000-0000-0000-0001-000000000034", // Cálidda
        ),
    ),
    GAS(
        label = "Estaciones de Servicio",
        canonicalNames = setOf("petroperu", "primax", "repsol"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000023", // Primax
            "00000000-0000-0000-0001-000000000024", // Repsol
            "00000000-0000-0000-0001-000000000025", // Petroperú
        ),
    ),
    HEALTH(
        label = "Farmacias y Salud",
        canonicalNames = setOf("inkafarma", "mifarma"),
        canonicalIds = setOf(
            "00000000-0000-0000-0001-000000000017", // Inkafarma
            "00000000-0000-0000-0001-000000000018", // Mifarma
        ),
    ),
    EDUCATION(
        label = "Educación",
        canonicalNames = setOf("wikipedia"),
    );

    val merchants: Set<String> get() = canonicalNames

    fun matches(entry: MerchantCatalogEntry): Boolean {
        if (canonicalIds.contains(entry.id.value)) return true
        val assignedToOtherGroup = entries.any { other ->
            other != this && other.canonicalIds.contains(entry.id.value)
        }
        if (assignedToOtherGroup) return false
        val normName = CategoryRules.normalizeText(entry.name)
        val normStored = CategoryRules.normalizeText(entry.normalizedName)
        return canonicalNames.contains(normName) || canonicalNames.contains(normStored)
    }
}

data class MerchantVisualProfile(
    val initials: String,
    val backgroundColor: Color,
    val foregroundColor: Color,
    val subtitle: String,
)

fun getMerchantVisualProfile(name: String, brandColor: String? = null): MerchantVisualProfile {
    val normalizedName = name.lowercase().trim()
    val words = name.trim().split(" ").filter { it.isNotBlank() }
    val initials = if (words.size >= 2) {
        "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
    } else if (words.isNotEmpty()) {
        words[0].take(2).uppercase()
    } else {
        "C"
    }
    val parsedColor = runCatching {
        brandColor?.let { Color(android.graphics.Color.parseColor(it)) }
    }.getOrNull()
    val fallbackColor = when (normalizedName) {
        "tambo" -> Color(0xFF652D90)
        "starbucks" -> Color(0xFF006241)
        "plaza vea" -> Color(0xFFC8102E)
        "metro" -> Color(0xFFE5AB00)
        "tottus" -> Color(0xFFE2231A)
        "netflix" -> Color(0xFFE50914)
        "spotify" -> Color(0xFF1DB954)
        "uber" -> Color(0xFF000000)
        else -> Color.hsl(((normalizedName.hashCode() and Int.MAX_VALUE) % 360).toFloat(), 0.62f, 0.42f)
    }
    val backgroundColor = parsedColor ?: fallbackColor
    val foregroundColor = readableContentColor(backgroundColor)
    return MerchantVisualProfile(initials, backgroundColor, foregroundColor, "Comercio o servicio")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MerchantPickerBottomSheet(
    state: MerchantPickerState,
    onQueryChange: (String) -> Unit,
    onSelectMerchant: (MerchantCatalogEntry) -> Unit,
    onSetProvisionalText: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    title: String = "Comercio o pagador",
    subtitle: String = "Elige uno del catálogo o escribe un nombre",
    allowProvisionalText: Boolean = true,
    selectedMerchantIds: Set<String> = emptySet(),
    dismissOnSelect: Boolean = true,
) {
    val kipuColors = rememberKipuColors()
    ModalBottomSheet(
        properties = kipuBottomSheetProperties(),
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = kipuColors.surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(kipuColors.dragHandle),
            )
        },
        modifier = modifier.testTag("merchant_picker_sheet"),
    ) {
        KipuModalSystemBars()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Header: título, subtítulo y botón cerrar (lienzo "Selector de comercio (global)")
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(modifier = Modifier.weight(1f).padding(top = 4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = kipuColors.inkPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontSize = 13.sp,
                        color = kipuColors.inkSecondary,
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(48.dp).testTag("merchant_picker_close"),
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar", tint = kipuColors.inkSecondary)
                }
            }

            // Embedded MerchantPicker
            MerchantPicker(
                state = state,
                onQueryChange = onQueryChange,
                onSelectMerchant = {
                    onSelectMerchant(it)
                    if (dismissOnSelect) onDismiss()
                },
                onSetProvisionalText = {
                    onSetProvisionalText(it)
                    if (dismissOnSelect) onDismiss()
                },
                onClearSelection = onClearSelection,
                allowProvisionalText = allowProvisionalText,
                selectedMerchantIds = selectedMerchantIds,
                listHeight = 360.dp,
            )
        }
    }
}

@Composable
fun MerchantPicker(
    state: MerchantPickerState,
    onQueryChange: (String) -> Unit,
    onSelectMerchant: (MerchantCatalogEntry) -> Unit,
    onSetProvisionalText: (String) -> Unit,
    onClearSelection: () -> Unit,
    modifier: Modifier = Modifier,
    allowProvisionalText: Boolean = true,
    selectedMerchantIds: Set<String> = emptySet(),
    listHeight: Dp = 280.dp,
) {
    var selectedUiGroup by remember { mutableStateOf<UiMerchantGroup?>(null) }
    val typedName = state.query.trim()

    val catalogToDisplay = remember(state.catalogEntries, state.searchResults, state.query) {
        if (state.query.isBlank()) {
            state.catalogEntries
        } else {
            val baseList = if (state.catalogEntries.isNotEmpty()) state.catalogEntries else state.searchResults
            baseList.filter { CategoryRules.matchesNormalized(it.name, state.query) }
        }
    }

    val availableGroups = remember(state.catalogEntries, catalogToDisplay) {
        val baseCatalog = state.catalogEntries.ifEmpty { catalogToDisplay }
        UiMerchantGroup.entries.filter { group ->
            baseCatalog.any { merchant -> group.matches(merchant) }
        }
    }

    LaunchedEffect(availableGroups, selectedUiGroup) {
        if (selectedUiGroup != null && selectedUiGroup !in availableGroups) {
            selectedUiGroup = null
        }
    }

    // Una vez obtenida la lista de coincidencias correcta, aplicamos el filtro de taxonomía UI canónico.
    val visibleMerchants = remember(catalogToDisplay, selectedUiGroup) {
        if (selectedUiGroup == null) {
            catalogToDisplay
        } else {
            catalogToDisplay.filter { merchant -> selectedUiGroup!!.matches(merchant) }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Preview de selección actual
        if (state.hasSelection) {
            SelectedMerchantCard(
                selectedMerchant = state.selectedMerchant,
                provisionalText = state.provisionalText,
                onClear = onClearSelection,
            )
        }

        // Barra de Búsqueda
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(if (allowProvisionalText) "Buscar comercio o ingresar nuevo…" else "Buscar comercio…")
            },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.size(48.dp),
                    ) {
                        Icon(Icons.Default.Clear, contentDescription = "Limpiar búsqueda")
                    }
                } else if (state.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("merchant_search_input"),
        )

        // Chips de filtro UI (Taxonomía Visual)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            item {
                MerchantGroupPill(
                    label = "Todos",
                    selected = selectedUiGroup == null,
                    onClick = { selectedUiGroup = null },
                )
            }

            items(availableGroups, key = { it.name }) { group ->
                MerchantGroupPill(
                    label = group.label,
                    selected = selectedUiGroup == group,
                    onClick = {
                        selectedUiGroup = if (selectedUiGroup == group) null else group
                    },
                )
            }
        }

        // Banner de estado de catálogo
        if (state.catalogStatus != CatalogStatus.AVAILABLE) {
            CatalogStatusBanner(status = state.catalogStatus)
        }

        // Category chips filter the local merchant cache immediately.
        // "Usar “texto”" goes first, as in the reference, unless the text already names a catalog merchant.
        val typedMatchesCatalog = visibleMerchants.any { CategoryRules.normalizeText(it.name) == CategoryRules.normalizeText(typedName) }
        val showUseTyped = allowProvisionalText && typedName.isNotEmpty() && visibleMerchants.isNotEmpty() && !typedMatchesCatalog

        if (state.query.isNotBlank() || state.catalogEntries.isNotEmpty()) {
            if (showUseTyped) {
                FallbackProvisionalRow(
                    query = typedName,
                    onUseProvisional = { onSetProvisionalText(state.query) },
                )
            }
            Text(
                text = if (state.query.isBlank()) "Comercios del catálogo:" else "Resultados del catálogo:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (visibleMerchants.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = listHeight)
                        .testTag("merchant_results_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(visibleMerchants, key = { it.id.value }) { entry ->
                        MerchantResultItem(
                            entry = entry,
                            onClick = { onSelectMerchant(entry) },
                            isSelected = entry.id.value in selectedMerchantIds ||
                                entry.id == state.selectedMerchant?.id,
                        )
                    }
                }
            } else if (state.query.isNotBlank() && !state.isSearching) {
                if (allowProvisionalText) {
                    EmptyResultWithProvisionalOption(
                        query = state.query,
                        onUseProvisional = { onSetProvisionalText(state.query) },
                    )
                } else {
                    Text(
                        text = "No se encontró \"${state.query}\" en el catálogo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun MerchantAvatar(
    drawableId: Int,
    remoteLogoUrl: String?,
    profile: MerchantVisualProfile,
    size: Dp = 42.dp,
    modifier: Modifier = Modifier,
    fallbackContent: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        var isRemoteLoaded by remember(remoteLogoUrl) { mutableStateOf(false) }
        var isRemoteError by remember(remoteLogoUrl) { mutableStateOf(false) }

        if (remoteLogoUrl != null && !isRemoteError) {
            if (!isRemoteLoaded) {
                if (drawableId != 0) {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(drawableId),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(size),
                        )
                    }
                } else {
                    fallbackContent?.invoke() ?: MerchantInitialsBox(profile = profile, size = size)
                }
            }

            AsyncImage(
                model = remoteLogoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(size)
                    .then(if (isRemoteLoaded) Modifier.background(Color.White) else Modifier),
                onSuccess = { isRemoteLoaded = true },
                onError = { isRemoteError = true },
            )
        } else if (drawableId != 0) {
            Box(
                modifier = Modifier
                    .size(size)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(drawableId),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(size),
                )
            }
        } else {
            fallbackContent?.invoke() ?: MerchantInitialsBox(profile = profile, size = size)
        }
    }
}

@Composable
private fun MerchantInitialsBox(
    profile: MerchantVisualProfile,
    size: Dp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .background(profile.backgroundColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = profile.initials,
            color = profile.foregroundColor,
            fontWeight = FontWeight.Bold,
            fontSize = if (size <= 40.dp) 13.sp else 14.sp,
        )
    }
}

@Composable
fun SelectedMerchantCard(
    selectedMerchant: MerchantCatalogEntry?,
    provisionalText: String?,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayName = selectedMerchant?.name ?: provisionalText ?: ""
    val profile = getMerchantVisualProfile(displayName, selectedMerchant?.brandColor)

    val context = LocalContext.current
    val drawableId = remember(selectedMerchant?.logoKey) {
        selectedMerchant?.logoKey?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val remoteLogoUrl = selectedMerchant?.logoUrl

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
        ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MerchantAvatar(
                drawableId = drawableId,
                remoteLogoUrl = remoteLogoUrl,
                profile = profile,
                size = 40.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selectedMerchant != null) "Comercio del catálogo" else "Texto provisional",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            IconButton(
                onClick = onClear,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("clear_merchant_button"),
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Quitar comercio seleccionado",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MerchantResultItem(
    entry: MerchantCatalogEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val profile = getMerchantVisualProfile(entry.name, entry.brandColor)
    // Subtitle: the visual group the merchant belongs to (e.g. "Supermercados y Tiendas").
    val groupLabel = remember(entry.id) { UiMerchantGroup.entries.firstOrNull { it.matches(entry) }?.label }
    val context = LocalContext.current
    val drawableId = remember(entry.logoKey) {
        entry.logoKey?.let { context.resources.getIdentifier(it, "drawable", context.packageName) } ?: 0
    }
    val remoteLogoUrl = entry.logoUrl

    val kipuColors = rememberKipuColors()
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = if (isSelected) kipuColors.selectedSurface else kipuColors.surface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .border(BorderStroke(if (isSelected) 2.dp else 1.dp, if (isSelected) kipuColors.primary else kipuColors.border), shape)
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics { selected = isSelected }
            .testTag("merchant_item_${entry.id.value}"),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MerchantAvatar(
                drawableId = drawableId,
                remoteLogoUrl = remoteLogoUrl,
                profile = profile,
                size = 44.dp,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = kipuColors.inkPrimary,
                )
                Text(
                    text = groupLabel ?: profile.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = kipuColors.inkSecondary,
                )
            }

            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "Seleccionado",
                    tint = kipuColors.primary,
                    modifier = Modifier.size(22.dp),
                )
            } else {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = kipuColors.inkSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun FallbackProvisionalRow(
    query: String,
    onUseProvisional: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kipuColors = rememberKipuColors()
    val shape = RoundedCornerShape(16.dp)

    Surface(
        shape = shape,
        color = kipuColors.selectedSurface,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(BorderStroke(1.5.dp, kipuColors.primary), shape)
            .clip(shape)
            .clickable(onClick = onUseProvisional)
            .testTag("merchant_use_typed"),
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(kipuColors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = kipuColors.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Usar \u201C${query.trim()}\u201D",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = kipuColors.primaryText,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun EmptyResultWithProvisionalOption(
    query: String,
    onUseProvisional: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No se encontró \"$query\" en el catálogo",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Puedes asignarlo como texto provisional para este movimiento.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(
                onClick = onUseProvisional,
                modifier = Modifier
                    .height(48.dp)
                    .testTag("use_provisional_button"),
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Usar \"$query\" como texto provisional", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun CatalogStatusBanner(
    status: CatalogStatus,
    modifier: Modifier = Modifier,
) {
    val message = when (status) {
        CatalogStatus.STALE -> "Catálogo sin sincronizar recientemente. Los resultados locales pueden estar desactualizados."
        CatalogStatus.UNAVAILABLE -> "Catálogo de comercios no disponible sin conexión. Puedes usar un texto provisional."
        CatalogStatus.AVAILABLE -> ""
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = "Estado del catálogo",
                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

/** Merchant group filter pill, same shape and 48dp target as the account selector pills. */
@Composable
private fun MerchantGroupPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kipuColors = rememberKipuColors()
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier else Modifier.border(BorderStroke(1.5.dp, kipuColors.border), shape))
            .background(if (selected) kipuColors.primary else kipuColors.surface)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .defaultMinSize(minHeight = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (selected) kipuColors.onPrimary else kipuColors.inkSecondary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}
