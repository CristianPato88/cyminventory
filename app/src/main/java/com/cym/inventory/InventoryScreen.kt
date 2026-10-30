package com.cym.inventory

import android.app.DatePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import android.widget.Toast
import androidx.compose.material3.Button
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material3.Icon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.util.Locale

internal val Syne = FontFamily(Font(R.font.syne, weight = FontWeight.Normal))
internal val Manrope = FontFamily(Font(R.font.manrope, weight = FontWeight.Normal))

/** Named palette so every screen can react to light/dark mode without duplicating colors. */
internal data class Palette(
    val cream: Color,
    val paper: Color,
    val ink: Color,
    val pine: Color,
    val pineLight: Color,
    val mint: Color,
    val peach: Color,
    val sand: Color,
    val muted: Color,
    val line: Color,
    val danger: Color,
    val onPine: Color,
)

internal val LightPalette = Palette(
    cream = Color(0xFFF5F3ED),
    paper = Color(0xFFFFFEFA),
    ink = Color(0xFF1D2924),
    pine = Color(0xFF203B32),
    pineLight = Color(0xFF2C4F42),
    mint = Color(0xFFD8E9D8),
    peach = Color(0xFFE4A68C),
    sand = Color(0xFFE9D6BF),
    muted = Color(0xFF54615B),
    line = Color(0xFFE2E5DB),
    danger = Color(0xFFB14D3F),
    onPine = Color(0xFFFFFEFA),
)

internal val DarkPalette = Palette(
    cream = Color(0xFF101613),
    paper = Color(0xFF1C2621),
    ink = Color(0xFFF3F6F1),
    pine = Color(0xFF3E7360),
    pineLight = Color(0xFF8FCBB0),
    mint = Color(0xFF29392F),
    peach = Color(0xFFE4A68C),
    sand = Color(0xFFC9B08C),
    muted = Color(0xFFAEBAB3),
    line = Color(0xFF2C3A33),
    danger = Color(0xFFE38672),
    onPine = Color(0xFFF5F3ED),
)

internal val LocalPalette = staticCompositionLocalOf { LightPalette }

private enum class Tab(val title: String, val icon: Int) {
    PLANNED("Por comprar", R.drawable.ic_pending),
    HOME("En casa", R.drawable.ic_home),
    WISHLIST("Deseados", R.drawable.ic_wishlist),
    SHARED("Compartido", R.drawable.ic_shared),
    EXPENSES("Gastos", R.drawable.ic_expenses),
}

@Composable
internal fun AppRoot(store: InventoryStore, repo: HouseholdRepository) {
    var householdId by remember { mutableStateOf(repo.householdId) }
    val context = LocalContext.current
    var darkOverride by remember { mutableStateOf(ThemePrefs.getOverride(context)) }
    val darkTheme = darkOverride ?: isSystemInDarkTheme()
    val colors = if (darkTheme) DarkPalette else LightPalette
    val scheme = if (darkTheme) {
        darkColorScheme(primary = colors.pine, onPrimary = colors.onPine, surface = colors.paper,
            onSurface = colors.ink, background = colors.cream, onBackground = colors.ink)
    } else {
        lightColorScheme(primary = colors.pine, onPrimary = colors.onPine, surface = colors.paper,
            onSurface = colors.ink, background = colors.cream, onBackground = colors.ink)
    }
    CompositionLocalProvider(LocalPalette provides colors) {
        MaterialTheme(colorScheme = scheme) {
            val id = householdId
            if (id == null) {
                HouseholdOnboardingScreen(repo, onReady = { householdId = it })
            } else {
                InventoryApp(store, repo, id, darkTheme, onToggleTheme = {
                    val newValue = !darkTheme
                    ThemePrefs.setOverride(context, newValue)
                    darkOverride = newValue
                })
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HouseholdOnboardingScreen(repo: HouseholdRepository, onReady: (String) -> Unit) {
    val colors = LocalPalette.current
    var mode by remember { mutableStateOf(0) } // 0 elegir, 1 crear, 2 unirse
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var createdCode by remember { mutableStateOf<String?>(null) }
    var photo by remember { mutableStateOf<Uri?>(null) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(colors.cream).windowInsetsPadding(WindowInsets.safeDrawing)
        .verticalScroll(rememberScrollState()).padding(24.dp)) {
        Spacer(Modifier.height(40.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Image(painter = painterResource(R.drawable.cym_icon), contentDescription = null,
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(13.dp)))
            Text("Gestión de inventario de nuestro hogar.", color = colors.ink, fontFamily = Syne,
                fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 19.sp)
        }
        Spacer(Modifier.height(48.dp))
        val code0 = createdCode
        if (code0 != null) {
            Text("Vuestro hogar está listo.", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 28.sp)
            Spacer(Modifier.height(10.dp))
            Text("Compartid este código con la otra persona para que se una desde su móvil.",
                color = colors.muted, fontFamily = Manrope, fontSize = 13.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(22.dp))
            Surface(color = colors.paper, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Text(code0, color = colors.pine, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 30.sp,
                    letterSpacing = 3.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp))
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = { onReady(code0) }, colors = ButtonDefaults.buttonColors(containerColor = colors.pine),
                modifier = Modifier.fillMaxWidth()) { Text("Entrar", fontFamily = Manrope, fontWeight = FontWeight.Bold) }
        } else when (mode) {
            0 -> {
                Text("Bienvenidos.", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 34.sp)
                Spacer(Modifier.height(10.dp))
                Text("Cread un hogar nuevo o uníos a uno que ya exista con el código que os hayan pasado.",
                    color = colors.muted, fontFamily = Manrope, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(26.dp))
                Button(onClick = { mode = 1 }, colors = ButtonDefaults.buttonColors(containerColor = colors.pine),
                    modifier = Modifier.fillMaxWidth()) { Text("Crear nuestro hogar", fontFamily = Manrope, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { mode = 2 }, modifier = Modifier.fillMaxWidth()) {
                    Text("Unirme con un código", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                }
            }
            else -> {
                Text(if (mode == 1) "Vuestro hogar" else "Unirse a un hogar", color = colors.ink, fontFamily = Syne,
                    fontWeight = FontWeight.Bold, fontSize = 30.sp)
                Spacer(Modifier.height(10.dp))
                Text(if (mode == 1) "Le pondremos un código único para compartir con la otra persona."
                    else "Introduce el código de 8 caracteres que os han compartido.",
                    color = colors.muted, fontFamily = Manrope, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(22.dp))
                ProfilePhotoPicker(name, photo, onPick = { photoPicker.launch("image/*") })
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Tu nombre") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                if (mode == 2) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(code, onValueChange = { code = it.uppercase() }, label = { Text("Código del hogar") },
                        singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (error.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(error, color = colors.danger, fontFamily = Manrope, fontSize = 12.sp)
                }
                Spacer(Modifier.height(18.dp))
                Button(enabled = !loading, onClick = {
                    if (name.isBlank()) { error = "Dinos cómo te llamas"; return@Button }
                    if (mode == 2 && code.isBlank()) { error = "Escribe el código del hogar"; return@Button }
                    error = ""
                    loading = true
                    scope.launch {
                        try {
                            if (mode == 1) {
                                createdCode = repo.createHousehold(name.trim(), photo)
                            } else {
                                if (repo.joinHousehold(code.trim(), name.trim(), photo)) onReady(repo.householdId!!)
                                else error = "No encontramos ese código. Revísalo con la otra persona."
                            }
                        } catch (e: Exception) {
                            error = "No se pudo conectar. Comprueba tu conexión a internet."
                        }
                        loading = false
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = colors.pine), modifier = Modifier.fillMaxWidth()) {
                    Text(if (loading) "Un momento…" else if (mode == 1) "Crear hogar" else "Unirme",
                        fontFamily = Manrope, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = { mode = 0; error = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("Volver", color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

private fun Throwable.toSaveErrorMessage(): String = when (this) {
    is AttachmentTooLargeException -> message ?: "El archivo adjunto es demasiado grande."
    else -> "No se pudo guardar. Comprueba tu conexión e inténtalo de nuevo."
}

@Composable
internal fun InventoryApp(store: InventoryStore, repo: HouseholdRepository, householdId: String,
    darkTheme: Boolean, onToggleTheme: () -> Unit) {
    val colors = LocalPalette.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val itemsFlow = remember(householdId) { repo.itemsFlow(householdId) }
    val wishlistFlow = remember(householdId) { repo.wishlistFlow(householdId) }
    val roomsFlow = remember(householdId) { repo.roomsFlow(householdId) }
    val rawItems by itemsFlow.collectAsState(initial = emptyList())
    val wishlist by wishlistFlow.collectAsState(initial = emptyList())
    val rooms by roomsFlow.collectAsState(initial = emptyList())
    // El nombre de la habitación sale siempre de la lista de habitaciones: un renombrado se ve en todas partes al momento.
    val items = remember(rawItems, rooms) {
        val byId = rooms.associateBy { it.id }
        rawItems.map { item -> byId[item.effectiveRoomId()]?.let { item.copy(roomId = it.id, room = it.name) } ?: item }
    }
    val categories = remember(items) {
        items.map { it.category.trim() }.filter(String::isNotBlank).distinctBy(::normalizeKey).sortedBy(::normalizeKey)
    }
    var receiptError by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(Tab.PLANNED) }
    var sheetItem by remember { mutableStateOf<InventoryItem?>(null) }
    var sheetPurchase by remember { mutableStateOf(false) }
    var sheetRoomId by remember { mutableStateOf("") }
    var sheetOpen by remember { mutableStateOf(false) }
    var wishlistSheetItem by remember { mutableStateOf<WishlistItem?>(null) }
    var wishlistSheetOpen by remember { mutableStateOf(false) }
    var roomsManagerOpen by remember { mutableStateOf(false) }
    var mediaViewerUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(householdId) { runCatching { repo.ensureRooms(householdId) } }
    LaunchedEffect(rawItems, rooms) {
        if (rooms.isNotEmpty()) runCatching { repo.assignRoomIds(householdId, rawItems, rooms) }
    }

    fun openSheet(item: InventoryItem?, purchase: Boolean, roomId: String = "") {
        sheetItem = item
        sheetPurchase = purchase
        sheetRoomId = roomId
        sheetOpen = true
    }
    fun saveItem(item: InventoryItem, pickedPhoto: Uri?, pickedReceipt: Uri?) {
        sheetOpen = false
        val toSave = if (item.status == ItemStatus.PURCHASED && item.paidBy.isBlank())
            item.copy(paidBy = repo.memberName.ifBlank { "Alguien de casa" }, paidById = repo.memberId)
        else item
        scope.launch {
            runCatching { repo.saveItem(householdId, toSave, pickedPhoto, pickedReceipt) }
                .onFailure { receiptError = it.toSaveErrorMessage() }
        }
    }
    fun quickAdd(name: String, roomId: String) {
        val room = rooms.firstOrNull { it.id == roomId }
        scope.launch {
            runCatching { repo.saveItem(householdId, InventoryItem(name = name, roomId = room?.id ?: "", room = room?.name ?: ""), null, null) }
                .onFailure { receiptError = it.toSaveErrorMessage() }
        }
    }
    fun deleteItem(item: InventoryItem) {
        sheetOpen = false
        scope.launch { runCatching { repo.deleteItem(householdId, item) } }
    }
    fun saveRoom(room: Room, previousName: String?) {
        scope.launch {
            runCatching { repo.saveRoom(householdId, room, previousName) }
                .onFailure { receiptError = "No se pudo guardar la habitación. Comprueba tu conexión." }
        }
    }
    fun deleteRoom(room: Room, moveTo: Room?) {
        scope.launch {
            runCatching { repo.deleteRoom(householdId, room, moveTo) }
                .onFailure { receiptError = "No se pudo eliminar la habitación. Comprueba tu conexión." }
        }
    }

    fun openWishlistSheet(item: WishlistItem?) {
        wishlistSheetItem = item
        wishlistSheetOpen = true
    }
    fun saveWishlistItem(item: WishlistItem, pickedPhoto: Uri?) {
        wishlistSheetOpen = false
        scope.launch {
            runCatching { repo.saveWishlistItem(householdId, item, pickedPhoto) }
                .onFailure { receiptError = it.toSaveErrorMessage() }
        }
    }
    fun deleteWishlistItem(item: WishlistItem) {
        scope.launch { runCatching { repo.deleteWishlistItem(householdId, item) } }
    }
    fun moveWishlistToPending(item: WishlistItem) {
        scope.launch {
            runCatching {
                repo.saveItem(householdId, InventoryItem(name = item.name, shop = item.shop, location = item.location,
                    description = item.notes, priceCents = item.priceCents), null, null)
                repo.deleteWishlistItem(householdId, item)
            }
        }
    }

    fun openReceipt(item: InventoryItem) {
        scope.launch {
            val cached = store.receiptUri(item)
            if (cached == null) Toast.makeText(context, "Descargando ticket…", Toast.LENGTH_SHORT).show()
            val uri = cached ?: runCatching { repo.downloadReceipt(householdId, item) }.getOrNull()
            if (uri == null) {
                receiptError = "No se pudo descargar el ticket. Comprueba tu conexión e inténtalo de nuevo."
                return@launch
            }
            if (store.receiptMimeType(item).startsWith("image/")) {
                mediaViewerUri = uri
                return@launch
            }
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, store.receiptMimeType(item))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                })
            } catch (_: ActivityNotFoundException) {
                receiptError = "Instala una app que pueda abrir este ticket."
            }
        }
    }

    fun openPhoto(item: InventoryItem) {
        val uri = store.photoUri(item)
        if (uri == null) {
            receiptError = "La foto aún no se ha sincronizado en este móvil. Prueba de nuevo en un momento."
            return
        }
        mediaViewerUri = uri
    }

    fun exportInventory() {
        try {
            val uri = store.exportCsv(items)
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Exportar inventario"))
        } catch (_: Exception) {
            receiptError = "No se pudo exportar el inventario."
        }
    }

    Column(Modifier.fillMaxSize().background(colors.cream).windowInsetsPadding(WindowInsets.safeDrawing)) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                Tab.PLANNED -> PlannedScreen(items.filter { it.status == ItemStatus.PLANNED }, store, rooms,
                    recentHome = items.filter { it.status == ItemStatus.PURCHASED }.sortedByDescending { it.updatedAt }.take(3),
                    onAdd = { roomId -> openSheet(null, false, roomId) }, onQuickAdd = ::quickAdd,
                    onBuy = { openSheet(it, true) }, onEdit = { openSheet(it, false) },
                    onOpenHome = { openSheet(it, true) }, onSeeAllHome = { tab = Tab.HOME })
                Tab.HOME -> HomeScreen(items.filter { it.status == ItemStatus.PURCHASED }, items.filter { it.status == ItemStatus.PLANNED },
                    rooms, store, onAddPurchase = { openSheet(null, true) }, onEdit = { openSheet(it, true) },
                    onReceipt = ::openReceipt, onPhoto = ::openPhoto, onExport = ::exportInventory,
                    onManageRooms = { roomsManagerOpen = true })
                Tab.WISHLIST -> WishlistScreen(wishlist, store,
                    onAdd = { openWishlistSheet(null) }, onEdit = { openWishlistSheet(it) },
                    onDelete = ::deleteWishlistItem, onMoveToPending = ::moveWishlistToPending)
                Tab.SHARED -> SharedScreen(store, repo, householdId)
                Tab.EXPENSES -> ExpenseScreen(items.filter { it.status == ItemStatus.PURCHASED })
            }
            Box(Modifier.align(Alignment.TopEnd).padding(top = 20.dp, end = 20.dp).size(38.dp)
                .clip(CircleShape).background(colors.paper).clickable(onClick = onToggleTheme),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(if (darkTheme) R.drawable.ic_sun else R.drawable.ic_moon),
                    contentDescription = if (darkTheme) "Cambiar a modo claro" else "Cambiar a modo oscuro",
                    tint = colors.pine, modifier = Modifier.size(18.dp))
            }
        }
        BottomBar(tab, onTab = { tab = it })
    }
    mediaViewerUri?.let { uri -> FullScreenImageViewer(uri, onDismiss = { mediaViewerUri = null }) }
    if (receiptError.isNotBlank()) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { receiptError = "" },
            title = { Text("Aviso") },
            text = { Text(receiptError) },
            confirmButton = { TextButton(onClick = { receiptError = "" }) { Text("Cerrar") } },
        )
    }
    if (sheetOpen) {
        ItemSheet(
            item = sheetItem,
            purchase = sheetPurchase,
            store = store,
            rooms = rooms,
            categories = categories,
            initialRoomId = sheetRoomId,
            onDismiss = { sheetOpen = false },
            onSave = ::saveItem,
            onDelete = ::deleteItem,
            onCreateRoom = { room -> saveRoom(room, null) },
        )
    }
    if (wishlistSheetOpen) {
        WishlistSheet(
            item = wishlistSheetItem,
            store = store,
            onDismiss = { wishlistSheetOpen = false },
            onSave = ::saveWishlistItem,
        )
    }
    if (roomsManagerOpen) {
        RoomsManagerSheet(rooms, items, onDismiss = { roomsManagerOpen = false }, onSave = ::saveRoom, onDelete = ::deleteRoom)
    }
}

@Composable
private fun BrandHeader() {
    val colors = LocalPalette.current
    val isDebugBuild = LocalContext.current.packageName.endsWith(".debug")
    // Hueco a la derecha para el botón flotante de modo claro/oscuro, que si no tapa el título.
    Row(Modifier.fillMaxWidth().padding(end = 46.dp), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Image(painter = painterResource(R.drawable.cym_icon), contentDescription = null,
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)))
        Column(Modifier.weight(1f).padding(top = 2.dp)) {
            Text("Gestión de inventario de nuestro hogar.", color = colors.ink, fontFamily = Syne,
                fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp)
            if (isDebugBuild) {
                Spacer(Modifier.height(5.dp))
                Text("DEBUG", color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 9.sp, letterSpacing = 1.sp,
                    modifier = Modifier.background(colors.danger, RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 2.dp))
            }
        }
    }
}

@Composable
private fun PageLabel(text: String) {
    val colors = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        Box(Modifier.size(width = 19.dp, height = 2.dp).background(colors.peach))
        Text(text.uppercase(), color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 1.6.sp)
    }
}

@Composable
private fun HeroCard(count: Int, estimatedCents: Long) {
    val colors = LocalPalette.current
    Box(Modifier.fillMaxWidth().heightIn(min = 192.dp).background(colors.pine, RoundedCornerShape(27.dp))) {
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 15.dp).size(135.dp).background(colors.peach, CircleShape))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 39.dp).width(90.dp).height(122.dp)
            .rotate(16f).background(colors.sand, RoundedCornerShape(topStart = 50.dp, topEnd = 50.dp, bottomStart = 6.dp, bottomEnd = 6.dp)))
        Box(Modifier.align(Alignment.CenterEnd).padding(end = 66.dp, top = 48.dp).width(34.dp).height(45.dp)
            .rotate(16f).background(colors.pineLight, RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)))
        Column(Modifier.align(Alignment.TopStart).padding(23.dp)) {
            Text("NUESTRA LISTA", color = Color(0xFFB8D1B8), fontFamily = Manrope,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Spacer(Modifier.height(9.dp))
            Text(count.toString().padStart(2, '0'), color = colors.paper, fontFamily = Syne,
                fontSize = 68.sp, fontWeight = FontWeight.Bold, letterSpacing = (-3).sp)
            Text("compras pendientes", color = colors.paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(if (estimatedCents > 0) "≈ ${formatMoney(estimatedCents)} estimados" else "Paso a paso, ya va tomando forma.",
                color = Color(0xFFAEC7B7), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp)
        }
    }
}

private enum class PlannedSort(val label: String) { RECENT("Recientes"), PRICE("Precio estimado"), NAME("Nombre") }

@Composable
private fun PlannedScreen(items: List<InventoryItem>, store: InventoryStore, rooms: List<Room>, recentHome: List<InventoryItem>,
    onAdd: (roomId: String) -> Unit, onQuickAdd: (name: String, roomId: String) -> Unit,
    onBuy: (InventoryItem) -> Unit, onEdit: (InventoryItem) -> Unit, onOpenHome: (InventoryItem) -> Unit,
    onSeeAllHome: () -> Unit) {
    val colors = LocalPalette.current
    var query by remember { mutableStateOf("") }
    var roomFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(PlannedSort.RECENT) }
    var quickName by remember { mutableStateOf("") }
    val counts = remember(items) { items.groupingBy { it.effectiveRoomId() }.eachCount() }
    val estimatedTotal = items.sumOf { it.priceCents ?: 0L }
    val filtered = items.filter {
        (roomFilter == null || it.effectiveRoomId() == roomFilter) &&
            (query.isBlank() || it.name.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true))
    }.let { list ->
        when (sort) {
            PlannedSort.RECENT -> list.sortedByDescending { it.updatedAt }
            PlannedSort.PRICE -> list.sortedByDescending { it.priceCents ?: -1L }
            PlannedSort.NAME -> list.sortedBy { normalizeKey(it.name) }
        }
    }
    // Si hay una habitación filtrada, lo que se añade rápido va a esa habitación.
    val targetRoomId = roomFilter.orEmpty()
    val targetRoomName = rooms.firstOrNull { it.id == targetRoomId }?.name
    fun submitQuickAdd() {
        val name = quickName.trim()
        if (name.isEmpty()) return
        onQuickAdd(name.replaceFirstChar { it.uppercase() }, targetRoomId)
        quickName = ""
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 25.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        item {
            BrandHeader()
            Spacer(Modifier.height(31.dp))
            PageLabel("Por comprar")
            Spacer(Modifier.height(10.dp))
            Text("Aún por", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                fontSize = 46.sp, lineHeight = 44.sp, letterSpacing = (-2.5).sp)
            Text("llegar.", color = colors.pineLight, fontFamily = Syne, fontWeight = FontWeight.Bold,
                fontSize = 46.sp, lineHeight = 44.sp, letterSpacing = (-2.5).sp)
            Spacer(Modifier.height(14.dp))
            Text("Todo lo que falta para nuestra casa, claro y al alcance de los dos.",
                color = colors.muted, fontFamily = Manrope, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(24.dp))
            HeroCard(items.size, estimatedTotal)
            Spacer(Modifier.height(24.dp))
            // Añadir rápido: nombre + Intro. Los detalles se completan después tocando el artículo.
            Row(Modifier.fillMaxWidth().height(54.dp).background(colors.paper, RoundedCornerShape(15.dp))
                .padding(start = 15.dp, end = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(quickName, onValueChange = { quickName = it }, singleLine = true,
                        textStyle = TextStyle(color = colors.ink, fontFamily = Manrope, fontSize = 13.sp),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submitQuickAdd() }),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (quickName.isEmpty()) {
                                Text(if (targetRoomName != null) "Añadir a $targetRoomName…" else "Añade algo que falta…",
                                    color = colors.muted, fontFamily = Manrope, fontSize = 13.sp)
                            }
                            inner()
                        })
                }
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(11.dp))
                    .background(if (quickName.isBlank()) colors.mint else colors.pine)
                    .clickable(enabled = quickName.isNotBlank()) { submitQuickAdd() }, contentAlignment = Alignment.Center) {
                    Text("＋", color = if (quickName.isBlank()) colors.pine else colors.onPine, fontFamily = Syne,
                        fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(46.dp).background(colors.paper, RoundedCornerShape(14.dp))
                .padding(horizontal = 15.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(query, onValueChange = { query = it }, singleLine = true,
                    textStyle = TextStyle(color = colors.ink, fontFamily = Manrope, fontSize = 12.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (query.isEmpty()) Text("Buscar en pendientes", color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
                        inner()
                    })
            }
            Spacer(Modifier.height(12.dp))
            RoomFilterChips(rooms, counts, roomFilter) { roomFilter = it }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Pendientes", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                        fontSize = 23.sp, letterSpacing = (-1).sp)
                    Text(if (filtered.size == 1) "1 artículo" else "${filtered.size} artículos", color = colors.muted,
                        fontFamily = Manrope, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                SortSelector(PlannedSort.entries.map { it to it.label }, sort) { sort = it }
            }
            Spacer(Modifier.height(15.dp))
        }
        if (filtered.isEmpty()) {
            item {
                Surface(color = colors.paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(if (items.isEmpty()) "Empezad vuestra lista" else "No hay coincidencias",
                            color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text(if (items.isEmpty()) "Escribid arriba lo que falta para casa y pulsad Intro."
                            else "Prueba otra búsqueda o habitación.", color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(filtered, key = { it.id }) { item ->
                CompactItemCard(item, store, onBuy = { onBuy(item) }, onEdit = { onEdit(item) })
                Spacer(Modifier.height(11.dp))
            }
        }
        item {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().background(colors.mint, RoundedCornerShape(16.dp))
                .clickable { onAdd(targetRoomId) }.padding(15.dp), contentAlignment = Alignment.Center) {
                Text("＋  Añadir con todos los detalles", color = colors.pine, fontFamily = Manrope,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
        if (recentHome.isNotEmpty()) {
            item {
                Spacer(Modifier.height(30.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom) {
                    Text("Recién en casa", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                        fontSize = 19.sp, letterSpacing = (-0.5).sp)
                    Text("Ver todo ↗", color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, modifier = Modifier.clickable(onClick = onSeeAllHome))
                }
                Spacer(Modifier.height(13.dp))
            }
            items(recentHome, key = { "recent-" + it.id }) { item ->
                RecentHomeCard(item, store, onClick = { onOpenHome(item) })
                Spacer(Modifier.height(11.dp))
            }
        }
    }
}

@Composable
private fun RecentHomeCard(item: InventoryItem, store: InventoryStore, onClick: () -> Unit) {
    val colors = LocalPalette.current
    Surface(color = colors.paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onClick).padding(13.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PhotoAvatar(item.name, store.photoUri(item),
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(colors.sand), colors.pineLight, 18.sp)
            Column(Modifier.weight(1f)) {
                Text(item.name, color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(item.room, item.purchaseDate).filter { !it.isNullOrBlank() }.joinToString(" · "),
                    color = colors.muted, fontFamily = Manrope, fontSize = 10.sp, maxLines = 1)
            }
            Text(formatMoney(item.priceCents ?: 0), color = colors.pine, fontFamily = Manrope,
                fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun CompactItemCard(item: InventoryItem, store: InventoryStore, onBuy: () -> Unit, onEdit: () -> Unit) {
    val colors = LocalPalette.current
    Surface(color = colors.paper, shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.clickable(onClick = onEdit).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            PhotoAvatar(item.name, store.photoUri(item),
                Modifier.size(width = 56.dp, height = 66.dp).clip(RoundedCornerShape(13.dp)).background(colors.mint),
                colors.pineLight, 28.sp)
            Column(Modifier.weight(1f)) {
                Text(listOf(item.category, item.room).filter(String::isNotBlank).joinToString(" · ").ifBlank { "PENDIENTE" }.uppercase(),
                    color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(item.name, color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                item.priceCents?.let { Text("≈ ${formatMoney(it)}", color = colors.muted, fontFamily = Manrope,
                    fontWeight = FontWeight.Bold, fontSize = 11.sp) }
            }
            Column(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.mint).clickable(onClick = onBuy)
                .padding(horizontal = 10.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("↗", color = colors.pine, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Comprado", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 8.sp)
            }
        }
    }
}

private enum class HomeView { LIST, HOUSE }
private enum class HomeSort(val label: String) {
    RECENT("Más recientes"), OLDEST("Más antiguos"), PRICE_DESC("Precio: mayor"), PRICE_ASC("Precio: menor"), NAME("Nombre")
}
private enum class Period(val label: String) { ALL("Siempre"), MONTH("Este mes"), QUARTER("Últimos 3 meses"), YEAR("Este año") }
private enum class TicketFilter(val label: String) { ALL("Todos"), WITH("Con ticket"), WITHOUT("Sin ticket") }

private data class HomeFilters(
    val category: String? = null,
    val shop: String? = null,
    val paidBy: String? = null,
    val period: Period = Period.ALL,
    val ticket: TicketFilter = TicketFilter.ALL,
    val minPrice: String = "",
    val maxPrice: String = "",
) {
    fun matches(item: InventoryItem, today: LocalDate): Boolean {
        val price = item.priceCents ?: 0L
        val min = parseMoney(minPrice)
        val max = parseMoney(maxPrice)
        val date = item.purchaseDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val inPeriod = when (period) {
            Period.ALL -> true
            Period.MONTH -> date != null && date.year == today.year && date.month == today.month
            Period.QUARTER -> date != null && !date.isBefore(today.minusMonths(3))
            Period.YEAR -> date != null && date.year == today.year
        }
        return (category == null || normalizeKey(item.category) == normalizeKey(category)) &&
            (shop == null || normalizeKey(item.shop) == normalizeKey(shop)) &&
            (paidBy == null || item.paidBy == paidBy) && inPeriod &&
            (ticket == TicketFilter.ALL || (ticket == TicketFilter.WITH) == (item.receiptMime != null)) &&
            (min == null || price >= min) && (max == null || price <= max)
    }

    /** Filtros activos como chips quitables: texto + cómo quedan los filtros al quitarlo. */
    fun activeChips(): List<Pair<String, HomeFilters>> = buildList {
        category?.let { add(it to copy(category = null)) }
        shop?.let { add(it to copy(shop = null)) }
        paidBy?.let { add("Pagó $it" to copy(paidBy = null)) }
        if (period != Period.ALL) add(period.label to copy(period = Period.ALL))
        if (ticket != TicketFilter.ALL) add(ticket.label to copy(ticket = TicketFilter.ALL))
        if (minPrice.isNotBlank()) add("Desde $minPrice €" to copy(minPrice = ""))
        if (maxPrice.isNotBlank()) add("Hasta $maxPrice €" to copy(maxPrice = ""))
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun HomeScreen(items: List<InventoryItem>, pendingItems: List<InventoryItem>, rooms: List<Room>, store: InventoryStore,
    onAddPurchase: () -> Unit, onEdit: (InventoryItem) -> Unit, onReceipt: (InventoryItem) -> Unit, onPhoto: (InventoryItem) -> Unit,
    onExport: () -> Unit, onManageRooms: () -> Unit) {
    val colors = LocalPalette.current
    var view by remember { mutableStateOf(HomeView.LIST) }
    var query by remember { mutableStateOf("") }
    var roomFilter by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(HomeSort.RECENT) }
    var filters by remember { mutableStateOf(HomeFilters()) }
    var filterSheetOpen by remember { mutableStateOf(false) }
    val today = remember { LocalDate.now() }

    val categories = remember(items) { items.map { it.category.trim() }.filter(String::isNotBlank).distinctBy(::normalizeKey).sortedBy(::normalizeKey) }
    val shops = remember(items) { items.map { it.shop.trim() }.filter(String::isNotBlank).distinctBy(::normalizeKey).sortedBy(::normalizeKey) }
    val payers = remember(items) { items.map { it.paidBy }.filter(String::isNotBlank).distinct().sorted() }
    val counts = remember(items) { items.groupingBy { it.effectiveRoomId() }.eachCount() }

    val filtered = items.filter { item ->
        (roomFilter == null || item.effectiveRoomId() == roomFilter) &&
            (query.isBlank() || item.name.contains(query, true) || item.shop.contains(query, true) || item.category.contains(query, true)) &&
            filters.matches(item, today)
    }.let { list ->
        when (sort) {
            HomeSort.RECENT -> list.sortedByDescending { it.purchaseDate ?: "" }
            HomeSort.OLDEST -> list.sortedBy { it.purchaseDate ?: "9999" }
            HomeSort.PRICE_DESC -> list.sortedByDescending { it.priceCents ?: 0L }
            HomeSort.PRICE_ASC -> list.sortedBy { it.priceCents ?: 0L }
            HomeSort.NAME -> list.sortedBy { normalizeKey(it.name) }
        }
    }
    val filteredTotal = filtered.sumOf { it.priceCents ?: 0L }
    val chips = filters.activeChips()

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BrandHeader()
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                PageLabel("Inventario")
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.paper).clickable(onClick = onExport)
                    .padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(painterResource(R.drawable.ic_export), contentDescription = null, tint = colors.pine, modifier = Modifier.size(14.dp))
                    Text("Exportar", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Ya en casa.", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 39.sp)
            Text("Lo que ya habéis comprado y dónde está.", color = colors.muted, fontFamily = Manrope, fontSize = 13.sp)
            Spacer(Modifier.height(18.dp))
            SegmentedToggle(listOf(HomeView.LIST to "Lista", HomeView.HOUSE to "Casa"), view) { view = it }
            Spacer(Modifier.height(4.dp))
        }
        if (view == HomeView.HOUSE) {
            item {
                HouseMap(rooms, items, pendingItems, onOpenRoom = { roomId -> roomFilter = roomId; view = HomeView.LIST },
                    onManageRooms = onManageRooms)
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(48.dp).background(colors.paper, RoundedCornerShape(14.dp))
                        .padding(horizontal = 15.dp), contentAlignment = Alignment.CenterStart) {
                        BasicTextField(query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = colors.ink, fontFamily = Manrope, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Buscar por nombre, tienda o categoría", color = colors.muted,
                                    fontFamily = Manrope, fontSize = 12.sp)
                                inner()
                            })
                    }
                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (chips.isNotEmpty()) colors.pine else colors.paper)
                        .clickable { filterSheetOpen = true }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_filter), contentDescription = "Filtros",
                            tint = if (chips.isNotEmpty()) colors.onPine else colors.pine, modifier = Modifier.size(19.dp))
                    }
                }
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chips.forEach { (label, without) ->
                            Row(Modifier.clip(CircleShape).background(colors.mint).clickable { filters = without }
                                .padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(label, color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("✕", color = colors.pine, fontFamily = Manrope, fontSize = 10.sp)
                            }
                        }
                        Box(Modifier.clip(CircleShape).clickable { filters = HomeFilters() }.padding(horizontal = 8.dp, vertical = 7.dp)) {
                            Text("Limpiar", color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                RoomFilterChips(rooms, counts, roomFilter) { roomFilter = it }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                    Column {
                        Text("Inventario", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                            fontSize = 21.sp, letterSpacing = (-1).sp)
                        Text((if (filtered.size == 1) "1 artículo" else "${filtered.size} artículos") + " · " + formatMoney(filteredTotal),
                            color = colors.muted, fontFamily = Manrope, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    SortSelector(HomeSort.entries.map { it to it.label }, sort) { sort = it }
                }
                Spacer(Modifier.height(4.dp))
            }
            if (filtered.isEmpty()) {
                item {
                    Surface(color = colors.paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                            Text(if (items.isEmpty()) "Aún no hay compras" else "No hay coincidencias",
                                color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(if (items.isEmpty()) "Cuando registréis una compra, aparecerá aquí con toda su ficha."
                                else "Prueba otra búsqueda, otra habitación o quita algún filtro.", color = colors.muted,
                                fontFamily = Manrope, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                items(filtered, key = { it.id }) { item ->
                    HomeItemCard(item, store, onEdit = { onEdit(item) }, onReceipt = { onReceipt(item) }, onPhoto = { onPhoto(item) })
                }
            }
            item {
                Spacer(Modifier.height(3.dp))
                Button(onClick = onAddPurchase, colors = ButtonDefaults.buttonColors(containerColor = colors.pine),
                    modifier = Modifier.fillMaxWidth()) { Text("Registrar compra no prevista", fontFamily = Manrope, fontWeight = FontWeight.Bold) }
            }
        }
    }

    if (filterSheetOpen) {
        val previewCount = items.count { item ->
            (roomFilter == null || item.effectiveRoomId() == roomFilter) && filters.matches(item, today)
        }
        ModalBottomSheet(onDismissRequest = { filterSheetOpen = false }, containerColor = colors.paper) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Filtrar inventario", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                OptionRow("Cuándo se compró", Period.entries.map { it to it.label }, filters.period) { filters = filters.copy(period = it) }
                if (categories.isNotEmpty()) {
                    OptionRow("Categoría", listOf<Pair<String?, String>>(null to "Todas") + categories.map { it to it }, filters.category) {
                        filters = filters.copy(category = it)
                    }
                }
                if (shops.isNotEmpty()) {
                    OptionRow("Tienda", listOf<Pair<String?, String>>(null to "Todas") + shops.map { it to it }, filters.shop) {
                        filters = filters.copy(shop = it)
                    }
                }
                if (payers.size > 1) {
                    OptionRow("Quién pagó", listOf<Pair<String?, String>>(null to "Cualquiera") + payers.map { it to it }, filters.paidBy) {
                        filters = filters.copy(paidBy = it)
                    }
                }
                OptionRow("Ticket", TicketFilter.entries.map { it to it.label }, filters.ticket) { filters = filters.copy(ticket = it) }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("PRECIO (€)", color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 10.sp, letterSpacing = 1.2.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(filters.minPrice, onValueChange = { filters = filters.copy(minPrice = it) },
                            label = { Text("Desde") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                        OutlinedTextField(filters.maxPrice, onValueChange = { filters = filters.copy(maxPrice = it) },
                            label = { Text("Hasta") }, singleLine = true, modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(onClick = { filters = HomeFilters() }, modifier = Modifier.weight(1f)) {
                        Text("Limpiar", color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    }
                    Button(onClick = { filterSheetOpen = false }, colors = ButtonDefaults.buttonColors(containerColor = colors.pine),
                        modifier = Modifier.weight(1f)) {
                        Text(if (previewCount == 1) "Ver 1 resultado" else "Ver $previewCount resultados",
                            fontFamily = Manrope, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPalette.current
    Column(modifier) {
        Text(label.uppercase(), color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 9.sp, letterSpacing = 1.sp)
        Spacer(Modifier.height(3.dp))
        Text(value, color = colors.ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun HomeItemCard(item: InventoryItem, store: InventoryStore, onEdit: () -> Unit, onReceipt: () -> Unit, onPhoto: () -> Unit) {
    val colors = LocalPalette.current
    var expanded by remember(item.id) { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(color = colors.paper, shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize().clickable { expanded = !expanded }.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                PhotoAvatar(item.name, store.photoUri(item),
                    Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(colors.mint)
                        .let { if (item.photoMime != null) it.clickable(onClick = onPhoto) else it }, colors.pineLight, 22.sp)
                Column(Modifier.weight(1f)) {
                    Text(listOf(item.category, item.room).filter(String::isNotBlank).joinToString(" · ").ifBlank { "SIN CATEGORÍA" }.uppercase(),
                        color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(item.name, color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!item.purchaseDate.isNullOrBlank()) Text(item.purchaseDate, color = colors.muted, fontFamily = Manrope, fontSize = 10.sp)
                }
                Text(formatMoney(item.priceCents ?: 0), color = colors.pine, fontFamily = Syne,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = colors.muted,
                    modifier = Modifier.size(16.dp).rotate(rotation))
            }
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailRow("Tienda", item.shop.ifBlank { "—" }, Modifier.weight(1f))
                    DetailRow("Ubicación", item.location.ifBlank { "—" }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailRow("Habitación", item.room.ifBlank { "—" }, Modifier.weight(1f))
                    DetailRow("Para qué sirve", item.purpose.ifBlank { "—" }, Modifier.weight(1f))
                }
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text("DESCRIPCIÓN", color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                            fontSize = 9.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(item.description, color = colors.ink, fontFamily = Manrope, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.receiptMime != null) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.pine)
                            .clickable(onClick = onReceipt).padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_receipt), contentDescription = null, tint = colors.paper, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Ver ticket", color = colors.paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.cream)
                        .clickable(onClick = onEdit).padding(vertical = 12.dp), horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.pine, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Editar ficha", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedScreen(store: InventoryStore, repo: HouseholdRepository, householdId: String) {
    val colors = LocalPalette.current
    val scope = rememberCoroutineScope()
    val tasksFlow = remember(householdId) { repo.tasksFlow(householdId) }
    val notesFlow = remember(householdId) { repo.notesFlow(householdId) }
    val membersFlow = remember(householdId) { repo.membersFlow(householdId) }
    val tasks by tasksFlow.collectAsState(initial = emptyList())
    val notes by notesFlow.collectAsState(initial = emptyList())
    val members by membersFlow.collectAsState(initial = emptyList())
    val membersById = remember(members) { members.associateBy { it.id } }
    var taskInput by remember { mutableStateOf("") }
    var noteInput by remember { mutableStateOf("") }
    var showNotes by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(false) }
    var taskRecurrence by remember { mutableStateOf<Int?>(null) }
    var taskAssignee by remember { mutableStateOf<Member?>(null) }
    val author = repo.memberName.ifBlank { "Alguien de casa" }
    val ownMember = membersById[repo.memberId]

    LaunchedEffect(tasks) {
        val now = System.currentTimeMillis()
        tasks.filter { it.done && it.recurrenceDays != null && it.doneAt != null &&
            now - it.doneAt >= it.recurrenceDays.toLong() * 86_400_000L }
            .forEach { due -> runCatching { repo.setTaskDone(householdId, due.id, false) } }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BrandHeader()
            Spacer(Modifier.height(30.dp))
            PageLabel("Compartido")
            Spacer(Modifier.height(10.dp))
            Text("Entre los dos.", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 39.sp)
            Text("Tareas y notas al momento en los dos móviles.", color = colors.muted, fontFamily = Manrope, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.paper)
                .clickable { editingProfile = true }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(38.dp).clip(CircleShape).background(colors.mint)) {
                    PhotoAvatar(author, ownMember?.let(store::memberPhotoUri), Modifier.fillMaxSize(), colors.pineLight, 14.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(author, color = colors.ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Tu perfil · toca para cambiar tu foto", color = colors.muted, fontFamily = Manrope, fontSize = 10.sp)
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth().background(colors.paper, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Tareas" to false, "Notas" to true).forEach { (label, value) ->
                    val selected = showNotes == value
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (selected) colors.pine else Color.Transparent)
                        .clickable { showNotes = value }.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = if (selected) colors.onPine else colors.muted, fontFamily = Manrope,
                            fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            if (!showNotes) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(null to "No repetir", 1 to "↻ Diaria", 7 to "↻ Semanal", 30 to "↻ Mensual").forEach { (days, label) ->
                        val selected = taskRecurrence == days
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) colors.pine else colors.paper)
                            .clickable { taskRecurrence = days }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                            Text(label, color = if (selected) colors.onPine else colors.muted, fontFamily = Manrope,
                                fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (members.size > 1) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (listOf<Member?>(null) + members).forEach { m ->
                            val selected = taskAssignee?.id == m?.id
                            Box(Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) colors.pine else colors.paper)
                                .clickable { taskAssignee = m }.padding(horizontal = 12.dp, vertical = 7.dp)) {
                                Text(m?.name?.let { "Para: $it" } ?: "Para cualquiera",
                                    color = if (selected) colors.onPine else colors.muted, fontFamily = Manrope,
                                    fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 48.dp).background(colors.paper, RoundedCornerShape(14.dp))
                    .padding(horizontal = 15.dp, vertical = 12.dp), contentAlignment = Alignment.CenterStart) {
                    BasicTextField(if (!showNotes) taskInput else noteInput,
                        onValueChange = { if (!showNotes) taskInput = it else noteInput = it }, singleLine = !showNotes,
                        textStyle = androidx.compose.ui.text.TextStyle(color = colors.ink, fontFamily = Manrope, fontSize = 12.sp),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if ((if (!showNotes) taskInput else noteInput).isEmpty())
                                Text(if (!showNotes) "Añadir una tarea" else "Escribir una nota",
                                    color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
                            inner()
                        })
                }
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(colors.pine)
                    .clickable {
                        val text = (if (!showNotes) taskInput else noteInput).trim()
                        if (text.isBlank()) return@clickable
                        scope.launch {
                            runCatching {
                                if (!showNotes) repo.addTask(householdId, text, author, taskRecurrence,
                                    taskAssignee?.name ?: "", taskAssignee?.id ?: "")
                                else repo.addNote(householdId, text, author)
                            }
                        }
                        if (!showNotes) { taskInput = ""; taskRecurrence = null; taskAssignee = null } else noteInput = ""
                    }, contentAlignment = Alignment.Center) {
                    Text("＋", color = colors.onPine, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        if (!showNotes) {
            if (tasks.isEmpty()) {
                item { EmptySharedState("Sin tareas por ahora", "Añadid lo primero que se os ocurra para la casa.") }
            } else {
                items(tasks, key = { "task-" + it.id }) { task ->
                    TaskRow(task, membersById[task.createdById]?.let(store::memberPhotoUri),
                        onToggle = { scope.launch { runCatching { repo.setTaskDone(householdId, task.id, !task.done) } } },
                        onDelete = { scope.launch { runCatching { repo.deleteTask(householdId, task.id) } } })
                }
            }
        } else {
            if (notes.isEmpty()) {
                item { EmptySharedState("Sin notas todavía", "Dejad aquí lo que queráis recordar entre los dos.") }
            } else {
                items(notes, key = { "note-" + it.id }) { note ->
                    NoteRow(note, membersById[note.authorId]?.let(store::memberPhotoUri),
                        onDelete = { scope.launch { runCatching { repo.deleteNote(householdId, note.id) } } })
                }
            }
        }
    }
    if (editingProfile) {
        ProfileSheet(initialName = author, initialPhoto = ownMember?.let(store::memberPhotoUri),
            onDismiss = { editingProfile = false },
            onSave = { newName, newPhoto ->
                editingProfile = false
                scope.launch { runCatching { repo.saveMemberProfile(householdId, newName, newPhoto) } }
            })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSheet(initialName: String, initialPhoto: Uri?,
    onDismiss: () -> Unit, onSave: (String, Uri?) -> Unit) {
    val colors = LocalPalette.current
    var name by remember { mutableStateOf(initialName) }
    var photo by remember { mutableStateOf(initialPhoto) }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.cream) {
        Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tu perfil", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            ProfilePhotoPicker(name, photo, onPick = { photoPicker.launch("image/*") })
            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Tu nombre") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(enabled = name.isNotBlank(), onClick = { onSave(name.trim(), photo) },
                colors = ButtonDefaults.buttonColors(containerColor = colors.pine), modifier = Modifier.fillMaxWidth()) {
                Text("Guardar", fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun EmptySharedState(title: String, subtitle: String) {
    val colors = LocalPalette.current
    Surface(color = colors.paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(subtitle, color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
        }
    }
}

private fun recurrenceLabel(days: Int?): String? = when (days) {
    null -> null
    1 -> "↻ Diaria"
    7 -> "↻ Semanal"
    30 -> "↻ Mensual"
    else -> "↻ cada ${days}d"
}

@Composable
private fun TaskRow(task: TaskItem, authorPhoto: Uri?, onToggle: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalPalette.current
    Surface(color = colors.paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(24.dp).clip(CircleShape).background(if (task.done) colors.pine else colors.cream)
                .clickable(onClick = onToggle), contentAlignment = Alignment.Center) {
                if (task.done) Text("✓", color = colors.onPine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
            Column(Modifier.weight(1f)) {
                Text(task.text, color = if (task.done) colors.muted else colors.ink, fontFamily = Manrope,
                    fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    textDecoration = if (task.done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null)
                if (task.createdBy.isNotBlank()) Text(task.createdBy, color = colors.muted, fontFamily = Manrope, fontSize = 10.sp)
                if (task.assignedTo.isNotBlank()) Text("Para: ${task.assignedTo}", color = colors.pineLight,
                    fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                recurrenceLabel(task.recurrenceDays)?.let { Text(it, color = colors.pineLight, fontFamily = Manrope, fontSize = 10.sp) }
            }
            if (task.createdBy.isNotBlank()) {
                Box(Modifier.size(26.dp).clip(CircleShape).background(colors.mint)) {
                    PhotoAvatar(task.createdBy, authorPhoto, Modifier.fillMaxSize(), colors.pineLight, 10.sp)
                }
            }
            Icon(painterResource(R.drawable.ic_trash), contentDescription = "Eliminar", tint = colors.muted,
                modifier = Modifier.size(16.dp).clickable(onClick = onDelete))
        }
    }
}

@Composable
private fun NoteRow(note: NoteItem, authorPhoto: Uri?, onDelete: () -> Unit) {
    val colors = LocalPalette.current
    Surface(color = colors.paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(note.text, color = colors.ink, fontFamily = Manrope, fontSize = 13.sp, lineHeight = 19.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(20.dp).clip(CircleShape).background(colors.mint)) {
                        PhotoAvatar(note.author.ifBlank { "?" }, authorPhoto, Modifier.fillMaxSize(), colors.pineLight, 9.sp)
                    }
                    Text(note.author.ifBlank { "Anónimo" }, color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                Icon(painterResource(R.drawable.ic_trash), contentDescription = "Eliminar", tint = colors.muted,
                    modifier = Modifier.size(14.dp).clickable(onClick = onDelete))
            }
        }
    }
}

// Validated categorical chart palette (dataviz skill's default ramp) — the app's own earthy accent
// colors are too desaturated/close together to pass as chart-series colors, so charts borrow this
// instead, while every other chart element (card, labels, chrome) stays in the app's own palette.
private val ChartSeriesLight = listOf(0xFF2A78D6, 0xFFEB6834, 0xFF1BAF7A, 0xFFEDA100, 0xFFE87BA4).map { Color(it) }
private val ChartSeriesDark = listOf(0xFF3987E5, 0xFFD95926, 0xFF199E70, 0xFFC98500, 0xFFD55181).map { Color(it) }

/** Ranked horizontal-bar breakdown of spend by an item field (category, room, ...); hidden when there's
 * only one group (a one-bar chart isn't a chart — the hero total above already says that number). */
@Composable
private fun SpendBreakdownChart(title: String, items: List<InventoryItem>, groupKey: (InventoryItem) -> String) {
    val colors = LocalPalette.current
    val palette = if (colors === DarkPalette) ChartSeriesDark else ChartSeriesLight
    val grouped = items.groupBy { groupKey(it).ifBlank { "Sin especificar" } }
        .mapValues { (_, list) -> list.sumOf { it.priceCents ?: 0L } }
        .filterValues { it > 0 }
    if (grouped.size < 2) return
    val sorted = grouped.entries.sortedByDescending { it.value }.map { it.key to it.value }
    val rest = sorted.drop(5).sumOf { it.second }
    val rows = if (rest > 0) sorted.take(5) + ("Otros" to rest) else sorted
    val maxValue = rows.maxOf { it.second }.coerceAtLeast(1)

    Spacer(Modifier.height(18.dp))
    Text(title.uppercase(), color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp)
    Spacer(Modifier.height(10.dp))
    Surface(color = colors.paper, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 6.dp, horizontal = 16.dp)) {
            rows.forEachIndexed { index, (label, value) ->
                val barColor = if (label == "Otros") colors.muted else palette[index % palette.size]
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = colors.ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(78.dp))
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(18.dp)) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth((value.toFloat() / maxValue).coerceIn(0.04f, 1f))
                            .clip(RoundedCornerShape(topEnd = 5.dp, bottomEnd = 5.dp)).background(barColor))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(formatMoney(value), color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, maxLines = 1, modifier = Modifier.width(64.dp))
                }
            }
        }
    }
}

@Composable
private fun ExpenseScreen(items: List<InventoryItem>) {
    val colors = LocalPalette.current
    val total = items.sumOf { it.priceCents ?: 0L }
    val attributed = items.filter { it.paidBy.isNotBlank() }
    val byPayer = attributed.groupBy { it.paidBy }.mapValues { (_, list) -> list.sumOf { it.priceCents ?: 0L } }
    val totalAttributed = byPayer.values.sum()
    val fairShare = if (byPayer.isNotEmpty()) totalAttributed / byPayer.size else 0L
    val unassignedCount = items.size - attributed.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp)) {
        BrandHeader(); Spacer(Modifier.height(30.dp)); PageLabel("Gastos"); Spacer(Modifier.height(10.dp))
        Text("Cada compra cuenta.", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 35.sp)
        Spacer(Modifier.height(20.dp))
        Surface(color = colors.pine, shape = RoundedCornerShape(25.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(23.dp)) {
                Text("TOTAL INVERTIDO", color = colors.mint, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, letterSpacing = 1.sp)
                Text(formatMoney(total), color = colors.paper, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 38.sp)
                Text("${items.size} compras registradas", color = colors.mint, fontFamily = Manrope, fontSize = 12.sp)
            }
        }
        SpendBreakdownChart("Por categoría", items) { it.category }
        SpendBreakdownChart("Por habitación", items) { it.room }
        Spacer(Modifier.height(18.dp))
        if (byPayer.isEmpty()) {
            Text("Cuando registréis quién paga cada compra, aquí veréis el reparto entre los dos.",
                color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
        } else {
            PageLabel("Quién ha pagado")
            Spacer(Modifier.height(10.dp))
            byPayer.entries.sortedByDescending { it.value }.forEach { (name, paid) ->
                val balance = paid - fairShare
                Surface(color = colors.paper, shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text(name, color = colors.ink, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Ha pagado ${formatMoney(paid)}", color = colors.muted, fontFamily = Manrope, fontSize = 11.sp)
                        }
                        if (byPayer.size > 1) {
                            Text(
                                when {
                                    balance > 0 -> "+${formatMoney(balance)}"
                                    balance < 0 -> "-${formatMoney(-balance)}"
                                    else -> "Al día"
                                },
                                color = if (balance > 0) colors.pine else if (balance < 0) colors.danger else colors.muted,
                                fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
            if (byPayer.size == 2) {
                val sorted = byPayer.entries.sortedByDescending { it.value }.toList()
                val diff = sorted[0].value - sorted[1].value
                Spacer(Modifier.height(4.dp))
                if (diff > 0) {
                    Surface(color = colors.pine, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                        Text("${sorted[1].key} le debe ${formatMoney(diff / 2)} a ${sorted[0].key}",
                            color = colors.paper, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.padding(14.dp))
                    }
                } else {
                    Text("Vais igualados, ¡bien repartido!", color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
                }
            }
        }
        if (unassignedCount > 0) {
            Spacer(Modifier.height(10.dp))
            Text("$unassignedCount compra(s) sin persona asignada (de antes de esta actualización).",
                color = colors.muted, fontFamily = Manrope, fontSize = 11.sp)
        }
    }
}

@Composable
private fun BottomBar(selected: Tab, onTab: (Tab) -> Unit) {
    val colors = LocalPalette.current
    Row(Modifier.fillMaxWidth().background(colors.paper).padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly) {
        Tab.entries.forEach { tab ->
            Column(Modifier.weight(1f).clickable { onTab(tab) }, horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(width = 38.dp, height = 29.dp)
                    .background(if (selected == tab) colors.mint else Color.Transparent, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(tab.icon), contentDescription = null,
                        tint = if (selected == tab) colors.pine else colors.muted, modifier = Modifier.size(21.dp))
                }
                Text(tab.title, color = if (selected == tab) colors.pine else colors.muted, fontFamily = Manrope,
                    fontWeight = FontWeight.Bold, fontSize = 8.5.sp, lineHeight = 10.sp, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ItemSheet(item: InventoryItem?, purchase: Boolean, store: InventoryStore, rooms: List<Room>, categories: List<String>,
    initialRoomId: String, onDismiss: () -> Unit, onSave: (InventoryItem, Uri?, Uri?) -> Unit, onDelete: (InventoryItem) -> Unit,
    onCreateRoom: (Room) -> Unit) {
    val colors = LocalPalette.current
    var name by remember(item?.id, purchase) { mutableStateOf(item?.name ?: "") }
    var roomId by remember(item?.id, purchase) { mutableStateOf(item?.effectiveRoomId() ?: initialRoomId) }
    var category by remember(item?.id, purchase) { mutableStateOf(item?.category ?: "") }
    var price by remember(item?.id, purchase) { mutableStateOf(item?.priceCents?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "") }
    var shop by remember(item?.id, purchase) { mutableStateOf(item?.shop ?: "") }
    var description by remember(item?.id, purchase) { mutableStateOf(item?.description ?: "") }
    var location by remember(item?.id, purchase) { mutableStateOf(item?.location ?: "") }
    var purpose by remember(item?.id, purchase) { mutableStateOf(item?.purpose ?: "") }
    var date by remember(item?.id, purchase) { mutableStateOf(item?.purchaseDate ?: LocalDate.now().toString()) }
    var receipt by remember(item?.id, purchase) { mutableStateOf<Uri?>(null) }
    var photo by remember(item?.id, purchase) { mutableStateOf<Uri?>(null) }
    var error by remember(item?.id, purchase) { mutableStateOf("") }
    var saving by remember(item?.id, purchase) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Habitaciones creadas desde aquí: aún pueden no haber llegado de Firestore cuando se pulsa Guardar.
    var createdRooms by remember { mutableStateOf(listOf<Room>()) }
    val allRooms = rooms + createdRooms.filter { created -> rooms.none { it.id == created.id } }
    // Se guarda con rememberSaveable: si Android cierra la app mientras la cámara está abierta, no se pierde el destino.
    var cameraTarget by rememberSaveable { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { if (it != null) receipt = it }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { if (it != null) photo = it }
    val receiptCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) receipt = cameraTarget }
    val photoCamera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) photo = cameraTarget }
    val existingPhoto = remember(item?.id, purchase) { item?.let { store.photoUri(it) } }
    val categorySuggestions = remember(category, categories) {
        val key = normalizeKey(category)
        categories.filter { normalizeKey(it) != key && (key.isEmpty() || normalizeKey(it).contains(key)) }.take(10)
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.paper) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (purchase) "Registrar compra" else if (item == null) "Añadir pendiente" else "Editar pendiente", color = colors.ink,
                fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text(if (purchase) "Artículo, precio, fecha y ticket. Los detalles pueden esperar."
                else "Añadidlo ahora y completad la ficha después.", color = colors.muted,
                fontFamily = Manrope, fontSize = 12.sp)
            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Artículo") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            RoomSelector(allRooms, roomId, onSelect = { roomId = it }, onCreateRoom = { room ->
                createdRooms = createdRooms + room
                roomId = room.id
                onCreateRoom(room)
            })
            OutlinedTextField(category, onValueChange = { category = it }, label = { Text("Categoría (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
            if (categorySuggestions.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    categorySuggestions.forEach { suggestion ->
                        FilterPill(suggestion, null, null, false, idleColor = colors.cream) { category = suggestion }
                    }
                }
            }
            PhotoPicker(photo ?: existingPhoto, onPick = { photoPicker.launch("image/*") }, onCamera = {
                val uri = store.newCameraUri()
                cameraTarget = uri
                photoCamera.launch(uri)
            })
            OutlinedTextField(price, onValueChange = { price = it },
                label = { Text(if (purchase) "Precio (€)" else "Precio estimado (€, opcional)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            if (purchase) {
                val current = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                TextButton(onClick = {
                    DatePickerDialog(context, { _, year, month, day ->
                        date = "%04d-%02d-%02d".format(year, month + 1, day)
                    }, current.year, current.monthValue - 1, current.dayOfMonth).show()
                }) { Text("Fecha: $date", color = colors.pine, fontFamily = Manrope) }
                Text("TICKET", color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, letterSpacing = 1.2.sp)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SoftButton("📷  Hacer foto", Modifier.weight(1f)) {
                        val uri = store.newCameraUri()
                        cameraTarget = uri
                        receiptCamera.launch(uri)
                    }
                    SoftButton("📎  Adjuntar archivo", Modifier.weight(1f)) { picker.launch(arrayOf("image/*", "application/pdf")) }
                }
                Text(when {
                    receipt != null -> "Ticket nuevo listo para guardar ✓"
                    item?.receiptMime != null -> "Ya tiene ticket. Elige otro si quieres cambiarlo."
                    else -> "Foto o PDF de hasta ${InventoryStore.RECEIPT_PDF_MAX_BYTES / 1_000_000} MB."
                }, color = if (receipt != null) colors.pine else colors.muted, fontFamily = Manrope, fontSize = 11.sp)
                OutlinedTextField(shop, onValueChange = { shop = it }, label = { Text("Tienda (opcional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(location, onValueChange = { location = it }, label = { Text("Dónde está exactamente (opcional)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(purpose, onValueChange = { purpose = it }, label = { Text("Para qué sirve (opcional)") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(description, onValueChange = { description = it }, label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth())
            }
            if (error.isNotBlank()) Text(error, color = colors.danger, fontFamily = Manrope, fontSize = 11.sp)
            Button(enabled = !saving, onClick = {
                if (name.isBlank()) { error = "Escribe el nombre del artículo"; return@Button }
                val cents = parseMoney(price)
                if (purchase && cents == null) { error = "Indica un precio válido"; return@Button }
                if (!purchase && price.isNotBlank() && cents == null) { error = "El precio estimado no es válido"; return@Button }
                // Si la categoría ya existe con otra escritura ("cocina" / "Cocina"), se usa la que ya había.
                val cleanCategory = categories.firstOrNull { normalizeKey(it) == normalizeKey(category) } ?: category.trim()
                val room = allRooms.firstOrNull { it.id == roomId }
                saving = true
                val value = (item ?: InventoryItem(name = name.trim())).copy(
                    name = name.trim(), roomId = room?.id ?: "", room = room?.name ?: "", category = cleanCategory,
                    status = if (purchase) ItemStatus.PURCHASED else ItemStatus.PLANNED,
                    priceCents = cents, purchaseDate = if (purchase) date else null,
                    shop = shop.trim(), description = description.trim(), location = location.trim(), purpose = purpose.trim(),
                )
                onSave(value, photo, receipt)
            }, colors = ButtonDefaults.buttonColors(containerColor = colors.pine), modifier = Modifier.fillMaxWidth()) {
                Text(if (purchase) "Guardar compra" else if (item == null) "Añadir a pendientes" else "Guardar cambios",
                    fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
            if (item != null) {
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Eliminar artículo", color = colors.danger, fontFamily = Manrope, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    if (confirmDelete && item != null) {
        ConfirmDeleteDialog(
            title = "¿Eliminar «${item.name}»?",
            message = "Se borrará también del móvil de la otra persona, junto con su foto y su ticket.",
            onConfirm = { confirmDelete = false; onDelete(item) },
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun rememberThumbnail(uri: Uri?, sample: Int = 4): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, uri, sample) {
        value = if (uri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    val options = BitmapFactory.Options().apply { inSampleSize = sample }
                    BitmapFactory.decodeStream(input, null, options)
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

/** Full-screen, pinch-to-zoom viewer for an item's photo or ticket, dismissed by the X or tapping the backdrop. */
@Composable
private fun FullScreenImageViewer(uri: Uri, onDismiss: () -> Unit) {
    val bitmap = rememberThumbnail(uri, sample = 2)
    var scale by remember(uri) { mutableStateOf(1f) }
    var offset by remember(uri) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.95f))
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, onClick = onDismiss)) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                        .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y)
                        .pointerInput(uri) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offset += pan
                            }
                        })
            }
            Box(Modifier.align(Alignment.TopEnd).padding(20.dp).size(38.dp).clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                Text("✕", color = Color.White, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun PhotoAvatar(name: String, photoUri: Uri?, modifier: Modifier, letterColor: Color, letterSize: TextUnit) {
    val bitmap = rememberThumbnail(photoUri)
    Box(modifier, contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(name.take(1).uppercase(), fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = letterSize, color = letterColor)
        }
    }
}

@Composable
private fun ProfilePhotoPicker(name: String, source: Uri?, onPick: () -> Unit) {
    val colors = LocalPalette.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onPick), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(colors.mint)) {
            PhotoAvatar(name.ifBlank { "?" }, source, Modifier.fillMaxSize(), colors.pineLight, 20.sp)
        }
        Text(if (source != null) "Cambiar tu foto" else "Añadir tu foto (opcional)",
            color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun PhotoPicker(source: Uri?, onPick: () -> Unit, onCamera: (() -> Unit)? = null) {
    val colors = LocalPalette.current
    val bitmap = rememberThumbnail(source)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.cream)
        .clickable(onClick = onPick).padding(12.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(11.dp)).background(colors.mint), contentAlignment = Alignment.Center) {
            if (bitmap != null) {
                Image(bitmap = bitmap, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Icon(painterResource(R.drawable.ic_photo), contentDescription = null, tint = colors.pineLight, modifier = Modifier.size(19.dp))
            }
        }
        Text(if (bitmap != null) "Cambiar foto del artículo" else "Añadir foto del artículo (opcional)",
            color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.weight(1f))
        if (onCamera != null) {
            Box(Modifier.size(42.dp).clip(RoundedCornerShape(11.dp)).background(colors.paper).clickable(onClick = onCamera),
                contentAlignment = Alignment.Center) {
                Text("📷", fontSize = 18.sp)
            }
        }
    }
}

private fun openUrl(context: Context, url: String) {
    val normalized = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(normalized))) }
}

private fun openMap(context: Context, location: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(location)))) }
}

@Composable
private fun WishlistScreen(items: List<WishlistItem>, store: InventoryStore, onAdd: () -> Unit,
    onEdit: (WishlistItem) -> Unit, onDelete: (WishlistItem) -> Unit, onMoveToPending: (WishlistItem) -> Unit) {
    val colors = LocalPalette.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BrandHeader()
            Spacer(Modifier.height(30.dp))
            PageLabel("Deseados")
            Spacer(Modifier.height(10.dp))
            Text("Nos ha gustado.", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 39.sp)
            Text("Cosas que habéis visto por ahí y queréis recordar para más adelante.", color = colors.muted,
                fontFamily = Manrope, fontSize = 13.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Text("Guardados", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                    fontSize = 21.sp, letterSpacing = (-1).sp)
                Text("${items.size} artículos", color = colors.muted, fontFamily = Manrope, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(13.dp))
        }
        if (items.isEmpty()) {
            item {
                Surface(color = colors.paper, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text("Aún no hay nada guardado", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Guardad aquí lo que os guste de alguna tienda o web, con el enlace y dónde encontrarlo, para decidirlo con calma.",
                            color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(items, key = { it.id }) { item ->
                WishlistItemCard(item, store, onEdit = { onEdit(item) }, onDelete = { onDelete(item) },
                    onMoveToPending = { onMoveToPending(item) })
            }
        }
        item {
            Spacer(Modifier.height(3.dp))
            Box(Modifier.fillMaxWidth().background(colors.mint, RoundedCornerShape(16.dp))
                .clickable(onClick = onAdd).padding(15.dp), contentAlignment = Alignment.Center) {
                Text("＋  Añadir artículo deseado", color = colors.pine, fontFamily = Manrope,
                    fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun WishlistItemCard(item: WishlistItem, store: InventoryStore, onEdit: () -> Unit,
    onDelete: () -> Unit, onMoveToPending: () -> Unit) {
    val colors = LocalPalette.current
    val context = LocalContext.current
    var expanded by remember(item.id) { mutableStateOf(false) }
    var confirmDelete by remember(item.id) { mutableStateOf(false) }
    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    if (confirmDelete) {
        ConfirmDeleteDialog(title = "¿Quitar «${item.name}» de deseados?", message = "Se quitará también del móvil de la otra persona.",
            onConfirm = { confirmDelete = false; onDelete() }, onDismiss = { confirmDelete = false })
    }

    Surface(color = colors.paper, shape = RoundedCornerShape(19.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.animateContentSize().clickable { expanded = !expanded }.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                PhotoAvatar(item.name, store.wishlistPhotoUri(item),
                    Modifier.size(50.dp).clip(RoundedCornerShape(13.dp)).background(colors.sand), colors.pineLight, 22.sp)
                Column(Modifier.weight(1f)) {
                    Text(item.shop.ifBlank { "SIN TIENDA" }.uppercase(), color = colors.pineLight, fontFamily = Manrope,
                        fontWeight = FontWeight.Bold, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(item.name, color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold,
                        fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.priceCents != null) Text(formatMoney(item.priceCents), color = colors.pine, fontFamily = Syne,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Icon(painterResource(R.drawable.ic_chevron_down), contentDescription = null, tint = colors.muted,
                    modifier = Modifier.size(16.dp).rotate(rotation))
            }
            if (expanded) {
                Spacer(Modifier.height(14.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    DetailRow("Ubicación", item.location.ifBlank { "—" }, Modifier.weight(1f))
                    DetailRow("Añadido por", item.addedBy.ifBlank { "—" }, Modifier.weight(1f))
                }
                if (item.notes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth()) {
                        Text("NOTAS", color = colors.muted, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                            fontSize = 9.sp, letterSpacing = 1.sp)
                        Spacer(Modifier.height(3.dp))
                        Text(item.notes, color = colors.ink, fontFamily = Manrope, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(15.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (item.url.isNotBlank()) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.pine)
                            .clickable { openUrl(context, item.url) }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_link), contentDescription = null, tint = colors.onPine, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Ver producto", color = colors.onPine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                    if (item.location.isNotBlank()) {
                        Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.cream)
                            .clickable { openMap(context, item.location) }.padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_map_pin), contentDescription = null, tint = colors.pine, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(7.dp))
                            Text("Ver mapa", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.cream)
                        .clickable(onClick = onEdit).padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = null, tint = colors.pine, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("Editar", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Row(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(colors.mint)
                        .clickable(onClick = onMoveToPending).padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text("A pendientes", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    Box(Modifier.clip(RoundedCornerShape(12.dp)).background(colors.cream)
                        .clickable { confirmDelete = true }.padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Icon(painterResource(R.drawable.ic_trash), contentDescription = "Eliminar", tint = colors.danger, modifier = Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun WishlistSheet(item: WishlistItem?, store: InventoryStore, onDismiss: () -> Unit, onSave: (WishlistItem, Uri?) -> Unit) {
    val colors = LocalPalette.current
    var name by remember(item?.id) { mutableStateOf(item?.name ?: "") }
    var url by remember(item?.id) { mutableStateOf(item?.url ?: "") }
    var shop by remember(item?.id) { mutableStateOf(item?.shop ?: "") }
    var location by remember(item?.id) { mutableStateOf(item?.location ?: "") }
    var price by remember(item?.id) { mutableStateOf(item?.priceCents?.let { BigDecimal(it).movePointLeft(2).toPlainString() } ?: "") }
    var notes by remember(item?.id) { mutableStateOf(item?.notes ?: "") }
    var addedBy by remember(item?.id) { mutableStateOf(item?.addedBy ?: "") }
    var photo by remember(item?.id) { mutableStateOf<Uri?>(null) }
    var error by remember(item?.id) { mutableStateOf("") }
    var saving by remember(item?.id) { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { photo = it }
    val existingPhoto = remember(item?.id) { item?.let { store.wishlistPhotoUri(it) } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.paper) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (item == null) "Añadir artículo deseado" else "Editar deseado", color = colors.ink,
                fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 26.sp)
            Text("Algo que os ha gustado a los dos, para decidirlo con calma.", color = colors.muted,
                fontFamily = Manrope, fontSize = 12.sp)
            PhotoPicker(photo ?: existingPhoto, onPick = { picker.launch("image/*") })
            OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Artículo") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, onValueChange = { url = it }, label = { Text("Enlace del producto (opcional)") },
                singleLine = true, keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth())
            OutlinedTextField(shop, onValueChange = { shop = it }, label = { Text("Tienda o web (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, onValueChange = { location = it }, label = { Text("Ubicación o dirección (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(price, onValueChange = { price = it }, label = { Text("Precio aproximado (€, opcional)") },
                keyboardOptions = KeyboardOptions.Default.copy(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(addedBy, onValueChange = { addedBy = it }, label = { Text("Añadido por (opcional)") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(notes, onValueChange = { notes = it }, label = { Text("Notas (opcional)") },
                modifier = Modifier.fillMaxWidth())
            if (error.isNotBlank()) Text(error, color = colors.danger, fontFamily = Manrope, fontSize = 11.sp)
            Button(enabled = !saving, onClick = {
                if (name.isBlank()) { error = "Escribe el nombre del artículo"; return@Button }
                saving = true
                val value = (item ?: WishlistItem(name = name.trim())).copy(
                    name = name.trim(), url = url.trim(), shop = shop.trim(), location = location.trim(),
                    priceCents = parseMoney(price), notes = notes.trim(), addedBy = addedBy.trim(),
                )
                onSave(value, photo)
            }, colors = ButtonDefaults.buttonColors(containerColor = colors.pine), modifier = Modifier.fillMaxWidth()) {
                Text(if (item == null) "Añadir a deseados" else "Guardar cambios", fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun parseMoney(raw: String): Long? = runCatching {
    val amount = BigDecimal(raw.trim().replace(',', '.'))
    require(amount >= BigDecimal.ZERO)
    amount.multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
}.getOrNull()

internal fun formatMoney(cents: Long): String = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES"))
    .format(BigDecimal(cents).movePointLeft(2))

private val PreviewRooms = listOf(Room("salon", "Salón", "🛋️", 0), Room("dormitorio", "Dormitorio", "🛏️", 1), Room("cocina", "Cocina", "🍳", 2))

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun PlannedPreview() {
    val store = InventoryStore(LocalContext.current)
    PlannedScreen(listOf(
        InventoryItem(name = "Sofá", roomId = "salon", room = "Salón", category = "Muebles", priceCents = 59900),
        InventoryItem(name = "Lámpara de techo", roomId = "salon", room = "Salón", category = "Iluminación"),
        InventoryItem(name = "Juego de sábanas", roomId = "dormitorio", room = "Dormitorio", category = "Textil", priceCents = 3500),
    ), store, PreviewRooms, recentHome = listOf(
        InventoryItem(name = "Mesa de centro", roomId = "salon", room = "Salón", category = "Muebles",
            status = ItemStatus.PURCHASED, priceCents = 8900, purchaseDate = "2026-09-10"),
    ), onAdd = {}, onQuickAdd = { _, _ -> }, onBuy = {}, onEdit = {}, onOpenHome = {}, onSeeAllHome = {})
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun HomePreview() {
    val store = InventoryStore(LocalContext.current)
    HomeScreen(listOf(
        InventoryItem(name = "Mesa de centro", roomId = "salon", room = "Salón", category = "Muebles", status = ItemStatus.PURCHASED,
            priceCents = 8900, purchaseDate = "2026-09-10", shop = "IKEA", location = "Junto al sofá",
            purpose = "Apoyar cosas y decorar", description = "Mesa redonda de madera clara.", receiptMime = "image/jpeg"),
        InventoryItem(name = "Lámpara de pie", roomId = "dormitorio", room = "Dormitorio", category = "Iluminación",
            status = ItemStatus.PURCHASED, priceCents = 3200, purchaseDate = "2026-09-05", shop = "Leroy Merlin"),
    ), emptyList(), PreviewRooms, store, onAddPurchase = {}, onEdit = {}, onReceipt = {}, onPhoto = {}, onExport = {}, onManageRooms = {})
}

@Preview(showBackground = true, widthDp = 420, heightDp = 900)
@Composable
private fun WishlistPreview() {
    val store = InventoryStore(LocalContext.current)
    WishlistScreen(listOf(
        WishlistItem(name = "Silla de lectura", shop = "Maisons du Monde", location = "Calle Mayor 12, Madrid",
            priceCents = 12900, notes = "En tono verde, para el rincón de la ventana.", addedBy = "Cristian"),
    ), store, onAdd = {}, onEdit = {}, onDelete = {}, onMoveToPending = {})
}


