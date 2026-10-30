package com.cym.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer
import java.util.Locale

/** Habitación del hogar. Son comunes a los dos miembros y se gestionan desde la app para evitar duplicados. */
internal data class Room(val id: String, val name: String, val icon: String = DEFAULT_ROOM_ICON, val order: Int = 0)

internal const val DEFAULT_ROOM_ICON = "🏠"
internal val ROOM_ICONS = listOf("🛋️", "🍳", "🛏️", "🛁", "🚪", "🪴", "🍽️", "🧺", "💻", "🧸", "🚗", "📦", "🏠")
internal val DEFAULT_ROOMS = listOf("Salón" to "🛋️", "Cocina" to "🍳", "Dormitorio" to "🛏️", "Baño" to "🛁", "Entrada" to "🚪", "Terraza" to "🪴")

/** "  Salón " → "salon": para comparar sin tildes, mayúsculas ni espacios. */
internal fun normalizeKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

/** Id estable de una habitación a partir de su nombre ("Salón" → "salon"). Hace idempotente la migración. */
internal fun roomSlug(name: String): String = normalizeKey(name).replace(Regex("[^a-z0-9]+"), "-").trim('-')

/** Habitación del artículo: su roomId o, en artículos antiguos que solo tenían el nombre, el id derivado del nombre. "" = sin habitación. */
internal fun InventoryItem.effectiveRoomId(): String = roomId.ifBlank { if (room.isBlank()) "" else roomSlug(room) }

internal fun findDuplicateRoom(name: String, rooms: List<Room>, exceptId: String? = null): Room? =
    rooms.firstOrNull { it.id != exceptId && normalizeKey(it.name) == normalizeKey(name) }

internal fun buildNewRoom(name: String, icon: String, rooms: List<Room>): Room {
    val base = roomSlug(name).ifBlank { "habitacion" }
    var id = base
    var suffix = 2
    while (rooms.any { it.id == id }) id = "$base-${suffix++}"
    val cleanName = name.trim().replaceFirstChar { it.uppercase() }
    return Room(id, cleanName, icon, (rooms.maxOfOrNull { it.order } ?: 0) + 1)
}

// ---------- Filtros ----------

/** Chips de habitación con contador. Solo muestra las que tienen artículos (y la seleccionada). null = todas, "" = sin habitación. */
@Composable
internal fun RoomFilterChips(rooms: List<Room>, counts: Map<String, Int>, selected: String?, onSelect: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterPill("Todas", null, counts.values.sum(), selected == null) { onSelect(null) }
        rooms.filter { (counts[it.id] ?: 0) > 0 || it.id == selected }.forEach { room ->
            FilterPill(room.name, room.icon, counts[room.id] ?: 0, selected == room.id) {
                onSelect(if (selected == room.id) null else room.id)
            }
        }
        val unassigned = counts[""] ?: 0
        if (unassigned > 0 || selected == "") {
            FilterPill("Sin habitación", null, unassigned, selected == "") { onSelect(if (selected == "") null else "") }
        }
    }
}

// ---------- Selector en el formulario ----------

/** Elegir habitación al añadir o editar un artículo. Pulsar la seleccionada la quita. */
@Composable
internal fun RoomSelector(rooms: List<Room>, selectedId: String, onSelect: (String) -> Unit, onCreateRoom: (Room) -> Unit) {
    val colors = LocalPalette.current
    var creating by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("HABITACIÓN", color = colors.pineLight, fontFamily = Manrope, fontWeight = FontWeight.Bold,
            fontSize = 10.sp, letterSpacing = 1.2.sp)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rooms.forEach { room ->
                FilterPill(room.name, room.icon, null, room.id == selectedId, idleColor = colors.cream) {
                    onSelect(if (room.id == selectedId) "" else room.id)
                }
            }
            FilterPill("＋ Nueva", null, null, false, idleColor = colors.mint) { creating = true }
        }
    }
    if (creating) {
        RoomEditorDialog(initial = null, rooms = rooms, onDismiss = { creating = false }) { name, icon ->
            creating = false
            onCreateRoom(buildNewRoom(name, icon, rooms))
        }
    }
}

/** Crear o editar una habitación. Bloquea nombres repetidos aunque cambien tildes o mayúsculas. */
@Composable
internal fun RoomEditorDialog(initial: Room?, rooms: List<Room>, onDismiss: () -> Unit, onSave: (name: String, icon: String) -> Unit) {
    val colors = LocalPalette.current
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: ROOM_ICONS.first()) }
    val duplicate = if (name.isBlank()) null else findDuplicateRoom(name, rooms, initial?.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nueva habitación" else "Editar habitación", fontFamily = Syne, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, onValueChange = { name = it }, label = { Text("Nombre") }, singleLine = true,
                    isError = duplicate != null, modifier = Modifier.fillMaxWidth(),
                    supportingText = if (duplicate != null) {
                        { Text("Ya existe «${duplicate.name}»") }
                    } else null)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ROOM_ICONS.forEach { emoji ->
                        Box(Modifier.size(42.dp).clip(CircleShape).background(if (emoji == icon) colors.mint else colors.cream)
                            .clickable { icon = emoji }, contentAlignment = Alignment.Center) {
                            Text(emoji, fontSize = 20.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && duplicate == null, onClick = { onSave(name.trim(), icon) }) {
                Text("Guardar", fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = colors.muted, fontFamily = Manrope) } },
    )
}

// ---------- Gestión ----------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RoomsManagerSheet(rooms: List<Room>, items: List<InventoryItem>, onDismiss: () -> Unit,
    onSave: (room: Room, previousName: String?) -> Unit, onDelete: (room: Room, moveTo: Room?) -> Unit) {
    val colors = LocalPalette.current
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Room?>(null) }
    var deleting by remember { mutableStateOf<Room?>(null) }
    val counts = items.groupingBy { it.effectiveRoomId() }.eachCount()

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = colors.paper) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Habitaciones", color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            Text("Son las mismas para los dos. Al añadir algo, elegís una de esta lista.",
                color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            rooms.forEach { room ->
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.cream).padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(room.icon, fontSize = 22.sp)
                    Column(Modifier.weight(1f)) {
                        Text(room.name, color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val count = counts[room.id] ?: 0
                        Text(if (count == 1) "1 artículo" else "$count artículos", color = colors.muted, fontFamily = Manrope, fontSize = 10.sp)
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { editing = room }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_edit), contentDescription = "Editar ${room.name}", tint = colors.pine, modifier = Modifier.size(17.dp))
                    }
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { deleting = room }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_trash), contentDescription = "Eliminar ${room.name}", tint = colors.danger, modifier = Modifier.size(17.dp))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.mint).clickable { creating = true }
                .padding(15.dp), contentAlignment = Alignment.Center) {
                Text("＋  Nueva habitación", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (creating) {
        RoomEditorDialog(initial = null, rooms = rooms, onDismiss = { creating = false }) { name, icon ->
            creating = false
            onSave(buildNewRoom(name, icon, rooms), null)
        }
    }
    editing?.let { room ->
        RoomEditorDialog(initial = room, rooms = rooms, onDismiss = { editing = null }) { name, icon ->
            editing = null
            onSave(room.copy(name = name, icon = icon), room.name)
        }
    }
    deleting?.let { room ->
        DeleteRoomDialog(room, rooms.filter { it.id != room.id }, counts[room.id] ?: 0, onDismiss = { deleting = null }) { moveTo ->
            deleting = null
            onDelete(room, moveTo)
        }
    }
}

/** Al borrar una habitación con artículos, pregunta a cuál pasarlos (así se fusionan "Comedor" y "Salón"). */
@Composable
private fun DeleteRoomDialog(room: Room, others: List<Room>, itemCount: Int, onDismiss: () -> Unit, onConfirm: (Room?) -> Unit) {
    val colors = LocalPalette.current
    var target by remember { mutableStateOf<Room?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Eliminar «${room.name}»", fontFamily = Syne, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (itemCount == 0) {
                    Text("No tiene artículos.", fontFamily = Manrope)
                } else {
                    Text(if (itemCount == 1) "Tiene 1 artículo. ¿A qué habitación lo pasamos?"
                        else "Tiene $itemCount artículos. ¿A qué habitación los pasamos?", fontFamily = Manrope)
                    Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                        (listOf<Room?>(null) + others).forEach { option ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { target = option }.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = target?.id == option?.id, onClick = { target = option })
                                Spacer(Modifier.width(4.dp))
                                Text(option?.let { "${it.icon}  ${it.name}" } ?: "Sin habitación", fontFamily = Manrope)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(target) }) {
                Text("Eliminar", color = colors.danger, fontFamily = Manrope, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar", color = colors.muted, fontFamily = Manrope) } },
    )
}

// ---------- Vista Casa ----------

private data class RoomStats(val id: String, val name: String, val icon: String, val home: Int, val pending: Int, val spentCents: Long)

/**
 * Plano de la casa: cada habitación es una estancia del plano. Las que tienen más cosas ocupan más ancho.
 * Al tocar una, se abre el inventario filtrado por ella.
 */
@Composable
internal fun HouseMap(rooms: List<Room>, purchased: List<InventoryItem>, pending: List<InventoryItem>,
    onOpenRoom: (String) -> Unit, onManageRooms: () -> Unit) {
    val colors = LocalPalette.current
    val homeByRoom = purchased.groupBy { it.effectiveRoomId() }
    val pendingByRoom = pending.groupingBy { it.effectiveRoomId() }.eachCount()
    val stats = rooms.map { room ->
        val home = homeByRoom[room.id].orEmpty()
        RoomStats(room.id, room.name, room.icon, home.size, pendingByRoom[room.id] ?: 0, home.sumOf { it.priceCents ?: 0L })
    }.let { list ->
        val unassignedHome = homeByRoom[""].orEmpty()
        val unassignedPending = pendingByRoom[""] ?: 0
        if (unassignedHome.isEmpty() && unassignedPending == 0) list
        else list + RoomStats("", "Sin habitación", "📦", unassignedHome.size, unassignedPending, unassignedHome.sumOf { it.priceCents ?: 0L })
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (stats.isEmpty()) {
            Text("Aún no hay habitaciones.", color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
        } else {
            Text("Toca una habitación para ver lo que hay dentro.", color = colors.muted, fontFamily = Manrope, fontSize = 12.sp)
            // El borde grueso hace de muros exteriores; los huecos entre estancias, de tabiques.
            Column(Modifier.fillMaxWidth().border(3.dp, colors.pine, RoundedCornerShape(24.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                stats.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { room ->
                            RoomTile(room, Modifier.weight((room.home + room.pending + 3).toFloat())) { onOpenRoom(room.id) }
                        }
                    }
                }
            }
        }
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(colors.paper).clickable(onClick = onManageRooms)
            .padding(14.dp), contentAlignment = Alignment.Center) {
            Text("Gestionar habitaciones", color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun RoomTile(room: RoomStats, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalPalette.current
    val background = when {
        room.home > 0 -> colors.mint
        room.pending > 0 -> colors.paper
        else -> colors.cream
    }
    Column(modifier.height(124.dp).clip(RoundedCornerShape(16.dp)).background(background).clickable(onClick = onClick).padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Text(room.icon, fontSize = 24.sp)
            Spacer(Modifier.weight(1f))
            if (room.pending > 0) {
                // Texto oscuro fijo: el melocotón es igual en modo claro y oscuro.
                Text("${room.pending} por comprar", color = androidx.compose.ui.graphics.Color(0xFF1D2924), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 9.sp,
                    maxLines = 1, modifier = Modifier.clip(CircleShape).background(colors.peach).padding(horizontal = 7.dp, vertical = 3.dp))
            }
        }
        Column {
            Text(room.name, color = colors.ink, fontFamily = Syne, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(when (room.home) { 0 -> "Vacía"; 1 -> "1 cosa en casa"; else -> "${room.home} cosas en casa" },
                color = colors.muted, fontFamily = Manrope, fontSize = 10.sp, maxLines = 1)
            if (room.spentCents > 0) {
                Text(formatMoney(room.spentCents), color = colors.pine, fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        }
    }
}
